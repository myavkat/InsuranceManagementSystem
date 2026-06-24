# Plan: Fix 09 — Fix OutboxRelay `@Transactional` Self-Invocation Bypass & Message Loss Window

## Objective

Fix the **critical defect** where `@Transactional` annotations on `OutboxRelay.processOutbox()` and `cleanupFailedEvents()` are silently bypassed because `ScheduledExecutorService` calls `this::processOutbox` on the raw bean, not the Spring AOP proxy. Also fix the message-loss window where `StreamBridge.send()` (non-blocking) returns before Kafka acknowledges, yet the outbox row is immediately deleted.

## Root Cause

### Problem 1: @Transactional bypass

```java
// OutboxRelay.java init():
scheduler.scheduleWithFixedDelay(this::processOutbox, 5, pollIntervalMs, TimeUnit.MILLISECONDS);
```

`this::processOutbox` captures a method reference on the **raw bean instance**. Spring AOP creates the transactional proxy **after** `@PostConstruct` returns. The scheduler's thread calls the raw method directly — `@Transactional` is **never applied**.

**Consequence:**
- Each JPA call (`save`, `delete`, `findTop...`) runs in its own implicit transaction
- `@Lock(PESSIMISTIC_WRITE)` on `findTop10ByStatus...` is acquired then immediately released — **race condition** where two relay instances can process the same event
- If crash occurs between `save(PUBLISHING)` and `delete()`, event is stuck at `PUBLISHING` forever (never retried)
- No rollback on partial failures

### Problem 2: Async Kafka + immediate delete

```java
messagePublisher.publish(event.getTopic(), event.getPayload());  // ASYNC — returns immediately
outboxEventRepository.delete(event);                               // DELETED before Kafka ack
```

`StreamBridge.send()` returns as soon as the producer buffer accepts the message — NOT when Kafka confirms delivery. The outbox row is deleted before Kafka acknowledges. If the broker is down or the send fails asynchronously, the message is **lost forever**.

### Problem 3: Integration test validates wrong path

`EstimationServiceIntegrationTest` injects `OutboxRelay` via `@Autowired`, which IS the proxy. `@Transactional` works in tests. The test passes but validates behavior that **doesn't exist in production**. The test must be updated after the fix.

---

## Cross-Service Analysis

| Service | Affected? | Details |
|---------|-----------|---------|
| **estimation-service** | ✅ | `OutboxRelay.java` — both `processOutbox()` and `cleanupFailedEvents()` |
| customer-service | ❌ | No outbox relay |
| insurance-service | ❌ | No outbox relay |

**Only estimation-service is affected.**

---

## Context Files to Read First

### Primary files to modify
1. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/OutboxRelay.java`** (106 lines)
   - `init()` line 49 — `this::processOutbox` self-invocation
   - `processOutbox()` line 61-93 — `@Transactional` + `publish()` + `delete()`
   - `cleanupFailedEvents()` line 97 — `@Transactional`

2. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/OutboxEventRepository.java`** (20 lines)
   - `@Lock(PESSIMISTIC_WRITE)` on `findTop10ByStatusOrderByCreatedAtAsc`
   - `findByStatusAndCreatedAtBefore`

3. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/OutboxEvent.java`** (70 lines)
   - `Status` enum: `PENDING`, `PUBLISHING`, `FAILED` — may need `PUBLISHED` status

### Test files to update
4. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/OutboxRelayTest.java`** (132 lines)
   - Unit test — injects via `@InjectMocks` (no proxy), tests current (broken) path

5. **`services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceIntegrationTest.java`** (334 lines)
   - Integration test — uses `@Autowired` (proxy), validates `@Transactional` behavior

### Reference — pattern for TransactionTemplate usage
6. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`** (120 lines)
   - Uses `@Transactional` correctly on public methods called from controller

---

## Design Decision

### Approach A: Self-injection (simplest, minimal refactor)

```java
@Component
public class OutboxRelay {
    // Self-injection through a setter or @Lazy @Autowired
    @Lazy
    @Autowired
    private OutboxRelay self;   // this IS the proxy

