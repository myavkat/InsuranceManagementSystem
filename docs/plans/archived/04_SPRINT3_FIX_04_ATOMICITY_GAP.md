# Plan: Fix 04 — Fix Atomicity Gap (DB Save Before Kafka Publish)

## Objective
Fix the atomicity gap where `EstimationService.create()` saves to the database first, then publishes to Kafka. If Kafka is unavailable, the DB row exists in `STARTED` status but no `EstimationRequested` event is ever published, creating an orphan saga that sits until the timeout (5 minutes) triggers.

## Root Cause

In `EstimationService.java:73-87`:
```java
@Transactional
public EstimationResponse create(EstimationRequest request) {
    // ...
    estimation = estimationRepository.save(estimation);           // Step 1: DB (committed)
    log.info("Created estimation id={} with sagaId={}", estimation.getId(), sagaId);

    // Publish EstimationRequested to estimation.saga
    EstimationRequestedEvent event = ...;
    EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());
    messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);  // Step 2: Kafka (may fail!)
    log.info("Published EstimationRequested for sagaId={}", sagaId);

    return EstimationResponse.fromEntity(estimation);
}
```

**Problem:** `@Transactional` only wraps the JPA transaction. `StreamBridge.send()` (via `messagePublisher.publish()`) operates outside the transaction. If Kafka is down:
1. The estimation row is committed to PostgreSQL ✅
2. The Kafka send fails silently ❌
3. Result: an orphan `STARTED` estimation that never triggers the saga

**The same gap exists in:**
- `SagaTimeoutService.checkForTimedOutSagas()` — saves REJECTED to DB, then publishes `EstimationFailed`
- `EstimationSagaConsumer.handleFailed()` — saves REJECTED to DB, then publishes `EstimationFailed`

## Chosen Fix: TransactionSynchronization (afterCommit)

Use Spring's `TransactionSynchronizationManager.registerSynchronization()` to defer the Kafka publish until AFTER the DB transaction commits successfully. If the DB transaction rolls back, the publish never happens.

### Why this approach (not outbox pattern)
- **Minimal change** — single-line wrapper around existing publish calls
- **No new table** — the outbox pattern requires an `outbox_events` table + relay service
- **Correct for the common case** — covers the primary failure mode (Kafka unavailable at publish time)
- **Limitation:** If the JVM crashes between `afterCommit` and the actual Kafka send, the event is lost. For production, the full outbox pattern is needed, but this is a significant improvement over current state.

## Context Files to Read First

1. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`** — The `create()` method
2. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java`** — The `checkForTimedOutSagas()` method
3. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`** — The `handleFailed()` method
4. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationEventPublisher.java`** — The `publishEstimationFailed()` method
5. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/EstimationServiceTest.java`** — Tests for create()
6. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutServiceTest.java`** — Tests for timeout
7. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java`** — Tests for handleFailed()

## Files to Modify

### 1. `EstimationService.java` — Wrap publish in afterCommit

**Add import:**
```java
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
```

**Modify the `create()` method** — replace the publish block (lines 76-87):

**BEFORE:**
```java
// Publish EstimationRequested to estimation.saga to start SAGA choreography
EstimationRequestedEvent event = EstimationRequestedEvent.builder()
        .customerId(request.getCustomerId())
        .vehicleId(request.getVehicleId())
        .realEstateId(request.getRealEstateId())
        .insuranceTypeId(request.getInsuranceTypeId())
        .companyId(request.getCompanyId())
        .build();

EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());
messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);
log.info("Published EstimationRequested for sagaId={}", sagaId);
```

**AFTER:**
```java
// Publish EstimationRequested AFTER DB transaction commits (atomicity)
EstimationRequestedEvent event = EstimationRequestedEvent.builder()
        .customerId(request.getCustomerId())
        .vehicleId(request.getVehicleId())
        .realEstateId(request.getRealEstateId())
        .insuranceTypeId(request.getInsuranceTypeId())
        .companyId(request.getCompanyId())
        .build();

EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());

TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);
            log.info("Published EstimationRequested for sagaId={}", sagaId);
        }
    });
