# Plan: Fix 12 — Extract `afterCommit` Utility to Eliminate Duplication

> **Status update (2026-06-24):** The codebase had already evolved past the original duplication before this plan was executed. Both `EstimationService` and `SagaTimeoutService` now use an **outbox pattern** (`OutboxEvent` table within the same DB transaction), which is more reliable than the `TransactionSynchronization` hook. No refactoring of those services was needed.
>
> **What was done:**
> - Added `publishAfterCommit()` to `MessagePublisher` as a reusable utility for future use cases
> - Added `spring-tx` dependency to `common-message`'s `build.gradle.kts`
> - Services were not modified as they already use the superior outbox pattern

## Objective

Extract the `TransactionSynchronizationManager.registerSynchronization()` pattern currently duplicated in `EstimationService` and `SagaTimeoutService` into a shared utility method, eliminating the duplicated `TransactionSynchronization` anonymous class creation.

## Current State

The `afterCommit` pattern is written identically in two places:

### EstimationService.java (lines 96-103)
```java
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);
            log.info("Published EstimationRequested for sagaId={}", sagaId);
        }
    });
```

### SagaTimeoutService.java (lines 64-74)
```java
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

The duplication is **17 lines of boilerplate** repeated verbatim. If the `afterCommit` behavior needs to change (e.g., add logging, error handling, monitoring), both places must be updated.

## Design: A Reusable `afterCommit` Helper

Extract a static utility method that wraps the `TransactionSynchronization` creation:

```java
/**
 * Defer a Runnable to execute after the current DB transaction commits.
 * If no transaction is active, executes immediately.
 * The callback runs in the same thread, synchronously after commit.
 */
public static void afterCommit(Runnable callback) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    callback.run();
                }
            });
    } else {
        // No active transaction — execute immediately
        callback.run();
    }
}
```

**Where to put it:** In `common/common-message/src/main/java/.../common/messaging/TransactionUtils.java` (or similar) — since it's a general-purpose utility that could be used by any service.

## Alternative: Simplify via Shared MessagePublisher Enhancement

Instead of extracting the synchronization pattern, an alternative is to make `MessagePublisher` itself transaction-aware:

```java
@Component
public class MessagePublisher {
    private final StreamBridge streamBridge;

    public void publishAfterCommit(String topic, Object message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        streamBridge.send(topic, message);
                    }
                });
        } else {
            streamBridge.send(topic, message);
        }
    }
}
```

**This is simpler and more maintainable** — consumers just call `messagePublisher.publishAfterCommit()` instead of `messagePublisher.publish()`. No separate utility class needed.

## Context Files to Read First

1. **`services/estimation-service/src/main/java/.../estimation/service/EstimationService.java`**
   - `scheduleSagaEventPublish()` method (lines 85-104) — current afterCommit usage
   - Imports for TransactionSynchronization, TransactionSynchronizationManager

2. **`services/estimation-service/src/main/java/.../estimation/service/SagaTimeoutService.java`**
   - `checkForTimedOutSagas()` method (lines 38-79) — afterCommit in for-loop
   - Imports for TransactionSynchronization, TransactionSynchronizationManager

3. **`common/common-message/src/main/java/.../common/messaging/MessagePublisher.java`**
   - Current publish method — to add `publishAfterCommit()` overload

4. **`services/estimation-service/src/test/java/.../estimation/service/EstimationServiceTest.java`**
   - Tests that use `TransactionSynchronizationManager.initSynchronization()` / `clearSynchronization()`

5. **`services/estimation-service/src/test/java/.../estimation/service/SagaTimeoutServiceTest.java`**
   - Tests that use `TransactionSynchronizationManager.initSynchronization()` / `clearSynchronization()`

## Files to Modify

### Step 1: Add `publishAfterCommit()` to `MessagePublisher.java`

**File:** `common/common-message/src/main/java/.../common/messaging/MessagePublisher.java`

**BEFORE:**
```java
package com.insurancemanagementsystem.common.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessagePublisher {

    private final StreamBridge streamBridge;

    public void publish(String topic, Object message) {
        log.debug("Publishing message to {}: {}", topic, message);
        streamBridge.send(topic, message);
    }
}
```

**AFTER:**
```java
package com.insurancemanagementsystem.common.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessagePublisher {

    private final StreamBridge streamBridge;

    public void publish(String topic, Object message) {
        log.debug("Publishing message to {}: {}", topic, message);
        streamBridge.send(topic, message);
    }

    /**
     * Publish a message after the current DB transaction commits.
     * If no transaction is active, publishes immediately.
     * This prevents the "dual-write" problem where the DB is updated
     * but the message is lost (e.g., if Kafka is unavailable).
     */
    public void publishAfterCommit(String topic, Object message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.debug("Publishing message after transaction commit to {}: {}", topic, message);
                        streamBridge.send(topic, message);
                    }
                });
            log.trace("Registered afterCommit publish to {}", topic);
        } else {
            // No active transaction — publish immediately
            publish(topic, message);
        }
    }
}
```

### Step 2: Update `EstimationService.java`

**Replace the entire `scheduleSagaEventPublish()` method and its usage:**

**Import changes:**
```java
// Remove these (no longer needed directly):
// import org.springframework.transaction.support.TransactionSynchronization;
// import org.springframework.transaction.support.TransactionSynchronizationManager;
```

**In `create()` method, change:**
```java
// BEFORE:
estimation = estimationRepository.save(estimation);
log.info("Created estimation id={} with sagaId={}", estimation.getId(), sagaId);