    @PostConstruct
    public void init() {
        scheduler.scheduleWithFixedDelay(() -> self.processOutbox(), 5, pollIntervalMs, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(() -> self.cleanupFailedEvents(), 10, 30, TimeUnit.MINUTES);
    }
}
```

**Pros:** Minimal change. `@Lazy` avoids circular dependency at construction time.
**Cons:** Self-injection is a known anti-pattern. Some consider it a code smell.

### Approach B: TransactionTemplate injection (explicit, recommended)

```java
@Component
public class OutboxRelay {
    private final TransactionTemplate transactionTemplate;

    @PostConstruct
    public void init() {
        scheduler.scheduleWithFixedDelay(this::processOutbox, 5, pollIntervalMs, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::cleanupFailedEvents, 10, 30, TimeUnit.MINUTES);
    }

    public void processOutbox() {
        transactionTemplate.executeWithoutResult(status -> {
            // ... entire loop body in one transaction ...
        });
    }
}
```

**Pros:** Explicit, no proxy magic, clean.
**Cons:** Must inject `TransactionTemplate` (Spring Boot auto-configures it when `spring-tx` is on classpath).

### Approach C: Extract to separate @Service bean (cleanest architecture)

```java
@Service
@Transactional
public class OutboxProcessor {
    // ... move processOutbox() + cleanupFailedEvents() here ...
}

@Component
public class OutboxRelay {
    private final OutboxProcessor processor;

    @PostConstruct
    public void init() {
        scheduler.scheduleWithFixedDelay(processor::processOutbox, ...);
    }
}
```

**Pros:** Clear separation of concerns. `processor` is injected → always proxy. No self-reference.
**Cons:** More files changed. But architecturally cleanest.

### Chosen Approach: **Approach C** — Extract to `OutboxProcessor.java`

This is the cleanest pattern and matches the system's component-based architecture. The relay becomes a thin scheduler wrapper; the processor handles all transactional DB+Kafka logic.

### Fix for Problem 2: Message loss window

Replace the immediate `delete()` with a two-phase approach:
1. `save(PUBLISHING)` → `publish()` → `save(PUBLISHED)` (atomically in one transaction)
2. A separate scheduled cleanup pass deletes `PUBLISHED` events older than TTL

Or, simpler but slightly less robust: configure Kafka producer to be synchronous for outbox operations:
```yaml
spring:
  cloud:
    stream:
      kafka:
        binder:
          producer-properties:
            acks: all
```

**Chosen approach:** Add `PUBLISHED` status + separate cleanup pass. This works regardless of Kafka producer config.

---

## Files to Modify

### 1. `OutboxEvent.java` — Add `PUBLISHED` to `Status` enum

**BEFORE:**
```java
public enum Status {
    PENDING, PUBLISHING, FAILED
}
```

**AFTER:**
```java
public enum Status {
    PENDING, PUBLISHING, PUBLISHED, FAILED
}
```

### 2. `OutboxEventRepository.java` — Remove `@Lock` (no longer needed with TransactionTemplate)

**BEFORE:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
List<OutboxEvent> findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);
```

**AFTER:**
```java
List<OutboxEvent> findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);
```

Also add query for cleaning up PUBLISHED events:
```java
List<OutboxEvent> findByStatusAndCreatedAtBefore(OutboxEvent.Status status, Instant cutoff);
```
(This method already exists — it takes any `Status`. No change needed.)

### 3. Create `OutboxProcessor.java` (NEW FILE)

**Path:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/OutboxProcessor.java`

```java
package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.estimation.entity.OutboxEvent;
import com.insurancemanagementsystem.estimation.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final MessagePublisher messagePublisher;
    private final TransactionTemplate transactionTemplate;

    private int maxRetries = 3;
    private int failedTtlMinutes = 60;

