# Sub-Plan 3: Insurance Service — Messaging & SAGA Consumer

**Parent Plan:** `docs/plans/03_SPRINT2_INSURANCE_SERVICE.md`
**Checklist items:** 3.1 through 3.7
**Prerequisite:** Sub-plans 1 (Scaffold & Domain) and 2 (CRUD API) must be COMPLETE.

---

## Context Files to Read

Before implementing, Read these exact pattern files:
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/MessagePublisher.java` — StreamBridge wrapper pattern
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerEventPublisher.java` — domain event publisher pattern
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerSagaConsumer.java` — SAGA consumer pattern (Spring Cloud Stream `Consumer<String>`) 
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/DeduplicationStore.java` — idempotency store pattern
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/service/CustomerService.java` — how event publishing is wired into service
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java` — YOUR entity (created in Sub-plan 1)
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/service/InsuranceService.java` — YOUR service (created in Sub-plan 2, needs wire-in)
- `services/insurance-service/src/main/resources/application.yml` — YOUR config (needs stream bindings)

**Common-message event schemas to reference (Read as needed):**
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/BaseEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventEnvelope.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventConstants.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/InsuranceCreatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/InsuranceUpdatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/CustomerValidatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/VehicleValidatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/CustomerInvalidatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/VehicleInvalidatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/EstimationRequestedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/PremiumCalculatedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/CalculationFailedEvent.java`

---

## Architecture Context

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `estimation.saga` | SAGA workflow events (produce + consume) |
| `insurance.events` | Domain events — log-compacted, keyed by insuranceId |

### Event Schema (Common Envelope)

```json
{
  "sagaId": "uuid",
  "eventType": "CustomerValidated",
  "timestamp": "2026-06-20T12:00:00Z",
  "traceId": "uuid",
  "payload": { }
}
```

The `payload` field contains the typed event object. In transit, it's serialized as JSON.

### SAGA Event Flow (Insurance Service View)

```
Insurance Service
      │
      ├─── Receives: EstimationRequested (stores insurance context: typeId, companyId)
      ├─── Receives: CustomerValidated (stores customer data)
      ├─── Receives: VehicleValidated (stores vehicle data)
      │
      ├─── When all 3 present for same sagaId: Calculate Premium
      │    ├── Look up Insurance entity by typeId + companyId
      │    ├── Apply basePremium + risk factors from customer/vehicle data
      │    └── Publish: PremiumCalculated
      │
      ├─── Receives: CustomerInvalidated or VehicleInvalidated (skip calculation)
      │    └── Publish: CalculationFailed
      │
      └─── Receives: EstimationFailed → log only
```

> **Design rationale:** The Insurance Service also consumes `EstimationRequested` to capture the insurance type and company context. Without this, it cannot know which insurance product to calculate premiums for. The customer/vehicle validation events only carry entity IDs and basic info — not the insurance product selection.

### Event Catalog (Insurance Service's View)

| Event | Direction | Description |
|-------|-----------|-------------|
| `EstimationRequested` | Consume | Store insurance typeId, companyId per sagaId |
| `CustomerValidated` | Consume | Store customer data per sagaId; check if ready |
| `VehicleValidated` | Consume | Store vehicle data per sagaId; check if ready |
| `CustomerInvalidated` | Consume | Publish CalculationFailed |
| `VehicleInvalidated` | Consume | Publish CalculationFailed |
| `PremiumCalculated` | Produce | Calculation result with premium + breakdown |
| `CalculationFailed` | Produce | Calculation failure with reason |
| `EstimationFailed` | Consume | Log only (no reversible action) |
| `InsuranceCreated` | Produce (domain) | Published to `insurance.events` |
| `InsuranceUpdated` | Produce (domain) | Published to `insurance.events` |

### BaseEvent.toEnvelope() Pattern

All event classes extend `BaseEvent` and call `event.toEnvelope(sagaId, traceId)` to create an `EventEnvelope`:

```java
PremiumCalculatedEvent event = PremiumCalculatedEvent.builder()
    .premium(BigDecimal.valueOf(1500))
    .breakdown(breakdownMap)
    .build();
EventEnvelope envelope = event.toEnvelope(sagaId, traceId);
```

---

## Step 3.1: Create MessagePublisher.java

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/MessagePublisher.java`

Copy the exact pattern from `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/MessagePublisher.java`. Update the package only.

```java
package com.insurancemanagementsystem.insurance.config;

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

