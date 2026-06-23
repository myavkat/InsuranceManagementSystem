# Plan: Fix 02 — Fix `details` JSON Corruption from `Map.toString()`

## Objective
Fix the bug where `estimation.setDetails(event.getBreakdown().toString())` in `EstimationSagaConsumer.handlePremiumCalculated()` produces non-JSON string for a JSONB database column.

## Root Cause

### What's happening now
In `EstimationSagaConsumer.java:136-138`:
```java
if (event.getBreakdown() != null) {
    estimation.setDetails(event.getBreakdown().toString());
}
```

- `event.getBreakdown()` returns `Map<String, BigDecimal>` (e.g., `{base=1500.00, riskFactor=1.0}`)
- `Map.toString()` (which calls `LinkedHashMap.toString()`) produces: `{base=1500.00, riskFactor=1.0}`
- This is NOT valid JSON. Valid JSON would be: `{"base": 1500.00, "riskFactor": 1.0}` (colons instead of equals, quotes around keys)
- The `details` column in the `estimations` table is defined as `JSONB`
- The Java entity field is annotated `@JdbcTypeCode(SqlTypes.JSON)` — Hibernate will pass the string to PostgreSQL as JSON
- PostgreSQL will REJECT the value because `{base=1500.00}` is not valid JSON — this causes a runtime SQL error

### Why it currently might not fail in tests
The current tests for `EstimationSagaConsumer` may not exercise this code path with a non-null breakdown, or the test's mocked repository doesn't actually save to a real database.

### Key clarification
The user asked: "shows accepts string? Isn't JSON held as String in Java as seen in Estimation.java?" — Correct: `details` is `String` in Java, and `setDetails(String)` accepts any String. But the STRING CONTENT must be valid JSON because the database column is JSONB and Hibernate passes the string through to PostgreSQL's JSON parser. Java `String` ≠ automatically valid JSON. `Map.toString()` produces a format that Java understands as a map representation, but PostgreSQL's JSON parser does NOT.

## Context Files to Read First

1. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/PremiumCalculatedEvent.java`** — Verify `breakdown` field type: `Map<String, BigDecimal>`
2. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java`** — Verify `details` field: `String` with `@JdbcTypeCode(SqlTypes.JSON)`
3. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`** — The `handlePremiumCalculated()` method (lines 112-143)
4. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java`** — Tests that may need updating to verify JSON output

## Fix

### In `EstimationSagaConsumer.java:136-138`

**Replace:**
```java
if (event.getBreakdown() != null) {
    estimation.setDetails(event.getBreakdown().toString());
}
```

**With:**
```java
if (event.getBreakdown() != null) {
    estimation.setDetails(jsonMapper.writeValueAsString(event.getBreakdown()));
}
```

This uses Jackson's `JsonMapper.writeValueAsString()` which serializes `Map<String, BigDecimal>` to valid JSON: `{"base":1500.00,"riskFactor":1.0}`.

### Why `jsonMapper` is available here
The `handlePremiumCalculated` method already receives `JsonMapper jsonMapper` as a parameter (passed from the `processEstimationSaga` bean method). Look at the method signature:

```java
private void handlePremiumCalculated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
```

The `jsonMapper` is already used in this same method at line 122-123:
```java
PremiumCalculatedEvent event = jsonMapper.convertValue(
        envelope.getPayload(), PremiumCalculatedEvent.class);
```

So `jsonMapper.writeValueAsString()` is directly available with no additional changes needed.

### Verify: fallback for null breakdown
When `event.getBreakdown()` is `null`, the code already skips setting details via the `if (event.getBreakdown() != null)` guard. This is correct — no change needed here.

## Test Update

### In `EstimationSagaConsumerTest.java`

Find the test case for `PremiumCalculated` event (it tests that premium and details are set on the estimation entity). The current assertion likely checks `estimation.getDetails()` or `estimation.setDetails()` — update it to verify the value is valid JSON.

**Example update for the `premiumCalculatedEvent_transitionsToCompleted()` test:**

After the fix, `estimation.getDetails()` should contain valid JSON like `{"base":1500.00}`. Add an assertion:

```java
// After the fix, details should be valid JSON, not Map.toString() format
assertThat(estimation.getDetails()).startsWith("{").endsWith("}");
assertThat(estimation.getDetails()).contains("\"base\"");
```

Or for a more precise test, construct the expected JSON string and compare:

```java
String expectedJson = jsonMapper.writeValueAsString(
        Map.of("base", new BigDecimal("1500.00")));
assertThat(estimation.getDetails()).isEqualTo(expectedJson);
```

### Integration test impact
The integration test `EstimationServiceApplicationTests` uses Testcontainers with a real PostgreSQL — if a test exercises `handlePremiumCalculated` with a non-null breakdown, the old code would have failed at the database level. After the fix, it should pass.

## Verification

```bash
# 1. Compile
.\gradlew.bat :services:estimation-service:compileJava

# 2. Run unit tests (especially the PremiumCalculated consumer test)
.\gradlew.bat :services:estimation-service:test

# 3. Run integration tests (with real PostgreSQL via Testcontainers)
.\gradlew.bat :services:estimation-service:test --tests "*ApplicationTests"
```

---

## Files Summary

### Modified
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java` — Line 137: `toString()` → `jsonMapper.writeValueAsString()`
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java` — Update assertions to verify valid JSON output

### Risk Assessment
- **Risk:** LOW. The `jsonMapper` is already in scope. The fix is a one-line change.
- **Regression risk:** None — the old behavior was broken (produced invalid JSON for PostgreSQL). The fix makes it correct.
- **Performance:** Negligible — `jsonMapper.writeValueAsString()` is the standard Jackson serialization, same as used elsewhere in the codebase.