    void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    void setFailedTtlMinutes(int failedTtlMinutes) { this.failedTtlMinutes = failedTtlMinutes; }

    /**
     * Process pending outbox events within a single transaction.
     * Called by {@link OutboxRelay} on its scheduled thread.
     */
    public void processOutbox() {
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEvent> pendingEvents = outboxEventRepository
                    .findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING);

            if (pendingEvents.isEmpty()) {
                return;
            }

            log.debug("Processing {} outbox events", pendingEvents.size());

            for (OutboxEvent event : pendingEvents) {
                try {
                    // Mark as PUBLISHING
                    event.setStatus(OutboxEvent.Status.PUBLISHING);
                    outboxEventRepository.save(event);

                    // Publish to Kafka
                    messagePublisher.publish(event.getTopic(), event.getPayload());

                    // Mark as PUBLISHED (will be cleaned up later, not immediately deleted)
                    event.setStatus(OutboxEvent.Status.PUBLISHED);
                    outboxEventRepository.save(event);
                    log.debug("Published outbox event id={} to topic={}", event.getId(), event.getTopic());
                } catch (Exception e) {
                    log.error("Failed to publish outbox event id={} to topic={}: {}",
                            event.getId(), event.getTopic(), e.getMessage());
                    event.setStatus(OutboxEvent.Status.FAILED);
                    event.setRetryCount(event.getRetryCount() + 1);
                    event.setLastError(e.getMessage());
                    if (event.getRetryCount() >= maxRetries) {
                        log.warn("Outbox event id={} reached max retries ({}). Giving up.", event.getId(), maxRetries);
                    } else {
                        event.setStatus(OutboxEvent.Status.PENDING);
                    }
                    outboxEventRepository.save(event);
                }
            }
        });
    }

    /**
     * Clean up successfully published events and old FAILED events.
     * Called periodically by {@link OutboxRelay}.
     */
    public void cleanupEvents() {
        transactionTemplate.executeWithoutResult(status -> {
            Instant publishCutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
            List<OutboxEvent> stalePublished = outboxEventRepository
                    .findByStatusAndCreatedAtBefore(OutboxEvent.Status.PUBLISHED, publishCutoff);
            if (!stalePublished.isEmpty()) {
                outboxEventRepository.deleteAllInBatch(stalePublished);
                log.info("Cleaned up {} stale PUBLISHED outbox events", stalePublished.size());
            }

            Instant failedCutoff = Instant.now().minus(failedTtlMinutes, ChronoUnit.MINUTES);
            List<OutboxEvent> staleFailed = outboxEventRepository
                    .findByStatusAndCreatedAtBefore(OutboxEvent.Status.FAILED, failedCutoff);
            if (!staleFailed.isEmpty()) {
                outboxEventRepository.deleteAllInBatch(staleFailed);
                log.info("Cleaned up {} stale FAILED outbox events", staleFailed.size());
            }

            // Recover PUBLISHING zombies (stuck for > 5 minutes)
            Instant publishingCutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
            List<OutboxEvent> stalePublishing = outboxEventRepository
                    .findByStatusAndCreatedAtBefore(OutboxEvent.Status.PUBLISHING, publishingCutoff);
            if (!stalePublishing.isEmpty()) {
                for (OutboxEvent event : stalePublishing) {
                    event.setStatus(OutboxEvent.Status.PENDING);
                    log.warn("Recovering stuck PUBLISHING outbox event id={}", event.getId());
                }
                outboxEventRepository.saveAll(stalePublishing);
                log.info("Recovered {} stuck PUBLISHING outbox events", stalePublishing.size());
            }
        });
    }
}
```

### 4. Modify `OutboxRelay.java` — Thin scheduler wrapper

**Full replacement:**

```java
package com.insurancemanagementsystem.estimation.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxProcessor outboxProcessor;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "outbox-relay");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${estimation.outbox.poll-interval-ms:1000}")
    private int pollIntervalMs;

    @Value("${estimation.outbox.max-retries:3}")
    private int maxRetries;

    @Value("${estimation.outbox.failed-ttl-minutes:60}")
    private int failedTtlMinutes;

    @PostConstruct
    public void init() {
        outboxProcessor.setMaxRetries(maxRetries);
        outboxProcessor.setFailedTtlMinutes(failedTtlMinutes);

        // Now calls through injected OutboxProcessor (always the proxy for @Transactional methods)
        scheduler.scheduleWithFixedDelay(outboxProcessor::processOutbox, 5, pollIntervalMs, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(outboxProcessor::cleanupEvents, 10, 30, TimeUnit.MINUTES);
        log.info("OutboxRelay initialized: pollInterval={}ms, maxRetries={}", pollIntervalMs, maxRetries);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
```

### 5. Update `OutboxRelayTest.java`

Unit test now tests `OutboxProcessor`, not `OutboxRelay`:

- Rename class to `OutboxProcessorTest`
- Inject `@Mock TransactionTemplate`, `@Mock OutboxEventRepository`, `@Mock MessagePublisher`
- Mock `transactionTemplate.executeWithoutResult()` to execute the `Consumer<TransactionStatus>` callback
- Update test cases to match new flow (PUBLISHED status, not delete)
- Remove `ReflectionTestUtils.setField` for relay-specific fields

### 6. Update `EstimationServiceIntegrationTest.java`

- Inject `OutboxProcessor` instead of / in addition to `OutboxRelay`
- Update assertions: event should transition to `PUBLISHED`, not be deleted
- Verify that `OutboxProcessor.processOutbox()` publishes events

---

## Verification

```bash
# 1. Compile estimation-service
.\gradlew.bat :services:estimation-service:compileJava

# 2. Run OutboxRelay unit tests (now OutboxProcessorTest)
.\gradlew.bat :services:estimation-service:test --tests "*OutboxProcessorTest"

# 3. Run integration tests
.\gradlew.bat :services:estimation-service:test --tests "*EstimationServiceIntegrationTest"

# 4. Run all estimation-service tests
.\gradlew.bat :services:estimation-service:test
```

---

## Execution Checklist

- [ ] Read all 6 context files
- [ ] Edit `OutboxEvent.java` — add `PUBLISHED` to Status enum
- [ ] Edit `OutboxEventRepository.java` — remove `@Lock(PESSIMISTIC_WRITE)`
- [ ] Create `OutboxProcessor.java` with `processOutbox()` and `cleanupEvents()`
- [ ] Refactor `OutboxRelay.java` — thin wrapper calling `outboxProcessor`
- [ ] Rename/rewrite `OutboxRelayTest.java` → `OutboxProcessorTest.java`
- [ ] Update `EstimationServiceIntegrationTest.java` — new flow assertions
- [ ] Compile: `BUILD SUCCESSFUL`
- [ ] All tests pass

---

## Risk Assessment

- **Risk:** MEDIUM. Restructures the outbox from a single component to two (relay + processor). The relay becomes a pure scheduling concern; the processor handles transactional logic.
- **TransactionTemplate:** `transactionTemplate.executeWithoutResult()` creates a new transaction for each call. This means `processOutbox()` has a SINGLE transaction per poll cycle — all events processed in one call share a transaction. If one event's Kafka publish throws, the entire batch rolls back (all events return to PENDING). This is acceptable for at-most-10 events per batch.
- **PUBLISHED cleanup:** Events now stay in the DB for 5 minutes after successful publish (instead of immediate delete). Slightly more storage, but eliminates the message-loss window.
- **Regression risk:** LOW. All existing outbox behavior (PENDING→publish→delete) is replaced with PENDING→PUBLISHING→publish→PUBLISHED→cleanup. Consumer behavior is unchanged.

---

## Dependencies

- This plan must be implemented BEFORE Plan 10 (ACID gaps in services), as both `SagaTimeoutService` and `EstimationSagaConsumer` write to the outbox table.
