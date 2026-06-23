# Plan: Fix 07 — Implement Outbox Pattern for Atomic DB+Kafka Operations

## Objective

Fix the dual-write atomicity gap where DB saves and Kafka publishes are not atomic. Currently, if Kafka is unavailable after the DB transaction commits, the event is lost. Replace the `TransactionSynchronization.afterCommit()` workaround with a proper **Transactional Outbox pattern** using an `outbox_events` database table.

## Current Situation & Problem

### Current architecture (after Fix 04)

The `afterCommit` pattern was applied in Fix 04 as an improvement over direct publish:

```java
// EstimationService.java (current)
@Transactional
public EstimationResponse create(EstimationRequest request) {
    // ... DB save ...
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);
            }
        });
}
```

**Limitations of afterCommit approach:**
1. If Kafka publish throws (broker down), the event is **silently lost** — no retry mechanism
2. If JVM crashes between `afterCommit` registration and execution, the event is lost
3. Does NOT work in `handleFailed()` (no enclosing `@Transactional`)
4. Not horizontally scalable — each instance has its own in-memory afterCommit queue

### Cross-Service Analysis

| Service | DB→Kafka atomicity gap location | Current approach |
|---------|-------------------------------|------------------|
| **estimation-service** | `EstimationService.create()` → EstimationRequested | `afterCommit` (partial fix) |
| **estimation-service** | `SagaTimeoutService.checkForTimedOutSagas()` → EstimationFailed | `afterCommit` (partial fix) |
| **estimation-service** | `EstimationSagaConsumer.handleFailed()` → EstimationFailed | Direct publish (no atomicity) |
| **customer-service** | `CustomerSagaConsumer.handleEstimationRequested()` → outcome events | Direct publish (no atomicity) |
| **insurance-service** | `InsuranceSagaConsumer.*()` → PremiumCalculated / CalculationFailed | Direct publish (no atomicity) |

**ALL 3 services** have the atomicity gap where they publish events after DB operations without guarantee that the publish succeeds.

## Design: Transactional Outbox Pattern

### How it works

1. Instead of publishing to Kafka directly, insert the event into an `outbox_events` table **in the same DB transaction**
2. A separate background **Outbox Relay** polls the outbox table for unprocessed events and publishes them to Kafka
3. Once published, the relay marks the outbox event as processed (or deletes it)
4. The relay has retry logic with exponential backoff

### Benefits over afterCommit

| Aspect | afterCommit | Outbox |
|--------|-------------|--------|
| **Atomicity** | ❌ Event lost if Kafka publish fails | ✅ Event persisted in DB, retried |
| **Crash safety** | ❌ JVM crash = lost event | ✅ Event survives in DB |
| **Scale-out** | ❌ Per-instance queue | ✅ All instances poll same DB |
| **Retry** | ❌ No retry | ✅ Exponential backoff |
| **Ordering** | ✅ In-order per thread | ⚠️ At-least-once, may reorder |

### Database Table

```sql
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID,
    topic VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
      CHECK (status IN ('PENDING', 'PUBLISHING', 'FAILED')),
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events(status, created_at);
```

## Context Files to Read First

### Estimation-service files
1. **`services/estimation-service/src/main/java/.../estimation/service/EstimationService.java`** — `create()` method, `scheduleSagaEventPublish()` — currently uses afterCommit
2. **`services/estimation-service/src/main/java/.../estimation/service/SagaTimeoutService.java`** — `checkForTimedOutSagas()` — currently uses afterCommit
3. **`services/estimation-service/src/main/java/.../estimation/config/EstimationSagaConsumer.java`** — `handleFailed()` — direct publish, no atomicity
4. **`services/estimation-service/src/main/java/.../estimation/config/EstimationEventPublisher.java`** — event publisher that should use outbox
5. **`services/estimation-service/src/main/java/.../estimation/entity/Estimation.java`** — entity pattern to follow
6. **`services/estimation-service/src/main/resources/application.yml`** — may need relay config
7. **`infra/sql/estimation_db/init.sql`** — current DB schema (add outbox_events table)

### Reference: existing SagaEvent entity (pattern to follow)
8. **`services/estimation-service/src/main/java/.../estimation/entity/SagaEvent.java`** — entity structure pattern (UUID PK, timestamps, builder)
9. **`services/estimation-service/src/main/java/.../estimation/repository/SagaEventRepository.java`** — repository pattern

### Customer-service (cross-service reference for later migration)
10. **`services/customer-service/src/main/java/.../customer/config/CustomerSagaConsumer.java`** — direct publish after DB validation
11. **`services/customer-service/src/main/java/.../customer/config/CustomerEventPublisher.java`** — event publisher (if exists)
12. **`infra/sql/customer_db/init.sql`** — will need outbox table added later

