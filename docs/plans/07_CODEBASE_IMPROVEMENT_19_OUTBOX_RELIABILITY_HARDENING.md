# Plan 19: Outbox Reliability Hardening — StreamBridge Return Value, Pessimistic Lock, Index Fix

## Severity: HIGH (silent message loss) / MEDIUM (duplicate events) / LOW (index mismatch)

## Status

- [ ] Fix `MessagePublisher.publish()` — check `StreamBridge.send()` return value
- [ ] Fix `OutboxEventRepository` — add pessimistic lock or `SKIP LOCKED` query to prevent duplicate processing
- [ ] Fix `OutboxEvent` @Index — correct column name from `createdAt` to `created_at`
- [ ] Fix `OutboxEvent` — add CHECK constraint on `status` column in all three `init.sql` files
- [ ] Run all outbox tests and verify

---

## Context

### Problem 1: StreamBridge.send() Return Value Ignored

**File:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/MessagePublisher.java`

```java
// Line 17-20
public void publish(String topic, Object message) {
    log.debug("Publishing message to {}: {}", topic, message);
    streamBridge.send(topic, message);
    // ^^^ boolean return value is DISCARDED
}
```

`StreamBridge.send()` returns `false` when:
- The Kafka producer buffer is full
- The broker is unavailable
- The topic doesn't exist
- The binder is in an error state

When `false` is returned, the message was NOT accepted by Kafka. But `OutboxProcessor.processOutbox()` at line 52 of each service's copy sees no exception and proceeds to mark the event `PUBLISHED`. After 5 minutes, `cleanupEvents()` permanently deletes it. **The message is silently lost.**

**Fix:**
```java
public void publish(String topic, Object message) {
    log.debug("Publishing message to {}: {}", topic, message);
    boolean sent = streamBridge.send(topic, message);
    if (!sent) {
        throw new IllegalStateException(
            "Failed to send message to topic " + topic + " — StreamBridge returned false");
    }
}
```

This causes `OutboxProcessor.processOutbox()` to catch the exception in its existing `catch(Exception e)` block (line 58), which correctly marks the event as FAILED and increments retryCount. The event will be retried on the next poll cycle.

### Problem 2: No Pessimistic Lock on Outbox Fetch — Duplicate Processing Across Instances

**File:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/repository/OutboxEventRepository.java`

```java
// Line 14
List<OutboxEvent> findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);
```

Without `@Lock(LockModeType.PESSIMISTIC_WRITE)` or a `SKIP LOCKED` query hint, two service instances polling simultaneously both get the same 10 PENDING rows. Both mark them PUBLISHING, both publish to Kafka, both mark PUBLISHED. The zombie recovery mechanism (5-minute timeout for PUBLISHING→PENDING) mitigates this eventually, but for up to 5 minutes, **duplicate outbound messages are published**.

The old per-service repository had `@Lock(PESSIMISTIC_WRITE)` which was lost during extraction to common-message.

**Fix — add a native query with SKIP LOCKED (preferred over @Lock):**
```java
@Query(value = "SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 10 FOR UPDATE SKIP LOCKED", nativeQuery = true)
List<OutboxEvent> findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);
```

`SKIP LOCKED` is preferred over `PESSIMISTIC_WRITE` because:
- It doesn't block — if instance A locks rows 1-10, instance B skips them and gets rows 11-20
- It's PostgreSQL-native and performant
- It's the standard outbox pattern for horizontal scaling

**Alternatively**, the existing `findTop10ByStatusOrderByCreatedAtAsc` can keep its name and add `@Lock(PESSIMISTIC_WRITE)` plus `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))` for the same effect.

### Problem 3: @Index columnList Uses Java Field Name Instead of DB Column Name