---

## Step 3.2: Create InsuranceEventPublisher.java

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceEventPublisher.java`

**Pattern:** Copy from `CustomerEventPublisher.java`. Publish to `insurance.events`.

```java
package com.insurancemanagementsystem.insurance.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.domain.InsuranceCreatedEvent;
import com.insurancemanagementsystem.common.event.domain.InsuranceUpdatedEvent;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InsuranceEventPublisher {

    private final MessagePublisher messagePublisher;

    public void publishInsuranceCreated(Insurance insurance) {
        InsuranceCreatedEvent event = InsuranceCreatedEvent.builder()
                .insuranceId(insurance.getId())
                .typeId(insurance.getTypeId())
                .companyId(insurance.getCompanyId())
                .build();

        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.INSURANCE_EVENTS, envelope);
        log.info("Published InsuranceCreated event for insurance id: {}", insurance.getId());
    }

    public void publishInsuranceUpdated(Insurance insurance) {
        InsuranceUpdatedEvent event = InsuranceUpdatedEvent.builder()
                .insuranceId(insurance.getId())
                .typeId(insurance.getTypeId())
                .companyId(insurance.getCompanyId())
                .build();

        EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
        messagePublisher.publish(EventConstants.INSURANCE_EVENTS, envelope);
        log.info("Published InsuranceUpdated event for insurance id: {}", insurance.getId());
    }
}
```

---

## Step 3.3: Wire Event Publishing into InsuranceService

**File to EDIT:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/service/InsuranceService.java`

Add `InsuranceEventPublisher` to the constructor (via Lombok `@RequiredArgsConstructor`).

Add `publishInsuranceCreated(saved)` after save in `create()` method.
Add `publishInsuranceUpdated(saved)` after save in `update()` method.

**Changes:**
1. Add field: `private final InsuranceEventPublisher insuranceEventPublisher;`
2. In `create()` method, after `Insurance saved = insuranceRepository.save(insurance);`, add: `insuranceEventPublisher.publishInsuranceCreated(saved);`
3. In `update()` method, after `Insurance saved = insuranceRepository.save(insurance);`, add: `insuranceEventPublisher.publishInsuranceUpdated(saved);`

**IMPORTANT:** Do NOT inject KafkaTemplate directly. Use InsuranceEventPublisher → MessagePublisher → StreamBridge pattern exactly as in CustomerService.

---

## Step 3.4: Create DeduplicationStore.java

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/DeduplicationStore.java`

Copy EXACTLY from `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/DeduplicationStore.java`. Update the package only. No functional changes needed — the dedup logic (sagaId:eventType key) is the same for all services.

---

## Step 3.5: Create SagaAggregationStore.java

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/SagaAggregationStore.java`

This is a NEW component (not in customer-service). It manages correlation state for multi-event SAGA aggregation.

**Requirements:**
- Stores correlation state per `sagaId`
- State includes: customer validation event, vehicle validation event, estimation request event
- When all three events for a sagaId are present, returns `true` from a `isReady(sagaId)` check
- Thread-safe (ConcurrentHashMap)
- Automatic cleanup via scheduled task (same pattern as DeduplicationStore, TTL=10min)

