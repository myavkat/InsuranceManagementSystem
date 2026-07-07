# Fix 01 — Transaction Boundary Violations in SAGA Consumers

## Status: COMPLETED
## Parent: Post-Review Fixes (Phase 2 code review, 2026-07-07)
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Fix transaction boundary violations discovered during code review of the `phase2-message-queue-event-driven-integration` branch. Three related issues must be fixed:

1. **`handleEstimationFailed` calls `tryInsertDedup()` without active transaction** — ALL 5 SAGA consumers
2. **`MessageListener.asConsumer()` calls `tryInsertDedup()` without active transaction** — the shared abstraction
3. **`SagaTimeoutService.checkForTimedOutSagas()` has no top-level try-catch** — AGENTS.md violation

All three are caused by `SagaEventRepository.insertDedupMarker()` having `@Transactional(propagation = Propagation.MANDATORY)`, which throws `IllegalTransactionStateException` when called without an active transaction.

## Context — Why This Matters

`SagaEventRepository.tryInsertDedup()` delegates to `insertDedupMarker()` — a native `INSERT … ON CONFLICT DO NOTHING` query. This method is annotated `@Transactional(propagation = Propagation.MANDATORY)`, meaning it MUST run inside an existing transaction. Without one, every call throws `IllegalTransactionStateException`.

The other handler methods in all 5 consumers (e.g., `handleCustomerValidated`, `handlePremiumCalculated`, `handleFailed`) correctly wrap their logic in `transactionTemplate.executeWithoutResult()`. Only `handleEstimationFailed` was missed — it calls `tryInsertDedup()` directly, outside any transaction.

Additionally, the shared `MessageListener` abstraction (which has zero subclasses today) has the same defect — `asConsumer()` calls `tryInsertDedup()` raw.

Finally, `SagaTimeoutService.checkForTimedOutSagas()` is a `@Scheduled` method with no top-level try-catch. AGENTS.md requires: *"Every method invoked by ScheduledExecutorService MUST have a top-level try-catch that logs the error and prevents it from propagating."*

## Files to Read Before Starting

1. `AGENTS.md` — SAGA Consumer Rules (line 20: transaction boundaries), Scheduled & Background Task Rules (line 37: top-level exception handler)
2. `common/common-message/src/main/java/com/insurancemanagementsystem/common/repository/SagaEventRepository.java` — `tryInsertDedup()` and `insertDedupMarker()` Javadoc and annotations
3. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java` — lines 230-238 (`handleEstimationFailed`)
4. `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerSagaConsumer.java` — lines 130-138 (`handleEstimationFailed`)
5. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/config/VehicleSagaConsumer.java` — lines 142-149 (`handleEstimationFailed`)
6. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/config/RealEstateSagaConsumer.java` — lines 127-134 (`handleEstimationFailed`)
7. `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java` — lines 172-181 (`handleEstimationFailed`)
8. `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/MessageListener.java` — lines 806-846 (`asConsumer()`)
9. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java` — entire file, especially line 43 (`checkForTimedOutSagas()`)
10. `common/common-message/src/test/java/com/insurancemanagementsystem/common/repository/SagaEventRepositoryTest.java` — test `tryInsertDedup_withoutTransaction_shouldThrow` confirms the failure mode

## Current State

### Bug Pattern (confirmed across ALL 5 consumers)

```java
// ❌ BROKEN — no transaction wrapping
private void handleEstimationFailed(EventEnvelope envelope) {
    UUID sagaId = envelope.getSagaId();
    String eventType = envelope.getEventType();
    if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {  // THROWS IllegalTransactionStateException
        return;
    }
    log.warn("Estimation failed for saga: {} — no compensation needed", sagaId);
}
```

### Correct Pattern (used by all other handlers)

```java
// ✅ CORRECT — wrapped in transaction
private void handleCustomerValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
    String eventType = envelope.getEventType();
    transactionTemplate.executeWithoutResult(status -> {
        if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
            return;
        }
        // ... business logic
    });
}
```

### Failure Scenario (what happens today)

