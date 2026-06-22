# Plan: Sprint 3 — Estimation Service — Step 5: SAGA Choreography Handler

## Objective
Create the `EstimationSagaConsumer` — the core SAGA state machine that listens for terminal events on `estimation.saga`, transitions estimation status, and handles failure/compensation.

This is the most complex component. It must:
1. Receive `CustomerValidated` / `VehicleValidated` — log progress, persist correlation
2. Receive `CustomerInvalidated` / `VehicleInvalidated` / `CalculationFailed` — transition to `REJECTED`, publish `EstimationFailed`
3. Receive `PremiumCalculated` — transition to `COMPLETED` with premium and details
4. Maintain state machine in DB (status transitions: STARTED → COMPLETED or REJECTED)
5. Use idempotency (DeduplicationStore) to skip duplicate events

## SAGA Event Flow Diagram

```
Estimation Service produces: EstimationRequested
Estimation Service consumes:
  ├── CustomerValidated    → log, no state change yet
  ├── VehicleValidated     → log, no state change yet
  ├── CustomerInvalidated  → REJECTED + publish EstimationFailed
  ├── VehicleInvalidated   → REJECTED + publish EstimationFailed
  ├── CalculationFailed    → REJECTED + publish EstimationFailed
  ├── PremiumCalculated    → COMPLETED (update premium + details)
  └── (EstimationFailed from self on timeout)
```

## Context Files to Read First
1. **`services/insurance-service/src/main/java/.../config/InsuranceSagaConsumer.java`** — The consumer pattern to follow exactly (JsonMapper, EventEnvelope, try/catch, MDC, switch on eventType, DeduplicationStore)
2. **`services/customer-service/src/main/java/.../config/CustomerSagaConsumer.java`** — Simpler consumer example
3. **`common/common-message/src/main/java/.../event/EventEnvelope.java`** — Envelope structure
4. **`common/common-message/src/main/java/.../event/EventConstants.java`** — Event type constants
5. **`common/common-message/src/main/java/.../event/saga/*.java`** — All SAGA event POJOs (CustomerValidatedEvent, CustomerInvalidatedEvent, VehicleValidatedEvent, VehicleInvalidatedEvent, PremiumCalculatedEvent, CalculationFailedEvent, EstimationFailedEvent)
6. **`docs/outlines/03_SAGA_PATTERN.md`** — SAGA flow, event catalog, idempotency rules
7. **`docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md`** — Estimation service SAGA producers/consumers (section 6)

## File to Create

### `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`

