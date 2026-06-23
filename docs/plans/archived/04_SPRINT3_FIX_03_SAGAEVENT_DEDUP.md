# Plan: Fix 03 — Replace In-Memory Dedup with DB-Backed SagaEvent Table

## Objective
Replace the in-memory `DeduplicationStore` in `EstimationSagaConsumer` with the DB-backed `SagaEvent` table (via `SagaEventRepository`) for durable, crash-safe, horizontally-scalable event deduplication. Remove the `DeduplicationStore.java` class and its tests after the migration.

## Why
1. **Durability gap:** In-memory dedup is lost on service restart — duplicate events can be re-processed
2. **Horizontal scaling:** In-memory dedup doesn't work with multiple service replicas
3. **Dead code:** `SagaEvent` entity and `SagaEventRepository` already exist with the correct schema and unique constraint but are unused
4. **Database guarantees:** The `UNIQUE(saga_id, event_type)` constraint on the `saga_events` table provides atomic, durable dedup at the database level

## Context Files to Read First

1. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`** — All dedup usage sites (lines 78-82, 97-101, 115-119, 151-155)
2. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/SagaEvent.java`** — Entity structure, builder, `@PrePersist`, unique constraint
3. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/SagaEventRepository.java`** — Existing methods (`existsBySagaIdAndEventType`, `findBySagaIdAndEventType`)
4. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/DeduplicationStore.java`** — To be deleted (60 lines)
5. **`infra/sql/estimation_db/init.sql`** — Confirm `saga_events` table schema matches entity
6. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java`** — Test changes needed
7. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/DeduplicationStoreTest.java`** — To be deleted

## Design: Try-Insert Pattern

The most robust approach is **try-insert, catch duplicate**:

```java
// Attempt to insert a dedup record
SagaEvent dedup = SagaEvent.builder()
        .sagaId(sagaId)
        .eventType(eventType)
        .build();
try {
    sagaEventRepository.save(dedup);
} catch (DataIntegrityViolationException e) {
    // Unique constraint violation → duplicate event
    log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
    return;
}
```

**Why this pattern instead of check-then-insert?**
- `existsBySagaIdAndEventType()` + `save()` is NOT atomic — two concurrent requests can both pass the check, then one save succeeds and the other throws `DataIntegrityViolationException` anyway
- The unique constraint is the ULTIMATE authority — let the database enforce it
- The try-catch is simpler and correct under all concurrency scenarios

**Trade-off:** Most inserts succeed (not duplicates), so the exception path is rarely exercised. The cost is minimal.

## Files to Modify

### 1. `EstimationSagaConsumer.java` — Replace dedup logic

#### Step 1a: Add dependency
Add `SagaEventRepository` as a constructor dependency (replace `DeduplicationStore`):

```java
// BEFORE:
private final DeduplicationStore deduplicationStore;

// AFTER:
private final SagaEventRepository sagaEventRepository;
```

Add the import:
```java
import com.insurancemanagementsystem.estimation.repository.SagaEventRepository;
import com.insurancemanagementsystem.estimation.entity.SagaEvent;
import org.springframework.dao.DataIntegrityViolationException;
```

Remove the old import:
```java
// REMOVE: import com.insurancemanagementsystem.estimation.config.DeduplicationStore;
```

#### Step 1b: Create common dedup guard method

Extract the repeated dedup pattern into a private method (DRY within the consumer itself):

```java
/**
 * Returns true if this event was already processed (duplicate).
 * Inserts a dedup record; if a unique constraint violation occurs,
 * the event is a duplicate.
 *
 * @return true if duplicate (caller should skip processing)
 */
private boolean isDuplicateSagaEvent(UUID sagaId, String eventType) {
    SagaEvent dedup = SagaEvent.builder()
            .sagaId(sagaId)
            .eventType(eventType)
            .build();
    try {
        sagaEventRepository.save(dedup);
        return false; // Successfully inserted → not a duplicate
    } catch (DataIntegrityViolationException e) {
        log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
        return true;
    }
}
```

#### Step 1c: Replace all 4 dedup guards

Replace each occurrence of:
```java
if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
    log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
    return;
}
deduplicationStore.markProcessed(sagaId.toString(), eventType);
```

With:
```java
if (isDuplicateSagaEvent(sagaId, eventType)) {
    return;
}
```

Affected methods in `EstimationSagaConsumer.java`:
- `handleCustomerValidated()` — lines ~78-82
- `handleVehicleValidated()` — lines ~97-101
- `handlePremiumCalculated()` — lines ~115-119
- `handleFailed()` — lines ~151-155

#### Step 1d: `handleEstimationFailed` — No dedup needed
`handleEstimationFailed()` currently does NOT check dedup (it just logs). This is correct — leave it as-is. This event is idempotent (log-only) and the dedup table would just fill up with useless entries.

### 2. Delete `DeduplicationStore.java`