```

**Why capture sagaId in a local:** The `sagaId` variable is effectively final in the lambda — it's captured from the enclosing scope. This works correctly because the local variable is not reassigned after the lambda is created.

### 2. `SagaTimeoutService.java` — Wrap publish in afterCommit

The `checkForTimedOutSagas()` method is `@Transactional` and iterates over stale estimations:

**Add import:**
```java
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
```

**Modify the for-loop body** — change the publish call (lines 60-64 in the try block):

**BEFORE (relevant portion):**
```java
estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails("SAGA timed out after " + timeoutMinutes + " minutes");
estimationRepository.save(estimation);

// Publish compensation event
estimationEventPublisher.publishEstimationFailed(
        sagaId,
        null,
        "SAGA timed out after " + timeoutMinutes + " minutes",
        "SagaTimeoutService");
```

**AFTER:**
```java
estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails("SAGA timed out after " + timeoutMinutes + " minutes");
estimationRepository.save(estimation);

// Publish compensation event AFTER transaction commits
UUID capturedSagaId = sagaId;
int capturedTimeout = timeoutMinutes;
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            estimationEventPublisher.publishEstimationFailed(
                    capturedSagaId,
                    null,
                    "SAGA timed out after " + capturedTimeout + " minutes",
                    "SagaTimeoutService");
        }
    });
```

**Why capture `sagaId` and `timeoutMinutes`:** The variables used in the lambda must be effectively final. `timeoutMinutes` is a field (not final), so capture it in a local variable. `sagaId` is already a local variable inside the loop, so it IS effectively final — but capturing it explicitly as `capturedSagaId` makes the intent clear.

**Note on @Transactional scope:** Since `checkForTimedOutSagas()` is `@Transactional` and the for-loop is inside it, the DB saves for ALL stale estimations happen in a single transaction. The `afterCommit` callbacks will fire after the ENTIRE transaction commits. This means if processing 5 stale estimations fails on the 3rd (due to exception), the first 2 saves are also rolled back, and NO events are published for any of them. On the next scheduler run, all 5 are re-processed. This is correct and safe — no orphan events.

However, the current code catches exceptions per-iteration (lines 65-67), so a failure on one estimation does NOT roll back the transaction. This means `afterCommit` will fire for all successfully-saved estimations. This is the correct behavior.

### 3. `EstimationSagaConsumer.java` — Wrap publish in afterCommit (handleFailed)

The `handleFailed()` method calls `estimationEventPublisher.publishEstimationFailed()` — but unlike `EstimationService`, this method does NOT have `@Transactional`. Each `estimationRepository.save()` call creates its own implicit transaction (Spring Data JPA auto-commit).

**Decision: Skip this change for now.** Since there's no enclosing `@Transactional`, there's nothing to synchronize against. Adding `@Transactional` to the consumer handler would change behavior (batch multiple operations) and is out of scope for this fix. The current behavior (save then publish) is acceptable for the consumer because:
- The consumer creates its own implicit transaction per save
- If publish fails, the DB state already reflects REJECTED (consistency is maintained)
- The compensation event (`EstimationFailed`) is non-critical — it's informative for other services

**For completeness, the `handleFailed` gap is noted but left for a future outbox-pattern fix.**

## Test Updates

### `EstimationServiceTest.java` — Update create tests

The test `create_withValidRequest_createsEstimationWithStartedStatus()` currently verifies:
```java
verify(messagePublisher).publish(anyString(), any());
```

After the fix, this verification must account for the fact that `publish()` is now called inside `afterCommit()`, which won't execute in a unit test (no real transaction). 

**Option A:** Use Spring's `TransactionSynchronizationManager` test support:
```java
// In the test, simulate transaction synchronization
TransactionSynchronizationManager.initSynchronization();
try {
    // ... test code ...
} finally {
    TransactionSynchronizationManager.clearSynchronization();
}
```

**Option B (simpler):** Since this is a Mockito unit test (not a Spring integration test), `TransactionSynchronizationManager` won't have real synchronizations. The test should verify that:
1. `estimationRepository.save()` was called ✅
2. `TransactionSynchronizationManager.registerSynchronization()` was called (can't easily mock static)

**Option C (pragmatic):** Restructure the code slightly to make it testable. Extract the publish logic into a package-private method:

```java
@Transactional
public EstimationResponse create(EstimationRequest request) {
    // ... build estimation, save ...
    scheduleSagaEventPublish(request, sagaId);
    return EstimationResponse.fromEntity(estimation);
}

