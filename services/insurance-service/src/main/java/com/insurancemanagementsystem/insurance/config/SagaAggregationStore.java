package com.insurancemanagementsystem.insurance.config;

import com.insurancemanagementsystem.common.event.EventEnvelope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SagaAggregationStore {

    private final ConcurrentHashMap<String, SagaState> store = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(10);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "saga-agg-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
        log.info("SagaAggregationStore initialized with TTL={}m", ttl.toMinutes());
    }

    /**
     * Store an event for a given sagaId.
     * Returns true if all required events are now present (saga is ready for calculation).
     */
    public boolean storeAndCheckReady(String sagaId, String eventType, EventEnvelope envelope) {
        SagaState state = store.computeIfAbsent(sagaId, k -> new SagaState());

        switch (eventType) {
            case "EstimationRequested" -> state.setEstimationRequest(envelope);
            case "CustomerValidated" -> state.setCustomerValidated(envelope);
            case "VehicleValidated" -> state.setVehicleValidated(envelope);
        }

        boolean ready = state.isComplete();
        if (ready) {
            log.info("SAGA aggregation complete for sagaId={}", sagaId);
        } else {
            log.debug("SAGA state for sagaId={}: hasEstimation={}, hasCustomer={}, hasVehicle={}",
                    sagaId, state.hasEstimationRequest(), state.hasCustomerValidated(), state.hasVehicleValidated());
        }
        return ready;
    }

    /**
     * Retrieve and remove aggregation state (one-shot consumption — state consumed once).
     */
    public SagaState retrieve(String sagaId) {
        SagaState state = store.remove(sagaId);
        log.debug("Retrieved and removed SAGA state for sagaId={}", sagaId);
        return state;
    }

    /**
     * Remove saga state on invalidation (no calculation needed).
     */
    public void remove(String sagaId) {
        store.remove(sagaId);
        log.debug("Removed SAGA state for sagaId={} (invalidated)", sagaId);
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(ttl);
        int before = store.size();
        store.values().removeIf(state -> state.getCreatedAt().isBefore(cutoff));
        int after = store.size();
        if (before != after) {
            log.debug("SagaAggregationStore cleanup: removed {} entries ({} remaining)", before - after, after);
        }
    }

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
