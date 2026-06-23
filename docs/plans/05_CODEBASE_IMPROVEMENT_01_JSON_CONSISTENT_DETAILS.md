# Plan: Fix 06 — Make `details` JSONB Content Consistent (JSON for Rejections Too)

## Objective

Fix the inconsistency where `estimation.details` (a JSONB column) stores valid JSON for COMPLETED transitions but plain strings for REJECTED transitions. PostgreSQL's JSONB parser will store a plain string as a JSON string literal (with surrounding quotes), but the Java code must produce valid JSON always to avoid downstream deserialization errors.

## Root Cause

In `EstimationSagaConsumer.java`, two code paths set the `details` column:
1. **COMPLETED path (line 155):** `estimation.setDetails(jsonMapper.writeValueAsString(event.getBreakdown()))` — stores valid JSON like `{"base":1500.00,"riskFactor":1.0}` ✅
2. **REJECTED path (line 184):** `estimation.setDetails(reason)` — stores a plain string like `"Customer validation failed"` ❌

Additionally, `SagaTimeoutService.java:58` sets:
3. **TIMEOUT path:** `estimation.setDetails("SAGA timed out after " + timeoutMinutes + " minutes")` — same plain string problem ❌

When PostgreSQL receives a plain string for a JSONB column, it stores it as a JSON string literal (`"\"Customer validation failed\""` or `"Customer validation failed"` depending on JDBC driver). Downstream consumers that parse `details` as JSON will get a string back (parsed as JSON), but the content won't be a JSON object with structured fields.

## Cross-Service Analysis

| Service | Has `details` field? | `setDetails()` calls | Issue |
|---------|---------------------|---------------------|-------|
| **estimation-service** | ✅ `Estimation.details` (JSONB) | 3 calls (see above) | ❌ 2 plain strings |
| **customer-service** | ❌ No `details` field | None | Not affected |
| **insurance-service** | ❌ No `details` field | None | Not affected |

**Only estimation-service is affected.**

## Context Files to Read First

### Main source files
1. **`services/estimation-service/src/main/java/.../estimation/config/EstimationSagaConsumer.java`**
   - `handlePremiumCalculated()` — line ~155 (COMPLETED — correct JSON serialization)
   - `handleFailed()` — line ~184 (REJECTED — plain string)

2. **`services/estimation-service/src/main/java/.../estimation/service/SagaTimeoutService.java`**
   - `checkForTimedOutSagas()` — line ~58 (TIMEOUT — plain string)

3. **`services/estimation-service/src/main/java/.../estimation/entity/Estimation.java`**
   - `details` field — verify type (`String`), column definition (`JSONB`)

### Test files
4. **`services/estimation-service/src/test/java/.../estimation/config/EstimationSagaConsumerTest.java`**
   - Tests for `handleFailed()` and `handlePremiumCalculated()` — update assertions

5. **`services/estimation-service/src/test/java/.../estimation/service/SagaTimeoutServiceTest.java`**
   - Test for stale estimation → check details value

## Design Decision: JSON Structure for Rejection Details

Use a consistent JSON object format for ALL `details` values:

```
{"reason": "<failure reason text>"}
```

This keeps `details` as a proper JSON object (consistent with the COMPLETED path which stores a breakdown map) and makes it parseable by any downstream consumer.

## Files to Modify

### 1. `services/estimation-service/src/main/java/.../estimation/config/EstimationSagaConsumer.java`

**Location:** `handleFailed()` method, line ~184

**BEFORE:**
```java
estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails(reason);   // ← plain string
estimationRepository.save(estimation);
```

**AFTER:**
```java
estimation.setStatus(Estimation.Status.REJECTED);
try {
    estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
} catch (Exception e) {
    log.warn("Failed to serialize rejection details for sagaId={}", sagaId, e);
    estimation.setDetails("{\"reason\":\"" + reason + "\"}"); // fallback
}
estimationRepository.save(estimation);
```

**Important:** The `jsonMapper.writeValueAsString()` may throw a checked exception (Jackson's `JsonProcessingException`). Wrap in try-catch with a manual JSON fallback.

**Alternative approach (simpler, no exception handling):**
```java
estimation.setDetails("{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}");
```

**Chosen approach:** Use `jsonMapper.writeValueAsString()` with try-catch and manual JSON fallback (more robust, handles special characters in reason).

**Import to add:**
```java
import java.util.Map;
```

### 2. `services/estimation-service/src/main/java/.../estimation/service/SagaTimeoutService.java`

**Location:** `checkForTimedOutSagas()` method, line ~58

**BEFORE:**
```java
estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails("SAGA timed out after " + timeoutMinutes + " minutes");
estimationRepository.save(estimation);
```

**AFTER:**
```java
estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails("{\"reason\":\"SAGA timed out after " + timeoutMinutes + " minutes\"}");
estimationRepository.save(estimation);
```

For the timeout path, use the simple approach (the reason content is known and safe — no user input, no special characters). The `timeoutMinutes` is an integer — safe for direct string concatenation.

**Import to add:** None needed (no JSON library required for simple case).

## Test Updates

### 3. `services/estimation-service/src/test/java/.../estimation/config/EstimationSagaConsumerTest.java`

Find all tests that verify `estimation.getDetails()` after a failure event:

**Test: `customerInvalidatedEvent_transitionsToRejected()` (or equivalent)**
- Update assertion from `assertThat(estimation.getDetails()).isEqualTo("Customer validation failed")` to:
```java
assertThat(estimation.getDetails()).contains("reason");
assertThat(estimation.getDetails()).contains("Customer validation failed");
```

**Test: failed event for non-STARTED estimation**
- Verify details unchanged (no transition)
- Add JSON format assertion

### 4. `services/estimation-service/src/test/java/.../estimation/service/SagaTimeoutServiceTest.java`

Find test `staleEstimations_areRejected()`:

**Update assertion:**
```java
// BEFORE:
assertThat(stale.getDetails()).contains("timed out");

// AFTER:
assertThat(stale.getDetails()).isEqualTo("{\"reason\":\"SAGA timed out after 5 minutes\"}");
```

Also update test `multipleStaleEstimations_allProcessed()` — add assertion for JSON format.

## Verification

```bash
# 1. Compile
.\gradlew.bat :services:estimation-service:compileJava

# 2. Run specific consumer tests
.\gradlew.bat :services:estimation-service:test --tests "*EstimationSagaConsumerTest"

# 3. Run timeout tests
.\gradlew.bat :services:estimation-service:test --tests "*SagaTimeoutServiceTest"

# 4. Run all estimation-service tests
.\gradlew.bat :services:estimation-service:test
```

## Execution Checklist

- [ ] Read context files
- [ ] Fix `EstimationSagaConsumer.java:184` — use `jsonMapper.writeValueAsString(Map.of("reason", reason))`
- [ ] Fix `SagaTimeoutService.java:58` — use manual JSON string with `{"reason":...}`
- [ ] Update consumer test assertions for JSON format
- [ ] Update timeout test assertions for JSON format
- [ ] Compile: `BUILD SUCCESSFUL`
- [ ] All tests pass

## Risk Assessment

- **Risk:** LOW. Change is limited to serialization format only. No behavioral or state machine changes.
- **Regression risk:** LOW. Only assertion changes in tests. The COMPLETED path already works correctly with JSON — we're making REJECTED/TIMEOUT paths match.
- **DB impact:** Existing rows with plain-text `details` won't be retroactively fixed. New rejection/timeout rows will have proper JSON. This is acceptable — old rows remain readable.
