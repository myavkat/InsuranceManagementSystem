# Plan 16: Fix Missing Transaction Boundaries in EstimationSagaConsumer + SagaTimeoutService

## Severity: CRITICAL (EstimationSagaConsumer ACID gap) / HIGH (SagaTimeoutService rollback loop)

## Status

- [x] Add `TransactionTemplate` to `EstimationSagaConsumer` — wrap all handlers
- [x] Fix `SagaTimeoutService.checkForTimedOutSagas()` — per-iteration catch poisons EntityManager
- [x] Fix or remove `SagaTimeoutServiceTest.exceptionDuringProcessing_otherEstimationsStillProcessed` — gives false confidence
- [x] Run all affected tests and verify

---

## Context

### Problem 1: EstimationSagaConsumer Handlers Have No Transaction Boundary

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`

The `handleFailed()` method (line 148) and `handlePremiumCalculated()` method (line 114) perform **two separate database writes** without any wrapping transaction:

```java
// handleFailed() lines 177-178:
estimationRepository.save(estimation);      // COMMITS in its own implicit TX (JpaRepository default)
outboxEventRepository.save(outboxEvent);    // COMMITS in its own implicit TX
```

If `outboxEventRepository.save()` fails (transient DB error, constraint violation):
- `estimationRepository.save()` is already committed → estimation is REJECTED
- No outbox event is persisted → `EstimationFailed` event is never published
- Downstream services never learn of the rejection → **saga is permanently orphaned**

The same gap exists in `handlePremiumCalculated()` (lines 117-139):
- `tryInsertDedup()` commits in its own TX (line 117)
- `estimationRepository.save()` commits in its own TX (line 139)
- If estimation save fails after dedup succeeds, the event is dedup-marked as processed but the estimation never transitions → retry is silently skipped

**Contrast with working implementations:**
- `CustomerSagaConsumer` wraps everything in `transactionTemplate.executeWithoutResult()` (line 77)
- `InsuranceSagaConsumer` wraps everything in `transactionTemplate.executeWithoutResult()` (line 86)

### Problem 2: SagaTimeoutService Per-iteration Catch Poisons EntityManager

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java`

The `checkForTimedOutSagas()` method at line 39 is annotated `@Transactional` on the whole method, but catches exceptions per-iteration at line 72:

```java
for (Estimation estimation : staleEstimations) {
    try {
        // ... serialize, save estimation, save outbox ...
    } catch (Exception e) {
        log.error("Failed to process timeout for estimation id={}", estimation.getId(), e);
    }
}
```

If any single iteration throws a `DataIntegrityViolationException` or other exception that Spring's exception translator marks as rollback-only:
1. The EntityManager's transaction is poisoned (marked rollback-only)
2. The catch block swallows the exception — continues to the next iteration
3. All subsequent `save()` calls fail because the transaction cannot commit
4. When the method returns, Spring throws `UnexpectedRollbackException`
5. **ALL** estimations in the batch are rolled back — including ones that appeared to succeed
6. The next scheduled run (30 seconds later) repeats the same failure — **permanent retry loop**

### Problem 3: Test Gives False Confidence

**File:** `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutServiceTest.java`

The test `exceptionDuringProcessing_otherEstimationsStillProcessed` (line 130-149) mocks two saves where the first throws and the second succeeds. In real Hibernate, a save failure within a `@Transactional` method that throws `DataIntegrityViolationException` marks the EntityManager rollback-only — the second save would NOT succeed. The mock-based test bypasses real JPA semantics and gives false confidence that the per-item catch-resume pattern works.

---

## Fix Strategy

### Fix 1: Wrap EstimationSagaConsumer Handlers in TransactionTemplate

Inject `TransactionTemplate` into `EstimationSagaConsumer` (follow the same pattern as `CustomerSagaConsumer` and `InsuranceSagaConsumer`):

**Step 1:** Add field to `EstimationSagaConsumer` (add after line 32):
```java
private final TransactionTemplate transactionTemplate;
```

