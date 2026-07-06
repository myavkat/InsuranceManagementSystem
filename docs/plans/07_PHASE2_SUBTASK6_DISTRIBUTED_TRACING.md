# Subtask 6: Setup Distributed Tracing

## Status: NOT STARTED
## Parent: `07_PHASE2_MASTER_PLAN.md`
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Add Micrometer Tracing (the successor to Spring Cloud Sleuth) to all services with:
1. Trace propagation via Kafka headers
2. Zipkin collector for trace visualization
3. Each log entry includes `traceId`, `sagaId`, `eventId` as structured JSON fields
4. Zipkin container in Docker Compose

## Files to Read Before Starting

1. `services/*/build.gradle.kts` (all 6 services + common-message) — current dependencies
2. `services/*/application.yml` (all 6 services) — current logging config
3. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventEnvelope.java` — traceId field already exists
4. `infra/docker/docker-compose.yml` — services to add Zipkin to
5. `docs/outlines/01_SYSTEM_ARCHITECTURE.md` — tech stack, ports
6. `docs/outlines/13_ENVIRONMENT_QUIRKS.md` — port allocation

## Current State

- `EventEnvelope` already has `traceId` field
- All services' `application.yml` already have MDC-based logging patterns referencing `traceId` and `sagaId`:
  ```yaml
  logging:
    pattern:
      console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [%X{traceId:-},%X{sagaId:-}] - %msg%n"
  ```
- No Micrometer Tracing, Brave, Zipkin, or Sleuth dependencies exist in any build file
- Trace ID is manually passed in event envelopes but not automatically propagated via Kafka headers
- No Zipkin container in Docker Compose

## Architecture Decision

Use **Micrometer Tracing** (the Spring Boot 4.x standard) with **Brave** as the tracer implementation and **Zipkin** as the collector:

- `micrometer-tracing-bridge-brave` — Brave tracer integration
- `zipkin-reporter-brave` — sends traces to Zipkin
- Kafka trace propagation via `spring.kafka.consumer.properties` and Brave's `KafkaTracing`

---

## Implementation Steps

### Step 1: Add Zipkin to Docker Compose

- [ ] **1.1** Add Zipkin service to `infra/docker/docker-compose.yml`:

  ```yaml
  # ============================================================
  # Zipkin (Distributed Tracing)
  # ============================================================
  zipkin:
    image: openzipkin/zipkin:latest
    container_name: zipkin
    ports:
      - "9411:9411"
    networks:
      - insurance-net
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:9411/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s
  ```

- [ ] **1.2** Update `docs/outlines/13_ENVIRONMENT_QUIRKS.md` — add Zipkin to port allocation table:
  ```
  | Zipkin | `9411` |
  ```

### Step 2: Add Micrometer Tracing Dependencies

- [ ] **2.1** Add to `common/common-message/build.gradle.kts` (api scope so all services inherit):

  ```kotlin
  // Micrometer Tracing (distributed tracing — successor to Spring Cloud Sleuth)
  api("io.micrometer:micrometer-tracing-bridge-brave")
  api("io.zipkin.reporter2:zipkin-reporter-brave")
  ```

- [ ] **2.2** Since `common-message` is an `api` dependency of all services, the tracing libraries will be available everywhere.

- [ ] **2.3** For the BOM, ensure Micrometer Tracing version is managed. Spring Boot 4.x includes Micrometer Tracing in its BOM — check that no explicit version is needed. If not managed, add:

  ```kotlin
  dependencyManagement {
      imports {
          mavenBom("io.micrometer:micrometer-tracing-bom:latest.release")
      }
  }
  ```

### Step 3: Configure Tracing in application.yml

- [ ] **3.1** Add tracing configuration to each service's `application.yml`. Since this is shared config, consider adding it to a common configuration or applying to all 6 services:

  ```yaml
  management:
    tracing:
      sampling:
        probability: 1.0  # 100% for dev; reduce for production
    zipkin:
      tracing:
        endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}

  spring:
    kafka:
      consumer:
        properties:
          # Propagate trace context via Kafka headers
          spring.kafka.consumer.properties.isolation.level: read_committed
      producer:
        properties:
          # Include trace headers in produced messages
          spring.kafka.producer.properties.interceptor.classes: io.micrometer.tracing.brave.bridge.KafkaTracingProducerInterceptor
  ```

- [ ] **3.2** Update `logging.pattern.console` in each service to include trace and span IDs from Micrometer Tracing:

  ```yaml
  logging:
    pattern:
      console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [%X{traceId:-},%X{spanId:-},%X{sagaId:-}] - %msg%n"
    level:
      com.insurancemanagementsystem: DEBUG
  ```

  Micrometer Tracing automatically populates MDC with `traceId` and `spanId`. The `sagaId` is populated manually by the consumer's MDC setup.

- [ ] **3.3** Apply this configuration to all 6 services (customer, vehicle, realestate, insurance, estimation, reference-data).

### Step 4: Configure Kafka Trace Propagation

- [ ] **4.1** Create a shared Kafka tracing configuration bean in `common-message`:

  `common/common-message/src/main/java/com/insurancemanagementsystem/common/config/KafkaTracingConfig.java`:

  ```java
  package com.insurancemanagementsystem.common.config;

  import brave.Tracing;
  import brave.kafka.clients.KafkaTracing;
  import org.springframework.boot.autoconfigure.AutoConfiguration;
  import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
  import org.springframework.context.annotation.Bean;

  /**
   * Auto-configuration for Kafka trace propagation via Brave.
   * <p>
   * When Micrometer Tracing with Brave is on the classpath, this bean
   * enables automatic injection of trace headers (b3) into Kafka
   * producer records and extraction from consumer records.
   */
  @AutoConfiguration
  @ConditionalOnClass({Tracing.class, KafkaTracing.class})
  public class KafkaTracingConfig {

      @Bean
      public KafkaTracing kafkaTracing(Tracing tracing) {
          return KafkaTracing.create(tracing);
      }
  }
  ```

- [ ] **4.2** Register a `KafkaTracingProducerInterceptor` in each service's Kafka producer configuration. Since Spring Boot auto-configures Kafka, the interceptor class is set via properties (Step 3.1 above).

- [ ] **4.3** For Spring Cloud Stream, trace propagation is handled by the binder when `micrometer-tracing` is on the classpath. The binder automatically injects/extracts trace headers from messages. Verify this works by checking Spring Cloud Stream documentation.

### Step 5: Ensure Saga Consumers Populate MDC with traceId

- [ ] **5.1** Verify that every SAGA consumer correctly puts `traceId` into MDC. Currently, consumers do:

  ```java
  MDC.put("traceId", traceId != null ? traceId.toString() : "");
  ```

  This is correct but should ALSO set the Micrometer Tracing `traceId` so that spans are linked. Micrometer Tracing's `Observation` API handles this natively when using `Observation.createNotStarted()`.

- [ ] **5.2** Update the `MessageListener` abstraction (from Subtask 3) to use Micrometer Observation:

  ```java
  // In MessageListener.asConsumer():
  Observation observation = Observation.createNotStarted("saga.process", registry)
          .contextualName("process " + eventClass.getSimpleName())
          .lowCardinalityKeyValue("event.type", eventType)
          .highCardinalityKeyValue("saga.id", sagaId.toString());

  observation.observe(() -> {
      T event = jsonMapper.convertValue(envelope.getPayload(), eventClass);
      handleEvent(event, envelope);
  });
  ```

- [ ] **5.3** For existing consumers (not using `MessageListener`), wrap the handler body with an Observation or at minimum ensure MDC is correctly populated.

### Step 6: Add Trace ID to Outbox Events (Ensure Propagation)

- [ ] **6.1** Verify that all outbox events carry the correct `traceId`. Current pattern:

  ```java
  // In EstimationService.create():
  EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID()); // ❌ NEW traceId!

  // Should be:
  EventEnvelope envelope = event.toEnvelope(sagaId, currentTraceId()); // ✅ propagated
  ```

- [ ] **6.2** Fix the `EstimationService.create()` method to use a proper initial trace ID. Since this is the SAGA entry point, a new trace ID is acceptable — but it should use `CorrelationIdGenerator.generateTraceId()` (from Subtask 3) rather than inline `UUID.randomUUID()`.

- [ ] **6.3** Verify that all consumer-to-producer chains propagate `traceId`:
  - `CustomerSagaConsumer`: receives `EstimationRequested` with traceId X → publishes `CustomerValidated` with traceId X ✅ (check code)
  - `VehicleSagaConsumer`: receives with traceId X → publishes with traceId X ✅
  - `InsuranceSagaConsumer`: receives with traceId X → publishes with traceId X ✅
  - `EstimationSagaConsumer`: receives with traceId X → publishes `EstimationFailed` with traceId X ✅

- [ ] **6.4** The one exception is `SagaTimeoutService` which originates new events. It currently uses `sagaId` as `traceId` (line 65 of SagaTimeoutService.java):
  ```java
  OutboxEvent outboxEvent = outboxEventSerializer.buildEstimationFailedOutboxEvent(
          sagaId, sagaId, reason, "SagaTimeoutService", EventConstants.ESTIMATION_SAGA);
  ```
  Per AGENTS.md rule: "Never pass sagaId as traceId — conflates trace identity with business identity." Fix by storing the original `traceId` on the `Estimation` entity at creation time, then using `estimation.getTraceId()` at timeout.

### Step 7: Fix SagaTimeoutService Trace Propagation

- [ ] **7.1** Add `traceId` field to `Estimation` entity:
  - `services/estimation-service/.../entity/Estimation.java` — add:
    ```java
    @Column(name = "trace_id")
    private UUID traceId;
    ```

- [ ] **7.2** Update `EstimationService.create()` to store the initial traceId:
  ```java
  estimation.setTraceId(CorrelationIdGenerator.generateTraceId());
  ```

- [ ] **7.3** Update `SagaTimeoutService.checkForTimedOutSagas()` to use `estimation.getTraceId()`:
  ```java
  OutboxEvent outboxEvent = outboxEventSerializer.buildEstimationFailedOutboxEvent(
          sagaId, estimation.getTraceId(), reason, "SagaTimeoutService", EventConstants.ESTIMATION_SAGA);
  ```

- [ ] **7.4** Add `trace_id` column to estimation_db init.sql:
  - `infra/sql/estimation_db/init.sql` — add column:
    ```sql
    trace_id UUID,
    ```
    After the `company_id` line in the `estimations` table.

- [ ] **7.5** Update `EstimationSagaConsumer` to propagate `traceId` from incoming envelopes rather than using `UUID.randomUUID()`.

### Step 8: Verify

- [ ] **8.1** Start infrastructure with Zipkin:
  ```bash
  docker compose -f infra/docker/docker-compose.yml up -d
  ```

- [ ] **8.2** Verify Zipkin is running: `http://localhost:9411/` — UI should be accessible

