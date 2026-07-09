package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.*;
import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.repository.SagaEventRepository;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class EstimationSagaConsumer {

    private final EstimationRepository estimationRepository;
    private final SagaEventRepository sagaEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventSerializer outboxEventSerializer;
    private final TransactionTemplate transactionTemplate;
    private final ObservationRegistry observationRegistry;

    @Bean
    public Consumer<String> processEstimationSaga(JsonMapper jsonMapper) {
        return message -> {
            // Deserialize — JacksonException (including StreamReadException) is a
            // RuntimeException in Jackson 3, but deserialization failures are
            // poison-pill messages that cannot be fixed by retry.
            EventEnvelope envelope;
            try {
                envelope = jsonMapper.readValue(message, EventEnvelope.class);
            } catch (Exception e) {
                log.error("Failed to deserialize SAGA message — routing to DLQ: {}", e.getMessage(), e);
                throw new RuntimeException("Deserialization failed — routing to DLQ", e);
            }

            try {
                UUID sagaId = envelope.getSagaId();
                UUID traceId = envelope.getTraceId();
                String eventType = envelope.getEventType();

                MDC.put("sagaId", sagaId != null ? sagaId.toString() : "");
                MDC.put("traceId", traceId != null ? traceId.toString() : "");

                log.info("Received SAGA event: {} for sagaId: {}", eventType, sagaId);

                // Handle each event type — note: EstimationService already published
                // EstimationRequested, so we don't consume it here.
                // Wrap business logic in a Micrometer Observation for Zipkin tracing
                Observation observation = Observation.createNotStarted("saga.estimation.process", observationRegistry)
                        .contextualName("process " + eventType)
                        .lowCardinalityKeyValue("event.type", eventType)
                        .highCardinalityKeyValue("saga.id", sagaId != null ? sagaId.toString() : "");

                observation.observe(() -> {
                switch (eventType) {
                    case EventConstants.CUSTOMER_VALIDATED ->
                        handleCustomerValidated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.VEHICLE_VALIDATED ->
                        handleVehicleValidated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.CUSTOMER_INVALIDATED ->
                        handleFailed(envelope, sagaId, traceId, "Customer validation failed", jsonMapper);
                    case EventConstants.VEHICLE_INVALIDATED ->
                        handleFailed(envelope, sagaId, traceId, "Vehicle validation failed", jsonMapper);
                    case EventConstants.REAL_ESTATE_VALIDATED ->
                        handleRealEstateValidated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.REAL_ESTATE_INVALIDATED ->
                        handleFailed(envelope, sagaId, traceId, "Real estate validation failed", jsonMapper);
                    case EventConstants.CALCULATION_FAILED ->
                        handleFailed(envelope, sagaId, traceId, "Premium calculation failed", jsonMapper);
                    case EventConstants.PREMIUM_CALCULATED ->
                        handlePremiumCalculated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.ESTIMATION_FAILED ->
                        handleEstimationFailed(envelope, sagaId);
                    default ->
                        log.warn("Unknown SAGA event type: {}", eventType);
                }
                });
            } catch (Exception e) {
                log.error("Error processing SAGA message: {}", e.getMessage(), e);
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException("Failed to process SAGA message", e);
            } finally {
                MDC.clear();
            }
        };
    }

    // ---------------------------------------------------------------
    // CustomerValidated — log progress (no state change needed in estimation)
    // ---------------------------------------------------------------
    private void handleCustomerValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        transactionTemplate.executeWithoutResult(status -> {
            if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                return;
            }

            // Convert to typed event for logging
            CustomerValidatedEvent event = jsonMapper.convertValue(
                    envelope.getPayload(), CustomerValidatedEvent.class);
            log.info("Customer validated for sagaId={}: customerId={}, {} {}",
                    sagaId, event.getCustomerId(), event.getFirstName(), event.getLastName());
        });
    }

    // ---------------------------------------------------------------
    // VehicleValidated — log progress (no state change needed in estimation)
    // ---------------------------------------------------------------
    private void handleVehicleValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        transactionTemplate.executeWithoutResult(status -> {
            if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                return;
            }

            // Convert to typed event for logging
            VehicleValidatedEvent event = jsonMapper.convertValue(
                    envelope.getPayload(), VehicleValidatedEvent.class);
            log.info("Vehicle validated for sagaId={}: vehicleId={}, plate={}", sagaId, event.getVehicleId(), event.getPlate());
        });
    }

    // ---------------------------------------------------------------
    // RealEstateValidated — log progress (no state change needed in estimation)
    // ---------------------------------------------------------------
    private void handleRealEstateValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        transactionTemplate.executeWithoutResult(status -> {
            if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                return;
            }

            // Convert to typed event for logging
            RealEstateValidatedEvent event = jsonMapper.convertValue(
                    envelope.getPayload(), RealEstateValidatedEvent.class);
            log.info("Real estate validated for sagaId={}: realEstateId={}, address={}",
                    sagaId, event.getRealEstateId(), event.getAddress());
        });
    }

    // ---------------------------------------------------------------
    // PremiumCalculated — transition estimation to WAITING_APPROVAL
    // ---------------------------------------------------------------
    private void handlePremiumCalculated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        transactionTemplate.executeWithoutResult(status -> {
            if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                return;
            }

            // Convert payload
            PremiumCalculatedEvent event = jsonMapper.convertValue(
                    envelope.getPayload(), PremiumCalculatedEvent.class);

            // Find estimation by sagaId
            estimationRepository.findBySagaId(sagaId).ifPresentOrElse(estimation -> {
                // Transition: STARTED → WAITING_APPROVAL
                if (estimation.getStatus() != Estimation.Status.STARTED) {
                    log.warn("Estimation {} is in status {} — cannot transition to WAITING_APPROVAL",
                            estimation.getId(), estimation.getStatus());
                    return;
                }

                estimation.setStatus(Estimation.Status.WAITING_APPROVAL);
                estimation.setPremium(event.getPremium());
                if (event.getBreakdown() != null) {
                    estimation.setDetails(jsonMapper.writeValueAsString(event.getBreakdown()));
                }
                estimationRepository.save(estimation);
                log.info("Estimation {} waiting approval for sagaId={}: premium={}",
                        estimation.getId(), sagaId, event.getPremium());
            }, () -> log.warn("No estimation found for sagaId={}", sagaId));
        });
    }

    // ---------------------------------------------------------------
    // Failed events — transition estimation to REJECTED, publish EstimationFailed
    // ---------------------------------------------------------------
    private void handleFailed(EventEnvelope envelope, UUID sagaId, UUID traceId, String reason, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        transactionTemplate.executeWithoutResult(status -> {
            if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                return;
            }

            log.warn("SAGA failed for sagaId={}: eventType={}, reason={}", sagaId, eventType, reason);

            // Find and reject estimation
            estimationRepository.findBySagaId(sagaId).ifPresentOrElse(estimation -> {
                if (estimation.getStatus() != Estimation.Status.STARTED) {
                    log.warn("Estimation {} is in status {} — cannot transition to REJECTED",
                            estimation.getId(), estimation.getStatus());
                    return;
                }

                // Serialize outbox event FIRST — if serialization fails, exception propagates
                // and the estimation stays STARTED
                OutboxEvent outboxEvent = outboxEventSerializer.buildEstimationFailedOutboxEvent(
                        sagaId, traceId, reason, eventType, EventConstants.ESTIMATION_SAGA);

                estimation.setStatus(Estimation.Status.REJECTED);
                try {
                    estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
                } catch (Exception e) {
                    log.warn("Failed to serialize rejection details for sagaId={} — using safe fallback", sagaId, e);
                    try {
                        // Nested try with a fresh JsonMapper to avoid any poisoned state
                        estimation.setDetails(tools.jackson.databind.json.JsonMapper.builder()
                                .build().writeValueAsString(Map.of("reason", reason)));
                    } catch (Exception ex) {
                        // Absolute last resort — log and set a minimal valid JSON literal
                        log.error("Safe fallback serialization also failed for sagaId={}: {}", sagaId, ex.getMessage(), ex);
                        estimation.setDetails("{\"reason\":\"serialization failed\"}");
                    }
                }
                estimationRepository.save(estimation);
                outboxEventRepository.save(outboxEvent);

                log.info("Estimation {} rejected for sagaId={}: {}", estimation.getId(), sagaId, reason);
            }, () -> log.warn("No estimation found for sagaId={}", sagaId));
        });
    }

    // ---------------------------------------------------------------
    // EstimationFailed — log only (no reversible action for read-only services)
    // ---------------------------------------------------------------
    private void handleEstimationFailed(EventEnvelope envelope, UUID sagaId) {
        String eventType = envelope.getEventType();

        transactionTemplate.executeWithoutResult(status -> {
            if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                return;
            }

            log.warn("Estimation failed for saga: {} — no compensation needed (estimation state updated)", sagaId);
        });
    }
}