**Step 2:** Wrap `handlePremiumCalculated()` body inside `transactionTemplate.executeWithoutResult()`:
```java
private void handlePremiumCalculated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
    String eventType = envelope.getEventType();
    
    transactionTemplate.executeWithoutResult(status -> {
        if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
            return;
        }
        // ... rest of handler (existing code, unchanged) ...
    });
}
```

**Step 3:** Wrap `handleFailed()` body inside `transactionTemplate.executeWithoutResult()`:
```java
private void handleFailed(EventEnvelope envelope, UUID sagaId, UUID traceId, String reason, JsonMapper jsonMapper) {
    String eventType = envelope.getEventType();
    
    transactionTemplate.executeWithoutResult(status -> {
        if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
            return;
        }
        // ... rest of handler (existing code, unchanged) ...
    });
}
```

**Step 4 (OPTIONAL):** Also wrap `handleCustomerValidated()` and `handleVehicleValidated()` for consistency. These handlers are log-only (no state mutation), so the risk is negligible, but consistent transaction wrapping makes the code uniform.

### Fix 2: Fix SagaTimeoutService — Remove Per-iteration Catch

**Option A (Recommended): Remove the per-iteration catch**
Let the exception propagate. `@Transactional` will roll back the entire batch, and the next scheduled run (30 seconds) will retry. This is correct behavior — partial commits are worse than full rollback+retry.

```java
for (Estimation estimation : staleEstimations) {
    // REMOVE try-catch wrapper
    UUID sagaId = estimation.getSagaId();
    // ... (existing logic) ...
    estimationRepository.save(estimation);
    outboxEventRepository.save(outboxEvent);
}
```

**Option B: Process each estimation in its own transaction**
Use `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW` for each iteration so that a single failure doesn't poison the whole batch. This requires injecting `TransactionTemplate` and calling `executeWithoutResult` inside the loop. However, this changes the method's `@Transactional` semantics — decide whether batch atomicity matters.

**Choose Option A.** The 30-second retry window means no single estimation is delayed by more than 30 seconds, and full rollback is safer than partial commit.

### Fix 3: Remove or Rewrite Misleading Test

Delete the test `exceptionDuringProcessing_otherEstimationsStillProcessed` at line 130-149 of `SagaTimeoutServiceTest.java`. If Option A from Fix 2 is chosen, the test is no longer applicable (the method no longer catches per-iteration exceptions).

If Option B is chosen, rewrite the test to use `TransactionTemplate` mocks instead of mocking `save()` behavior that contradicts JPA semantics.

---

## Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | `services/estimation-service/src/main/java/.../estimation/config/EstimationSagaConsumer.java` | Add `TransactionTemplate` field; wrap `handlePremiumCalculated()` and `handleFailed()` in `transactionTemplate.executeWithoutResult()` |
| 2 | `services/estimation-service/src/main/java/.../estimation/service/SagaTimeoutService.java` | Remove per-iteration `try-catch` at line 52-74 |
| 3 | `services/estimation-service/src/test/java/.../estimation/service/SagaTimeoutServiceTest.java` | Delete test `exceptionDuringProcessing_otherEstimationsStillProcessed` (lines 130-149) |

---

## Files to Read for Full Context

| File | Purpose |
|------|---------|
| `services/customer-service/src/main/java/.../config/CustomerSagaConsumer.java` | Reference — correct `TransactionTemplate` usage pattern |
| `services/insurance-service/src/main/java/.../config/InsuranceSagaConsumer.java` | Reference — correct `TransactionTemplate` usage pattern in aggregation handlers |
| `services/estimation-service/src/main/java/.../EstimationServiceApplication.java` | Verify `TransactionTemplate` bean is available (check for `@EnableTransactionManagement`) |
| `docs/outlines/03_SAGA_PATTERN.md` | SAGA atomicity guarantees |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Java conventions |

---

## Risk Assessment

**RISK: MEDIUM.** The EstimationSagaConsumer fix (adding `TransactionTemplate`) is straightforward — follows the exact pattern already working in two other services. The SagaTimeoutService fix (removing per-iteration catch) changes behavior: currently individual failures are logged and swallowed; after the fix, any failure rolls back the entire batch. The 30-second retry interval ensures no permanent data loss — each failed batch is retried on the next cycle.
