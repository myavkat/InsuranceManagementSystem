# Subtask 3: Build Common Message Library

## Status: COMPLETED
## Parent: `07_PHASE2_MASTER_PLAN.md`
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Build out the `common-message` module with reusable abstractions that all services can use. This includes:
1. ~~`MessageListener<T>` abstraction for typed event consumption~~ **(REMOVED in Fix 05 — broken: no transaction wrapping, inconsistent poison-pill behavior, zero adopters)**
2. `CorrelationIdGenerator` utility
3. ~~`SagaContext` holder (ThreadLocal-based MDC context)~~ **(REMOVED in Fix 05 — zero callers, minimal value over inline MDC)**
4. Make domain event publishers use the outbox pattern instead of direct `StreamBridge.send()`
5. Populate the `common-test` module with shared test utilities

## Files to Read Before Starting

1. `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/MessagePublisher.java`
2. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/BaseEvent.java`
3. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventEnvelope.java`
4. `common/common-message/src/main/java/com/insurancemanagementsystem/common/config/OutboxProcessor.java`
5. `common/common-message/src/main/java/com/insurancemanagementsystem/common/config/OutboxRelay.java`
6. `services/customer-service/.../config/CustomerEventPublisher.java` — example of domain event publisher
7. `services/customer-service/.../config/CustomerSagaConsumer.java` — example of saga consumer pattern
8. `services/insurance-service/.../config/InsuranceSagaConsumer.java` — example of saga consumer with aggregation
9. `docs/outlines/03_SAGA_PATTERN.md` — consumer implementation rules
10. `docs/outlines/11_TESTING_CONVENTIONS.md` — test conventions for common-test
11. `AGENTS.md` — SAGA, Outbox, DB safety rules

## Current State

### What Exists
- `MessagePublisher` — wraps `StreamBridge.send()`, has deprecated `publishAfterCommit()`
- `BaseEvent` — abstract base with `toEnvelope()`, `toJson()`, `fromJson()`
- `EventEnvelope` — common wrapper
- `EventConstants` — all constants
- Outbox infrastructure: `OutboxEvent`, `OutboxEventRepository`, `OutboxProcessor`, `OutboxRelay`
- Saga dedup: `SagaEvent`, `SagaEventRepository` with `tryInsertDedup()`
- `CommonPersistenceAutoConfiguration`

### What's Missing
- No `MessageListener<T>` — each service manually deserializes `EventEnvelope` in its consumer bean
- No `CorrelationIdGenerator` — correlation IDs generated ad-hoc
- No `SagaContext` — MDC is managed manually in each consumer
- Domain event publishers use `MessagePublisher.publish()` directly (not outbox)
- `common-test` module is empty skeleton, commented out in `settings.gradle.kts`

---

## Implementation Steps

### Step 1: Create `MessageListener<T>` Abstraction

- [x] **1.1** Create `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/MessageListener.java`:

  ```java
  package com.insurancemanagementsystem.common.messaging;

  import com.insurancemanagementsystem.common.event.BaseEvent;
  import com.insurancemanagementsystem.common.event.EventEnvelope;
  import com.insurancemanagementsystem.common.repository.SagaEventRepository;
  import lombok.extern.slf4j.Slf4j;
  import org.slf4j.MDC;
  import org.springframework.transaction.support.TransactionTemplate;
  import tools.jackson.databind.json.JsonMapper;

  import java.util.UUID;
  import java.util.function.Consumer;

  /**
   * Typed message listener base class for SAGA event consumers.
   * <p>
   * Handles deserialization, MDC context setup, dedup checking, and
   * exception handling so concrete consumers only implement business logic.
   *
   * @param <T> the specific event type this listener handles
   */
  @Slf4j
  public abstract class MessageListener<T extends BaseEvent> {

      protected final JsonMapper jsonMapper;
      protected final SagaEventRepository sagaEventRepository;
      protected final TransactionTemplate transactionTemplate;
      protected final Class<T> eventClass;

      protected MessageListener(JsonMapper jsonMapper,
                                SagaEventRepository sagaEventRepository,
                                TransactionTemplate transactionTemplate,
                                Class<T> eventClass) {
          this.jsonMapper = jsonMapper;
          this.sagaEventRepository = sagaEventRepository;
          this.transactionTemplate = transactionTemplate;
          this.eventClass = eventClass;
      }

      /**
       * Returns a Spring Cloud Stream functional Consumer bean.
       * Subclasses call this in their {@code @Bean} method.
       */
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

                  if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                      log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
                      return;
                  }

                  T event = jsonMapper.convertValue(envelope.getPayload(), eventClass);
                  handleEvent(event, envelope);
              } catch (Exception e) {
                  log.error("Error processing message: {}", e.getMessage(), e);
                  if (e instanceof RuntimeException re) throw re;
                  throw new RuntimeException("Failed to process message", e);
              } finally {
                  MDC.clear();
              }
          };
      }

      /**
       * Implement business logic for the event. Called after deserialization
       * and dedup check. The transaction is managed by the caller — use
       * {@code transactionTemplate.executeWithoutResult()} for multi-write operations.
       */
      protected abstract void handleEvent(T event, EventEnvelope envelope);
  }
  ```

