# Plan: Sprint 3 — Estimation Service — Step 4: Messaging Infrastructure

## Objective
Create the messaging infrastructure: `MessagePublisher` (wraps `StreamBridge` for Kafka), `DeduplicationStore` (in-memory dedup), and `EstimationEventPublisher` (domain events + SAGA event publisher).

## Context Files to Read First
1. **`services/insurance-service/src/main/java/.../config/MessagePublisher.java`** — Exact `MessagePublisher` pattern (StreamBridge wrapper)
2. **`services/insurance-service/src/main/java/.../config/DeduplicationStore.java`** — Exact `DeduplicationStore` pattern (ConcurrentHashMap, TTL, cleanup scheduler)
3. **`services/insurance-service/src/main/java/.../config/InsuranceEventPublisher.java`** — Event publisher pattern using `BaseEvent.toEnvelope()`
4. **`common/common-message/src/main/java/.../event/EventEnvelope.java`** — Event envelope structure
5. **`common/common-message/src/main/java/.../event/BaseEvent.java`** — Base event class with `toEnvelope()` method
6. **`common/common-message/src/main/java/.../event/EventConstants.java`** — Topic names (`ESTIMATION_SAGA`) and event type constants
7. **`docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md`** — Kafka topic `estimation.saga`

## Files to Create

### 1. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/MessagePublisher.java`

Identical to `insurance-service` and `customer-service` version. Package: `com.insurancemanagementsystem.estimation.config`.

```java
package com.insurancemanagementsystem.estimation.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessagePublisher {

    private final StreamBridge streamBridge;

    public void publish(String topic, Object message) {
        log.debug("Publishing message to {}: {}", topic, message);
        streamBridge.send(topic, message);
    }
}
```

### 2. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/DeduplicationStore.java`

Identical to existing services' version. Same package.

```java
package com.insurancemanagementsystem.estimation.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DeduplicationStore {

    private final ConcurrentHashMap<String, Instant> store = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(10);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "dedup-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
        log.info("DeduplicationStore initialized with TTL={}m, cleanup interval=5m", ttl.toMinutes());
    }

    public boolean isDuplicate(String sagaId, String eventType) {
        return store.containsKey(buildKey(sagaId, eventType));
    }

    public void markProcessed(String sagaId, String eventType) {
        store.put(buildKey(sagaId, eventType), Instant.now());
        log.trace("Marked as processed: sagaId={}, eventType={}", sagaId, eventType);
    }

    private static String buildKey(String sagaId, String eventType) {
        return sagaId + ":" + eventType;
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(ttl);
        int before = store.size();
        store.values().removeIf(ts -> ts.isBefore(cutoff));
        int after = store.size();
        if (before != after) {
            log.debug("DeduplicationStore cleanup: removed {} entries ({} remaining)", before - after, after);
        }
    }
}
```

### 3. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationEventPublisher.java`

This publisher publishes domain events about estimation (EstimationCompleted / EstimationRejected). It also provides helper methods to publish `EstimationFailed` event when the saga times out or fails.

```java
package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EstimationEventPublisher {

    private final MessagePublisher messagePublisher;

    /**
     * Publish EstimationFailed to estimation.saga (compensation event).
     * Called when saga times out or when validation/calculation fails.
     */
    public void publishEstimationFailed(UUID sagaId, UUID traceId, String reason, String failedStep) {
        EstimationFailedEvent event = EstimationFailedEvent.builder()
                .originalSagaId(sagaId)
                .reason(reason)
                .failedStep(failedStep)
                .build();

        EventEnvelope envelope = event.toEnvelope(sagaId, traceId != null ? traceId : UUID.randomUUID());
        messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);
        log.warn("Published EstimationFailed for sagaId={}: reason={}, failedStep={}", sagaId, reason, failedStep);
    }
}
```

### 4. Update `EstimationService` — Wire Messaging

Now open **`services/estimation-service/src/main/java/.../service/EstimationService.java`** (created in Step 3) and **wire the event publishing** by:

a) Adding `private final MessagePublisher messagePublisher;` as a dependency
b) Replacing the `// TODO` block in the `create()` method with actual event publishing:

**Import to add:**
```java
import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent;
```

**Updated `create()` method — the TODO block becomes:**
```java
// Publish EstimationRequested to estimation.saga to start SAGA choreography
EstimationRequestedEvent event = EstimationRequestedEvent.builder()
        .customerId(request.getCustomerId())
        .vehicleId(request.getVehicleId())
        .realEstateId(request.getRealEstateId())
        .insuranceTypeId(request.getInsuranceTypeId())
        .companyId(request.getCompanyId())
        .build();

EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());
messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);
log.info("Published EstimationRequested for sagaId={}", sagaId);
```

## Verification
```bash
.\gradlew.bat :services:estimation-service:compileJava
```

## Summary
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/MessagePublisher.java` ✅
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/DeduplicationStore.java` ✅
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationEventPublisher.java` ✅
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java` (updated — event publishing wired) ✅