1. An `EstimationFailed` event arrives at any of the 5 SAGA consumers
2. The consumer calls `handleEstimationFailed(envelope)`
3. `tryInsertDedup()` invokes `insertDedupMarker()` which requires `Propagation.MANDATORY`
4. No transaction exists → `IllegalTransactionStateException` is thrown
5. The exception propagates through the consumer lambda's catch block
6. The catch block re-throws it as `RuntimeException`
7. Spring Cloud Stream's binder retries (default: 3 attempts)
8. All retries fail identically (the method always lacks a transaction)
9. After max retries exhausted → message routed to `dlq.saga`
10. The dedup marker is NEVER written — the event is lost, not dedup'd

---

## Implementation Steps

### Step 1: Fix `handleEstimationFailed` in ALL 5 SAGA Consumers

- [x] **1.1** `EstimationSagaConsumer.java` (estimation-service) — wrap `handleEstimationFailed` body in `transactionTemplate.executeWithoutResult()`:

  **Current (broken):**
  ```java
  private void handleEstimationFailed(EventEnvelope envelope, UUID sagaId) {
      String eventType = envelope.getEventType();

      if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
          return;
      }

      log.warn("Estimation failed for saga: {} — no compensation needed (estimation state updated)", sagaId);
  }
  ```

  **Fixed:**
  ```java
  private void handleEstimationFailed(EventEnvelope envelope, UUID sagaId) {
      String eventType = envelope.getEventType();

      transactionTemplate.executeWithoutResult(status -> {
          if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
              return;
          }

          log.warn("Estimation failed for saga: {} — no compensation needed (estimation state updated)", sagaId);
      });
  }
  ```

  Note: The method signature is `handleEstimationFailed(EventEnvelope envelope, UUID sagaId)` — different from other services which take only `EventEnvelope envelope`. This is fine, just note the difference.

- [x] **1.2** `CustomerSagaConsumer.java` (customer-service) — same fix:

  **Current (broken):**
  ```java
  private void handleEstimationFailed(EventEnvelope envelope) {
      UUID sagaId = envelope.getSagaId();
      String eventType = envelope.getEventType();
      if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
          return;
      }

      log.warn("Estimation failed for saga: {} — no compensation needed (read-only validation)", sagaId);
  }
  ```

  **Fixed:**
  ```java
  private void handleEstimationFailed(EventEnvelope envelope) {
      UUID sagaId = envelope.getSagaId();
      String eventType = envelope.getEventType();

      transactionTemplate.executeWithoutResult(status -> {
          if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
              return;
          }

          log.warn("Estimation failed for saga: {} — no compensation needed (read-only validation)", sagaId);
      });
  }
  ```

- [x] **1.3** `VehicleSagaConsumer.java` (vehicle-service) — identical fix (same pattern as CustomerSagaConsumer)

- [x] **1.4** `RealEstateSagaConsumer.java` (realestate-service) — identical fix (same pattern as CustomerSagaConsumer)

- [x] **1.5** `InsuranceSagaConsumer.java` (insurance-service) — identical fix (same pattern as CustomerSagaConsumer)

### Step 2: Fix `MessageListener.asConsumer()` Transaction Wrapping

