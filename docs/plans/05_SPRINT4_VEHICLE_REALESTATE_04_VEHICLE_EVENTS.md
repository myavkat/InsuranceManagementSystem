# Plan: Sprint 4 — Vehicle & RealEstate — Step 4: Vehicle Service Event Integration

## Objective
Create `VehicleEventPublisher` for domain events and `VehicleSagaConsumer` for SAGA participation, then wire the event publisher into `VehicleService` CRUD methods.

## Context Files to Read First
1. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerEventPublisher.java`** — Domain event publisher pattern (MessagePublisher, EventEnvelope, event classes)
2. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerSagaConsumer.java`** — SAGA consumer pattern (TransactionTemplate, tryInsertDedup, outbox save) — THIS IS THE MOST IMPORTANT REFERENCE
3. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/service/CustomerService.java`** — How event publisher is injected and called after save
4. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/MessagePublisher.java`** — StreamBridge wrapper
5. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventConstants.java`** — Topic and event type constants
6. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/VehicleCreatedEvent.java`** — Domain event class
7. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/VehicleUpdatedEvent.java`** — Domain event class
8. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/VehicleDeletedEvent.java`** — Domain event class
9. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/VehicleValidatedEvent.java`** — SAGA event class
10. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/VehicleInvalidatedEvent.java`** — SAGA event class
11. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/entity/OutboxEvent.java`** — OutboxEvent entity
12. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/repository/SagaEventRepository.java`** — tryInsertDedup() method
13. **`services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/Vehicle.java`** — Vehicle entity
14. **`services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/VehicleRepository.java`** — Vehicle repository
15. **`services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java`** — Service to wire into (from Step 3)

## Files to Create

### 1. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/config/VehicleEventPublisher.java`

Follow the EXACT pattern from `CustomerEventPublisher.java`. Use `MessagePublisher.publish()` directly (NOT outbox — domain events use direct publish; only SAGA response events use outbox).

```java
package com.insurancemanagementsystem.vehicle.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.domain.VehicleCreatedEvent;
import com.insurancemanagementsystem.common.event.domain.VehicleDeletedEvent;
import com.insurancemanagementsystem.common.event.domain.VehicleUpdatedEvent;
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class VehicleEventPublisher {

    private final MessagePublisher messagePublisher;

    public void publishVehicleCreated(Vehicle vehicle) {
        VehicleCreatedEvent event = VehicleCreatedEvent.builder()
                .vehicleId(vehicle.getId())
                .plate(vehicle.getPlate())
                .build();

        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.VEHICLE_EVENTS, envelope);
        log.info("Published VehicleCreated event for vehicle id: {}", vehicle.getId());
    }

    public void publishVehicleUpdated(Vehicle vehicle) {
        VehicleUpdatedEvent event = VehicleUpdatedEvent.builder()
                .vehicleId(vehicle.getId())
                .plate(vehicle.getPlate())
                .build();

        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.VEHICLE_EVENTS, envelope);
        log.info("Published VehicleUpdated event for vehicle id: {}", vehicle.getId());
    }

    public void publishVehicleDeleted(Vehicle vehicle) {
        VehicleDeletedEvent event = VehicleDeletedEvent.builder()
                .vehicleId(vehicle.getId())
                .plate(vehicle.getPlate())
                .build();

        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.VEHICLE_EVENTS, envelope);
        log.info("Published VehicleDeleted event for vehicle id: {}", vehicle.getId());
    }
}
```

### 2. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/config/VehicleSagaConsumer.java`

Follow the EXACT pattern from `CustomerSagaConsumer.java`. This is the most critical file — the SAGA consumer pattern MUST be followed precisely.

CRITICAL RULES (from AGENTS.md):
- Use `TransactionTemplate.executeWithoutResult()` — never rely on `@Transactional` alone
- Use `SagaEventRepository.tryInsertDedup()` for idempotency — NEVER `existsBySagaIdAndEventType()` + `save()`
- Response events MUST go through OutboxEvent table, not direct StreamBridge
- Propagate `traceId` from incoming `EventEnvelope`
- JSON serialization via `jsonMapper.writeValueAsString()`
- Handle `EstimationFailed` as log-only (read-only validation has no compensation)
- Top-level try-catch in the Consumer bean lambda