- [x] **1.2** This abstraction encapsulates:
  - JSON deserialization of `EventEnvelope` (with poison-pill skip)
  - MDC context setup/teardown (sagaId, traceId)
  - Atomic dedup via `SagaEventRepository.tryInsertDedup()`
  - Typed payload conversion via `jsonMapper.convertValue()`
  - Re-throw of `RuntimeException` for binder-level retry

- [x] **1.3** Note: This is an **optional** abstraction. Existing consumers can continue using their manual pattern. New consumers should use this. Don't retroactively refactor all existing consumers unless time permits — the task says "build the library," not "refactor all consumers."

### Step 2: Create `CorrelationIdGenerator`

- [x] **2.1** Create `common/common-message/src/main/java/com/insurancemanagementsystem/common/util/CorrelationIdGenerator.java`:

  ```java
  package com.insurancemanagementsystem.common.util;

  import java.util.UUID;

  /**
   * Generates correlation IDs for tracing and SAGA orchestration.
   * <p>
   * Uses UUID v4 (random) for all generated IDs. This is suitable for
   * distributed tracing where global uniqueness is required across services.
   */
  public final class CorrelationIdGenerator {

      private CorrelationIdGenerator() {}

      /** Generate a new saga ID (UUID v4). */
      public static UUID generateSagaId() {
          return UUID.randomUUID();
      }

      /** Generate a new trace ID (UUID v4). */
      public static UUID generateTraceId() {
          return UUID.randomUUID();
      }

      /** Generate a new correlation ID (UUID v4) — alias for generateTraceId(). */
      public static UUID generateCorrelationId() {
          return UUID.randomUUID();
      }
  }
  ```

- [x] **2.2** This is a simple utility. The key rule is: **never generate a fresh traceId for outbound SAGA events — propagate the original** (as stated in AGENTS.md). This utility is for the ORIGINATING event only (e.g., when EstimationService starts a new SAGA).

### Step 3: Create `SagaContext` Holder

- [x] **3.1** Create `common/common-message/src/main/java/com/insurancemanagementsystem/common/util/SagaContext.java`:

  ```java
  package com.insurancemanagementsystem.common.util;

  import org.slf4j.MDC;

  import java.util.UUID;

  /**
   * Thread-local SAGA context for MDC management.
   * <p>
   * Sets sagaId and traceId in SLF4J MDC so that structured logging
   * captures these for every log statement within the scope.
   * Use in try-with-resources to auto-clear:
   * <pre>{@code
   * try (var ctx = SagaContext.enter(sagaId, traceId)) {
   *     // ... business logic — all logs carry sagaId + traceId
   * }
   * }</pre>
   */
  public final class SagaContext implements AutoCloseable {

      private static final String SAGA_ID_KEY = "sagaId";
      private static final String TRACE_ID_KEY = "traceId";

      private SagaContext() {}

      public static SagaContext enter(UUID sagaId, UUID traceId) {
          SagaContext ctx = new SagaContext();
          MDC.put(SAGA_ID_KEY, sagaId != null ? sagaId.toString() : "");
          MDC.put(TRACE_ID_KEY, traceId != null ? traceId.toString() : "");
          return ctx;
      }

      @Override
      public void close() {
          MDC.remove(SAGA_ID_KEY);
          MDC.remove(TRACE_ID_KEY);
      }
  }
  ```

