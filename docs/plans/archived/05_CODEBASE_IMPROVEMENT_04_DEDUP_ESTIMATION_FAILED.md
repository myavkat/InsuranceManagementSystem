# Plan: Fix 09 — Add Dedup Check to `handleEstimationFailed()` (All Services)

## Objective

Add the `isDuplicateSagaEvent()` dedup check to `EstimationSagaConsumer.handleEstimationFailed()` in estimation-service, and add equivalent dedup to `handleEstimationFailed()` in customer-service and insurance-service. This ensures consistent idempotent event handling across all services.

## Current Behavior

All 3 services handle `ESTIMATION_FAILED` events via `handleEstimationFailed()`:

| Service | File | Line | Consumes ESTIMATION_FAILED? | Has dedup? |
|---------|------|------|----------------------------|------------|
| **estimation-service** | `EstimationSagaConsumer.java` | 196-198 | ✅ Logs and returns | ❌ No dedup |
| **customer-service** | `CustomerSagaConsumer.java` | 109-113 | ✅ Logs and returns | ❌ No dedup |
| **insurance-service** | `InsuranceSagaConsumer.java` | 146-149 | ✅ Logs and returns | ❌ No dedup |

**Current code (estimation-service):**
```java
private void handleEstimationFailed(EventEnvelope envelope, UUID sagaId) {
    log.warn("Estimation failed for saga: {} — no compensation needed (estimation state updated)", sagaId);
}
```

This is inconsistent with ALL other event handlers which check `isDuplicateSagaEvent()` before processing.

## Why This Matters

Even though `EstimationFailed` handling is currently a no-op (log only), **inconsistency is a risk**:

1. If future behavior is added to `handleEstimationFailed` (e.g., notification, compensation), the dedup guard must already be there
2. `EstimationFailed` is published by estimation-service itself when it rejects estimations due to customer/vehicle invalidation, calculation failure, or timeout — the same saga could receive this event multiple times (e.g., if multiple services publish it)
3. In a SAGA pattern, every event handler should be idempotent by default

## Cross-Service Analysis

### estimation-service: Uses DB-backed SagaEvent dedup
- `isDuplicateSagaEvent()` inserts into `saga_events` table, catches `DataIntegrityViolationException`
- SagaEvent entity + repository exist ✅
- `saga_events` table exists in `estimation_db` ✅

### customer-service: Uses in-memory DeduplicationStore
- `DeduplicationStore.isDuplicate()` / `markProcessed()` — ConcurrentHashMap with TTL
- No SagaEvent entity or repository
- No `saga_events` table in `customer_db`

### insurance-service: Uses in-memory DeduplicationStore
- Same as customer-service
- Also has `SagaAggregationStore` (additional in-memory store)

**This fix aligns with the existing dedup strategy for each service:**
- estimation-service → use `isDuplicateSagaEvent()` (DB-backed)
- customer-service → use `deduplicationStore.isDuplicate()` / `markProcessed()` (in-memory, until Fix 11 migrates them)
- insurance-service → use `deduplicationStore.isDuplicate()` / `markProcessed()` (in-memory, until Fix 11 migrates them)

## Context Files to Read First

### Estimation-service
1. **`services/estimation-service/src/main/java/.../estimation/config/EstimationSagaConsumer.java`**
   - `isDuplicateSagaEvent()` method (line ~38 — DB-backed dedup)
   - `handleEstimationFailed()` (line ~196 — no dedup)
   - Other handler methods (e.g., `handleCustomerValidated` line ~99) — reference for dedup pattern

2. **`services/estimation-service/src/test/java/.../estimation/config/EstimationSagaConsumerTest.java`**
   - Existing test for `handleEstimationFailed` (test `estimationFailedEvent_loggedOnly`)
   - Reference for argument captor pattern

### Customer-service
3. **`services/customer-service/src/main/java/.../customer/config/CustomerSagaConsumer.java`**
   - `handleEstimationFailed()` (line ~109 — no dedup)
   - `handleEstimationRequested()` (line ~63) — reference for dedup pattern using `DeduplicationStore`

4. **`services/customer-service/src/main/java/.../customer/config/DeduplicationStore.java`**
   - In-memory dedup store (full content)

5. **`services/customer-service/src/test/java/.../customer/saga/CustomerSagaConsumerTest.java`**
   - Tests for the consumer

### Insurance-service
6. **`services/insurance-service/src/main/java/.../insurance/config/InsuranceSagaConsumer.java`**
   - `handleEstimationFailed()` (line ~146 — no dedup)
   - `handleEstimationRequested()` (line ~77) — reference for dedup pattern

7. **`services/insurance-service/src/main/java/.../insurance/config/DeduplicationStore.java`**
   - In-memory dedup store

## Files to Modify

### Step 1: `EstimationSagaConsumer.java` (estimation-service)

**Add dedup guard to `handleEstimationFailed()`:**

**BEFORE:**
```java
private void handleEstimationFailed(EventEnvelope envelope, UUID sagaId) {
    log.warn("Estimation failed for saga: {} — no compensation needed (estimation state updated)", sagaId);
}
```

**AFTER:**
```java
private void handleEstimationFailed(EventEnvelope envelope, UUID sagaId) {
    String eventType = envelope.getEventType();

    if (isDuplicateSagaEvent(sagaId, eventType)) {
        return;
    }

    log.warn("Estimation failed for saga: {} — no compensation needed (estimation state updated)", sagaId);
}
```

No other code changes needed — `isDuplicateSagaEvent()` is already available in the same class.

### Step 2: `CustomerSagaConsumer.java` (customer-service)

