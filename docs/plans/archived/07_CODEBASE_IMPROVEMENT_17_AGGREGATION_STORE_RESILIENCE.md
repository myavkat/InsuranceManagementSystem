# Plan 17: SagaAggregationStore Resilience — Fix In-Memory State Loss

## Severity: CRITICAL (retrieve() before DB commit) / HIGH (in-memory crash loss)

## Status

- [ ] Fix `retrieve()` order in `InsuranceSagaConsumer.calculatePremium()` — defer or guard against TX rollback
- [ ] Migrate `SagaAggregationStore` from in-memory `ConcurrentHashMap` to DB-backed persistence
- [ ] Run insurance-service saga consumer tests and verify

---

## Context

### Problem 1: retrieve() Removes State BEFORE DB Commit

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`

The `calculatePremium()` method (line 197) is called from within `transactionTemplate.executeWithoutResult()`. At line 198, it calls:

```java
SagaAggregationStore.SagaState state = aggregationStore.retrieve(sagaId.toString());
```

The `retrieve()` method (`SagaAggregationStore.java` line 63-66) atomically removes state from the `ConcurrentHashMap`:

```java
public SagaState retrieve(String sagaId) {
    SagaState state = store.remove(sagaId);  // IRREVERSIBLE
    return state;
}
```

If the subsequent database operations fail:
- `outboxEventRepository.save()` at line 261 throws `RuntimeException` (DB deadlock, constraint violation)
- The `TransactionTemplate` rolls back the entire DB transaction — including the dedup marker for the triggering event
- But the in-memory state was already `remove()`d — it's permanently gone
- On Kafka redelivery, `storeAndCheckReady()` creates a fresh empty state with only ONE event type (the redelivered one)
- The other two event types' data is lost — **saga permanently deadlocked at STARTED**

**Worse than the crash-loss problem that justified DeduplicationStore→DB migration.** The old pattern could only lose state on process crash. This pattern loses state on any DB error (deadlock, transient network issue, constraint violation) — which are routine occurrences.

### Problem 2: SagaAggregationStore Is Entirely In-Memory

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/SagaAggregationStore.java`

The `DeduplicationStore` (previously used by customer-service and insurance-service) was replaced with the DB-backed `SagaEvent` entity specifically because:

1. On service restart, all in-memory state is lost
2. Multiple service instances each have their own in-memory store — the same event can be processed by different instances

Yet `SagaAggregationStore` remains a `ConcurrentHashMap<String, SagaState>` with TTL-based eviction (10 minutes). It holds the critical `ESTIMATION_REQUESTED` + `CUSTOMER_VALIDATED` + `VEHICLE_VALIDATED` correlation state needed for premium calculation. A rolling restart during active sagas permanently orphans all in-flight correlations — the dedup rows for individual events are in the DB, but the aggregation state that ties them together is gone.

---

## Fix Strategy

### Fix 1: Defer retrieve() to After Outbox Save (Minimal Fix)

Move `aggregationStore.retrieve()` in `calculatePremium()` to AFTER the outbox save commits successfully. This prevents state loss on DB rollback.

**Current code (InsuranceSagaConsumer.java lines 197-261):**
```java
private void calculatePremium(UUID sagaId, UUID traceId) {
    SagaAggregationStore.SagaState state = aggregationStore.retrieve(sagaId.toString());
    // ... use state for business logic ...
    outboxEventRepository.save(buildOutboxEvent(sagaId, outcome, ...));
}
```

**Fix approach — use try/finally or two-phase:**
```java
private void calculatePremium(UUID sagaId, UUID traceId) {
    // Phase 1: Peek at state WITHOUT removing
    SagaAggregationStore.SagaState state = aggregationStore.peek(sagaId.toString());
    if (state == null) {
        log.warn("SAGA state not found for sagaId={} — already consumed?", sagaId);
        return;
    }
    
    // ... business logic using state (unchanged) ...
    
    // Phase 2: Save to DB
    outboxEventRepository.save(buildOutboxEvent(sagaId, outcome, ...));
    
    // Phase 3: Only remove AFTER DB commit succeeds
    aggregationStore.remove(sagaId.toString());
}
```

