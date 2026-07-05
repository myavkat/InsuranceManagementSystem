# Plan: Fix 14 — Outbox Relay Robustness: Zombie Recovery, Dead Field Removal, deleteAllInBatch

## Objective

Fix three medium-severity robustness issues in the estimation-service outbox relay:

1. **PUBLISHING zombie events never recovered** — If the relay crashes between `save(PUBLISHING)` and `publish()`, the event stays stuck in `PUBLISHING` status forever. The relay only queries `PENDING` events. No recovery mechanism exists.

2. **Dead `maxRetries` field in `OutboxEvent` entity** — The entity stores `maxRetries` per row (default 3 via `@Builder.Default`), but `OutboxRelay` / `OutboxProcessor` uses a global `@Value` config. The column is never queried and diverges from the actual config.

3. **`deleteAll()` memory risk** — `cleanupEvents()` uses Spring Data JPA's `deleteAll(Iterable)`, which loads all entities into memory before issuing individual DELETE statements. For large stale event backlogs, this can cause OOM.

---

## Cross-Service Analysis

| Service | Has outbox? | Affected? |
|---------|-----------|-----------|
| **estimation-service** | ✅ | ✅ All 3 issues apply |
| customer-service | ✅ (after Plan 11) | ✅ Same issues (apply fix after Plan 11) |
| insurance-service | ✅ (after Plan 11) | ✅ Same issues (apply fix after Plan 11) |

**All 3 services with outbox are affected.** This plan applies the fix to estimation-service (primary). If Plans 09 and 11 are complete, customer-service and insurance-service get the same fixes by virtue of copying the fixed `OutboxProcessor.java` and `OutboxEvent.java` from estimation-service.

---

## Context Files to Read First

### Primary — estimation-service
1. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/OutboxProcessor.java`** (new file from Plan 09)
   - `processOutbox()` — PUBLISHING→PENDING zombie recovery not present
   - `cleanupEvents()` — uses `deleteAll()` currently

2. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/OutboxEvent.java`** (70 lines)
   - `maxRetries` field — lines 43-45
   - `Status` enum — `PENDING, PUBLISHING, PUBLISHED, FAILED`

3. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/OutboxEventRepository.java`** (20 lines)
   - Query methods: `findTop10ByStatusOrderByCreatedAtAsc`, `findByStatusAndCreatedAtBefore`

4. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/OutboxProcessorTest.java`** (new/renamed from Plan 09)
   - Tests for zombie recovery and cleanup

### Secondary — SQL init scripts
5. **`infra/sql/estimation_db/init.sql`** — `outbox_events` table DDL (may need `max_retries` column removed)

---

## Files to Modify

### Issue 1: PUBLISHING zombie recovery

**File:** `OutboxProcessor.java` — `cleanupEvents()` method