### Step 4: Extract Shared Constants/Topics for Outbox Publisher

- [x] **4.1** Create `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/OutboxMessagePublisher.java`:

  ```java
  package com.insurancemanagementsystem.common.messaging;

  import com.insurancemanagementsystem.common.entity.OutboxEvent;
  import com.insurancemanagementsystem.common.event.BaseEvent;
  import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;
  import tools.jackson.databind.json.JsonMapper;

  import java.util.UUID;

  /**
   * Publishes events via the transactional outbox pattern.
   * <p>
   * All methods save an {@link OutboxEvent} to the database. The
   * {@link com.insurancemanagementsystem.common.config.OutboxRelay}
   * picks up PENDING events and delivers them to Kafka.
   * <p>
   * This is the canonical way to publish SAGA events. For domain events,
   * services may use this or direct {@link MessagePublisher} depending on
   * durability requirements.
   */
  @Component
  @RequiredArgsConstructor
  @Slf4j
  public class OutboxMessagePublisher {

      private final OutboxEventRepository outboxEventRepository;
      private final JsonMapper jsonMapper;

      /**
       * Publish a SAGA event via the outbox.
       *
       * @param event  the event payload (must extend BaseEvent)
       * @param sagaId the saga correlation ID
       * @param traceId the trace ID (propagated from incoming event)
       * @param topic  the Kafka topic
       */
      public void publish(BaseEvent event, UUID sagaId, UUID traceId, String topic) {
          String payloadJson;
          try {
              payloadJson = jsonMapper.writeValueAsString(event.toEnvelope(sagaId, traceId));
          } catch (Exception e) {
              throw new RuntimeException("Failed to serialize outbox payload for sagaId=" + sagaId, e);
          }

          OutboxEvent outboxEvent = OutboxEvent.builder()
                  .sagaId(sagaId)
                  .topic(topic)
                  .payload(payloadJson)
                  .status(OutboxEvent.Status.PENDING)
                  .build();
          outboxEventRepository.save(outboxEvent);
          log.debug("Saved outbox event for sagaId={}, eventType={} to topic={}",
                  sagaId, event.getEventType(), topic);
      }
  }
  ```

### Step 5: Populate `common-test` Module

- [x] **5.1** Create `common/common-test/build.gradle.kts`:

  ```kotlin
  plugins {
      java
      `java-library`
  }

  group = "com.insurancemanagementsystem"
  version = "0.0.1-SNAPSHOT"

  java {
      toolchain {
          languageVersion.set(JavaLanguageVersion.of(25))
      }
  }

  dependencies {
      api(project(":common:common-message"))
      api("org.springframework.boot:spring-boot-starter-test")
      api("org.testcontainers:testcontainers")
      api("org.testcontainers:postgresql")
      api("org.testcontainers:kafka")
      api("org.testcontainers:junit-jupiter")
      api("org.springframework.kafka:spring-kafka-test")
  }

  tasks.test {
      useJUnitPlatform()
  }
  ```

- [x] **5.2** Uncomment `include("common:common-test")` in root `settings.gradle.kts`

- [x] **5.3** Create shared test base class: `common/common-test/src/main/java/com/insurancemanagementsystem/common/test/AbstractIntegrationTest.java`:

  ```java
  package com.insurancemanagementsystem.common.test;

  import org.junit.jupiter.api.BeforeEach;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.boot.testcontainers.context.ImportTestcontainers;
  import org.springframework.test.context.DynamicPropertyRegistry;
  import org.springframework.test.context.DynamicPropertySource;
  import org.testcontainers.containers.PostgreSQLContainer;
  import org.testcontainers.junit.jupiter.Container;
  import org.testcontainers.junit.jupiter.Testcontainers;

  /**
   * Base class for integration tests that need PostgreSQL + Kafka.
   * Extend this in service integration tests to get pre-configured containers.
   */
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  @Testcontainers
  public abstract class AbstractIntegrationTest {

      @Container
      static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
              .withDatabaseName("testdb")
              .withUsername("test")
              .withPassword("test");

      @DynamicPropertySource
      static void configureProperties(DynamicPropertyRegistry registry) {
          registry.add("spring.datasource.url", postgres::getJdbcUrl);
          registry.add("spring.datasource.username", postgres::getUsername);
          registry.add("spring.datasource.password", postgres::getPassword);
      }
  }
  ```

