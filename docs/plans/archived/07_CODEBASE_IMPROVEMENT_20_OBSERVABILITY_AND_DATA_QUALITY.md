# Plan 20: Fix Observability & Data Quality — traceId Propagation + JSON Serialization

## Severity: MEDIUM (broken distributed tracing) / MEDIUM (malformed JSON on edge cases)

## Status

- [ ] Propagate traceId through `OutboxEventSerializer.buildEstimationFailedOutboxEvent()`
- [ ] Update all call sites to pass traceId
- [ ] Fix `SagaTimeoutService` — use `jsonMapper.writeValueAsString()` instead of string concatenation
- [ ] Run affected tests and verify

---

## Context

### Problem 1: traceId Not Propagated — Broken End-to-End Tracing

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/OutboxEventSerializer.java`

At line 33, `buildEstimationFailedOutboxEvent()` generates a **fresh random traceId** instead of propagating the original:

```java
public OutboxEvent buildEstimationFailedOutboxEvent(
        UUID sagaId, String reason, String failedStep, String topic) {

    EstimationFailedEvent event = EstimationFailedEvent.builder()
            .originalSagaId(sagaId)
            .reason(reason)
            .failedStep(failedStep)
            .build();

    EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());
    //                                              ^^^^^^^^^^^^^^^
    //                                              NEW random traceId — breaks the chain
    // ...
}
```

The callers HAVE the original traceId but don't pass it:
- **`EstimationSagaConsumer.handleFailed()`** (line 148): receives `traceId` as parameter but never passes it to `buildEstimationFailedOutboxEvent` (line 167-168)
- **`SagaTimeoutService.checkForTimedOutSagas()`** (line 55-63): has `estimation.getSagaId()` but the original traceId is available nowhere in the timeout path (the estimation entity doesn't store it)

When a saga fails (customer invalidation → estimation rejection → `EstimationFailed` event), the failure event carries a random traceId. In log aggregation (ELK/Datadog), **the rejection event is disconnected from all related saga events**. Operators cannot trace the failure back to its cause without manually correlating `sagaId` across services.

### Problem 2: Manual JSON String Concatenation — Malformed JSON on Special Characters

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java`

At line 67, the `details` field is built via string concatenation:

```java
estimation.setDetails("{\"reason\":\"" + reason + "\"}");
```

If `reason` contains double-quotes or backslashes (e.g., `Customer said "invalid" policy`), the result is malformed JSON:
```
{"reason":"Customer said "invalid" policy"}
```

Compare with **`EstimationSagaConsumer.handleFailed()`** at lines 171-175, which correctly uses Jackson:

```java
try {
    estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
} catch (Exception e) {
    log.warn("Failed to serialize rejection details for sagaId={}", sagaId, e);
    estimation.setDetails("{\"reason\":\"" + reason + "\"}"); // fallback
}
```

Even the `handleFailed()` fallback has the same concatenation problem, but at least its primary path uses proper serialization.

---

## Fix Strategy

### Fix 1: Propagate traceId Through OutboxEventSerializer

**Step 1:** Add `traceId` parameter to `buildEstimationFailedOutboxEvent()`:

```java
public OutboxEvent buildEstimationFailedOutboxEvent(
        UUID sagaId, UUID traceId, String reason, String failedStep, String topic) {
    //                                                  ^^^^^^^^^ new parameter

    EstimationFailedEvent event = EstimationFailedEvent.builder()
            .originalSagaId(sagaId)
            .reason(reason)
            .failedStep(failedStep)
            .build();

    EventEnvelope envelope = event.toEnvelope(sagaId, traceId);
    //                                              ^^^^^^^ use the propagated traceId
    // ...
}
```

**Step 2:** Update call site in `EstimationSagaConsumer.handleFailed()` (line 167-168):
```java
// Change FROM:
OutboxEvent outboxEvent = outboxEventSerializer.buildEstimationFailedOutboxEvent(
        sagaId, reason, eventType, EventConstants.ESTIMATION_SAGA);

// Change TO:
OutboxEvent outboxEvent = outboxEventSerializer.buildEstimationFailedOutboxEvent(
        sagaId, traceId, reason, eventType, EventConstants.ESTIMATION_SAGA);
```

**Step 3:** Update call site in `SagaTimeoutService.checkForTimedOutSagas()` (lines 62-63):