Add recovery logic for stuck `PUBLISHING` events (partially added in Plan 09 design, but ensure it's present):

```java
public void cleanupEvents() {
    transactionTemplate.executeWithoutResult(status -> {
        // ... existing PUBLISHED cleanup (deleteAllInBatch) ...
        // ... existing FAILED cleanup (deleteAllInBatch) ...

        // 🔥 NEW: Recover PUBLISHING zombies (stuck for > 5 minutes)
        Instant publishingCutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<OutboxEvent> stalePublishing = outboxEventRepository
                .findByStatusAndCreatedAtBefore(OutboxEvent.Status.PUBLISHING, publishingCutoff);
        if (!stalePublishing.isEmpty()) {
            for (OutboxEvent event : stalePublishing) {
                event.setStatus(OutboxEvent.Status.PENDING);
                log.warn("Recovering stuck PUBLISHING outbox event id={}, sagaId={}", event.getId(), event.getSagaId());
            }
            outboxEventRepository.saveAll(stalePublishing);
            log.info("Recovered {} stuck PUBLISHING outbox events → PENDING", stalePublishing.size());
        }
    });
}
```

**Explanation:** If an event is `PUBLISHING` for >5 minutes, it was likely interrupted (relay crash, network timeout). Reset to `PENDING` so it's retried on the next `processOutbox()` cycle. The risk of double-publish is handled by downstream idempotent consumers (SagaEvent dedup).

### Issue 2: Remove dead `maxRetries` field

**File:** `OutboxEvent.java`

Delete the `maxRetries` field (lines 43-45) and its `@Builder.Default`:
```java
// DELETE these lines:
@Column(name = "max_retries")
@Builder.Default
private int maxRetries = 3;
```

The relay/processor already reads `maxRetries` from `@Value("${estimation.outbox.max-retries:3}")`. The per-row field is dead code.

**File:** `infra/sql/estimation_db/init.sql`

Remove the `max_retries` column from the `outbox_events` table DDL:
```sql
-- DELETE this line:
max_retries INT DEFAULT 3,
```

### Issue 3: Replace `deleteAll` with `deleteAllInBatch`

**File:** `OutboxProcessor.java` — `cleanupEvents()` method

```java
// BEFORE:
outboxEventRepository.deleteAll(stalePublished);
outboxEventRepository.deleteAll(staleFailed);

// AFTER:
outboxEventRepository.deleteAllInBatch(stalePublished);
outboxEventRepository.deleteAllInBatch(staleFailed);
```

`deleteAllInBatch()` issues a single JPQL `DELETE FROM outbox_events WHERE id IN (?)` — constant memory, much faster for large sets.

**File:** `OutboxEventRepository.java`

Add the batch delete method (Spring Data JPA standard):
```java
// Already available via JpaRepository — no code needed.
// deleteAllInBatch() is inherited from JpaRepository.
// Verify it exists (it's a standard method).
```

Actually, `deleteAllInBatch(Iterable)` is available on `JpaRepository` by default. No repository change needed.

---

## Test Updates

### OutboxProcessorTest.java changes

**Test 1: PUBLISHING zombie recovery**
```java
@Test
void stalePublishingEvents_areRecoveredToPending() {
    // Given: a PUBLISHING event older than 5 minutes
    OutboxEvent zombie = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .sagaId(UUID.randomUUID())
            .topic("estimation.saga")
            .payload("{}")
            .status(OutboxEvent.Status.PUBLISHING)
            .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
            .build();

    when(outboxEventRepository.findByStatusAndCreatedAtBefore(
            eq(OutboxEvent.Status.PUBLISHING), any(Instant.class)))
            .thenReturn(List.of(zombie));

    // When: cleanup runs
    outboxProcessor.cleanupEvents();

    // Then: zombie is reset to PENDING
    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository).saveAll(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
}
```

**Test 2: deleteAllInBatch called**
```java
@Test
void cleanupUsesBatchDelete() {
    List<OutboxEvent> stale = List.of(createPublishedEvent(), createPublishedEvent());
    when(outboxEventRepository.findByStatusAndCreatedAtBefore(eq(OutboxEvent.Status.PUBLISHED), any()))
            .thenReturn(stale);

    outboxProcessor.cleanupEvents();

    verify(outboxEventRepository).deleteAllInBatch(stale);
    verify(outboxEventRepository, never()).deleteAll(anyIterable());
}
```

**Test 3: No maxRetries field assertion**

After removing the field, any test that set `maxRetries` on an `OutboxEvent` via builder must be updated. Search `OutboxProcessorTest.java` and other test files for `.maxRetries(` and remove that builder call.

---

## Verification

```bash
# 1. Compile estimation-service
.\gradlew.bat :services:estimation-service:compileJava

# 2. Run OutboxProcessor tests
.\gradlew.bat :services:estimation-service:test --tests "*OutboxProcessorTest"

# 3. Run all estimation-service tests
.\gradlew.bat :services:estimation-service:test

# 4. If customer-service and insurance-service have outbox (post-Plan 11):
.\gradlew.bat :services:customer-service:test
.\gradlew.bat :services:insurance-service:test
```

---

## Execution Checklist

### Issue 1 — PUBLISHING zombie recovery
- [x] Add zombie recovery logic to `OutboxProcessor.cleanupEvents()`
- [x] Add test for `stalePublishingEvents_areRecoveredToPending()`
- [x] Verify `findByStatusAndCreatedAtBefore` works for `PUBLISHING` status

### Issue 2 — Remove dead `maxRetries` field
- [x] Delete `maxRetries` field from `OutboxEvent.java`
- [x] Remove `max_retries` column from `infra/sql/estimation_db/init.sql`
- [x] Remove `max_retries` column from `infra/sql/customer_db/init.sql`
- [x] Remove `max_retries` column from `infra/sql/insurance_db/init.sql`
- [x] Update all test builders that set `.maxRetries(...)` — remove the call
- [x] Verify no code references `event.getMaxRetries()` or `event.setMaxRetries()`

### Issue 3 — `deleteAll` → `deleteAllInBatch`
- [x] Replace `deleteAll()` with `deleteAllInBatch()` in `OutboxProcessor.cleanupEvents()`
- [x] Add test verifying batch delete is used
- [x] Remove any `verify(outboxEventRepository).deleteAll(stalePublished)` assertions from tests

### Final
- [x] Compile estimation-service: SUCCESS
- [x] All tests pass
- [x] Apply same fixes to customer-service and insurance-service `OutboxProcessor.java` if they exist (post-Plan 11)

---

## Risk Assessment

- **Risk: VERY LOW.** All changes are within a single component (`OutboxProcessor`) and its entity. No API changes. No behavioral changes for normal operation.
  - **Issue 1:** Zombie recovery is a safety net — only triggers for events stuck >5 minutes. Downstream idempotent consumers handle any double-publish.
  - **Issue 2:** Pure dead code removal. The `maxRetries` field was never read by anyone.
  - **Issue 3:** `deleteAllInBatch` is functionally identical to `deleteAll` but uses JPQL bulk delete instead of entity-by-entity. Same result, less memory.
- **Test brittleness:** Any test that explicitly checked for `deleteAll()` instead of `deleteAllInBatch()` needs updating. This is expected and documented above.

---

## Dependencies

- **Prerequisite: Plan 09** (OutboxRelay refactor) — `OutboxProcessor.java` must exist.
- **Affected by: Plan 11** (dual-write fix) — If customer-service and insurance-service get outbox, apply the same fixes to their `OutboxProcessor.java` and `OutboxEvent.java`.
- **Affected by: Plan 12** (DRY extraction) — After Plan 12 moves `OutboxEvent` to `common`, the `maxRetries` field removal applies to the shared entity.
