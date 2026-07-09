# Plan 02: Backend — Status Transition Logic and New Endpoints

**Status:** Not started
**Depends on:** Plan 01 (entity, enum, DDL changes must be complete)
**Blocks:** Plan 04 (frontend payment flow needs these endpoints)

---

## Objective

1. Modify the `PremiumCalculated` SAGA handler to transition estimation to `WAITING_APPROVAL` instead of `COMPLETED`.
2. Add a new REST endpoint `PUT /api/estimations/{id}/accept-offer` to transition `WAITING_APPROVAL` → `PAYMENT_WAITING`.
3. Add a new REST endpoint `PUT /api/estimations/{id}/process-payment` to transition `PAYMENT_WAITING` → `ACTIVE` and set `start_date`/`end_date`.
4. Keep the existing `COMPLETED` status for historical data — existing COMPLETED rows remain untouched.

---

## Files to Read First (before writing any code)

| # | File Path | Purpose |
|---|-----------|---------|
| 1 | `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java` | The `handlePremiumCalculated` method you will modify |
| 2 | `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/controller/EstimationController.java` | The controller where you'll add new endpoints |
| 3 | `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java` | The service where you'll add new methods |
| 4 | `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java` | Verify the entity has the new statuses and fields from Plan 01 |
| 5 | `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationResponse.java` | Verify DTO has new fields from Plan 01 |
| 6 | `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/OutboxEventSerializer.java` | For adding a new outbox event builder if needed |
| 7 | `AGENTS.md` | SAGA consumer rules, outbox rules |
| 8 | `docs/outlines/03_SAGA_PATTERN.md` | SAGA consumer implementation rules (transaction boundaries, idempotency, JSON serialization) |
| 9 | `docs/outlines/14_EVENT_SCHEMA_REGISTRY.md` | Event schemas and event type index |

---

## Important Conventions (from AGENTS.md and outlines)

### Transaction Boundaries
Every consumer handler that does >1 DB write MUST wrap all writes in `TransactionTemplate.executeWithoutResult()`. Never rely on JpaRepository implicit transactions.

### Atomic Dedup
ALWAYS use `SagaEventRepository.tryInsertDedup()` for idempotency — never `existsBySagaIdAndEventType()` followed by `save()`.

### Dedup Requires Transaction
Every call to `tryInsertDedup()` MUST be inside `transactionTemplate.executeWithoutResult()` or an active `@Transactional`.

### JSON via ObjectMapper Only
Never build JSON strings via concatenation. Use `jsonMapper.writeValueAsString()`. If serialization can fail, catch and use a properly-escaped fallback (see AGENTS.md JSON Serialization Rules).

### Fallback Must Produce Valid JSON
When `writeValueAsString()` throws, the fallback must produce valid JSON. See the pattern in `EstimationSagaConsumer.java` `handleFailed()` method (lines 224-236) — that's the canonical pattern. Copy it exactly.

### Trace Propagation
All outbound SAGA events must carry the original `traceId` from the triggering `EventEnvelope`, never a fresh `UUID.randomUUID()`.

### Controller Response Envelope
All controller methods return `ResponseEntity<ApiResponse<T>>` using `ApiResponse.success(message, data)` or `ApiResponse.error(message)`. Follow the existing pattern in `EstimationController.java`.

---

## Steps

### Step 1: Modify `handlePremiumCalculated` — Status Transition

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`

In the `handlePremiumCalculated` method (around line 164-195), change the status transition from `COMPLETED` to `WAITING_APPROVAL`.

Currently (lines 177-185):
```java
// Transition: STARTED → COMPLETED
if (estimation.getStatus() != Estimation.Status.STARTED) {
    log.warn("Estimation {} is in status {} — cannot transition to COMPLETED",
            estimation.getId(), estimation.getStatus());
    return;
}

estimation.setStatus(Estimation.Status.COMPLETED);
```

Change to:
```java
// Transition: STARTED → WAITING_APPROVAL
if (estimation.getStatus() != Estimation.Status.STARTED) {
    log.warn("Estimation {} is in status {} — cannot transition to WAITING_APPROVAL",
            estimation.getId(), estimation.getStatus());
    return;
}

estimation.setStatus(Estimation.Status.WAITING_APPROVAL);
```

Also update the log line after `estimationRepository.save(estimation)`:
Change:
```java
log.info("Estimation {} completed for sagaId={}: premium={}",
        estimation.getId(), sagaId, event.getPremium());
```
To:
```java
log.info("Estimation {} waiting approval for sagaId={}: premium={}",
        estimation.getId(), sagaId, event.getPremium());
```

Do NOT change anything else in this method — the dedup, premium setting, details serialization, and save logic remain identical.

### Step 2: Add `acceptOffer` Method to EstimationService

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`

Add a new public method `acceptOffer`:

```java
@Transactional
public EstimationResponse acceptOffer(UUID id) {
    Estimation estimation = estimationRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Estimation not found with id: " + id));

    if (estimation.getStatus() != Estimation.Status.WAITING_APPROVAL) {
        throw new IllegalStateException(
                "Cannot accept offer: estimation " + id + " is in status " + estimation.getStatus()
                + ". Expected status: WAITING_APPROVAL.");
    }

    estimation.setStatus(Estimation.Status.PAYMENT_WAITING);
    estimation = estimationRepository.save(estimation);

    log.info("Offer accepted for estimation {}: status changed to PAYMENT_WAITING", id);
    return EstimationResponse.fromEntity(estimation);
}
```

