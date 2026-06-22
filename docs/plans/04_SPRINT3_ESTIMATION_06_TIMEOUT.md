# Plan: Sprint 3 — Estimation Service — Step 6: Timeout Compensation Scheduler

## Objective
Create a scheduled task that detects stale `STARTED` estimations (exceeding configurable timeout) and transitions them to `REJECTED`, publishing `EstimationFailed`.

## Context Files to Read First
1. **`common/common-message/src/main/java/.../event/saga/EstimationFailedEvent.java`** — The event POJO to publish
2. **`docs/outlines/03_SAGA_PATTERN.md`** — Timeout rules (section "Timeout" — 5 min default, `@Scheduled`)
3. **`docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md`** — Estimation service SAGA consumers/producers (section 6)
4. **`services/estimation-service/src/main/resources/application.yml`** — Already has `estimation.saga.timeout-minutes: 5` at bottom

## Design

The timeout scheduler:
- Runs every 30 seconds (fixed delay)
- Queries `estimationRepository.findByStatusAndCreatedAtBefore(Status.STARTED, cutoffInstant)`
- For each stale estimation:
  1. Sets status to `REJECTED`
  2. Sets details to `"SAGA timed out"`
  3. Publishes `EstimationFailed` to `estimation.saga` via `EstimationEventPublisher`
- The timeout is configurable via `estimation.saga.timeout-minutes` in `application.yml`

## File to Create

### `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java`

```java
package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.estimation.config.EstimationEventPublisher;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaTimeoutService {

    private final EstimationRepository estimationRepository;
    private final EstimationEventPublisher estimationEventPublisher;

    @Value("${estimation.saga.timeout-minutes:5}")
    private int timeoutMinutes;

    /**
     * Scheduled task that runs every 30 seconds.
     * Finds all estimations in STARTED status older than timeoutMinutes,
     * transitions them to REJECTED, and publishes EstimationFailed.
     */
    @Scheduled(fixedDelayString = "${estimation.saga.poll-interval-ms:30000}")
    @Transactional
    public void checkForTimedOutSagas() {
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
                UUID sagaId = estimation.getSagaId();
                log.warn("Timing out estimation id={}, sagaId={}, created at {}",
                        estimation.getId(), sagaId, estimation.getCreatedAt());

                // Transition to REJECTED
                estimation.setStatus(Estimation.Status.REJECTED);
                estimation.setDetails("SAGA timed out after " + timeoutMinutes + " minutes");
                estimationRepository.save(estimation);

                // Publish compensation event
                estimationEventPublisher.publishEstimationFailed(
                        sagaId,
                        null,
                        "SAGA timed out after " + timeoutMinutes + " minutes",
                        "SagaTimeoutService");
            } catch (Exception e) {
                log.error("Failed to process timeout for estimation id={}", estimation.getId(), e);
            }
        }
    }
}
```

## Configuration

The new `fixedDelayString` config property `${estimation.saga.poll-interval-ms:30000}` should be added to `application.yml`:

```yaml
estimation:
  saga:
    timeout-minutes: 5
    poll-interval-ms: 30000
```

This means:
- `timeout-minutes: 5` — estimations older than 5 minutes are considered stale
- `poll-interval-ms: 30000` — the scheduler checks every 30 seconds

Both are configurable via application.yml or environment variables.

## How It Works

**Activation path:**
1. `@EnableScheduling` on `EstimationServiceApplication` (created in Step 1) enables `@Scheduled` detection
2. `@Scheduled(fixedDelayString = "${estimation.saga.poll-interval-ms:30000}")` tells Spring to invoke `checkForTimedOutSagas()` every 30s
3. The fixedDelay ensures the next run waits for the previous run to complete (avoids overlapping executions)

**Timeout logic:**
- `Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES)` calculates the cutoff
- `findByStatusAndCreatedAtBefore(STARTED, cutoff)` finds stale entries using the repository query method
- For each stale estimation: transition + publish compensation

**Idempotency:**
- The `@Scheduled` task does not need deduplication because it writes state changes to the DB
- If the same estimation is found in consecutive runs (shouldn't happen since status changed to REJECTED), `findByStatusAndCreatedAtBefore(STARTED, ...)` won't find it

## Verification

```bash
.\gradlew.bat :services:estimation-service:compileJava
```

The scheduler doesn't need a running database to compile — just verify it compiles cleanly.

## Summary
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java` ✅
- `services/estimation-service/src/main/resources/application.yml` (updated — added `poll-interval-ms`) ✅