**Delete:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/DeduplicationStore.java`

### 3. Delete `DeduplicationStoreTest.java`

**Delete:** `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/DeduplicationStoreTest.java`

### 4. Update `EstimationSagaConsumerTest.java`

The test currently mocks `DeduplicationStore`. Must be refactored:

#### Step 4a: Replace mock
```java
// BEFORE:
@Mock
private DeduplicationStore deduplicationStore;

// AFTER:
@Mock
private SagaEventRepository sagaEventRepository;
```

Remove import:
```java
// REMOVE: import com.insurancemanagementsystem.estimation.config.DeduplicationStore;
```
Add imports:
```java
import com.insurancemanagementsystem.estimation.repository.SagaEventRepository;
import com.insurancemanagementsystem.estimation.entity.SagaEvent;
import org.springframework.dao.DataIntegrityViolationException;
```

#### Step 4b: Update test setup for dedup behavior

For tests where the event is NOT a duplicate:
```java
// OLD:
when(deduplicationStore.isDuplicate(anyString(), anyString())).thenReturn(false);
// ... test runs ...
verify(deduplicationStore).markProcessed(sagaId.toString(), eventType);

// NEW:
when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(any());
// ... test runs ...
verify(sagaEventRepository).save(any(SagaEvent.class));
```

For tests where the event IS a duplicate:
```java
// OLD:
when(deduplicationStore.isDuplicate(anyString(), anyString())).thenReturn(true);

// NEW:
when(sagaEventRepository.save(any(SagaEvent.class)))
        .thenThrow(new DataIntegrityViolationException("unique constraint violation"));
```

#### Step 4c: Update test assertion for dedup mark

The old tests verify `deduplicationStore.markProcessed()`. The new tests should verify `sagaEventRepository.save()` was called with a `SagaEvent` containing the correct `sagaId` and `eventType`. Use `ArgumentCaptor`:

```java
@Captor
private ArgumentCaptor<SagaEvent> sagaEventCaptor;

// In test:
verify(sagaEventRepository).save(sagaEventCaptor.capture());
SagaEvent saved = sagaEventCaptor.getValue();
assertThat(saved.getSagaId()).isEqualTo(sagaId);
assertThat(saved.getEventType()).isEqualTo(EventConstants.CUSTOMER_VALIDATED);
```

## Verification

```bash
# 1. Compile
.\gradlew.bat :services:estimation-service:compileJava

# 2. Run tests (unit + integration)
.\gradlew.bat :services:estimation-service:test

# 3. Run specific consumer test
.\gradlew.bat :services:estimation-service:test --tests "*EstimationSagaConsumerTest"
```

Make sure ALL 15 test cases in `EstimationSagaConsumerTest` pass after refactoring. The test cases that specifically test dedup behavior are:
- Test 5: "duplicate PremiumCalculated is skipped"
- Test 8: "duplicate CustomerInvalidated is skipped"  
- Test 10: "duplicate VehicleInvalidated is skipped"
- Test 11: "duplicate CalculationFailed is skipped"

Also verify `DeduplicationStoreTest` no longer exists (should not cause a test discovery error — Gradle just skips missing test classes).

## Files Summary

### Modified
- `services/estimation-service/src/main/java/.../config/EstimationSagaConsumer.java` — Replace DeduplicationStore with SagaEventRepository + isDuplicateSagaEvent() method
- `services/estimation-service/src/test/java/.../config/EstimationSagaConsumerTest.java` — Replace mock DeduplicationStore with mock SagaEventRepository

### Deleted
- `services/estimation-service/src/main/java/.../config/DeduplicationStore.java`
- `services/estimation-service/src/test/java/.../config/DeduplicationStoreTest.java`

### Unchanged (but now used)
- `services/estimation-service/src/main/java/.../entity/SagaEvent.java` — Already exists, now wired into consumer
- `services/estimation-service/src/main/java/.../repository/SagaEventRepository.java` — Already exists, now wired into consumer

---

## Important Notes for Implementer

1. **Transactions:** The `isDuplicateSagaEvent()` method is called from within consumer handlers that are NOT `@Transactional`. The `sagaEventRepository.save()` call creates its own implicit transaction (Spring Data JPA default). This means the dedup insert is committed immediately — even if the subsequent business logic in the handler fails, the dedup record persists. This is actually desired behavior: we WANT to record that we saw the event, even if processing fails, so retries don't re-process.

2. **DataIntegrityViolationException import:** This is `org.springframework.dao.DataIntegrityViolationException` — part of `spring-tx` which is already a transitive dependency of `spring-data-jpa` in the estimation-service.

3. **Do NOT modify SagaEvent entity or repository** — they are already correct. Only wire them into the consumer.

4. **Do NOT create new tests for SagaEventRepository** — the dedup behavior is tested via the consumer tests. The repository is a Spring Data JPA interface with no custom implementation to test.