**File:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/entity/OutboxEvent.java`

```java
// Line 12-14
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status", columnList = "status, createdAt")  // BUG: 'createdAt' not 'created_at'
})
```

The `createdAt` field is mapped as:
```java
@Column(name = "created_at", updatable = false)  // Line 46
private Instant createdAt;
```

The `@Index` annotation should use the physical column name `created_at`, not the Java field name `createdAt`. With `ddl-auto=validate` (production config), this is silently accepted. With `ddl-auto=update` or `create` (local dev, tests), Hibernate generates a DDL column named `createdAt` that doesn't match the physical column.

**Fix:**
```java
@Index(name = "idx_outbox_status", columnList = "status, created_at")
```

### Problem 4: No CHECK Constraint on outbox_events.status

**Files:** `infra/sql/customer_db/init.sql`, `infra/sql/insurance_db/init.sql`, `infra/sql/estimation_db/init.sql`

The `outbox_events.status` column is `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` with no CHECK constraint. A bug or manual edit could insert `'PENDIN'` or `'PUBLISH'` — rows that would never be picked up by the status-filtered queries, silently leaking unrecoverable events.

The `estimations.status` column in `estimation_db/init.sql` already has the correct pattern:
```sql
status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED','COMPLETED','REJECTED')),
```

**Fix — add CHECK constraint to all three init.sql files:**
```sql
status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','PUBLISHING','PUBLISHED','FAILED')),
```

---

## Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | `common/common-message/src/main/java/.../messaging/MessagePublisher.java` | Check `streamBridge.send()` return value; throw on `false` |
| 2 | `common/common-message/src/main/java/.../repository/OutboxEventRepository.java` | Add `@Lock(PESSIMISTIC_WRITE)` + `SKIP LOCKED` or native query |
| 3 | `common/common-message/src/main/java/.../entity/OutboxEvent.java` | Fix `@Index columnList` from `createdAt` → `created_at` |
| 4 | `infra/sql/customer_db/init.sql` | Add CHECK constraint on `outbox_events.status` |
| 5 | `infra/sql/insurance_db/init.sql` | Add CHECK constraint on `outbox_events.status` |
| 6 | `infra/sql/estimation_db/init.sql` | Add CHECK constraint on `outbox_events.status` |

---

## Files to Read for Full Context

| File | Purpose |
|------|---------|
| `common/common-message/src/main/java/.../messaging/MessagePublisher.java` | Current publish() — 49 lines |
| `common/common-message/src/main/java/.../repository/OutboxEventRepository.java` | Current query methods — 17 lines |
| `common/common-message/src/main/java/.../entity/OutboxEvent.java` | Current entity — 66 lines |
| `infra/sql/estimation_db/init.sql` | Reference — CHECK constraint on estimations.status |
| Each service's `OutboxProcessor.java` (3 files) | Verify catch block handles new exception from publish() |
| `docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md` | Kafka topology and delivery semantics |

---

## Test Files to Check

| File | What to verify |
|------|---------------|
| `services/estimation-service/src/test/.../config/OutboxProcessorTest.java` | Tests should still pass; publish failure now retries correctly |
| `services/customer-service/src/test/.../config/OutboxProcessorTest.java` | Same |
| `services/insurance-service/src/test/.../config/OutboxProcessorTest.java` | Same |

---

## Verification Checklist

- [ ] `MessagePublisher.publish()` throws when `StreamBridge.send()` returns `false`
- [ ] `OutboxProcessor.processOutbox()` catch block handles the new exception (marks FAILED, retries)
- [ ] `OutboxEventRepository.findTop10...` uses `SKIP LOCKED` or `PESSIMISTIC_WRITE`
- [ ] `OutboxEvent` @Index uses `created_at` not `createdAt`
- [ ] All three `init.sql` files have CHECK constraint on `outbox_events.status`
- [ ] All existing outbox tests pass across all three services

---

## Risk Assessment

**Fix 1 (StreamBridge check): LOW.** The exception is thrown and caught within the existing try-catch in `OutboxProcessor.processOutbox()`. No change to the catch logic needed.

**Fix 2 (PESSIMISTIC_WRITE / SKIP LOCKED): LOW-MEDIUM.** Requires testing with PostgreSQL to ensure the query hint works with Hibernate 6. `SKIP LOCKED` requires PostgreSQL 9.5+ (the project uses PostgreSQL 16). The existing `@Lock` was removed during extraction to common-message; re-adding it restores the previous behavior.

**Fix 3 (@Index fix): VERY LOW.** `ddl-auto=validate` in production ignores the index annotation. Only affects dev environments with `create` or `update`.

**Fix 4 (CHECK constraint): VERY LOW.** Only applies to new databases created from init.sql. Existing databases need a manual `ALTER TABLE` migration — document this.
