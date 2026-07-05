# Plan 21: Code Cleanup & Conventions — Dead Code Removal + Lombok/Annotation Fixes

## Severity: LOW — Conventions violations and dead code

## Status

- [x] Deprecate `MessagePublisher.publishAfterCommit()` — added `@Deprecated(forRemoval = true)` and updated Javadoc
- [x] Remove `EstimationEventPublisher` — confirmed zero callers in production code; deleted class + test
- [x] Fix Lombok annotation order in `OutboxEvent.java` — Lombok annotations before JPA per convention
- [x] Remove redundant `columnDefinition = "JSONB"` from `OutboxEvent.payload` field — kept only `@JdbcTypeCode(SqlTypes.JSON)`
- [x] Run all tests and verify — compilation clean; unit tests pass (3 pre-existing infra-dependent integration test failures)

---

## Context

### Problem 1: publishAfterCommit() Is Dead Code

**File:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/MessagePublisher.java`

```java
// Lines 22-48
/**
 * Publish a message after the current DB transaction commits.
 * ...
 * <strong>Prefer the outbox pattern for critical data.</strong> This method uses an in-memory
 * callback that is lost if the application crashes between transaction commit and
 * callback execution. ...
 */
public void publishAfterCommit(String topic, Object message) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    streamBridge.send(topic, message);
                }
            });
    } else {
        publish(topic, message);
    }
}
```

This method:
- Has **zero callers** in production code (confirmed by grep across all services)
- Was added as a planned utility (Plan 07) but the outbox pattern was chosen instead for all service code
- Its own Javadoc warns it's unreliable ("lost if the application crashes between transaction commit and callback execution")
- Serves as a **trap for future developers** who might mistakenly use it instead of the outbox pattern

**Fix:** Deprecate the method with `@Deprecated` and a Javadoc reference to the outbox pattern:
```java
/**
 * @deprecated Use the outbox pattern ({@link com.insurancemanagementsystem.common.entity.OutboxEvent})
 *             instead. This method uses an in-memory callback that loses messages on crash.
 */
@Deprecated(forRemoval = true)
public void publishAfterCommit(String topic, Object message) {
    // ... unchanged
}
```

### Problem 2: EstimationEventPublisher May Be Dead Code

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationEventPublisher.java`

The `publishEstimationFailed()` method on this class was the exclusive publish path for `EstimationFailed` events. Both callers migrated to `OutboxEventSerializer` + `OutboxEventRepository`. If grep confirms zero callers, remove both the class and its test.

**Check before removing:**
```bash
grep -r "EstimationEventPublisher" services/estimation-service/src/main/
```

### Problem 3: Lombok Annotation Order Convention Violation