Place this method after the `create` method (around line 160) and before the `parseStatus` private method.

### Step 3: Add `processPayment` Method to EstimationService

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`

Add a new public method `processPayment`:

```java
@Transactional
public EstimationResponse processPayment(UUID id) {
    Estimation estimation = estimationRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Estimation not found with id: " + id));

    if (estimation.getStatus() != Estimation.Status.PAYMENT_WAITING) {
        throw new IllegalStateException(
                "Cannot process payment: estimation " + id + " is in status " + estimation.getStatus()
                + ". Expected status: PAYMENT_WAITING.");
    }

    Instant now = Instant.now();
    estimation.setStatus(Estimation.Status.ACTIVE);
    if (estimation.getStartDate() == null) {
        estimation.setStartDate(now);
    }
    estimation.setEndDate(now.plus(365, java.time.temporal.ChronoUnit.DAYS)); // 1 year
    estimation = estimationRepository.save(estimation);

    log.info("Payment processed for estimation {}: status ACTIVE, start_date={}, end_date={}",
            id, estimation.getStartDate(), estimation.getEndDate());
    return EstimationResponse.fromEntity(estimation);
}
```

Place this method after the `acceptOffer` method you just added.

The `plus(365, ChronoUnit.DAYS)` adds exactly 365 days (1 year) to the current timestamp. This is a simple 1-year duration — no calendar-based year arithmetic needed.

### Step 4: Add Controller Endpoints

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/controller/EstimationController.java`

Add two new endpoint methods. Place them after the `getAll` method (around line 47) and before the closing `}` of the class.

#### Step 4a: Accept Offer Endpoint

```java
@PutMapping("/{id}/accept-offer")
public ResponseEntity<ApiResponse<EstimationResponse>> acceptOffer(@PathVariable UUID id) {
    EstimationResponse updated = estimationService.acceptOffer(id);
    return ResponseEntity.ok(ApiResponse.success("Offer accepted — payment is now required", updated));
}
```

#### Step 4b: Process Payment Endpoint

```java
@PutMapping("/{id}/process-payment")
public ResponseEntity<ApiResponse<EstimationResponse>> processPayment(@PathVariable UUID id) {
    EstimationResponse updated = estimationService.processPayment(id);
    return ResponseEntity.ok(ApiResponse.success("Payment processed — policy is now active", updated));
}
```

### Step 5: Update SagaTimeoutService for New Statuses

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java`

This step overlaps with Plan 01 Step 6. If Plan 01 Step 6 is already done, verify the timeout service looks correct. If not done, implement it now:

1. The timeout should apply to both `STARTED` and `WAITING_APPROVAL` estimations.
2. For `STARTED` estimations: timeout transitions to `REJECTED` (existing behavior).
3. For `WAITING_APPROVAL` estimations: timeout should also transition to `REJECTED` (offer expired).
4. Read the existing `SagaTimeoutService.java` to understand the exact implementation pattern, then modify accordingly.

Follow the exiting patterns for: `TransactionTemplate.executeWithoutResult()`, `OutboxEventSerializer.buildEstimationFailedOutboxEvent()`, `OutboxEventRepository.save()`, and `SagaEventRepository.tryInsertDedup()`.

### Step 6: Verify Compilation and Tests

Run the estimation service build:

```bash
cd services/estimation-service && ../gradlew compileJava
```

Then run the estimation service tests:

```bash
cd services/estimation-service && ../gradlew test
```

If tests fail:
- Look at test files that reference `Estimation.Status.COMPLETED` — they may need updating to `WAITING_APPROVAL` for the PremiumCalculated handler tests.
- Key test files to check: `EstimationSagaConsumerTest.java`, `EstimationServiceTest.java`, `SagaTimeoutServiceTest.java`, `SagaE2ETest.java`, `EstimationControllerTest.java`.
- Do NOT blindly update all COMPLETED references — only update the ones related to PremiumCalculated handler assertions.

---

## Acceptance Criteria

- [ ] `handlePremiumCalculated` transitions estimation status to `WAITING_APPROVAL` (not `COMPLETED`)
- [ ] `PUT /api/estimations/{id}/accept-offer` transitions `WAITING_APPROVAL` → `PAYMENT_WAITING` and returns 200
- [ ] `PUT /api/estimations/{id}/accept-offer` returns 500 (IllegalStateException) if status is not `WAITING_APPROVAL`
- [ ] `PUT /api/estimations/{id}/process-payment` transitions `PAYMENT_WAITING` → `ACTIVE`, sets `start_date` (if null) to now, sets `end_date` to now + 365 days, and returns 200
- [ ] `PUT /api/estimations/{id}/process-payment` returns 500 if status is not `PAYMENT_WAITING`
- [ ] `SagaTimeoutService` handles both STARTED and WAITING_APPROVAL estimations
- [ ] Estimation service compiles and all existing tests pass (or are updated to match new status transitions)