```java
package com.insurancemanagementsystem.insurance.config;

import com.insurancemanagementsystem.common.event.EventEnvelope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
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
public class SagaAggregationStore {

    private final ConcurrentHashMap<String, SagaState> store = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(10);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "saga-agg-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
        log.info("SagaAggregationStore initialized with TTL={}m", ttl.toMinutes());
    }

    /**
     * Store an event for a given sagaId.
     * Returns true if all required events are now present (saga is ready for calculation).
     */
    public boolean storeAndCheckReady(String sagaId, String eventType, EventEnvelope envelope) {
        SagaState state = store.computeIfAbsent(sagaId, k -> new SagaState());

        switch (eventType) {
            case "EstimationRequested" -> state.setEstimationRequest(envelope);
            case "CustomerValidated" -> state.setCustomerValidated(envelope);
            case "VehicleValidated" -> state.setVehicleValidated(envelope);
        }

        boolean ready = state.isComplete();
        if (ready) {
            log.info("SAGA aggregation complete for sagaId={}", sagaId);
        } else {
            log.debug("SAGA state for sagaId={}: hasEstimation={}, hasCustomer={}, hasVehicle={}",
                    sagaId, state.hasEstimationRequest(), state.hasCustomerValidated(), state.hasVehicleValidated());
        }
        return ready;
    }

    /**
     * Retrieve and remove aggregation state (one-shot consumption — state consumed once).
     */
    public SagaState retrieve(String sagaId) {
        SagaState state = store.remove(sagaId);
        log.debug("Retrieved and removed SAGA state for sagaId={}", sagaId);
        return state;
    }

    /**
     * Remove saga state on invalidation (no calculation needed).
     */
    public void remove(String sagaId) {
        store.remove(sagaId);
        log.debug("Removed SAGA state for sagaId={} (invalidated)", sagaId);
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(ttl);
        int before = store.size();
        store.values().removeIf(state -> state.getCreatedAt().isBefore(cutoff));
        int after = store.size();
        if (before != after) {
            log.debug("SagaAggregationStore cleanup: removed {} entries ({} remaining)", before - after, after);
        }
    }

    @Getter
    @Setter
    @ToString
    public static class SagaState {
        private final Instant createdAt = Instant.now();
        private EventEnvelope estimationRequest;
        private EventEnvelope customerValidated;
        private EventEnvelope vehicleValidated;

        public boolean hasEstimationRequest() { return estimationRequest != null; }
        public boolean hasCustomerValidated() { return customerValidated != null; }
        public boolean hasVehicleValidated() { return vehicleValidated != null; }

        public boolean isComplete() {
            return hasEstimationRequest() && hasCustomerValidated() && hasVehicleValidated();
        }
    }
}
```

---