### Insurance-service (cross-service reference for later migration)
13. **`services/insurance-service/src/main/java/.../insurance/config/InsuranceSagaConsumer.java`** — direct publish after DB calculation
14. **`services/insurance-service/src/main/java/.../insurance/config/InsuranceEventPublisher.java`** — event publisher
15. **`infra/sql/insurance_db/init.sql`** — will need outbox table added later

## Files to Create

### 1. `services/estimation-service/src/main/java/.../estimation/entity/OutboxEvent.java`

```java
package com.insurancemanagementsystem.estimation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status", columnList = "status, createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID sagaId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, columnDefinition = "JSONB")
    private String payload;  // Serialized EventEnvelope JSON

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "retry_count")
    private int retryCount = 0;

    @Column(name = "max_retries")
    private int maxRetries = 3;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Status {
        PENDING, PUBLISHING, FAILED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

### 2. `services/estimation-service/src/main/java/.../estimation/repository/OutboxEventRepository.java`

```java
package com.insurancemanagementsystem.estimation.repository;

import com.insurancemanagementsystem.estimation.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEvent> findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);

    List<OutboxEvent> findByStatusAndCreatedAtBefore(OutboxEvent.Status status, Instant cutoff);
}
```

### 3. `services/estimation-service/src/main/java/.../estimation/config/OutboxRelay.java`

```java
package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.estimation.entity.OutboxEvent;
import com.insurancemanagementsystem.estimation.repository.OutboxEventRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final MessagePublisher messagePublisher;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "outbox-relay");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${estimation.outbox.poll-interval-ms:1000}")
    private int pollIntervalMs;

    @Value("${estimation.outbox.batch-size:10}")
    private int batchSize;

    @Value("${estimation.outbox.max-retries:3}")
    private int maxRetries;

    @Value("${estimation.outbox.failed-ttl-minutes:60}")
    private int failedTtlMinutes;

    @PostConstruct
    public void init() {
        scheduler.scheduleWithFixedDelay(this::processOutbox, 5, pollIntervalMs, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::cleanupFailedEvents, 10, 30, TimeUnit.MINUTES);
        log.info("OutboxRelay initialized: pollInterval={}ms, batchSize={}, maxRetries={}",
                pollIntervalMs, batchSize, maxRetries);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * Main relay loop: poll PENDING events, publish to Kafka, mark as processed (delete).
     */
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Processing {} outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Mark as PUBLISHING to prevent duplicate processing
                event.setStatus(OutboxEvent.Status.PUBLISHING);
                outboxEventRepository.save(event);

                // Publish to Kafka
                messagePublisher.publish(event.getTopic(), event.getPayload());

                // Successfully published — delete the outbox event
                outboxEventRepository.delete(event);
                log.debug("Published outbox event id={} to topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={} to topic={}: {}",
                        event.getId(), event.getTopic(), e.getMessage());
                event.setStatus(OutboxEvent.Status.FAILED);
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getMessage());
                if (event.getRetryCount() >= maxRetries) {
                    log.warn("Outbox event id={} reached max retries ({}). Giving up.", event.getId(), maxRetries);
                    // Leave as FAILED — manual intervention needed
                } else {
                    // Reset to PENDING for retry on next poll
                    event.setStatus(OutboxEvent.Status.PENDING);
                }
                outboxEventRepository.save(event);
            }
        }
    }

    /**
     * Clean up old FAILED events that exceed TTL.
     */
    @Transactional
    public void cleanupFailedEvents() {
        Instant cutoff = Instant.now().minus(failedTtlMinutes, ChronoUnit.MINUTES);
        List<OutboxEvent> staleFailed = outboxEventRepository
                .findByStatusAndCreatedAtBefore(OutboxEvent.Status.FAILED, cutoff);
        if (!staleFailed.isEmpty()) {
            outboxEventRepository.deleteAll(staleFailed);
            log.info("Cleaned up {} stale FAILED outbox events", staleFailed.size());
        }
    }
}
```

## Files to Modify

### 4. `services/estimation-service/src/main/java/.../estimation/service/EstimationService.java`

**Remove** the `scheduleSagaEventPublish()` method and its afterCommit usage.
**Replace** with: insert an `OutboxEvent` into the outbox table within the existing `@Transactional` method.

**BEFORE (current):**
```java
@Transactional
public EstimationResponse create(EstimationRequest request) {
    // ... validation ...
    // ... build estimation ...
    estimation = estimationRepository.save(estimation);
    log.info("Created estimation id={} with sagaId={}", estimation.getId(), sagaId);

    // Defer publish until after DB transaction commits (atomicity)
    scheduleSagaEventPublish(request, sagaId);

    return EstimationResponse.fromEntity(estimation);
}

