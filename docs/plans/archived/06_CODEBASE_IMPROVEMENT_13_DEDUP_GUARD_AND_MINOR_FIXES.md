# Plan: Fix 13 — Add Missing Dedup Guard to `handleInvalidated()` & Minor Fixes

## Objective

Fix three smaller issues:
1. **HIGH: Missing dedup guard** — `InsuranceSagaConsumer.handleInvalidated()` publishes `CalculationFailedEvent` without checking `isDuplicateSagaEvent()`. All other handler methods have the guard.
2. **MEDIUM: Hardcoded topic string** — `SagaTimeoutService` uses literal `"estimation.saga"` instead of `EventConstants.ESTIMATION_SAGA`
3. **MEDIUM: Jackson 2 dependency** — `customer-service/build.gradle.kts` has `jackson-datatype-jsr310` (Jackson 2), already built into Jackson 3

---

## Root Causes & Context

### Issue 1: Missing dedup guard

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java` — `handleInvalidated()`

```java
// Current code (no dedup check):
private void handleInvalidated(EventEnvelope envelope) {
    UUID sagaId = envelope.getSagaId();
    String eventType = envelope.getEventType();
    String reason = eventType + " received for saga: " + sagaId;

    log.warn("SAGA step invalidated: sagaId={}, eventType={}, reason={}", sagaId, eventType, reason);

    // Publish CalculationFailed directly — NO DEDUP CHECK!
    messagePublisher.publish(EventConstants.ESTIMATION_SAGA, event.toEnvelope(sagaId, traceId));
}
```

Duplicates of `CUSTOMER_INVALIDATED` or `VEHICLE_INVALIDATED` will each produce a duplicate `CalculationFailedEvent`. This violates the idempotency contract of the SAGA pattern.

**Fix:** Add the standard dedup check (after Plan 12, this becomes `sagaEventRepository.tryInsertDedup()`; before Plan 12, it uses `isDuplicateSagaEvent()`).

### Issue 2: Hardcoded topic string

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java` — `saveOutboxEvent()`

After Plan 10, this helper is removed. But if any hardcoded `"estimation.saga"` remains, replace with `EventConstants.ESTIMATION_SAGA`.

**Verification:** After Plans 09 and 10, check if any hardcoded `"estimation.saga"` strings remain in source code (excluding tests).

### Issue 3: Jackson 2 dependency

**File:** `services/customer-service/build.gradle.kts`

```kotlin
implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")   // Line 37
```

Jackson 3 (`tools.jackson.*`) includes JSR-310 support natively. This dependency is harmless at runtime but clutters the dependency tree. `insurance-service` and `estimation-service` already removed it. Remove from `customer-service` to be consistent.

---

## Cross-Service Analysis

| Service | Affected? | Details |
|---------|-----------|---------|
| **insurance-service** | ✅ | Missing dedup in `handleInvalidated()` |
| **estimation-service** | ⚠️ | Verify no hardcoded `"estimation.saga"` after Plans 09-10 |
| **customer-service** | ✅ | Jackson 2 dependency to remove |

---

## Context Files to Read First

### Issue 1
1. **`services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`** (251 lines)
   - `handleInvalidated()` — lines ~198-213
   - Other handler methods for reference pattern (e.g., `handleCustomerValidated()`)

2. **`services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/saga/InsuranceSagaConsumerTest.java`** (existing test)
   - Test for `handleInvalidated()` — add duplicate-event test

### Issue 2
3. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java`** (97 lines)
   - Check for hardcoded `"estimation.saga"` after Plan 10 modifications

4. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`** (243 lines)
   - Check `saveOutboxEvent()` / `buildEstimationFailedOutboxEvent()` — should use constant

### Issue 3
5. **`services/customer-service/build.gradle.kts`** (81 lines)
   - Line 37: `jackson-datatype-jsr310`

6. **`services/insurance-service/build.gradle.kts`** — reference: already removed, has comment explaining
7. **`services/estimation-service/build.gradle.kts`** — reference: already removed

---

## Files to Modify

### Issue 1: Add dedup to `handleInvalidated()`

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`

**BEFORE:**
```java
private void handleInvalidated(EventEnvelope envelope) {
    UUID sagaId = envelope.getSagaId();
    String eventType = envelope.getEventType();
    String reason = eventType + " received for saga: " + sagaId;

    log.warn("SAGA step invalidated: sagaId={}, eventType={}, reason={}", sagaId, eventType, reason);

    CalculationFailedEvent event = CalculationFailedEvent.builder()
            .originalSagaId(sagaId)
            .reason(reason)
            .failedStep(eventType)
            .build();

    UUID traceId = envelope.getTraceId() != null ? envelope.getTraceId() : UUID.randomUUID();
    messagePublisher.publish(EventConstants.ESTIMATION_SAGA, event.toEnvelope(sagaId, traceId));
}
```

**AFTER:**
```java
private void handleInvalidated(EventEnvelope envelope) {
    UUID sagaId = envelope.getSagaId();
    String eventType = envelope.getEventType();

    // Dedup guard — same pattern as all other handlers
    if (isDuplicateSagaEvent(sagaId, eventType)) {       // OR sagaEventRepository.tryInsertDedup() after Plan 12
        return;
    }

    String reason = eventType + " received for saga: " + sagaId;
    log.warn("SAGA step invalidated: sagaId={}, eventType={}, reason={}", sagaId, eventType, reason);

    CalculationFailedEvent event = CalculationFailedEvent.builder()
            .originalSagaId(sagaId)
            .reason(reason)
            .failedStep(eventType)
            .build();

    UUID traceId = envelope.getTraceId() != null ? envelope.getTraceId() : UUID.randomUUID();
    messagePublisher.publish(EventConstants.ESTIMATION_SAGA, event.toEnvelope(sagaId, traceId));
}
```

**IMPORTANT:** If Plan 11 (outbox) changes this method to use outbox insert, apply the dedup guard FIRST, then the outbox insert if applicable. The dedup guard code doesn't conflict with outbox changes — it's an early return at the top of the method.

### Issue 2: Replace hardcoded topic string

Search for `"estimation.saga"` in estimation-service source (not tests). If found:

```java
// BEFORE:
.topic("estimation.saga")