## Step 3.6: Create InsuranceSagaConsumer.java

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`

This is the most complex component. It listens on `estimation.saga` via Spring Cloud Stream and handles SAGA event correlation.

**Pattern adapt from:** `CustomerSagaConsumer.java` — same structure but with stateful aggregation.

**Key differences from CustomerSagaConsumer:**
1. Needs `JsonMapper` injected via bean method parameter (Spring Cloud Stream pattern)
2. Uses `SagaAggregationStore` for multi-event correlation
3. Calculates premium when all events arrive
4. Also handles `CustomerInvalidated` and `VehicleInvalidated` → publish `CalculationFailed`

**Premium Calculation Logic:**
1. Extract `EstimationRequestedEvent` → get `insuranceTypeId`, `companyId`
2. Look up Insurance entity: find by `typeId` and `companyId` where `isActive=true`
3. Extract `CustomerValidatedEvent` → get customer data
4. Extract `VehicleValidatedEvent` → get vehicle data
5. Apply basePremium from Insurance entity
6. Apply risk factors (simple multipliers based on available data)
7. Produce `PremiumCalculated` with premium and breakdown map

**If Insurance entity not found:** Publish `CalculationFailed` with reason.

**If CustomerInvalidated or VehicleInvalidated:** Publish `CalculationFailed` and remove aggregation state.

```java
package com.insurancemanagementsystem.insurance.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.*;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import com.insurancemanagementsystem.insurance.repository.InsuranceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class InsuranceSagaConsumer {

    private final InsuranceRepository insuranceRepository;
    private final MessagePublisher messagePublisher;
    private final DeduplicationStore deduplicationStore;
    private final SagaAggregationStore aggregationStore;

    @Bean
    public Consumer<String> processInsuranceSaga(JsonMapper jsonMapper) {
        return message -> {
            EventEnvelope envelope;
            try {
                envelope = jsonMapper.readValue(message, EventEnvelope.class);

                UUID sagaId = envelope.getSagaId();
                UUID traceId = envelope.getTraceId();
                String eventType = envelope.getEventType();

                MDC.put("sagaId", sagaId != null ? sagaId.toString() : "");
                MDC.put("traceId", traceId != null ? traceId.toString() : "");

                log.info("Received SAGA event: {} for sagaId: {}", eventType, sagaId);

                switch (eventType) {
                    case EventConstants.ESTIMATION_REQUESTED ->
                        handleEstimationRequested(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.CUSTOMER_VALIDATED ->
                        handleCustomerValidated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.VEHICLE_VALIDATED ->
                        handleVehicleValidated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.CUSTOMER_INVALIDATED ->
                        handleInvalidated(envelope, sagaId, traceId, "Customer validation failed");
                    case EventConstants.VEHICLE_INVALIDATED ->
                        handleInvalidated(envelope, sagaId, traceId, "Vehicle validation failed");
                    case EventConstants.ESTIMATION_FAILED ->
                        handleEstimationFailed(envelope);
                    default ->
                        log.warn("Unknown SAGA event type: {}", eventType);
                }
            } catch (Exception e) {
                log.error("Error processing SAGA message: {}", e.getMessage(), e);
            } finally {
                MDC.clear();
            }
        };
    }

    // ---------------------------------------------------------------
    // EstimationRequested — store insurance context
    // ---------------------------------------------------------------
    private void handleEstimationRequested(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        // Store in aggregation — will check if complete
        boolean ready = aggregationStore.storeAndCheckReady(sagaId.toString(), eventType, envelope);
        if (ready) {
            calculatePremium(sagaId, traceId, jsonMapper);
        }
    }

    // ---------------------------------------------------------------
    // CustomerValidated — store customer data
    // ---------------------------------------------------------------
    private void handleCustomerValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        boolean ready = aggregationStore.storeAndCheckReady(sagaId.toString(), eventType, envelope);
        if (ready) {
            calculatePremium(sagaId, traceId, jsonMapper);
        }
    }

    // ---------------------------------------------------------------
    // VehicleValidated — store vehicle data
    // ---------------------------------------------------------------
    private void handleVehicleValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        boolean ready = aggregationStore.storeAndCheckReady(sagaId.toString(), eventType, envelope);
        if (ready) {
            calculatePremium(sagaId, traceId, jsonMapper);
        }
    }

    // ---------------------------------------------------------------
    // Invalidated — calculation not possible, publish failure
    // ---------------------------------------------------------------
    private void handleInvalidated(EventEnvelope envelope, UUID sagaId, UUID traceId, String reason) {
        log.warn("SAGA invalidated for sagaId={}: {}", sagaId, reason);
        aggregationStore.remove(sagaId.toString());

        CalculationFailedEvent failed = CalculationFailedEvent.builder()
                .reason(reason)
                .build();
        EventEnvelope outcome = failed.toEnvelope(sagaId, traceId);
        messagePublisher.publish(EventConstants.ESTIMATION_SAGA, outcome);
    }

    // ---------------------------------------------------------------
    // EstimationFailed — log only (no reversible action)
    // ---------------------------------------------------------------
    private void handleEstimationFailed(EventEnvelope envelope) {
        log.warn("Estimation failed for saga: {} — no compensation needed (calculation is stateless)",
                envelope.getSagaId());
    }

    // ---------------------------------------------------------------
    // Premium Calculation — core business logic
    // ---------------------------------------------------------------
    private void calculatePremium(UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        SagaAggregationStore.SagaState state = aggregationStore.retrieve(sagaId.toString());
        if (state == null) {
            log.warn("SAGA state not found for sagaId={} — already consumed?", sagaId);
            return;
        }

        // Extract estimation context
        EstimationRequestedEvent estimationEvent = jsonMapper.convertValue(
                state.getEstimationRequest().getPayload(), EstimationRequestedEvent.class);
        UUID customerId = estimationEvent.getCustomerId();
        UUID vehicleId = estimationEvent.getVehicleId();
        Integer insuranceTypeId = estimationEvent.getInsuranceTypeId();
        UUID companyId = estimationEvent.getCompanyId();

        // Extract customer data
        CustomerValidatedEvent customerEvent = jsonMapper.convertValue(
                state.getCustomerValidated().getPayload(), CustomerValidatedEvent.class);

        // Extract vehicle data
        VehicleValidatedEvent vehicleEvent = jsonMapper.convertValue(
                state.getVehicleValidated().getPayload(), VehicleValidatedEvent.class);

        // Look up insurance entity by typeId + companyId
        Optional<Insurance> insuranceOpt = insuranceRepository
                .findByTypeIdAndCompanyIdAndIsActiveTrue(insuranceTypeId, companyId, null)
                .stream().findFirst();

        if (insuranceOpt.isEmpty()) {
            publishCalculationFailed(sagaId, traceId,
                    "No active insurance found for typeId=" + insuranceTypeId + ", companyId=" + companyId);
            return;
        }

        Insurance insurance = insuranceOpt.get();
        BigDecimal basePremium = insurance.getBasePremium();
        if (basePremium == null) {
            publishCalculationFailed(sagaId, traceId, "Insurance has no base premium defined");
            return;
        }

        // Calculate premium: basePremium * risk factor
        // Risk factor = 1.0 (default) with simple adjustments
        BigDecimal riskFactor = BigDecimal.ONE;

        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        breakdown.put("basePremium", basePremium);

        // Simple risk factors (can be extended with more data later)
        BigDecimal measuredAdjustment = BigDecimal.ZERO;
        breakdown.put("riskFactor", riskFactor);
        breakdown.put("adjustment", measuredAdjustment);

        BigDecimal totalPremium = basePremium.multiply(riskFactor).add(measuredAdjustment);

        // Publish PremiumCalculated
        PremiumCalculatedEvent premiumEvent = PremiumCalculatedEvent.builder()
                .premium(totalPremium)
                .breakdown(breakdown)
                .insuranceTypeId(insuranceTypeId)
                .companyId(companyId)
                .customerId(customerId)
                .vehicleId(vehicleId)
                .build();

        EventEnvelope outcome = premiumEvent.toEnvelope(sagaId, traceId);
        messagePublisher.publish(EventConstants.ESTIMATION_SAGA, outcome);
        log.info("Premium calculated for sagaId={}: premium={}, typeId={}, companyId={}",
                sagaId, totalPremium, insuranceTypeId, companyId);
    }

    private void publishCalculationFailed(UUID sagaId, UUID traceId, String reason) {
        CalculationFailedEvent failed = CalculationFailedEvent.builder()
                .reason(reason)
                .build();
        EventEnvelope outcome = failed.toEnvelope(sagaId, traceId);
        messagePublisher.publish(EventConstants.ESTIMATION_SAGA, outcome);
        log.warn("Calculation failed for sagaId={}: {}", sagaId, reason);
    }
}
```

---

## Step 3.7: Configure Spring Cloud Stream Bindings

**File to EDIT:** `services/insurance-service/src/main/resources/application.yml`

Add Spring Cloud Stream configuration. The SAGA function binding name matches the bean name: `processInsuranceSaga` → binding `processInsuranceSaga-in-0`.

Add the following block under `spring:` (after `kafka:` block, at the same level as `rabbitmq:`):

```yaml
  cloud:
    stream:
      default-binder: kafka
      dynamicDestinations: estimation.saga,insurance.events
      bindings:
        processInsuranceSaga-in-0:
          destination: estimation.saga
          group: insurance-service-group
      kafka:
        binder:
          brokers: localhost:9092
          defaultBrokerPort: 9092