- [ ] **8.3** Build all services: `.\gradlew.bat build`

- [ ] **8.4** Start one service (e.g., estimation-service) and make a request. Verify traces appear in Zipkin UI.

- [ ] **8.5** Trigger a full SAGA flow and verify the full trace chain is visible in Zipkin:
  - HTTP request → EstimationService → Kafka → CustomerService → Kafka → InsuranceService → Kafka → EstimationService
  - All spans should share the same `traceId`

- [ ] **8.6** Check logs for `traceId` and `spanId` in MDC output

---

## Files to Create

| File | Purpose |
|------|---------|
| `common/common-message/src/main/java/.../config/KafkaTracingConfig.java` | Shared Kafka tracing configuration bean |

## Files to Modify

| File | Change |
|------|--------|
| `infra/docker/docker-compose.yml` | Add Zipkin service |
| `common/common-message/build.gradle.kts` | Add Micrometer Tracing + Zipkin dependencies (api scope) |
| `services/*/application.yml` (6 services) | Add management.tracing + management.zipkin config |
| `services/*/application.yml` (6 services) | Update logging pattern to include spanId |
| `services/estimation-service/.../entity/Estimation.java` | Add `traceId` field |
| `services/estimation-service/.../service/EstimationService.java` | Store traceId at creation |
| `services/estimation-service/.../service/SagaTimeoutService.java` | Use stored traceId (not sagaId as traceId) |
| `infra/sql/estimation_db/init.sql` | Add `trace_id` column to estimations table |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Add Zipkin port (9411) |

## Dependencies
- Subtask 3 (Common Library) — `CorrelationIdGenerator`, `MessageListener`

## Completion Criteria
- [ ] Zipkin container running in Docker Compose
- [ ] Micrometer Tracing + Brave dependencies in all services (via common-message)
- [ ] All services configured with Zipkin endpoint
- [ ] Kafka trace propagation working (b3 headers on Kafka messages)
- [ ] All log entries include `traceId` and `spanId` from Micrometer Tracing
- [ ] `SagaTimeoutService` uses stored traceId (not sagaId)
- [ ] `Estimation` entity has `trace_id` column
- [ ] Full SAGA trace visible in Zipkin UI
- [ ] `.\gradlew.bat build` passes for all modules