The timeout path has no original traceId (the estimation entity doesn't store it). Options:
- **Option A:** Use `sagaId` as traceId (at least saga-correlatable)
- **Option B:** Store `traceId` on the `Estimation` entity at creation time
- **Option C:** Generate a new UUID but log a warning that trace context is lost

**Use Option A** — use `sagaId` as `traceId` in the timeout path. While not ideal (it conflates saga and trace), it's better than a random UUID because all log entries for this saga will share the same traceId, making correlation possible.

### Fix 2: Use jsonMapper for JSON Serialization

**Step 1:** Inject `JsonMapper` into `SagaTimeoutService` (add field after line 28):
```java
private final JsonMapper jsonMapper;
```

Note: `SagaTimeoutService` already uses `@RequiredArgsConstructor` and has other final fields, so adding this field automatically adds the constructor parameter.

**Step 2:** Replace string concatenation at line 67:

```java
// Change FROM:
estimation.setDetails("{\"reason\":\"" + reason + "\"}");

// Change TO:
try {
    estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
} catch (Exception e) {
    log.warn("Failed to serialize timeout details for sagaId={}", sagaId, e);
    estimation.setDetails("{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}");
}
```

The fallback now minimally escapes double-quotes to reduce (not eliminate) the chance of malformed JSON.

---

## Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | `services/estimation-service/src/main/java/.../estimation/config/OutboxEventSerializer.java` | Add `traceId` parameter to `buildEstimationFailedOutboxEvent()`; use it instead of `UUID.randomUUID()` |
| 2 | `services/estimation-service/src/main/java/.../estimation/config/EstimationSagaConsumer.java` | Pass `traceId` to `buildEstimationFailedOutboxEvent()` at line 167-168 |
| 3 | `services/estimation-service/src/main/java/.../estimation/service/SagaTimeoutService.java` | Inject `JsonMapper`; use `writeValueAsString()` instead of string concatenation; pass `sagaId` as traceId |
| 4 | `services/estimation-service/src/test/java/.../estimation/config/OutboxEventSerializerTest.java` | Update test if it exists — verify traceId propagation |

---

## Files to Read for Full Context

| File | Purpose |
|------|---------|
| `services/estimation-service/src/main/java/.../estimation/config/OutboxEventSerializer.java` | Full serializer — 49 lines |
| `services/estimation-service/src/main/java/.../estimation/config/EstimationSagaConsumer.java` | Call site in handleFailed() — line 167-168 |
| `services/estimation-service/src/main/java/.../estimation/service/SagaTimeoutService.java` | Call site + string concatenation — lines 62-67 |
| `services/estimation-service/src/main/java/.../estimation/entity/Estimation.java` | Check if traceId field exists for Option B |
| `common/common-message/src/main/java/.../event/saga/EstimationFailedEvent.java` | Verify event structure |
| `common/common-message/src/main/java/.../event/EventEnvelope.java` | Verify toEnvelope() signature |
| `docs/outlines/03_SAGA_PATTERN.md` | SAGA event propagation rules |

---

## Test Files to Update

| File | What to verify |
|------|---------------|
| `services/estimation-service/src/test/.../config/EstimationSagaConsumerTest.java` | Verify `buildEstimationFailedOutboxEvent` called with correct traceId |
| `services/estimation-service/src/test/.../service/SagaTimeoutServiceTest.java` | Mock `jsonMapper`; verify (`sagaId`, `sagaId` as traceId, reason, eventType, topic) passed |
| `services/estimation-service/src/test/.../config/OutboxProcessorTest.java` | No changes expected |

---

## Verification Checklist

- [ ] `buildEstimationFailedOutboxEvent()` signature includes `UUID traceId` parameter
- [ ] `UUID.randomUUID()` call removed from serializer; uses propagated `traceId` instead
- [ ] `EstimationSagaConsumer.handleFailed()` passes `traceId` correctly
- [ ] `SagaTimeoutService.checkForTimedOutSagas()` passes `sagaId` as traceId
- [ ] `SagaTimeoutService` uses `jsonMapper.writeValueAsString(Map.of("reason", reason))` for details
- [ ] `SagaTimeoutService` fallback minimally escapes double-quotes
- [ ] All existing tests compile and pass with updated signatures
- [ ] New test (optional): verify traceId propagation through the full estimation→failure flow

---

## Risk Assessment

**Fix 1 (traceId propagation): VERY LOW.** The `traceId` parameter addition is a source-compatible change — all call sites are in the same module and are updated simultaneously. The `UUID.randomUUID()` replacement means traceId is now meaningful instead of random — no behavioral change to the message content (same field type).

**Fix 2 (JSON serialization): LOW.** Replacing string concatenation with `jsonMapper.writeValueAsString()` is a pure improvement — it handles all JSON escaping correctly. The fallback now minimally escapes double-quotes. The `JsonMapper` injection into `SagaTimeoutService` requires verifying it's available as a bean (it is — defined in `EstimationSagaConsumer` as `@Bean` but also available as a standalone `@Component` via Spring Boot auto-configuration).