- [x] **2.1** `MessageListener.java` — wrap the dedup call AND `handleEvent()` in a transaction:

  **Current (broken):**
  ```java
  public Consumer<String> asConsumer() {
      return message -> {
          EventEnvelope envelope;
          try {
              envelope = jsonMapper.readValue(message, EventEnvelope.class);
          } catch (Exception e) {
              log.error("Failed to deserialize message — skipping (poison pill): {}", e.getMessage());
              return;
          }

          try {
              UUID sagaId = envelope.getSagaId();
              UUID traceId = envelope.getTraceId();
              String eventType = envelope.getEventType();

              MDC.put("sagaId", sagaId != null ? sagaId.toString() : "");
              MDC.put("traceId", traceId != null ? traceId.toString() : "");

              if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {  // ❌ NO TRANSACTION
                  log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
                  return;
              }

              Observation observation = Observation.createNotStarted("saga.process", observationRegistry)
                      .contextualName("process " + eventClass.getSimpleName())
                      .lowCardinalityKeyValue("event.type", eventType)
                      .highCardinalityKeyValue("saga.id", sagaId.toString());

              observation.observe(() -> {
                  T event = jsonMapper.convertValue(envelope.getPayload(), eventClass);
                  handleEvent(event, envelope);
              });
          } catch (Exception e) {
              log.error("Error processing message: {}", e.getMessage(), e);
              if (e instanceof RuntimeException re) throw re;
              throw new RuntimeException("Failed to process message", e);
          } finally {
              MDC.clear();
          }
      };
  }
  ```

  **Fixed:**
  ```java
  public Consumer<String> asConsumer() {
      return message -> {
          EventEnvelope envelope;
          try {
              envelope = jsonMapper.readValue(message, EventEnvelope.class);
          } catch (Exception e) {
              log.error("Failed to deserialize message — routing to DLQ: {}", e.getMessage(), e);
              throw new RuntimeException("Deserialization failed — routing to DLQ", e);
          }

          try {
              UUID sagaId = envelope.getSagaId();
              UUID traceId = envelope.getTraceId();
              String eventType = envelope.getEventType();

              MDC.put("sagaId", sagaId != null ? sagaId.toString() : "");
              MDC.put("traceId", traceId != null ? traceId.toString() : "");

              transactionTemplate.executeWithoutResult(status -> {
                  if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                      log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
                      return;
                  }

                  Observation observation = Observation.createNotStarted("saga.process", observationRegistry)
                          .contextualName("process " + eventClass.getSimpleName())
                          .lowCardinalityKeyValue("event.type", eventType)
                          .highCardinalityKeyValue("saga.id", sagaId.toString());

                  observation.observe(() -> {
                      T event = jsonMapper.convertValue(envelope.getPayload(), eventClass);
                      handleEvent(event, envelope);
                  });
              });
          } catch (Exception e) {
              log.error("Error processing message: {}", e.getMessage(), e);
              if (e instanceof RuntimeException re) throw re;
              throw new RuntimeException("Failed to process message", e);
          } finally {
              MDC.clear();
          }
      };
  }
  ```

  **Key changes:**
  1. Poison-pill deserialization now throws (routes to DLQ) instead of silently returning — matches the behavior of all 5 existing SAGA consumers after the Subtask 5 fix
  2. The dedup check AND `handleEvent()` are wrapped in `transactionTemplate.executeWithoutResult()` — ensures atomicity between dedup and business logic

- [x] **2.2** Verify the `TransactionTemplate` field is already injected (line 786 of MessageListener.java confirms it exists). No constructor change needed.

### Step 3: Add Top-Level Try-Catch to SagaTimeoutService