```

**Full application.yml after edit:**
```yaml
server:
  port: 8084

spring:
  application:
    name: insurance-service
  profiles:
    active: dev
  datasource:
    url: jdbc:postgresql://localhost:5436/insurance_db
    username: ims_user
    password: ims_password
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  cloud:
    stream:
      default-binder: kafka
      dynamicDestinations: estimation.saga,insurance.events
      bindings:
        processInsuranceSaga-in-0:
          destination: estimation.saga
          group: insurance-service-group
      kafka:
        binder:
          brokers: localhost:9092
          defaultBrokerPort: 9092
  kafka:
    consumer:
      group-id: insurance-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.insurancemanagementsystem.*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [%X{traceId:-},%X{sagaId:-}] - %msg%n"
  level:
    com.insurancemanagementsystem: DEBUG
```

**Key points:**
- `processInsuranceSaga-in-0` binding name matches the bean name `processInsuranceSaga` — Spring Cloud Stream auto-discovers it
- `dynamicDestinations` includes both `estimation.saga` and `insurance.events` so `StreamBridge.send()` can publish to both topics
- `group: insurance-service-group` ensures the consumer group name matches `spring.kafka.consumer.group-id`
- Kafka binder is auto-configured from `spring.kafka` properties

---

## Verification After Step 3

1. `.\gradlew.bat :services:insurance-service:compileJava` — compiles without errors
2. Apply `application.yml` edits
3. Start Kafka (should be running from Docker Compose)
4. `.\gradlew.bat :services:insurance-service:bootRun` — starts without binding errors
5. Check logs for: `SagaAggregationStore initialized` and no binding errors
