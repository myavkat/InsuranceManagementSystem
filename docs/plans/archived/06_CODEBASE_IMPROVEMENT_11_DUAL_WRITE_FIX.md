# Plan: Fix 11 — Close Dual-Write Gap in Customer & Insurance Services

## Objective

Close the **critical dual-write atomicity gap** in `CustomerSagaConsumer` and `InsuranceSagaConsumer`, where DB writes (dedup `SagaEventRepository.save()`) and Kafka publishes (`messagePublisher.publish()`) are not atomic. If the JVM crashes between the two operations, the dedup marker is committed but the response event is never published — the saga deadlocks permanently.

Additionally, both services bypass the `publishAfterCommit()` utility that was added to `MessagePublisher` in Plan 07.

## Root Cause

### CustomerSagaConsumer

```java
// handleEstimationRequested() — current flow:
sagaEventRepository.save(dedup);              // DB write
// ... validation logic, no DB writes ...
messagePublisher.publish(topic, envelope);     // Kafka publish — not atomic with save above
```

The `sagaEventRepository.save()` creates the dedup row. If the app crashes before `messagePublisher.publish()`, the `EstimationRequested` event is deduplicated (won't be processed again), but `CustomerValidated`/`CustomerInvalidated` was never sent. **Saga deadlocks.**

Same pattern in `InsuranceSagaConsumer`:
- `handleEstimationRequested()` — validates, publishes `CustomerValidated` / `CustomerInvalidated` equiv 
- `handleCustomerValidated()` — aggregates, publishes `PremiumCalculated` / `CalculationFailed`
- `handleVehicleValidated()` — aggregates, publishes `PremiumCalculated` / `CalculationFailed`
- `handleInvalidated()` — publishes `CalculationFailed`

All use direct `messagePublisher.publish()` after DB operations.

---

## Cross-Service Analysis

| Service | File | Direct publish locations | Has outbox? |
|---------|------|-------------------------|-------------|
| **estimation-service** | `EstimationSagaConsumer` | None (all use outbox) ✅ | ✅ |
| **estimation-service** | `SagaTimeoutService` | None (uses outbox) ✅ | ✅ |
| **estimation-service** | `EstimationService` | None (uses outbox) ✅ | ✅ |
| **customer-service** | `CustomerSagaConsumer` | 2 locations | ❌ |
| **insurance-service** | `InsuranceSagaConsumer` | 4 locations | ❌ |

**Both customer-service and insurance-service are affected.**

---

## Context Files to Read First

### Customer-service
1. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerSagaConsumer.java`** (132 lines)
   - `handleEstimationRequested()` — lines ~60-100, publishes directly at end
   - `handleEstimationFailed()` — lines ~124-130, log-only (no publish needed)

2. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/repository/SagaEventRepository.java`** (14 lines)
   - `existsBySagaIdAndEventType`, `findBySagaIdAndEventType`

3. **`services/customer-service/src/test/java/com/insurancemanagementsystem/customer/saga/CustomerSagaConsumerTest.java`** (existing test)
   - Verify mocks after change

4. **`services/customer-service/build.gradle.kts`** (81 lines)
   - Current dependencies — verify `common-message` dependency includes `spring-tx`

### Insurance-service
5. **`services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`** (251 lines)
   - `handleEstimationRequested()` — lines ~77-120, publishes at end
   - `handleCustomerValidated()` — lines ~122-150, publishes at end
   - `handleVehicleValidated()` — lines ~152-196, publishes at end
   - `handleInvalidated()` — lines ~198-213, publishes at end
   - `handleEstimationFailed()` — lines ~215-230, log-only

6. **`services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/SagaEventRepository.java`** (14 lines)

7. **`services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/saga/InsuranceSagaConsumerTest.java`** (existing test)

### Reference — estimation-service outbox pattern
8. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`** (243 lines)
   - `saveOutboxEvent()` helper (or `OutboxEventSerializer` after Plan 10) — pattern to follow

9. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/MessagePublisher.java`** (existing)
   - `publishAfterCommit()` utility — already available

---

## Design Decision

### Option A: Full outbox pattern (like estimation-service)

Add `OutboxEvent` entity, repository, relay, SQL table to both services. Most robust.

**Pros:** Full atomicity, crash-safe, retry built in
**Cons:** High effort — 6+ new files, 2 new SQL tables, 2 new relays

### Option B: Use `publishAfterCommit()` (lightweight)

Replace `messagePublisher.publish()` with `messagePublisher.publishAfterCommit()` in consumer handlers that run within `@Transactional`.

**Problem:** SAGA consumers receive events via `java.util.function.Consumer<String>` — they are NOT `@Transactional`. Spring Cloud Stream creates the consumer binding. The consumer handler has no enclosing transaction to register `afterCommit` against.

### Option C: Add `@Transactional` to consumer handler methods + `publishAfterCommit()`

Wrap each handler method with `@Transactional` (Spring allows this on consumer beans). Then `publishAfterCommit()` works.

**Problem:** The consumer is `@Component`, not `@Service`. But `@Transactional` works on any Spring bean. The challenge is that `handleEstimationRequested()` in CustomerSagaConsumer doesn't do all DB writes in one method — the dedup `save()` is inside a helper `isDuplicateSagaEvent()`.

### Option D: Outbox-lite — add `outbox_events` table only, no relay

Insert outbox events within the same `@Transactional` scope as the consumer handler. A lightweight relay or manual flush publishes them.

**Pros:** Atomic without a full relay
**Cons:** Still needs a publisher somewhere

### Chosen approach: **Option D — Outbox-lite** (least code, most impact)

For both services:
1. Add `outbox_events` table to DB init SQL (same schema as estimation-service)
2. Create `OutboxEvent` entity (can share from `common` module — see Plan 12)
3. Create `OutboxEventRepository`
4. Wrap consumer handlers with `@Transactional`
5. Replace `messagePublisher.publish()` with `outboxEventRepository.save(outboxEvent)`
6. Create a simple `OutboxRelay` (can copy from estimation-service after Plan 09 fix)

**Wait — Option D is essentially the full outbox pattern, just leaner.** This is acceptable because we're already fixing Plan 09 which refactors the outbox for estimation-service. Both services can share the pattern.

### Final choice: Full outbox pattern (matching estimation-service post-Plan-09)

Since Plan 09 already cleans up the outbox architecture (OutboxProcessor + OutboxRelay split), it's easy to replicate in customer-service and insurance-service. The shared SagaEvent + OutboxEvent entities from Plan 12 will reduce duplication further.

---

## Files to Modify

### Customer-service

#### 1. `infra/sql/customer_db/init.sql` — Add `outbox_events` table

```sql
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID,
    topic VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events(status, created_at);
```

#### 2. Create `OutboxEvent.java` entity

**Path:** `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/entity/OutboxEvent.java`

Copy from estimation-service's `OutboxEvent.java`. Change only package. Include `PUBLISHED` in Status enum (post-Plan-09).

#### 3. Create `OutboxEventRepository.java`

**Path:** `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/repository/OutboxEventRepository.java`

Two query methods:
```java
List<OutboxEvent> findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);
List<OutboxEvent> findByStatusAndCreatedAtBefore(OutboxEvent.Status status, Instant cutoff);
```

#### 4. Create `OutboxProcessor.java`

**Path:** `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/OutboxProcessor.java`

Copy from estimation-service (post-Plan-09). Change package only. Identical logic.

#### 5. Create `OutboxRelay.java`

**Path:** `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/OutboxRelay.java`

Copy from estimation-service (post-Plan-09). Change package and config prefix to `customer.outbox.*`.

#### 6. Modify `CustomerSagaConsumer.java`

**Changes:**
- Inject `OutboxEventRepository` and `OutboxEventSerializer`
- Add `@Transactional` to handler methods (or wrap in `TransactionTemplate`)
- Replace `messagePublisher.publish()` with outbox insert

**In `handleEstimationRequested()`:**

```java
// BEFORE:
messagePublisher.publish(topic, envelope);

// AFTER:
// Build and save outbox event within the same @Transactional scope
OutboxEvent outboxEvent = buildOutboxEvent(sagaId, envelope, topic);
outboxEventRepository.save(outboxEvent);
```

**Add helper method:**
```java
private OutboxEvent buildOutboxEvent(UUID sagaId, Object envelope, String topic) {
    String payloadJson;
    try {
        payloadJson = jsonMapper.writeValueAsString(envelope);
    } catch (Exception e) {
        throw new RuntimeException("Failed to serialize outbox payload for sagaId=" + sagaId, e);
    }
    return OutboxEvent.builder()
            .sagaId(sagaId)
            .topic(topic)
            .payload(payloadJson)
            .status(OutboxEvent.Status.PENDING)
            .build();
}
```

The same pattern for `handleEstimationFailed()` — currently log-only, no publish needed. Keep as-is.

#### 7. Modify `application.yml` — Add outbox config

```yaml
customer:
  outbox:
    poll-interval-ms: 1000
    max-retries: 3
    failed-ttl-minutes: 60
```

### Insurance-service

Same 7 steps, with package `com.insurancemanagementsystem.insurance.*` and config prefix `insurance.outbox.*`.

**Additional change in `InsuranceSagaConsumer.java`:**
- `handleEstimationRequested()` — outbox insert for response event
- `handleCustomerValidated()` — outbox insert (publishes `PremiumCalculated` or `CalculationFailed`)
- `handleVehicleValidated()` — outbox insert (publishes `PremiumCalculated` or `CalculationFailed`)
- `handleInvalidated()` — outbox insert (publishes `CalculationFailed`)
- `handleEstimationFailed()` — log-only, no publish

4 handler methods need outbox insert. Each follows the same pattern:
```java
OutboxEvent outboxEvent = buildOutboxEvent(sagaId, envelope, topic);
outboxEventRepository.save(outboxEvent);
```

---

## Test Updates

### Customer-service

- **`CustomerSagaConsumerTest.java`:** Add `@Mock OutboxEventRepository`, verify `outboxEventRepository.save()` is called instead of `messagePublisher.publish()`
- **NEW `OutboxProcessorTest.java`:** Unit test for outbox relay (copy pattern from estimation-service)

### Insurance-service

- **`InsuranceSagaConsumerTest.java`:** Add `@Mock OutboxEventRepository`, verify outbox insert instead of direct publish
- **NEW `OutboxProcessorTest.java`:** Unit test for outbox relay

---

## Verification

```bash
# Customer-service
.\gradlew.bat :services:customer-service:compileJava
.\gradlew.bat :services:customer-service:test

# Insurance-service
.\gradlew.bat :services:insurance-service:compileJava
.\gradlew.bat :services:insurance-service:test

# Estimation-service (regression check)
.\gradlew.bat :services:estimation-service:test
```

---

## Execution Checklist

### Customer-service
- [ ] Read context files for customer-service (files 1-4)
- [ ] Edit `infra/sql/customer_db/init.sql` — add `outbox_events` table
- [ ] Create `OutboxEvent.java` entity
- [ ] Create `OutboxEventRepository.java`
- [ ] Create `OutboxProcessor.java`
- [ ] Create `OutboxRelay.java`
- [ ] Modify `CustomerSagaConsumer.java` — replace direct publish with outbox insert
- [ ] Edit `application.yml` — add `customer.outbox.*` config
- [ ] Update `CustomerSagaConsumerTest.java` — mock outbox repo
- [ ] Create `OutboxProcessorTest.java`
- [ ] Compile and test: ALL PASS

### Insurance-service
- [ ] Read context files for insurance-service (files 5-7)
- [ ] Edit `infra/sql/insurance_db/init.sql` — add `outbox_events` table
- [ ] Create `OutboxEvent.java` entity
- [ ] Create `OutboxEventRepository.java`
- [ ] Create `OutboxProcessor.java`
- [ ] Create `OutboxRelay.java`
- [ ] Modify `InsuranceSagaConsumer.java` — replace direct publish in 4 handler methods with outbox insert
- [ ] Edit `application.yml` — add `insurance.outbox.*` config
- [ ] Update `InsuranceSagaConsumerTest.java` — mock outbox repo
- [ ] Create `OutboxProcessorTest.java`
- [ ] Compile and test: ALL PASS

### Regression
- [ ] Run estimation-service tests: ALL PASS

---

## Risk Assessment

- **Risk:** MEDIUM. Adds outbox infrastructure to 2 more services. Pattern is proven in estimation-service (after Plan 09 fix). Code duplication will be high until Plan 12 extracts shared entities to `common` module.
- **Atomicity gain:** Significant. All Kafka publishes become DB-atomic (within `@Transactional`). Crash-safe. Retry-supported via relay.
- **Performance:** Outbox relay polls 1×/sec per service. Negligible overhead for <10 events per batch.
- **Migration:** Existing running services will need `outbox_events` table added. `init.sql` handles new deployments; for running instances, a manual `CREATE TABLE` is needed.

---

## Dependencies

- **Prerequisite: Plan 09** (OutboxRelay transactional fix) — the fixed `OutboxProcessor` + `OutboxRelay` pattern from estimation-service is the template to replicate.
- **Prerequisite: Plan 10** (ACID gaps) — the `OutboxEventSerializer` pattern may also be replicated.
- **Related: Plan 12** (DRY extraction) — after this plan, SagaEvent + OutboxEvent entities will be duplicated across 3 services. Plan 12 extracts them to `common`.
