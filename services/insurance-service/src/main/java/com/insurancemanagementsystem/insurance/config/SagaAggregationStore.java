package com.insurancemanagementsystem.insurance.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.insurance.entity.SagaAggregation;
import com.insurancemanagementsystem.insurance.repository.SagaAggregationRepository;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * DB-backed saga aggregation store for correlating the three events
 * ({@code ESTIMATION_REQUESTED}, {@code CUSTOMER_VALIDATED},
 * {@code VEHICLE_VALIDATED}) needed for insurance premium calculation.
 * <p>
 * Replaces the former in-memory {@code ConcurrentHashMap} implementation.
 * The underlying {@code saga_aggregations} table provides:
 * <ul>
 *   <li>Crash resilience — state survives service restarts</li>
 *   <li>Multi-instance safety — single DB source of truth</li>
 *   <li>Transactional atomicity — {@link #retrieve(String)} uses
 *       SELECT FOR UPDATE + DELETE within the same transaction as
 *       the outbox save, so a rollback preserves the state for retry</li>
 * </ul>
 */
@Component
@Slf4j
public class SagaAggregationStore {

    private final SagaAggregationRepository repository;
    private final JsonMapper jsonMapper;

    public SagaAggregationStore(SagaAggregationRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        log.info("SagaAggregationStore initialized (DB-backed)");
    }

    /**
     * Store an event payload for a given sagaId.
     * Returns true when all three required events are present (saga is ready for calculation).
     */
    public boolean storeAndCheckReady(String sagaId, String eventType, EventEnvelope envelope) {
        UUID sagaUuid = UUID.fromString(sagaId);
        String payload = serializeEnvelope(sagaUuid, envelope);

        SagaAggregation agg = repository.findById(sagaUuid)
                .orElseGet(() -> SagaAggregation.builder().sagaId(sagaUuid).build());

        switch (eventType) {
            case EventConstants.ESTIMATION_REQUESTED -> agg.setEstimationRequestPayload(payload);
            case EventConstants.CUSTOMER_VALIDATED -> agg.setCustomerValidatedPayload(payload);
            case EventConstants.VEHICLE_VALIDATED -> agg.setVehicleValidatedPayload(payload);
            default -> log.warn("Unknown event type for aggregation: {}", eventType);
        }

        repository.save(agg);

        boolean ready = agg.isComplete();
        if (ready) {
            log.info("SAGA aggregation complete for sagaId={}", sagaId);
        } else {
            log.debug("SAGA state for sagaId={}: hasEstimation={}, hasCustomer={}, hasVehicle={}",
                    sagaId,
                    agg.getEstimationRequestPayload() != null,
                    agg.getCustomerValidatedPayload() != null,
                    agg.getVehicleValidatedPayload() != null);
        }
        return ready;
    }

    /**
     * Peek at aggregation state without consuming it (non-destructive read).
     */
    public SagaState peek(String sagaId) {
        UUID sagaUuid = UUID.fromString(sagaId);
        return repository.findById(sagaUuid)
                .map(this::toState)
                .orElse(null);
    }

    /**
     * Retrieve and consume aggregation state (atomic find-and-delete within the
     * current transaction). If the enclosing transaction rolls back, the DELETE
     * is undone and the state remains available for retry.
     */
    public SagaState retrieve(String sagaId) {
        UUID sagaUuid = UUID.fromString(sagaId);
        return repository.findByIdForUpdate(sagaUuid)
                .map(agg -> {
                    SagaState state = toState(agg);
                    repository.delete(agg);
                    log.debug("Retrieved and removed SAGA state for sagaId={}", sagaId);
                    return state;
                })
                .orElse(null);
    }

    /**
     * Remove saga state (e.g. on invalidation — no calculation needed).
     */
    public void remove(String sagaId) {
        UUID sagaUuid = UUID.fromString(sagaId);
        repository.deleteById(sagaUuid);
        log.debug("Removed SAGA state for sagaId={}", sagaId);
    }

    // ---------------------------------------------------------------
    // Serialization helpers
    // ---------------------------------------------------------------

    private SagaState toState(SagaAggregation agg) {
        SagaState state = new SagaState();
        if (agg.getEstimationRequestPayload() != null) {
            state.setEstimationRequest(deserializeEnvelope(agg.getSagaId(), agg.getEstimationRequestPayload()));
        }
        if (agg.getCustomerValidatedPayload() != null) {
            state.setCustomerValidated(deserializeEnvelope(agg.getSagaId(), agg.getCustomerValidatedPayload()));
        }
        if (agg.getVehicleValidatedPayload() != null) {
            state.setVehicleValidated(deserializeEnvelope(agg.getSagaId(), agg.getVehicleValidatedPayload()));
        }
        return state;
    }

    private String serializeEnvelope(UUID sagaId, EventEnvelope envelope) {
        try {
            return jsonMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize EventEnvelope for sagaId=" + sagaId, e);
        }
    }

    private EventEnvelope deserializeEnvelope(UUID sagaId, String payload) {
        try {
            return jsonMapper.readValue(payload, EventEnvelope.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize EventEnvelope for sagaId=" + sagaId, e);
        }
    }

    // ---------------------------------------------------------------
    // Saga state DTO — three correlated event envelopes
    // ---------------------------------------------------------------

    @Getter
    @Setter
    @ToString
    public static class SagaState {
        private final Instant createdAt = Instant.now();
        private EventEnvelope estimationRequest;
        private EventEnvelope customerValidated;
        private EventEnvelope vehicleValidated;

        public boolean hasEstimationRequest() { return estimationRequest != null; }
        public boolean hasCustomerValidated() { return customerValidated != null; }
        public boolean hasVehicleValidated() { return vehicleValidated != null; }

        public boolean isComplete() {
            return hasEstimationRequest() && hasCustomerValidated() && hasVehicleValidated();
        }
    }
}