void scheduleSagaEventPublish(EstimationRequest request, UUID sagaId) {
    EstimationRequestedEvent event = EstimationRequestedEvent.builder()
            .customerId(request.getCustomerId())
            .vehicleId(request.getVehicleId())
            .realEstateId(request.getRealEstateId())
            .insuranceTypeId(request.getInsuranceTypeId())
            .companyId(request.getCompanyId())
            .build();
    EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);
                log.info("Published EstimationRequested for sagaId={}", sagaId);
            }
        });
}
```

**AFTER:**
```java
@Transactional
public EstimationResponse create(EstimationRequest request) {
    // ... validation ...
    // ... build estimation ...
    estimation = estimationRepository.save(estimation);
    log.info("Created estimation id={} with sagaId={}", estimation.getId(), sagaId);

    // Insert outbox event instead of publishing directly (atomic with DB save)
    saveOutboxEvent(sagaId, request);

    return EstimationResponse.fromEntity(estimation);
}

private void saveOutboxEvent(UUID sagaId, EstimationRequest request) {
    EstimationRequestedEvent event = EstimationRequestedEvent.builder()
            .customerId(request.getCustomerId())
            .vehicleId(request.getVehicleId())
            .realEstateId(request.getRealEstateId())
            .insuranceTypeId(request.getInsuranceTypeId())
            .companyId(request.getCompanyId())
            .build();
    EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());

    String payloadJson;
    try {
        payloadJson = jsonMapper.writeValueAsString(envelope);
    } catch (Exception e) {
        throw new RuntimeException("Failed to serialize outbox payload for sagaId=" + sagaId, e);
    }

    OutboxEvent outboxEvent = OutboxEvent.builder()
            .sagaId(sagaId)
            .topic(EventConstants.ESTIMATION_SAGA)
            .payload(payloadJson)
            .status(OutboxEvent.Status.PENDING)
            .build();
    outboxEventRepository.save(outboxEvent);
    log.info("Saved outbox event for sagaId={} to topic={}", sagaId, EventConstants.ESTIMATION_SAGA);
}
```

**Imports to update:**
```java
// Remove:
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Add:
import com.insurancemanagementsystem.estimation.entity.OutboxEvent;
import com.insurancemanagementsystem.estimation.repository.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;
```

**Add dependencies:** Add `private final OutboxEventRepository outboxEventRepository;` and `private final JsonMapper jsonMapper;` to constructor.

### 5. `services/estimation-service/src/main/java/.../estimation/service/SagaTimeoutService.java`

**Replace** afterCommit pattern with outbox insert:

**BEFORE:**
```java
estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails("SAGA timed out after " + timeoutMinutes + " minutes");
estimationRepository.save(estimation);

// Defer publish until after DB transaction commits (atomicity)
UUID capturedSagaId = sagaId;
int capturedTimeout = timeoutMinutes;
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            estimationEventPublisher.publishEstimationFailed(...);
        }
    });
```

**AFTER:**
```java
estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails("{\"reason\":\"SAGA timed out after " + timeoutMinutes + " minutes\"}");
estimationRepository.save(estimation);

// Insert outbox event instead of direct publish
saveOutboxEvent(sagaId, "SAGA timed out after " + timeoutMinutes + " minutes", "SagaTimeoutService");
```

Add a private helper method:
```java
private void saveOutboxEvent(UUID sagaId, String reason, String failedStep) {
    EstimationFailedEvent event = EstimationFailedEvent.builder()
            .originalSagaId(sagaId)
            .reason(reason)
            .failedStep(failedStep)
            .build();
    EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());

    String payloadJson;
    try {
        payloadJson = jsonMapper.writeValueAsString(envelope);
    } catch (Exception e) {
        log.error("Failed to serialize outbox payload for sagaId={}", sagaId, e);
        return; // Acceptable — timeout will retry on next cycle
    }

    OutboxEvent outboxEvent = OutboxEvent.builder()
            .sagaId(sagaId)
            .topic(EventConstants.ESTIMATION_SAGA)
            .payload(payloadJson)
            .status(OutboxEvent.Status.PENDING)
            .build();
    outboxEventRepository.save(outboxEvent);
    log.info("Saved outbox event for sagaId={} — timeout rejection", sagaId);
}
```

**Imports to update:**
```java
// Remove:
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Add:
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent;
import com.insurancemanagementsystem.estimation.entity.OutboxEvent;
import com.insurancemanagementsystem.estimation.repository.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;
```

### 6. `services/estimation-service/src/main/java/.../estimation/config/EstimationSagaConsumer.java` — `handleFailed()`

This method is NOT `@Transactional`, so the afterCommit approach can't work. **Replace** direct publish with outbox insert:

**BEFORE (line 189):**
```java
estimation.setStatus(Estimation.Status.REJECTED);
estimation.setDetails(reason);
estimationRepository.save(estimation);
log.info("Estimation {} rejected for sagaId={}: {}", estimation.getId(), sagaId, reason);