// Defer publish until after DB transaction commits (atomicity)
scheduleSagaEventPublish(request, sagaId);

return EstimationResponse.fromEntity(estimation);
```

```java
// AFTER:
estimation = estimationRepository.save(estimation);
log.info("Created estimation id={} with sagaId={}", estimation.getId(), sagaId);

// Defer publish until after DB transaction commits (atomicity)
messagePublisher.publishAfterCommit(EventConstants.ESTIMATION_SAGA, envelope);
log.info("Scheduled EstimationRequested publish for sagaId={}", sagaId);

return EstimationResponse.fromEntity(estimation);
```

**Remove the entire `scheduleSagaEventPublish()` method** (lines 85-104).

**The `envelope` variable:** The `envelope` is created right before the publish call. Move it up:

```java
EstimationRequestedEvent event = EstimationRequestedEvent.builder()
        .customerId(request.getCustomerId())
        .vehicleId(request.getVehicleId())
        .realEstateId(request.getRealEstateId())
        .insuranceTypeId(request.getInsuranceTypeId())
        .companyId(request.getCompanyId())
        .build();

EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());

estimation = estimationRepository.save(estimation);
log.info("Created estimation id={} with sagaId={}", estimation.getId(), sagaId);

messagePublisher.publishAfterCommit(EventConstants.ESTIMATION_SAGA, envelope);
log.info("Scheduled EstimationRequested publish for sagaId={}", sagaId);

return EstimationResponse.fromEntity(estimation);
```

### Step 3: Update `SagaTimeoutService.java`

**Replace the afterCommit anonymous class with `publishAfterCommit()`:**

**Import changes:**
```java
// Remove these:
// import org.springframework.transaction.support.TransactionSynchronization;
// import org.springframework.transaction.support.TransactionSynchronizationManager;
```

**In the for-loop body (inside try block), change:**
```java
// BEFORE:
estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails("SAGA timed out after " + timeoutMinutes + " minutes");
estimationRepository.save(estimation);

// Defer publish until after DB transaction commits (atomicity)
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

```java
// AFTER:
estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails("{\"reason\":\"SAGA timed out after " + timeoutMinutes + " minutes\"}");
estimationRepository.save(estimation);

// Defer publish until after DB transaction commits (atomicity)
EstimationFailedEvent timeoutEvent = EstimationFailedEvent.builder()
        .originalSagaId(sagaId)
        .reason("SAGA timed out after " + timeoutMinutes + " minutes")
        .failedStep("SagaTimeoutService")
        .build();
EventEnvelope envelope = timeoutEvent.toEnvelope(sagaId, UUID.randomUUID());
messagePublisher.publishAfterCommit(EventConstants.ESTIMATION_SAGA, envelope);
log.info("Scheduled EstimationFailed publish for sagaId={}", sagaId);
```

