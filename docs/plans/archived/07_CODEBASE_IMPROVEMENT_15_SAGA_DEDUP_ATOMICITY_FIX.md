# Plan 15: Fix TOCTOU Dedup Race — Replace existsBySagaIdAndEventType()+save() with Atomic tryInsertDedup()

## Severity: CRITICAL — Data loss under concurrent Kafka delivery

## Status

- [ ] Replace exists+save with tryInsertDedup in CustomerSagaConsumer (1 handler)
- [ ] Replace exists+save with tryInsertDedup in InsuranceSagaConsumer (4 handlers)
- [ ] Run affected tests and verify

---

## Context

### The Problem

The `CustomerSagaConsumer` and `InsuranceSagaConsumer` use a **non-atomic** dedup pattern:

```java
// TOCTOU — Time-of-Check-Time-of-Use race
if (sagaEventRepository.existsBySagaIdAndEventType(sagaId, eventType)) {
    return; // duplicate
}
sagaEventRepository.save(SagaEvent.builder()...);
```

Under concurrent Kafka delivery (at-least-once semantics, partition rebalance, consumer retries), two threads can both pass the `existsBySagaIdAndEventType()` check before either INSERTs. The second INSERT hits the `UNIQUE(saga_id, event_type)` constraint, throwing an **unhandled `DataIntegrityViolationException`** that propagates out of the `TransactionTemplate` callback and crashes the consumer thread. In `InsuranceSagaConsumer`, this also corrupts the in-memory `SagaAggregationStore` because the losing thread's `storeAndCheckReady()` call has already mutated in-memory state that cannot be rolled back.

### The Fix

The `SagaEventRepository` already provides an atomic alternative via `tryInsertDedup()`:

```java
// Located in: common/common-message/.../repository/SagaEventRepository.java:20-31
default boolean tryInsertDedup(UUID sagaId, String eventType) {
    SagaEvent dedup = SagaEvent.builder()
            .sagaId(sagaId)
            .eventType(eventType)
            .build();
    try {
        save(dedup);
        return false; // new event, not a duplicate
    } catch (DataIntegrityViolationException e) {
        return true;  // duplicate — already processed
    }
}
```

This atomically INSERTs the dedup marker and catches the constraint violation if it already exists. It is already correctly used by:
- `EstimationSagaConsumer` — all 5 handlers
- `CustomerSagaConsumer.handleEstimationFailed()` (line 131)
- `InsuranceSagaConsumer.handleEstimationFailed()` (line 187)

### Scope of the Fix

Replace the five remaining `existsBySagaIdAndEventType()` + `save()` call sites with `tryInsertDedup()` — a purely mechanical change at each site.

---

## Files to Modify

### 1. CustomerSagaConsumer.java
**Path:** `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerSagaConsumer.java`

**Current code (lines 77-82):**
```java
transactionTemplate.executeWithoutResult(status -> {
    // Idempotency check using SELECT — the UNIQUE constraint guards against races at commit time
    if (sagaEventRepository.existsBySagaIdAndEventType(sagaId, eventType)) {
        log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
        return;
    }

    // Insert dedup marker
    sagaEventRepository.save(SagaEvent.builder()
            .sagaId(sagaId)
            .eventType(eventType)
            .build());
    // ... rest of handler
```

**Replace with:**
```java
transactionTemplate.executeWithoutResult(status -> {
    if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
        log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
        return;
    }
    // ... rest of handler (dedup marker already inserted atomically)
```

**Note:** The explicit `sagaEventRepository.save(SagaEvent.builder()...)` lines (84-88) are removed — `tryInsertDedup()` handles the INSERT via its own `save()` call.

### 2. InsuranceSagaConsumer.java
**Path:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`

Four handlers need the same fix. The pattern is identical — replace `existsBySagaIdAndEventType()` + `save(SagaEvent.builder()...)` with `tryInsertDedup()`:

| Handler | Line (exists check) | Lines (save call to remove) |
|---------|---------------------|-----------------------------|
| `handleEstimationRequested` | 87 | 92-95 |
| `handleCustomerValidated` | 111 | 116-119 |
| `handleVehicleValidated` | 135 | 140-143 |
| `handleInvalidated` | 159 | 164-167 |

**Pattern to apply at each site — change FROM:**
```java
transactionTemplate.executeWithoutResult(status -> {
    if (sagaEventRepository.existsBySagaIdAndEventType(sagaId, eventType)) {
        log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
        return;
    }

    sagaEventRepository.save(SagaEvent.builder()
            .sagaId(sagaId)
            .eventType(eventType)
            .build());

    // ... handler-specific logic
```

**Change TO:**
```java
transactionTemplate.executeWithoutResult(status -> {
    if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
        log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
        return;
    }

    // ... handler-specific logic (dedup marker already inserted)
```

---

## Files to Read for Full Context

| File | Purpose |
|------|---------|
| `common/common-message/src/main/java/com/insurancemanagementsystem/common/repository/SagaEventRepository.java` | Verify `tryInsertDedup()` implementation is correct |
| `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java` | Reference implementation — already uses `tryInsertDedup()` correctly for all handlers |
| `docs/outlines/03_SAGA_PATTERN.md` | SAGA idempotency requirements |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Java conventions |

---

## Test Files

| File | What to verify |
|------|---------------|
| `services/customer-service/src/test/java/.../customer/saga/CustomerSagaConsumerTest.java` | Tests should still pass; verify mock setup uses `tryInsertDedup()` not `existsBySagaIdAndEventType()` |
| `services/insurance-service/src/test/java/.../insurance/saga/InsuranceSagaConsumerTest.java` | Tests should still pass; verify mock setup uses `tryInsertDedup()` not `existsBySagaIdAndEventType()` |

---

## Verification Checklist

- [ ] `CustomerSagaConsumer.handleEstimationRequested()` uses `tryInsertDedup()`
- [ ] `InsuranceSagaConsumer.handleEstimationRequested()` uses `tryInsertDedup()`
- [ ] `InsuranceSagaConsumer.handleCustomerValidated()` uses `tryInsertDedup()`
- [ ] `InsuranceSagaConsumer.handleVehicleValidated()` uses `tryInsertDedup()`
- [ ] `InsuranceSagaConsumer.handleInvalidated()` uses `tryInsertDedup()`
- [ ] No remaining calls to `existsBySagaIdAndEventType()` in production code (may still exist in tests)
- [ ] All existing saga consumer tests pass

---

## Risk Assessment

**RISK: VERY LOW.** This is a mechanical replacement of a non-atomic pattern with its existing atomic equivalent. The `tryInsertDedup()` method is already battle-tested in `EstimationSagaConsumer` across all 5 event handlers. The change:
- Does not alter the transactional boundary (still inside `transactionTemplate.executeWithoutResult`)
- Does not change the logging output
- Does not change the return type or behavior (duplicate → skip, new → proceed)
- Reduces 2 DB round-trips to 1 (SELECT+INSERT → INSERT only)
