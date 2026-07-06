# Plan: Sprint 4 — Vehicle & RealEstate — Step 8: RealEstate Service Event Integration

## Objective
Create `RealEstateEventPublisher` for domain events and `RealEstateSagaConsumer` for SAGA participation, then wire the event publisher into `RealEstateService` CRUD methods.

## Prerequisite
Step 1 (Common-Message RealEstate SAGA Events) must be completed first — this step uses `RealEstateValidatedEvent` and `RealEstateInvalidatedEvent`. If those classes don't exist yet, create them as part of this step (see Step 1 plan for exact code).

## Context Files to Read First
1. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerEventPublisher.java`** — Domain event publisher pattern
2. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerSagaConsumer.java`** — SAGA consumer pattern (CRITICAL REFERENCE — follow exactly)
3. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/RealEstateCreatedEvent.java`** — Domain event class
4. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/RealEstateUpdatedEvent.java`** — Domain event class
5. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/RealEstateDeletedEvent.java`** — Domain event class
6. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/RealEstateValidatedEvent.java`** — SAGA event class (created in Step 1)
7. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/RealEstateInvalidatedEvent.java`** — SAGA event class (created in Step 1)
8. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventConstants.java`** — Topic and event type constants
9. **`services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/entity/RealEstate.java`** — RealEstate entity
10. **`services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java`** — Service to wire into (from Step 7)

## Files to Create

### 1. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/config/RealEstateEventPublisher.java`

Follow the EXACT pattern from `CustomerEventPublisher.java`. Uses `MessagePublisher.publish()` directly (NOT outbox — domain events use direct publish).

```java
package com.insurancemanagementsystem.realestate.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.domain.RealEstateCreatedEvent;
import com.insurancemanagementsystem.common.event.domain.RealEstateDeletedEvent;
import com.insurancemanagementsystem.common.event.domain.RealEstateUpdatedEvent;
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.realestate.entity.RealEstate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RealEstateEventPublisher {

    private final MessagePublisher messagePublisher;

    public void publishRealEstateCreated(RealEstate realEstate) {
        RealEstateCreatedEvent event = RealEstateCreatedEvent.builder()
                .realEstateId(realEstate.getId())
                .build();
        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.REALESTATE_EVENTS, envelope);
        log.info("Published RealEstateCreated event for id: {}", realEstate.getId());
    }

    public void publishRealEstateUpdated(RealEstate realEstate) {
        RealEstateUpdatedEvent event = RealEstateUpdatedEvent.builder()
                .realEstateId(realEstate.getId())
                .build();
        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.REALESTATE_EVENTS, envelope);
        log.info("Published RealEstateUpdated event for id: {}", realEstate.getId());
    }

    public void publishRealEstateDeleted(RealEstate realEstate) {
        RealEstateDeletedEvent event = RealEstateDeletedEvent.builder()
                .realEstateId(realEstate.getId())
                .build();
        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.REALESTATE_EVENTS, envelope);
        log.info("Published RealEstateDeleted event for id: {}", realEstate.getId());
    }
}
```

### 2. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/config/RealEstateSagaConsumer.java`

Follow the EXACT pattern from `CustomerSagaConsumer.java`. CRITICAL RULES:
- `TransactionTemplate.executeWithoutResult()` — all DB writes in one transaction
- `SagaEventRepository.tryInsertDedup()` — atomic dedup (NEVER existsBy + save)
- Response events via OutboxEvent table (PENDING status) — NOT direct StreamBridge
- Propagate `traceId` from incoming `EventEnvelope`
- JSON via `jsonMapper.writeValueAsString()` — never string concatenation
- `EstimationFailed` handler: log only (read-only validation has no compensation)
- Top-level try-catch in the Consumer bean lambda
- `@Bean public Consumer<String> processRealEstateSaga(JsonMapper jsonMapperArg)`

The consumer handles two event types:
- `ESTIMATION_REQUESTED` → find RealEstate by `realEstateId` from the request event payload → if exists, build `RealEstateValidatedEvent` with `realEstateId`, `address`, `cityId` → if not exists, build `RealEstateInvalidatedEvent` with `realEstateId` and reason "Real estate not found" → save as PENDING OutboxEvent
- `ESTIMATION_FAILED` → dedup check + log only

If `requestEvent.getRealEstateId()` is null, publish an empty `RealEstateValidated` to unblock the saga (same pattern as VehicleSagaConsumer handles null vehicleId).

### 3. Modify `RealEstateService.java`

Wire in `RealEstateEventPublisher`:
1. Add field: `private final RealEstateEventPublisher realEstateEventPublisher;`
2. In `create()` — after save, call `realEstateEventPublisher.publishRealEstateCreated(saved)`
3. In `update()` — after save, call `realEstateEventPublisher.publishRealEstateUpdated(saved)`
4. In `delete()` — before delete, call `realEstateEventPublisher.publishRealEstateDeleted(realEstate)`

## Key Conventions
- Domain events → direct `MessagePublisher.publish()`
- SAGA response events → outbox pattern (OutboxEvent table)
- `TransactionTemplate.executeWithoutResult()` + `tryInsertDedup()` for SAGA handlers
- `traceId` propagation
- JSON via ObjectMapper only

## Verification

```bash
.\gradlew.bat :services:realestate-service:compileJava
```

## Files Written
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/config/RealEstateEventPublisher.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/config/RealEstateSagaConsumer.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java` ✅ (modified — event publisher wired in)