- [x] **5.4** Create `AbstractKafkaIntegrationTest.java` that adds a Kafka container:
  ```java
  package com.insurancemanagementsystem.common.test;

  import org.springframework.test.context.DynamicPropertyRegistry;
  import org.springframework.test.context.DynamicPropertySource;
  import org.testcontainers.containers.KafkaContainer;
  import org.testcontainers.junit.jupiter.Container;
  import org.testcontainers.utility.DockerImageName;

  public abstract class AbstractKafkaIntegrationTest extends AbstractIntegrationTest {

      @Container
      static KafkaContainer kafka = new KafkaContainer(
              DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

      @DynamicPropertySource
      static void configureKafka(DynamicPropertyRegistry registry) {
          registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
          registry.add("spring.cloud.stream.kafka.binder.brokers", kafka::getBootstrapServers);
      }
  }
  ```

- [x] **5.5** Build common-test: `.\gradlew.bat :common:common-test:build`

### Step 6: Update Domain Event Publishers to Use Outbox (Optional Enhancement)

- [x] **6.1** Review: Currently, domain event publishers (CustomerEventPublisher, VehicleEventPublisher, etc.) use `MessagePublisher.publish()` directly. This is acceptable for non-SAGA domain events where eventual consistency and crash-recovery are less critical. Document this design decision.

- [x] **6.2** If the task requires domain events to also use outbox, update each EventPublisher to save `OutboxEvent` instead:
  - `services/customer-service/.../CustomerEventPublisher.java`
  - `services/vehicle-service/.../VehicleEventPublisher.java`
  - `services/realestate-service/.../RealEstateEventPublisher.java`
  - `services/insurance-service/.../InsuranceEventPublisher.java`
  - `services/reference-data-service/.../ReferenceDataEventPublisher.java`

- [x] **6.3** For now, document that domain events use direct publish via `MessagePublisher` and note that migrating to outbox is a follow-up improvement. The SAGA events already use outbox.

### Step 7: Verify

- [x] **7.1** Build common-message: `.\gradlew.bat :common:common-message:build`
- [x] **7.2** Build common-test: `.\gradlew.bat :common:common-test:build`
- [x] **7.3** Build all services to verify no breakage: `.\gradlew.bat build`
- [x] **7.4** Run all tests: `.\gradlew.bat test`

---

## Files to Create

| File | Purpose |
|------|---------|
| `common/common-message/src/main/java/.../messaging/MessageListener.java` | Typed consumer abstraction |
| `common/common-message/src/main/java/.../messaging/OutboxMessagePublisher.java` | Convenience outbox publisher |
| `common/common-message/src/main/java/.../util/CorrelationIdGenerator.java` | UUID generation utility |
| `common/common-message/src/main/java/.../util/SagaContext.java` | MDC context holder (AutoCloseable) |
| `common/common-test/build.gradle.kts` | Build config for shared test utilities |
| `common/common-test/src/main/java/.../test/AbstractIntegrationTest.java` | Base test class (PostgreSQL) |
| `common/common-test/src/main/java/.../test/AbstractKafkaIntegrationTest.java` | Base test class (PostgreSQL + Kafka) |

## Files to Modify

| File | Change |
|------|--------|
| `settings.gradle.kts` (root) | Uncomment `include("common:common-test")` |

## Dependencies
- Subtask 1 (Event Schemas) — must complete first (event POJOs are the foundation)

## Completion Criteria
- [x] `MessageListener<T>` abstract class exists with deserialization, MDC, dedup
- [x] `CorrelationIdGenerator` utility exists
- [x] `SagaContext` AutoCloseable MDC holder exists
- [x] `OutboxMessagePublisher` convenience class exists
- [x] `common-test` module is built with `AbstractIntegrationTest` and `AbstractKafkaIntegrationTest`
- [x] `.\gradlew.bat build` passes for all modules