// Publish EstimationFailed for compensation in other services
estimationEventPublisher.publishEstimationFailed(sagaId, traceId, reason, eventType);
```

**AFTER:**
```java
estimation.setStatus(Estimation.Status.REJECTED);
try {
    estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
} catch (Exception e) {
    estimation.setDetails("{\"reason\":\"" + reason + "\"}");
}
estimationRepository.save(estimation);
log.info("Estimation {} rejected for sagaId={}: {}", estimation.getId(), sagaId, reason);

// Insert outbox event for atomic delivery
saveOutboxEvent(sagaId, reason, eventType);
```

Add same `saveOutboxEvent()` helper as in SagaTimeoutService.

### 7. `infra/sql/estimation_db/init.sql`

Add the `outbox_events` table:

```sql
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID,
    topic VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events(status, created_at);
```

### 8. `services/estimation-service/src/main/resources/application.yml`

Add outbox configuration:

```yaml
estimation:
  saga:
    timeout-minutes: 5
    poll-interval-ms: 30000
  outbox:
    poll-interval-ms: 1000
    batch-size: 10
    max-retries: 3
    failed-ttl-minutes: 60
```

## Test Updates

### 9. Update `EstimationServiceTest.java`

- Remove `TransactionSynchronizationManager.initSynchronization()` / `clearSynchronization()` from `@BeforeEach`/`@AfterEach` (no longer needed)
- Update `create()` test to verify `outboxEventRepository.save()` was called instead of `messagePublisher.publish()`
- Add mock for `OutboxEventRepository` and `JsonMapper`

### 10. Update `SagaTimeoutServiceTest.java`

- Remove `TransactionSynchronizationManager.initSynchronization()` / `clearSynchronization()`
- Update test to verify `outboxEventRepository.save()` was called with correct data
- Add mock for `OutboxEventRepository`

### 11. Update `EstimationSagaConsumerTest.java`

- Update `handleFailed()` tests to verify outbox insert instead of direct event publisher call

### 12. NEW: `OutboxRelayTest.java`

Create unit tests for `OutboxRelay.processOutbox()`:
- No pending events → no-op
- Pending event published → deleted from outbox
- Pending event publish fails → status set to FAILED, retried
- Event reaches max retries → stays FAILED

## Verification

```bash
# 1. Compile estimation-service
.\gradlew.bat :services:estimation-service:compileJava

# 2. Run all estimation-service tests
.\gradlew.bat :services:estimation-service:test

# 3. JaCoCo coverage check
.\gradlew.bat :services:estimation-service:jacocoCoverageVerification
```

## Execution Checklist

- [ ] Read context files
- [ ] Create `OutboxEvent.java` entity
- [ ] Create `OutboxEventRepository.java`
- [ ] Create `OutboxRelay.java` (scheduled relay)
- [ ] Modify `EstimationService.java` — replace afterCommit with outbox insert
- [ ] Modify `SagaTimeoutService.java` — replace afterCommit with outbox insert
- [ ] Modify `EstimationSagaConsumer.java` `handleFailed()` — add outbox insert
- [ ] Edit `infra/sql/estimation_db/init.sql` — add outbox_events table
- [ ] Edit `application.yml` — add outbox config
- [ ] Update `EstimationServiceTest.java` — remove sync init, add outbox verification
- [ ] Update `SagaTimeoutServiceTest.java` — remove sync init, add outbox verification
- [ ] Update `EstimationSagaConsumerTest.java` — verify outbox for handleFailed
- [ ] Create `OutboxRelayTest.java`
- [ ] Compile: `BUILD SUCCESSFUL`
- [ ] All tests pass

## Future Work (Not in Scope)

- Migrate customer-service and insurance-service to use the outbox pattern:
  - Add `outbox_events` table to `customer_db` and `insurance_db`
  - Create shared `OutboxEvent` entity and `OutboxRelay` in `common-message` module
  - Replace direct publish in `CustomerSagaConsumer` and `InsuranceSagaConsumer`

## Risk Assessment

- **Risk:** MEDIUM. Introduces new entity, repository, and background relay. The relay runs on a separate thread and polls the DB. Must ensure it doesn't cause DB contention under load.
- **Fallback:** If Kafka is down, events queue in the outbox table. Once Kafka comes back, the relay picks them up. Events that exceed maxRetries (3) stay in FAILED status until manual intervention.
- **Ordering:** Events within the same saga may be published out of order if the relay processes them in batches. Since SAGA events are state-machine-driven (not order-dependent), this is acceptable.
- **Duplicate events:** The existing `SagaEvent` dedup table on the consumer side already handles duplicate events. The outbox pattern may deliver the same event twice in rare cases (Kafka broker failure after send but before ack) — the dedup table handles this.