**Add dedup guard to `handleEstimationFailed()`:**

**BEFORE:**
```java
private void handleEstimationFailed(EventEnvelope envelope) {
    log.warn("Estimation failed for saga: {} — no compensation needed (read-only validation)",
            envelope.getSagaId());
}
```

**AFTER:**
```java
private void handleEstimationFailed(EventEnvelope envelope) {
    UUID sagaId = envelope.getSagaId();
    String eventType = envelope.getEventType();
    String sagaIdStr = sagaId.toString();

    if (deduplicationStore.isDuplicate(sagaIdStr, eventType)) {
        log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
        return;
    }
    deduplicationStore.markProcessed(sagaIdStr, eventType);

    log.warn("Estimation failed for saga: {} — no compensation needed (read-only validation)", sagaId);
}
```

### Step 3: `InsuranceSagaConsumer.java` (insurance-service)

**Add dedup guard to `handleEstimationFailed()`:**

**BEFORE:**
```java
private void handleEstimationFailed(EventEnvelope envelope) {
    log.warn("Estimation failed for saga: {} — no compensation needed (calculation is stateless)",
            envelope.getSagaId());
}
```

**AFTER:**
```java
private void handleEstimationFailed(EventEnvelope envelope) {
    UUID sagaId = envelope.getSagaId();
    String eventType = envelope.getEventType();

    if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
        log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
        return;
    }
    deduplicationStore.markProcessed(sagaId.toString(), eventType);

    log.warn("Estimation failed for saga: {} — no compensation needed (calculation is stateless)", sagaId);
}
```

### Step 4: Update tests — `EstimationSagaConsumerTest.java`

Find the test `estimationFailedEvent_loggedOnly` (or equivalent).

**BEFORE:** Test should verify that `isDuplicateSagaEvent` is called. Update to mock `sagaEventRepository.save()`:

```java
@Test
void estimationFailedEvent_handledWithDedup() {
    UUID sagaId = UUID.randomUUID();
    EstimationFailedEvent event = EstimationFailedEvent.builder()
            .originalSagaId(sagaId)
            .reason("Customer validation failed")
            .failedStep("CUSTOMER_INVALIDATED")
            .build();

    // When not a duplicate
    when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(any());

    consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

    verify(sagaEventRepository).save(any(SagaEvent.class));
}
```

**After the fix**, a duplicate `EstimationFailed` event should be silently skipped:

```java
@Test
void duplicateEstimationFailedEvent_skipped() {
    UUID sagaId = UUID.randomUUID();
    EstimationFailedEvent event = EstimationFailedEvent.builder()
            .originalSagaId(sagaId)
            .reason("Customer validation failed")
            .failedStep("CUSTOMER_INVALIDATED")
            .build();

    String eventJson = buildEventJson(event, sagaId);
    Consumer<String> consumerFunc = consumer.processEstimationSaga(jsonMapper);

    // First call — mark as duplicate
    when(sagaEventRepository.save(any(SagaEvent.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

    // Second call — should skip gracefully (no error)
    consumerFunc.accept(eventJson);
    consumerFunc.accept(eventJson);

    // Only one attempt to save the saga event
    verify(sagaEventRepository, times(2)).save(any(SagaEvent.class));
}
```

### Step 5: Update tests — `CustomerSagaConsumerTest.java`

Add test verifying that duplicate `ESTIMATION_FAILED` is skipped. Reference the existing dedup test pattern for `ESTIMATION_REQUESTED` in the same file.

### Step 6: Update tests — `InsuranceSagaConsumerTest.java`

Add test verifying that duplicate `ESTIMATION_FAILED` is skipped. Reference the existing dedup test pattern for `ESTIMATION_REQUESTED` in the same file.

## Verification

```bash
# 1. Compile estimation-service
.\gradlew.bat :services:estimation-service:compileJava

# 2. Run estimation-service consumer tests
.\gradlew.bat :services:estimation-service:test --tests "*EstimationSagaConsumerTest"

# 3. Compile customer-service
.\gradlew.bat :services:customer-service:compileJava

# 4. Run customer-service tests
.\gradlew.bat :services:customer-service:test

# 5. Compile insurance-service
.\gradlew.bat :services:insurance-service:compileJava

# 6. Run insurance-service tests
.\gradlew.bat :services:insurance-service:test
```

## Execution Checklist

- [x] Read context files for all 3 services
- [x] Edit `EstimationSagaConsumer.java` — add `isDuplicateSagaEvent()` call to `handleEstimationFailed()`
- [x] Edit `CustomerSagaConsumer.java` — add `deduplicationStore.isDuplicate()`/`markProcessed()` to `handleEstimationFailed()`
- [x] Edit `InsuranceSagaConsumer.java` — add `deduplicationStore.isDuplicate()`/`markProcessed()` to `handleEstimationFailed()`
- [x] Update `EstimationSagaConsumerTest.java` — add duplicate test for EstimationFailed
- [x] Update `CustomerSagaConsumerTest.java` — add duplicate test for EstimationFailed
- [x] Update `InsuranceSagaConsumerTest.java` — add duplicate test for EstimationFailed
- [x] Compile all 3 services
- [x] All tests pass

## Risk Assessment

- **Risk:** VERY LOW. Dedup check is a guard that returns early on duplicate events. Since the current `handleEstimationFailed()` implementation is a no-op (log only), there is zero behavioral change for first-time events.
- **Regression risk:** Near zero. The change adds a conditional check before logging.
- **Dedup strategy difference:** estimation-service uses DB-backed dedup, others use in-memory. This is intentional until Fix 11 migrates them.