```java
package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.*;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class EstimationSagaConsumer {

    private final EstimationRepository estimationRepository;
    private final DeduplicationStore deduplicationStore;
    private final EstimationEventPublisher estimationEventPublisher;

    @Bean
    public Consumer<String> processEstimationSaga(JsonMapper jsonMapper) {
        return message -> {
            EventEnvelope envelope;
            try {
                envelope = jsonMapper.readValue(message, EventEnvelope.class);

                UUID sagaId = envelope.getSagaId();
                UUID traceId = envelope.getTraceId();
                String eventType = envelope.getEventType();

                MDC.put("sagaId", sagaId != null ? sagaId.toString() : "");
                MDC.put("traceId", traceId != null ? traceId.toString() : "");

                log.info("Received SAGA event: {} for sagaId: {}", eventType, sagaId);

                // Handle each event type — note: EstimationService already published
                // EstimationRequested, so we don't consume it here.
                switch (eventType) {
                    case EventConstants.CUSTOMER_VALIDATED ->
                        handleCustomerValidated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.VEHICLE_VALIDATED ->
                        handleVehicleValidated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.CUSTOMER_INVALIDATED ->
                        handleFailed(envelope, sagaId, traceId, "Customer validation failed");
                    case EventConstants.VEHICLE_INVALIDATED ->
                        handleFailed(envelope, sagaId, traceId, "Vehicle validation failed");
                    case EventConstants.CALCULATION_FAILED ->
                        handleFailed(envelope, sagaId, traceId, "Premium calculation failed");
                    case EventConstants.PREMIUM_CALCULATED ->
                        handlePremiumCalculated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.ESTIMATION_FAILED ->
                        handleEstimationFailed(envelope, sagaId);
                    default ->
                        log.warn("Unknown SAGA event type: {}", eventType);
                }
            } catch (Exception e) {
                log.error("Error processing SAGA message: {}", e.getMessage(), e);
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

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        // Convert to typed event for logging
        CustomerValidatedEvent event = jsonMapper.convertValue(
                envelope.getPayload(), CustomerValidatedEvent.class);
        log.info("Customer validated for sagaId={}: customerId={}, {} {}",
                sagaId, event.getCustomerId(), event.getFirstName(), event.getLastName());
    }

    // ---------------------------------------------------------------
    // VehicleValidated — log progress (no state change needed in estimation)
    // ---------------------------------------------------------------
    private void handleVehicleValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        // Convert to typed event for logging
        VehicleValidatedEvent event = jsonMapper.convertValue(
                envelope.getPayload(), VehicleValidatedEvent.class);
        log.info("Vehicle validated for sagaId={}: vehicleId={}, plate={}", sagaId, event.getVehicleId(), event.getPlate());
    }

    // ---------------------------------------------------------------
    // PremiumCalculated — transition estimation to COMPLETED
    // ---------------------------------------------------------------
    private void handlePremiumCalculated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        // Convert payload
        PremiumCalculatedEvent event = jsonMapper.convertValue(
                envelope.getPayload(), PremiumCalculatedEvent.class);

        // Find estimation by sagaId
        estimationRepository.findBySagaId(sagaId).ifPresentOrElse(estimation -> {
            // Transition: STARTED → COMPLETED
            if (estimation.getStatus() != Estimation.Status.STARTED) {
                log.warn("Estimation {} is in status {} — cannot transition to COMPLETED",
                        estimation.getId(), estimation.getStatus());
                return;
            }

            estimation.setStatus(Estimation.Status.COMPLETED);
            estimation.setPremium(event.getPremium());
            if (event.getBreakdown() != null) {
                estimation.setDetails(event.getBreakdown().toString());
            }
            estimationRepository.save(estimation);
            log.info("Estimation {} completed for sagaId={}: premium={}",
                    estimation.getId(), sagaId, event.getPremium());
        }, () -> log.warn("No estimation found for sagaId={}", sagaId));
    }

    // ---------------------------------------------------------------
    // Failed events — transition estimation to REJECTED, publish EstimationFailed
    // ---------------------------------------------------------------
    private void handleFailed(EventEnvelope envelope, UUID sagaId, UUID traceId, String reason) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        log.warn("SAGA failed for sagaId={}: eventType={}, reason={}", sagaId, eventType, reason);

        // Find and reject estimation
        estimationRepository.findBySagaId(sagaId).ifPresentOrElse(estimation -> {
            if (estimation.getStatus() != Estimation.Status.STARTED) {
                log.warn("Estimation {} is in status {} — cannot transition to REJECTED",
                        estimation.getId(), estimation.getStatus());
                return;
            }

            estimation.setStatus(Estimation.Status.REJECTED);
            estimation.setDetails(reason);
            estimationRepository.save(estimation);
            log.info("Estimation {} rejected for sagaId={}: {}", estimation.getId(), sagaId, reason);

            // Publish EstimationFailed for compensation in other services
            estimationEventPublisher.publishEstimationFailed(sagaId, traceId, reason, eventType);
        }, () -> log.warn("No estimation found for sagaId={}", sagaId));
    }

    // ---------------------------------------------------------------
    // EstimationFailed — log only (no reversible action for read-only services)
    // ---------------------------------------------------------------
    private void handleEstimationFailed(EventEnvelope envelope, UUID sagaId) {
        log.warn("Estimation failed for saga: {} — no compensation needed (estimation state updated)", sagaId);
    }
}
```

## How This Works

1. **`@Bean` consumer function** — Spring Cloud Stream binds `processEstimationSaga-in-0` (from `application.yml`) to the `estimation.saga` Kafka topic. The function is called for each message.

2. **JsonMapper** — Jackson 3's `tools.jackson.databind.json.JsonMapper` is auto-configured by Spring Boot 4 and injected as a parameter.

3. **Idempotency** — Every handler checks `DeduplicationStore` before processing, using `(sagaId, eventType)` as the dedup key.

4. **State machine** — Only `PremiumCalculated` (→ COMPLETED) and failure events (→ REJECTED) write to the DB estimation status. `CustomerValidated` and `VehicleValidated` are logged but don't change estimation state — they're intermediate steps.

5. **Compensation** — When any failure event arrives, `EstimationFailed` is published to `estimation.saga` so all participating services can perform their local compensation.

## Verification

```bash
.\gradlew.bat :services:estimation-service:compileJava
```

## Summary
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java` ✅