- [x] **3.1** `SagaTimeoutService.java` — add top-level try-catch around the entire `checkForTimedOutSagas()` method body:

  **Current (missing try-catch):**
  ```java
  @Scheduled(fixedDelayString = "${estimation.saga.poll-interval-ms:30000}")
  @Transactional
  public void checkForTimedOutSagas() {
      Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
      List<Estimation> staleEstimations = estimationRepository
              .findByStatusAndCreatedAtBefore(Estimation.Status.STARTED, cutoff);
      // ... loop body
  }
  ```

  **Fixed:**
  ```java
  @Scheduled(fixedDelayString = "${estimation.saga.poll-interval-ms:30000}")
  @Transactional
  public void checkForTimedOutSagas() {
      try {
          Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
          List<Estimation> staleEstimations = estimationRepository
                  .findByStatusAndCreatedAtBefore(Estimation.Status.STARTED, cutoff);

          if (staleEstimations.isEmpty()) {
              log.trace("No timed-out estimations found (timeout={}min)", timeoutMinutes);
              return;
          }

          log.warn("Found {} timed-out estimations (timeout={}min)", staleEstimations.size(), timeoutMinutes);

          for (Estimation estimation : staleEstimations) {
              try {
                  // ... existing per-item logic (already has internal try-catch for details serialization)
                  UUID sagaId = estimation.getSagaId();
                  log.warn("Timing out estimation id={}, sagaId={}, created at {}",
                          estimation.getId(), sagaId, estimation.getCreatedAt());

                  String reason = "SAGA timed out after " + timeoutMinutes + " minutes";
                  UUID traceId = estimation.getTraceId() != null ? estimation.getTraceId() : sagaId;

                  OutboxEvent outboxEvent = outboxEventSerializer.buildEstimationFailedOutboxEvent(
                          sagaId, traceId, reason, "SagaTimeoutService", EventConstants.ESTIMATION_SAGA);

                  estimation.setStatus(Estimation.Status.REJECTED);
                  try {
                      estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
                  } catch (Exception e) {
                      log.warn("Failed to serialize timeout details for sagaId={}", sagaId, e);
                      estimation.setDetails("{\"reason\":\"" + reason.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
                  }
                  estimationRepository.save(estimation);
                  outboxEventRepository.save(outboxEvent);

                  log.info("Rejected timed-out estimation sagaId={} and saved outbox event", sagaId);
              } catch (Exception e) {
                  log.error("Failed to timeout estimation sagaId={}: {}", estimation.getSagaId(), e.getMessage(), e);
                  // Continue with next estimation — don't let one failure block others
              }
          }
      } catch (Exception e) {
          log.error("SagaTimeoutService.checkForTimedOutSagas() failed — scheduler will retry on next tick", e);
          // Do NOT re-throw — prevents silent scheduler cancellation
      }
  }
  ```

  **Key changes:**
  1. Outermost try-catch wraps the ENTIRE method body (including the repository query)
  2. The existing per-item try-catch for details serialization is preserved
  3. A new per-item try-catch wraps each estimation's full processing (not just serialization)
  4. Neither catch block re-throws — preserves the scheduler from silent cancellation

### Step 4: Build and Verify

- [x] **4.1** Build all affected modules:
  ```bash
  .\gradlew.bat :common:common-message:build
  .\gradlew.bat :services:estimation-service:build
  .\gradlew.bat :services:customer-service:build
  .\gradlew.bat :services:vehicle-service:build
  .\gradlew.bat :services:realestate-service:build
  .\gradlew.bat :services:insurance-service:build
  ```

- [x] **4.2** Run all tests:
  ```bash
  .\gradlew.bat test
  ```

- [x] **4.3** Run the E2E SAGA tests specifically:
  ```bash
  .\gradlew.bat :services:estimation-service:test --tests "*SagaE2ETest*"
  ```

- [x] **4.4** Verify `handleEstimationFailed` is exercised in existing tests. If no test covers `EstimationFailed` event consumption, consider adding a unit test in each service's `*SagaConsumerTest.java`.

---

## Files to Modify

| File | Change |
|------|--------|
| `services/estimation-service/.../config/EstimationSagaConsumer.java` | Wrap `handleEstimationFailed` in `transactionTemplate.executeWithoutResult()` |
| `services/customer-service/.../config/CustomerSagaConsumer.java` | Same |
| `services/vehicle-service/.../config/VehicleSagaConsumer.java` | Same |
| `services/realestate-service/.../config/RealEstateSagaConsumer.java` | Same |
| `services/insurance-service/.../config/InsuranceSagaConsumer.java` | Same |
| `common/common-message/.../messaging/MessageListener.java` | Wrap dedup+handleEvent in transaction; change poison-pill from silent-skip to throw |
| `services/estimation-service/.../service/SagaTimeoutService.java` | Add top-level try-catch + per-item try-catch |

---

## Dependencies

- None (standalone fix — no other subtask depends on this)

## Completion Criteria

- [x] All 5 `handleEstimationFailed` methods are wrapped in `transactionTemplate.executeWithoutResult()`
- [x] `MessageListener.asConsumer()` wraps dedup and `handleEvent()` in transaction
- [x] `MessageListener.asConsumer()` throws on deserialization failure (not silent return)
- [x] `SagaTimeoutService.checkForTimedOutSagas()` has top-level try-catch
- [x] `.\gradlew.bat build` passes for all modules
- [x] All existing tests pass (no regression)
- [x] `SagaEventRepositoryTest.tryInsertDedup_withoutTransaction_shouldThrow` still passes (verifies the MANDATORY enforcement still works)
- [x] **Status: COMPLETED**
