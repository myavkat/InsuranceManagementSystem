# Plan: Fix 10 — Fix ACID Gaps: Serialize Outbox Payload Before State Mutation

## Objective

Fix the **critical ACID gap** where `saveOutboxEvent()` silently catches `JsonProcessingException` and returns without saving, while the estimation state has already been committed as REJECTED. This causes saga deadlocks — downstream services wait for `EstimationFailed` events that were never published.

Affects two locations:
1. `SagaTimeoutService.java` — timeout rejection path
2. `EstimationSagaConsumer.java` — failure rejection path

## Root Cause

### Location 1: SagaTimeoutService

```java
// SagaTimeoutService.java (current, simplified)
@Transactional
public void checkForTimedOutSagas() {
    for (Estimation stale : staleEstimations) {
        estimation.setStatus(REJECTED);              // State mutated
        estimationRepository.save(estimation);       // Committed
        saveOutboxEvent(sagaId, reason, ...);        // ← can silently fail!
    }
}

private void saveOutboxEvent(...) {
    try {
        payloadJson = jsonMapper.writeValueAsString(envelope);
    } catch (Exception e) {
        log.error("...");
        return;   // ← SWALLOWS THE ERROR. Estimation is already REJECTED.
    }
    outboxEventRepository.save(outboxEvent);
}
```

**Consequence:** If JSON serialization fails (malformed data, OOM, Jackson bug), the estimation transitions to REJECTED with no `EstimationFailed` event published. The saga deadlocks.

### Location 2: EstimationSagaConsumer.handleFailed()

```java
// EstimationSagaConsumer.java handleFailed() (current, simplified)
estimation.setStatus(REJECTED);
estimationRepository.save(estimation);               // Committed
saveOutboxEvent(jsonMapper, sagaId, reason, ...);    // ← can silently fail!
```

Same problem. Identical `saveOutboxEvent()` helper with silent catch.

---

## Cross-Service Analysis

| Service | File | Issue |
|---------|------|-------|
| **estimation-service** | `SagaTimeoutService.java` | ACID gap in timeout rejection |
| **estimation-service** | `EstimationSagaConsumer.java` | ACID gap in failure rejection |
| customer-service | — | Not affected (no outbox produce) |
| insurance-service | — | Not affected (no outbox produce) |

---

## Context Files to Read First

1. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java`** (97 lines)
   - `checkForTimedOutSagas()` — lines ~40-80
   - `saveOutboxEvent()` — lines ~80-96

2. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`** (243 lines)
   - `handleFailed()` — lines ~170-200
   - `saveOutboxEvent()` — lines ~218-242 (identical copy of SagaTimeoutService's helper!)

3. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutServiceTest.java`** (existing test)
   - Tests with `@Mock OutboxEventRepository` — verify mock behavior remains correct

4. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java`** (existing test)
   - Tests for `handleFailed()` — verify mock behavior remains correct

---

## Design Decision

### Problem: Two identical `saveOutboxEvent()` helpers in two files

Currently, both `SagaTimeoutService` and `EstimationSagaConsumer` have identical private `saveOutboxEvent()` methods (same signature, same logic, same bug). 

### Solution: Extract shared helper + fix serialization order

**Option A: Extract to shared helper in OutboxProcessor**
- Add `OutboxProcessor.saveOutboxEvent()` — the processor already does outbox work
- But: OutboxProcessor is a separate bean, and Estimations/SagaTimeouts are saved in service/consumer scope

**Option B: Extract to EstimationEventPublisher (existing service-layer publisher)**  
- `EstimationEventPublisher` already has event-building logic
- Add a method that builds the envelope, serializes, and saves to outbox

**Option C: Keep in each class but fix the serialization order**
- Serialize FIRST (before state mutation). If it throws, the `@Transactional` method rolls back.
- Remove the try-catch in `saveOutboxEvent()` — let exceptions propagate.

### Chosen approach: **Option C** (simplest, least refactoring) + **Option A light** (shared serialization logic)

1. Create a small `OutboxEventSerializer` helper in the config package
2. Both `SagaTimeoutService` and `EstimationSagaConsumer` use it
3. Serialization happens BEFORE state mutation

---

## Files to Modify

### 1. Create `OutboxEventSerializer.java` (NEW)

**Path:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/OutboxEventSerializer.java`

```java
package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent;
import com.insurancemanagementsystem.estimation.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventSerializer {

    private final JsonMapper jsonMapper;

    /**
     * Build and serialize an EstimationFailed outbox event.
     * Throws RuntimeException if serialization fails — caller must handle.
     */
    public OutboxEvent buildEstimationFailedOutboxEvent(
            UUID sagaId, String reason, String failedStep, String topic) {

        EstimationFailedEvent event = EstimationFailedEvent.builder()
                .originalSagaId(sagaId)
                .reason(reason)
                .failedStep(failedStep)
                .build();

        EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());

        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize EstimationFailed outbox payload for sagaId=" + sagaId, e);
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

### 2. Modify `SagaTimeoutService.java`

**Changes:**
- Inject `OutboxEventSerializer`
- Remove private `saveOutboxEvent()` method
- Serialize outbox event BEFORE state mutation
- Remove transaction-related imports no longer needed

**Key change in `checkForTimedOutSagas()` loop body:**

```java
// BEFORE (current — state mutation before serialization):
estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails("{\"reason\":\"SAGA timed out after " + timeoutMinutes + " minutes\"}");
estimationRepository.save(estimation);
saveOutboxEvent(sagaId, "SAGA timed out after " + timeoutMinutes + " minutes", "SagaTimeoutService");
```

```java
// AFTER (fixed — serialization before state mutation):
String reason = "SAGA timed out after " + timeoutMinutes + " minutes";

// Serialize FIRST — if it fails, exception rolls back the transaction
OutboxEvent outboxEvent = outboxEventSerializer.buildEstimationFailedOutboxEvent(
        sagaId, reason, "SagaTimeoutService", EventConstants.ESTIMATION_SAGA);

estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails("{\"reason\":\"" + reason + "\"}");
estimationRepository.save(estimation);
outboxEventRepository.save(outboxEvent);

log.info("Rejected timed-out estimation sagaId={} and saved outbox event", sagaId);
```

**Imports to add:**
```java
import com.insurancemanagementsystem.estimation.config.OutboxEventSerializer;
import com.insurancemanagementsystem.estimation.entity.OutboxEvent;
```

**Imports to remove:**
```java
// Remove if no longer used:
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent;
import tools.jackson.databind.json.JsonMapper;
```

Also remove the private `saveOutboxEvent()` method entirely.

### 3. Modify `EstimationSagaConsumer.java`

**Changes:**
- Inject `OutboxEventSerializer`
- Remove private `saveOutboxEvent()` method
- Serialize outbox event BEFORE state mutation
- Remove duplicated helper method

**Key change in `handleFailed()`:**

```java
// BEFORE (current — state mutation before serialization):
estimation.setStatus(Estimation.Status.REJECTED);
try {
    estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
} catch (Exception e) {
    log.warn("Failed to serialize rejection details for sagaId={}", sagaId, e);
    estimation.setDetails("{\"reason\":\"" + reason + "\"}");
}
estimationRepository.save(estimation);
log.info("Estimation {} rejected for sagaId={}: {}", estimation.getId(), sagaId, reason);

saveOutboxEvent(jsonMapper, sagaId, reason, eventType);
```

```java
// AFTER (fixed — serialization before state mutation):
// Serialize FIRST — if it fails, exception propagates, estimation stays STARTED
OutboxEvent outboxEvent = outboxEventSerializer.buildEstimationFailedOutboxEvent(
        sagaId, reason, eventType, EventConstants.ESTIMATION_SAGA);

estimation.setStatus(Estimation.Status.REJECTED);
try {
    estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
} catch (Exception e) {
    log.warn("Failed to serialize rejection details for sagaId={}", sagaId, e);
    estimation.setDetails("{\"reason\":\"" + reason + "\"}");
}
estimationRepository.save(estimation);
outboxEventRepository.save(outboxEvent);

log.info("Estimation {} rejected for sagaId={}: {}", estimation.getId(), sagaId, reason);
```

**Imports to add:**
```java
import com.insurancemanagementsystem.estimation.config.OutboxEventSerializer;
```

**Imports to remove:**
```java
// Remove if no longer needed in this class:
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent;
```

Also remove the private `saveOutboxEvent()` method entirely (lines ~218-242).

### 4. Update `SagaTimeoutServiceTest.java`

- Add `@Mock OutboxEventSerializer` (or `@MockitoBean`)
- Update `checkForTimedOutSagas` test — verify `outboxEventSerializer.buildEstimationFailedOutboxEvent()` is called BEFORE `outboxEventRepository.save()`
- Remove assertions about the old private `saveOutboxEvent()` call

### 5. Update `EstimationSagaConsumerTest.java`

- Add `@Mock OutboxEventSerializer`
- Update `handleFailed` test — verify serialization happens before state mutation
- Test that serialization failure triggers rollback (estimation stays STARTED)

---

## Verification

```bash
# 1. Compile estimation-service
.\gradlew.bat :services:estimation-service:compileJava

# 2. Run timeout tests
.\gradlew.bat :services:estimation-service:test --tests "*SagaTimeoutServiceTest"

# 3. Run consumer tests
.\gradlew.bat :services:estimation-service:test --tests "*EstimationSagaConsumerTest"

# 4. Run all estimation-service tests
.\gradlew.bat :services:estimation-service:test
```

---

## Execution Checklist

- [ ] Read all 4 context files
- [ ] Create `OutboxEventSerializer.java` — shared serialization helper
- [ ] Modify `SagaTimeoutService.java` — serialize before state mutation, remove private helper
- [ ] Modify `EstimationSagaConsumer.java` — serialize before state mutation, remove private helper
- [ ] Update `SagaTimeoutServiceTest.java` — mock new dependency, verify ordering
- [ ] Update `EstimationSagaConsumerTest.java` — mock new dependency, add rollback test
- [ ] Compile: `BUILD SUCCESSFUL`
- [ ] All tests pass

---

## Risk Assessment

- **Risk:** LOW. The fix moves serialization earlier in the method. If serialization fails, the exception propagates and `@Transactional` rolls back. The estimation stays `STARTED` (no deadlock — timeout will retry on next cycle, or client can retry).
- **DRY benefit:** Removes duplicated `saveOutboxEvent()` from two classes (same method, same bug). Single `OutboxEventSerializer` handles both.
- **Behavioral change:** Previously, if serialization failed, the estimation was committed as REJECTED with no event (broken). Now it stays STARTED and the exception propagates — the transaction rolls back entirely.
- **Test changes:** Mock setup changes are minimal — add one `@Mock OutboxEventSerializer` and configure it to return a test `OutboxEvent`.

---

## Dependencies

- **Requires Plan 09** (OutboxRelay refactor) to be completed first, because `OutboxEventSerializer` uses `OutboxEvent` which may have been modified (new `PUBLISHED` status).