// AFTER:
.topic(EventConstants.ESTIMATION_SAGA)
```

After Plans 09 and 10, the only remaining location may be in `OutboxEventSerializer.java` (which takes topic as a parameter — no hardcode). Verify with grep.

### Issue 3: Remove Jackson 2 dependency

**File:** `services/customer-service/build.gradle.kts`

Delete line 37:
```kotlin
implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
```

Add a comment for consistency (matching insurance-service):
```kotlin
// Removed jackson-datatype-jsr310 — JSR-310 support is built into Jackson 3 (tools.jackson)
```

---

## Test Updates

### Issue 1 test: Duplicate `handleInvalidated`

**File:** `services/insurance-service/src/test/java/.../insurance/saga/InsuranceSagaConsumerTest.java`

Add test:
```java
@Test
void duplicateInvalidatedEvent_isSkipped() {
    // When dedup returns true (duplicate)
    when(sagaEventRepository.tryInsertDedup(any(), any())).thenReturn(true);   // post-Plan 12
    // or: when(sagaEventRepository.save(any())).thenThrow(DataIntegrityViolationException.class);

    // Act: process duplicate invalidated event
    consumer.accept(buildInvalidatedEventJson(sagaId));

    // Assert: messagePublisher.publish() should NOT be called
    verify(messagePublisher, never()).publish(anyString(), any());
}
```

### Issue 2: No test changes needed

Replacing a string literal with a constant doesn't change behavior.

### Issue 3: No test changes needed

Removing an unused dependency doesn't affect tests. Verify compilation.

---

## Verification

```bash
# 1. Compile insurance-service (Issue 1)
.\gradlew.bat :services:insurance-service:compileJava
.\gradlew.bat :services:insurance-service:test

# 2. Check for hardcoded topic strings (Issue 2)
rg "\"estimation\.saga\"" services/estimation-service/src/main/

# 3. Compile customer-service (Issue 3)
.\gradlew.bat :services:customer-service:compileJava
.\gradlew.bat :services:customer-service:test
```

---

## Execution Checklist

### Issue 1 — Missing dedup guard
- [ ] Read `InsuranceSagaConsumer.java` — `handleInvalidated()` method
- [ ] Add `isDuplicateSagaEvent()` / `tryInsertDedup()` at top of `handleInvalidated()`
- [ ] Add test for duplicate invalidated event
- [ ] Compile: SUCCESS
- [ ] Test: PASS

### Issue 2 — Hardcoded topic string
- [ ] Search for `"estimation.saga"` in estimation-service source files
- [ ] Replace with `EventConstants.ESTIMATION_SAGA` if found
- [ ] Compile: SUCCESS

### Issue 3 — Jackson 2 dependency
- [ ] Remove `jackson-datatype-jsr310` from `customer-service/build.gradle.kts`
- [ ] Add comment about Jackson 3 built-in support
- [ ] Compile: SUCCESS

### Final
- [ ] All 3 services compile
- [ ] All tests pass

---

## Risk Assessment

- **Risk: VERY LOW** for all 3 issues.
  - **Issue 1:** Adding an early-return dedup check is a pure safety improvement. No behavioral change for first-time events.
  - **Issue 2:** String constant replacement — no behavioral change.
  - **Issue 3:** Removing an unused Jackson 2 dependency — verified safe by the fact that insurance-service and estimation-service already removed it without issues.
- **Cross-plan dependency:** Issue 1's code change depends on whether `isDuplicateSagaEvent()` has been replaced by `sagaEventRepository.tryInsertDedup()` (Plan 12). Apply the dedup guard using whichever dedup mechanism is active at the time.

---

## Dependencies

- **Soft dependency on Plan 12:** The dedup guard in Issue 1 should use whichever dedup mechanism is present. If Plan 12 is done first, use `sagaEventRepository.tryInsertDedup()`. If not, use the existing `isDuplicateSagaEvent()` pattern.
- **Soft dependency on Plan 10:** If the hardcoded string search finds `"estimation.saga"` in a file that Plan 10 modifies, apply the fix after Plan 10.