This requires adding a `peek()` method to `SagaAggregationStore` that reads without removing:
```java
public SagaState peek(String sagaId) {
    return store.get(sagaId);  // get() does NOT remove
}
```

### Fix 2: Migrate SagaAggregationStore to DB-Backed Persistence (Full Fix)

Replace the `ConcurrentHashMap` with a DB table `saga_aggregations` that mirrors the schema:

```sql
CREATE TABLE IF NOT EXISTS saga_aggregations (
    saga_id UUID PRIMARY KEY,
    estimation_request_payload JSONB NOT NULL,
    customer_validated_payload JSONB,
    vehicle_validated_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

A `SagaAggregationRepository` (JPA `JpaRepository`) with atomic operations:
- `store(sagaId, eventType, payload)` — UPSERT that sets the appropriate column
- `findAndDelete(sagaId)` — SELECT FOR UPDATE + DELETE in one transaction (atomic consume)

This makes the aggregation store:
- Survive restarts
- Work across multiple instances (single DB source of truth)
- Atomically consume within the same DB transaction as the outbox save — `retrieve()` rolls back with `@Transactional` rollback

**This is the preferred fix but requires more work.** Start with Fix 1 as an immediate mitigation, then plan Fix 2 as a follow-up.

---

## Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | `services/insurance-service/src/main/java/.../insurance/config/SagaAggregationStore.java` | Add `peek()` method (no-remove read); `retrieve()` continues to exist for the invalidated case |
| 2 | `services/insurance-service/src/main/java/.../insurance/config/InsuranceSagaConsumer.java` | Change `calculatePremium()` to `peek()` then `remove()` after DB commit |
| 3 | `infra/sql/insurance_db/init.sql` | (Future) Add `saga_aggregations` table for DB-backed store |

---

## Files to Read for Full Context

| File | Purpose |
|------|---------|
| `services/insurance-service/src/main/java/.../config/SagaAggregationStore.java` | Full store implementation — add `peek()` |
| `services/insurance-service/src/main/java/.../config/InsuranceSagaConsumer.java` | All call sites of `retrieve()`, `storeAndCheckReady()`, `remove()` |
| `services/insurance-service/src/test/java/.../saga/InsuranceSagaConsumerTest.java` | Test coverage for saga flow |
| `common/common-message/src/main/java/.../entity/SagaEvent.java` | Reference — entity pattern for DB-backed state |
| `docs/outlines/03_SAGA_PATTERN.md` | SAGA semantics and state management |
| `infra/sql/insurance_db/init.sql` | Current insurance DB schema |

---

## Verification Checklist

- [ ] `SagaAggregationStore.peek()` added — returns state without removing
- [ ] `calculatePremium()` uses `peek()` + deferred `remove()` instead of `retrieve()`
- [ ] `handleInvalidated()` continues to use `remove()` (no calculation after invalidation, state is consumed)
- [ ] All existing `InsuranceSagaConsumerTest` tests pass
- [ ] New test: DB error during outbox save → aggregation state still present on retry
- [ ] New test: `peek()` returns correct state without modifying map

---

## Risk Assessment

**Fix 1 (peek+remove): LOW.** Adds one method, changes one call site. The `remove(sagaId)` call after the outbox save may never execute if the `TransactionTemplate` rolls back and the method exits via exception — but that's the desired behavior (state is preserved for retry). The worst case is a memory leak from abandoned sagas that neither complete nor get removed — the existing 10-minute TTL cleanup handles this.

**Fix 2 (DB-backed store): MEDIUM.** Requires schema migration and a new entity/repository. Follows the same pattern as the `SagaEvent` dedup migration that was already completed successfully.