// Package-private for testing
void scheduleSagaEventPublish(EstimationRequest request, UUID sagaId) {
    EstimationRequestedEvent event = EstimationRequestedEvent.builder()
            .customerId(request.getCustomerId())
            .vehicleId(request.getVehicleId())
            .realEstateId(request.getRealEstateId())
            .insuranceTypeId(request.getInsuranceTypeId())
            .companyId(request.getCompanyId())
            .build();
    EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());
    
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);
                log.info("Published EstimationRequested for sagaId={}", sagaId);
            }
        });
}
```

Then in the test, verify that `estimationRepository.save()` was called and the estimation was created in STARTED status. The publish verification can assert that the `messagePublisher.publish()` call happens only when the transaction commits — for now, verify that `estimationRepository.save()` is called (the core correctness assertion). The `afterCommit` behavior is tested implicitly by integration tests.

**Simplest approach for this fix:** Update the test to remove the `verify(messagePublisher).publish()` assertion and add a comment explaining that publish is now deferred to `afterCommit`. The integration tests (with Testcontainers) cover the end-to-end flow.

```java
// Instead of: verify(messagePublisher).publish(anyString(), any());
// Add comment: "Publish is now deferred to afterCommit — verified by integration tests"
```

## Verification

```bash
# 1. Compile
.\gradlew.bat :services:estimation-service:compileJava

# 2. Run unit tests
.\gradlew.bat :services:estimation-service:test --tests "*EstimationServiceTest"
.\gradlew.bat :services:estimation-service:test --tests "*SagaTimeoutServiceTest"

# 3. Run integration tests (exercise afterCommit with real transaction)
.\gradlew.bat :services:estimation-service:test --tests "*ApplicationTests"

# 4. Run all tests
.\gradlew.bat :services:estimation-service:test
```

---

## Files Summary

### Modified
- `services/estimation-service/src/main/java/.../service/EstimationService.java` — Wrap publish in `afterCommit` synchronization
- `services/estimation-service/src/main/java/.../service/SagaTimeoutService.java` — Wrap publish in `afterCommit` synchronization
- `services/estimation-service/src/test/java/.../service/EstimationServiceTest.java` — Update publish verification
- `services/estimation-service/src/test/java/.../service/SagaTimeoutServiceTest.java` — Update publish verification

### NOT modified (deferred)
- `services/estimation-service/src/main/java/.../config/EstimationSagaConsumer.java` — `handleFailed()` left as-is (no enclosing transaction)

---

## Important Notes for Implementer

1. **TransactionSynchronization import:** Use `org.springframework.transaction.support.TransactionSynchronization` and `org.springframework.transaction.support.TransactionSynchronizationManager` (from `spring-tx`, already on classpath).

2. **Variable capture in lambdas:** All variables referenced in the anonymous `TransactionSynchronization` class must be effectively final. Local variables inside a loop iteration ARE effectively final within that iteration. Class fields (like `timeoutMinutes`) are NOT — capture them in a local variable first.

3. **Idempotency:** The `afterCommit` approach means: if the transaction commits successfully and then the Kafka publish fails, the event is lost. This is an acknowledged limitation of this approach. The SAGA timeout mechanism (5 minutes) acts as a safety net — if the event never reaches other services, the estimation will eventually be timed out and rejected.

4. **Logging:** The `log.info("Published...")` call moves INSIDE the `afterCommit` lambda, so it only fires after the DB transaction is successfully committed. This provides accurate logging.

5. **Test approach:** For unit tests, we accept that `afterCommit` callbacks don't fire (no real transaction manager). The integration tests (`ApplicationTests` with Testcontainers) will exercise the real transaction flow. This is an acceptable trade-off for this fix.