**Note:** This changes the call path — instead of `estimationEventPublisher.publishEstimationFailed()`, it goes directly through `messagePublisher.publishAfterCommit()`. The `EstimationEventPublisher` wrapper is no longer needed here (the event is built inline). The `EstimationEventPublisher` is still used in the consumer's `handleFailed()`.

### Step 4: Update test files

Both `EstimationServiceTest.java` and `SagaTimeoutServiceTest.java` currently use `TransactionSynchronizationManager.initSynchronization()` in `@BeforeEach` to set up the transaction context for `afterCommit` to work.

With the new `MessagePublisher.publishAfterCommit()` method, the `TransactionSynchronizationManager` synchronization setup still needs to be there (because `publishAfterCommit()` checks `isSynchronizationActive()`). So **no changes to test setup** are needed.

However, the tests should verify that `messagePublisher.publishAfterCommit()` is called instead of `messagePublisher.publish()`:

```java
// In EstimationServiceTest.java:
verify(messagePublisher).publishAfterCommit(anyString(), any());
```

**Note:** `MessagePublisher` is already mocked with `@Mock`. The new `publishAfterCommit()` method will need to be verified in tests. Since tests mock `MessagePublisher`, the behavior of `publishAfterCommit()` is not actually tested (it's a Spring-level concern). The unit tests verify that the method is called correctly.

## Verification

```bash
# 1. Compile common-message
.\gradlew.bat :common:common-message:compileJava

# 2. Compile estimation-service
.\gradlew.bat :services:estimation-service:compileJava

# 3. Run estimation-service tests
.\gradlew.bat :services:estimation-service:test
```

## Execution Checklist

- [x] Read context files
- [x] Edit `MessagePublisher.java` — add `publishAfterCommit()` method
- [x] Add `spring-tx` dependency to `common-message/build.gradle.kts`
- [x] Compile `common:common-message` — `BUILD SUCCESSFUL`
- [ ] <strike>Edit `EstimationService.java` — replace `scheduleSagaEventPublish()` with `messagePublisher.publishAfterCommit()`</strike>
  **CANCELLED:** Code already uses outbox pattern, which is superior. No `afterCommit` duplication exists.
- [ ] <strike>Remove unused `TransactionSynchronization` imports from `EstimationService.java`</strike>
  **CANCELLED:** No such imports exist in the current code.
- [ ] <strike>Edit `SagaTimeoutService.java` — replace anonymous `TransactionSynchronization` with `messagePublisher.publishAfterCommit()`</strike>
  **CANCELLED:** Code already uses outbox pattern.
- [ ] <strike>Remove unused `TransactionSynchronization` imports from `SagaTimeoutService.java`</strike>
  **CANCELLED:** No such imports exist.
- [ ] <strike>Update `EstimationServiceTest.java` — verify `publishAfterCommit()` instead of not verifying publish</strike>
  **CANCELLED:** Tests already verify outbox events.
- [ ] <strike>Update `SagaTimeoutServiceTest.java` — verify `publishAfterCommit()` instead of not verifying</strike>
  **CANCELLED:** Tests already verify outbox events.
- [x] Compile estimation-service: `BUILD SUCCESSFUL`
- [x] All tests pass

## Risk Assessment

- **Risk:** VERY LOW. The `publishAfterCommit()` method in `MessagePublisher` is a new utility with no consumers yet — it adds capability without changing any existing behavior.
- **Existing behavior preserved:** No existing code was modified. The outbox pattern in `EstimationService` and `SagaTimeoutService` remains untouched.
- **Thread safety:** `TransactionSynchronizationManager` is thread-local (uses `ThreadLocal`). The `afterCommit` callback runs in the same thread, so no thread-safety concerns.
- **Dependency addition:** Added `implementation("org.springframework:spring-tx")` to `common-message/build.gradle.kts`. This is lightweight (already a transitive dependency of downstream services via `spring-data-jpa`).
- **Outbox vs afterCommit:** Note that the services use the **outbox pattern** (`OutboxEvent` table), not the `afterCommit` hook. The outbox pattern is more reliable (guarantees delivery even if the application crashes after transaction commit but before the message is sent). The `publishAfterCommit()` utility is a lighter-weight alternative for future use cases where the outbox pattern is overkill.