```java
package com.insurancemanagementsystem.vehicle.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.repository.SagaEventRepository;
import com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent;
import com.insurancemanagementsystem.common.event.saga.VehicleInvalidatedEvent;
import com.insurancemanagementsystem.common.event.saga.VehicleValidatedEvent;
import com.insurancemanagementsystem.vehicle.entity.CarBrand;
import com.insurancemanagementsystem.vehicle.entity.CarModel;
import com.insurancemanagementsystem.vehicle.entity.Vehicle;
import com.insurancemanagementsystem.vehicle.repository.CarBrandRepository;
import com.insurancemanagementsystem.vehicle.repository.CarModelRepository;
import com.insurancemanagementsystem.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class VehicleSagaConsumer {

    private final VehicleRepository vehicleRepository;
    private final CarBrandRepository carBrandRepository;
    private final CarModelRepository carModelRepository;
    private final SagaEventRepository sagaEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;

    @Bean
    public Consumer<String> processVehicleSaga(JsonMapper jsonMapperArg) {
        return message -> {
            EventEnvelope envelope;
            try {
                envelope = jsonMapperArg.readValue(message, EventEnvelope.class);

                UUID sagaId = envelope.getSagaId();
                UUID traceId = envelope.getTraceId();
                String eventType = envelope.getEventType();

                MDC.put("sagaId", sagaId != null ? sagaId.toString() : "");
                MDC.put("traceId", traceId != null ? traceId.toString() : "");

                log.info("Received SAGA event: {} for sagaId: {}", eventType, sagaId);

                switch (eventType) {
                    case EventConstants.ESTIMATION_REQUESTED ->
                        handleEstimationRequested(envelope, sagaId, traceId);
                    case EventConstants.ESTIMATION_FAILED ->
                        handleEstimationFailed(envelope);
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

    private void handleEstimationRequested(EventEnvelope envelope, UUID sagaId, UUID traceId) {
        String eventType = envelope.getEventType();

        transactionTemplate.executeWithoutResult(status -> {
            if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
                return;
            }

            EstimationRequestedEvent requestEvent = jsonMapper.convertValue(
                    envelope.getPayload(), EstimationRequestedEvent.class);
            UUID vehicleId = requestEvent.getVehicleId();

            EventEnvelope outcomeEnvelope;
            if (vehicleId != null) {
                Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId);
                if (vehicleOpt.isPresent()) {
                    Vehicle vehicle = vehicleOpt.get();
                    String brandName = carBrandRepository.findById(vehicle.getCarBrandId())
                            .map(CarBrand::getName).orElse(null);
                    String modelName = carModelRepository.findById(vehicle.getCarModelId())
                            .map(CarModel::getName).orElse(null);

                    VehicleValidatedEvent validatedEvent = VehicleValidatedEvent.builder()
                            .vehicleId(vehicleId)
                            .plate(vehicle.getPlate())
                            .brand(brandName)
                            .model(modelName)
                            .build();
                    outcomeEnvelope = validatedEvent.toEnvelope(sagaId, traceId);
                    log.info("Vehicle {} validated for saga: {}", vehicleId, sagaId);
                } else {
                    String reason = "Vehicle not found";
                    VehicleInvalidatedEvent invalidatedEvent = VehicleInvalidatedEvent.builder()
                            .vehicleId(vehicleId)
                            .reason(reason)
                            .build();
                    outcomeEnvelope = invalidatedEvent.toEnvelope(sagaId, traceId);
                    log.warn("Vehicle {} invalidated for saga: {} — {}", vehicleId, sagaId, reason);
                }
            } else {
                // No vehicleId in the estimation request — this estimation doesn't need vehicle validation
                // Still publish a validated event to unblock the saga
                VehicleValidatedEvent validatedEvent = VehicleValidatedEvent.builder()
                        .vehicleId(null)
                        .build();
                outcomeEnvelope = validatedEvent.toEnvelope(sagaId, traceId);
                log.info("No vehicleId in estimation request — publishing empty VehicleValidated for saga: {}", sagaId);
            }

            outboxEventRepository.save(buildOutboxEvent(sagaId, outcomeEnvelope, EventConstants.ESTIMATION_SAGA));
            log.debug("Saved outbox event for sagaId={}, eventType={}", sagaId, outcomeEnvelope.getEventType());
        });
    }

    private void handleEstimationFailed(EventEnvelope envelope) {
        UUID sagaId = envelope.getSagaId();
        String eventType = envelope.getEventType();
        if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
            return;
        }
        log.warn("Estimation failed for saga: {} — no compensation needed (read-only validation)", sagaId);
    }

    private OutboxEvent buildOutboxEvent(UUID sagaId, EventEnvelope envelope, String topic) {
        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox payload for sagaId=" + sagaId, e);
        }
        return OutboxEvent.builder()
                .sagaId(sagaId)
                .topic(topic)
                .payload(payloadJson)
                .status(OutboxEvent.Status.PENDING)
                .build();
    }
}
```

### 3. Modify `VehicleService.java`

Wire in `VehicleEventPublisher` and call it after CRUD operations:

1. Add field: `private final VehicleEventPublisher vehicleEventPublisher;`
2. In `create()` — after `vehicleRepository.save()`, call `vehicleEventPublisher.publishVehicleCreated(saved);`
3. In `update()` — after `vehicleRepository.save()`, call `vehicleEventPublisher.publishVehicleUpdated(saved);`
4. In `delete()` — before `vehicleRepository.delete()`, call `vehicleEventPublisher.publishVehicleDeleted(vehicle);`

## Key Conventions
- Domain events → direct `MessagePublisher.publish()` (like CustomerEventPublisher)
- SAGA response events → outbox pattern (OutboxEvent table, PENDING status) (like CustomerSagaConsumer)
- `TransactionTemplate.executeWithoutResult()` for SAGA handlers
- `SagaEventRepository.tryInsertDedup()` for idempotency
- `traceId` propagated from incoming `EventEnvelope`
- JSON always via `jsonMapper.writeValueAsString()` — never string concatenation
- All CRUD operations within the same `@Transactional` — if event publish fails (MessagePublisher throws), the transaction rolls back
- SAGA consumer: `@Bean public Consumer<String>` bound via Spring Cloud Stream to `processVehicleSaga-in-0`

## Verification

```bash
.\gradlew.bat :services:vehicle-service:compileJava
```

Should compile successfully. Full integration tests are in Step 5.

## Files Written
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/config/VehicleEventPublisher.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/config/VehicleSagaConsumer.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java` ✅ (modified — event publisher wired in)
