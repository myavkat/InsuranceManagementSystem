# Plan: Sprint 4 — Vehicle & RealEstate — Step 10: Estimation Service SAGA Consumer Update

## Objective
Update `EstimationSagaConsumer` to handle `RealEstateValidated` and `RealEstateInvalidated` events, following the same patterns used for Vehicle events. Add corresponding test cases.

## Context Files to Read First
1. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`** — The consumer to modify (pay attention to how VehicleValidated and VehicleInvalidated are handled)
2. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java`** — Existing SAGA consumer tests
3. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventConstants.java`** — New constants (REAL_ESTATE_VALIDATED, REAL_ESTATE_INVALIDATED from Step 1)
4. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/RealEstateValidatedEvent.java`** — New event class
5. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/RealEstateInvalidatedEvent.java`** — New event class

## Files to Modify

### 1. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`

**Step A: Add imports** at the top of the file:
```java
import com.insurancemanagementsystem.common.event.saga.RealEstateValidatedEvent;
import com.insurancemanagementsystem.common.event.saga.RealEstateInvalidatedEvent;
```

**Step B: Add switch cases** in `processEstimationSaga()` method's switch statement. Find the existing `VEHICLE_VALIDATED` and `VEHICLE_INVALIDATED` cases and add RealEstate equivalents:

```java
case EventConstants.REAL_ESTATE_VALIDATED ->
    handleRealEstateValidated(envelope, sagaId, traceId, jsonMapper);
case EventConstants.REAL_ESTATE_INVALIDATED ->
    handleFailed(envelope, sagaId, traceId, "Real estate validation failed", jsonMapper);
```

**Step C: Add handler method** `handleRealEstateValidated()`. This follows EXACTLY the same pattern as `handleVehicleValidated()` — log-only, no state change. The estimation stays STARTED and waits for premium calculation:

```java
private void handleRealEstateValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
    String eventType = envelope.getEventType();

    transactionTemplate.executeWithoutResult(status -> {
        if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
            return;
        }

        RealEstateValidatedEvent event = jsonMapper.convertValue(
                envelope.getPayload(), RealEstateValidatedEvent.class);
        log.info("Real estate validated for sagaId={}: realEstateId={}, address={}",
                sagaId, event.getRealEstateId(), event.getAddress());
    });
}
```

The `REAL_ESTATE_INVALIDATED` case reuses the existing `handleFailed()` method — no new method needed (same as VehicleInvalidated).

### 2. `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java`

Add test cases for the new RealEstate event handling:

**Test 1: `realEstateValidated_isIdempotent()`** — Send a RealEstateValidated event, verify it's processed without errors and logged. Send a duplicate, verify it's deduped (no exception thrown).

**Test 2: `realEstateInvalidated_rejectsEstimation()`** — Save a STARTED estimation, send RealEstateInvalidated, verify estimation transitions to REJECTED and an EstimationFailed outbox event is saved.

Test pattern follows existing `VehicleValidated`/`VehicleInvalidated` test cases in the same file.

## Key Conventions
- Same pattern as VehicleValidated: `handleRealEstateValidated()` is a log-only handler (dedup + convert + log)
- `RealEstateInvalidated` reuses `handleFailed()` — no new handler method needed
- `TransactionTemplate.executeWithoutResult()` wrapping dedup + logging in one transaction
- `jsonMapper.convertValue()` for typed event conversion
- `tryInsertDedup()` for idempotency

## Verification

```bash
.\gradlew.bat :services:estimation-service:test --tests "*EstimationSagaConsumerTest"
```

Or run all estimation tests:
```bash
.\gradlew.bat :services:estimation-service:test
```

All tests should pass. The new RealEstate test cases should exercise both RealEstateValidated and RealEstateInvalidated paths.

## Files Modified
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java` ✅ (switch cases + new handler method)
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java` ✅ (new test cases)

## Execution Log
- ✅ Added `REAL_ESTATE_VALIDATED` switch case calling `handleRealEstateValidated()` (log-only handler)
- ✅ Added `REAL_ESTATE_INVALIDATED` switch case reusing `handleFailed()` with "Real estate validation failed" reason
- ✅ Added `handleRealEstateValidated()` method following same pattern as `handleVehicleValidated()` (dedup + convert + log)
- ✅ Added `realEstateValidated_isIdempotent()` test verifying dedup and no estimation interaction
- ✅ Added `realEstateInvalidated_rejectsEstimation()` test verifying REJECTED transition + outbox event
- ✅ All 19 tests pass (`BUILD SUCCESSFUL`)