**File:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/entity/OutboxEvent.java`

The `docs/outlines/10_JAVA_CONVENTIONS.md` at line 84 states:

> The order is: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, then JPA annotations.

Current `OutboxEvent.java` order (lines 11-18):
```java
@Entity                          // JPA — SHOULD BE AFTER Lombok
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status", columnList = "status, createdAt")
})                                // JPA — SHOULD BE AFTER Lombok
@Data                            // Lombok — SHOULD BE FIRST
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
```

**Fix — reorder to match convention:**
```java
@Data                            // Lombok first
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity                          // JPA after Lombok
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status", columnList = "status, created_at")
})
public class OutboxEvent {
```

Reference file with correct order: `common/common-message/src/main/java/.../entity/SagaEvent.java` (and the original `Customer.java`, `Estimation.java` entities).

### Problem 4: Redundant columnDefinition = "JSONB" Alongside @JdbcTypeCode

**File:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/entity/OutboxEvent.java`

Lines 30-32:
```java
@Column(nullable = false, columnDefinition = "JSONB")   // ← REMOVE columnDefinition
@JdbcTypeCode(SqlTypes.JSON)                            // ← KEEP only this
private String payload;
```

The `docs/outlines/11_TESTING_CONVENTIONS.md` at lines 83-97 states:

> Replace `columnDefinition` with Hibernate 6+'s built-in `@JdbcTypeCode(SqlTypes.JSON)` ...
> `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(name = "details")` — works with both create-drop and validate

**Reason:** `columnDefinition = "JSONB"` causes Hibernate to emit native PostgreSQL JSONB DDL. With Hibernate 6's `create-drop` DDL (used in integration tests), having **both** annotations may cause DDL generation conflicts or type mapping ambiguity. The `@JdbcTypeCode` alone is sufficient — Hibernate 6 handles JSON serialization natively.

**Fix — remove `columnDefinition`:**
```java
@Column(nullable = false)
@JdbcTypeCode(SqlTypes.JSON)
private String payload;
```

Reference: `Estimation.details` field already correctly uses only `@JdbcTypeCode(SqlTypes.JSON)` without `columnDefinition`.

---

## Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | `common/common-message/src/main/java/.../messaging/MessagePublisher.java` | Add `@Deprecated(forRemoval = true)` to `publishAfterCommit()` |
| 2 | `services/estimation-service/src/main/java/.../config/EstimationEventPublisher.java` | DELETE if no callers confirmed |
| 3 | `services/estimation-service/src/test/java/.../config/EstimationEventPublisherTest.java` | DELETE if class deleted |
| 4 | `common/common-message/src/main/java/.../entity/OutboxEvent.java` | Reorder annotations: Lombok before JPA |
| 5 | `common/common-message/src/main/java/.../entity/OutboxEvent.java` | Remove `columnDefinition = "JSONB"` from payload field |

---

## Files to Read for Full Context

| File | Purpose |
|------|---------|
| `common/common-message/src/main/java/.../entity/OutboxEvent.java` | Entity to fix — 66 lines |
| `common/common-message/src/main/java/.../entity/SagaEvent.java` | Reference — correct Lombok order |
| `common/common-message/src/main/java/.../messaging/MessagePublisher.java` | Method to deprecate — 49 lines |
| `services/estimation-service/src/main/java/.../entity/Estimation.java` | Reference — correct @JdbcTypeCode usage without columnDefinition |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Lombok order rule (line 84) |
| `docs/outlines/11_TESTING_CONVENTIONS.md` | @JdbcTypeCode rule (lines 83-97) |

---

## Test Files to Check

| File | What to verify |
|------|---------------|
| `common/common-message/src/test/` | Any test referencing `publishAfterCommit()` — should still compile with `@Deprecated` |
| `services/estimation-service/src/test/.../config/EstimationEventPublisherTest.java` | Delete if class deleted |

---

## Verification Checklist

- [x] `publishAfterCommit()` annotated with `@Deprecated(forRemoval = true)`
- [x] No compiler warnings in code that imports `MessagePublisher` (deprecation is intentional)
- [x] `EstimationEventPublisher` removed — zero callers confirmed
- [x] `OutboxEvent.java` annotation order: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, then `@Entity`, `@Table`
- [x] `OutboxEvent.payload` has only `@Column(nullable = false)` + `@JdbcTypeCode(SqlTypes.JSON)` — no `columnDefinition`
- [x] Integration tests: 3 pre-existing failures in `EstimationServiceIntegrationTest` (require PostgreSQL/Kafka infra, unrelated to these changes)
- [x] All services compile and start up successfully

---

## Risk Assessment

**Fix 1 (deprecate publishAfterCommit): VERY LOW.** The method has zero callers. `@Deprecated` doesn't change behavior — it only generates a compiler warning if someone tries to use it.

**Fix 2 (remove EstimationEventPublisher): LOW.** Verify zero callers before deleting. If any caller remains (unlikely based on grep), skip this step.

**Fix 3 (Lombok order): VERY LOW.** Pure cosmetic — annotation order doesn't affect runtime behavior. Bringing it into compliance with the convention doc eliminates a future review finding.

**Fix 4 (remove columnDefinition): LOW-MEDIUM.** The `@JdbcTypeCode` alone is sufficient for Hibernate 6 JSON mapping. Removing `columnDefinition` means the integration test's `create-drop` DDL will infer the column type from `@JdbcTypeCode` (which maps to the correct dialect-specific type). However, if any manual SQL or migration tool reads the `columnDefinition`, they'll need to use the DDL-generated type instead. Verify integration tests pass after this change.
