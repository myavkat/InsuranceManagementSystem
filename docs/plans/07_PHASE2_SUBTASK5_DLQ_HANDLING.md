# Subtask 5: Implement Dead Letter Queue Handling

## Status: NOT STARTED
## Parent: `07_PHASE2_MASTER_PLAN.md`
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Implement Kafka-based Dead Letter Queue (DLQ) handling for failed SAGA event processing. This includes:
1. `dlq.saga` Kafka topic (provisioned in Subtask 2)
2. Retry consumer with exponential backoff (1s, 2s, 4s, 8s, max 5 retries)
3. After max retries: log the failed message, notify admin channel, stop consuming

## Files to Read Before Starting

1. `common/common-message/src/main/java/com/insurancemanagementsystem/common/config/OutboxProcessor.java` — existing retry pattern
2. `common/common-message/src/main/java/com/insurancemanagementsystem/common/config/OutboxRelay.java` — scheduled relay pattern
3. `services/estimation-service/.../config/EstimationSagaConsumer.java` — consumer pattern to enhance
4. `services/insurance-service/.../config/InsuranceSagaConsumer.java` — consumer with aggregation
5. `services/customer-service/.../config/CustomerSagaConsumer.java` — consumer pattern
6. `services/vehicle-service/.../config/VehicleSagaConsumer.java` — consumer pattern
7. `services/realestate-service/.../config/RealEstateSagaConsumer.java` — consumer pattern
8. Each service's `application.yml` — current Kafka/Stream config
9. `docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md` — DLQ topic configuration
10. `docs/outlines/03_SAGA_PATTERN.md` — consumer implementation rules

## Current State

- No DLQ topic exists (will be created by Subtask 2's `create-topics.sh`)
- No retry configuration in Spring Cloud Stream binder for any service
- `OutboxProcessor` has its own retry (max 3, resets to PENDING) — but this is for outbound publishing, not inbound consumption
- Consumer exceptions are re-thrown as `RuntimeException` in some services — this triggers Spring Cloud Stream's default retry (if configured) but no DLQ routing
- Poison-pill deserialization errors are silently caught and skipped — messages are lost
- No dead-letter publishing exists in any consumer

## Architecture Decision

Spring Cloud Stream with Kafka binder supports DLQ natively via `enableDlq: true` and `dlqName` properties. However, the project uses functional consumer beans (`Consumer<String>`), not the deprecated `@StreamListener` approach. DLQ configuration for functional bindings requires:

1. **Kafka-level:** Configure `ListenerContainerCustomizer` bean to set up `DeadLetterPublishingRecoverer` + `SeekToCurrentErrorHandler`
2. **Or Spring Cloud Stream binder level:** Use `spring.cloud.stream.kafka.bindings.<binding>.consumer.enableDlq: true`

The binder-level approach is simpler and preferred.

## Implementation Steps

### Step 1: Configure DLQ in Each Service's application.yml

- [ ] **1.1** For each of the 5 SAGA-consuming services (customer, vehicle, realestate, insurance, estimation), add DLQ configuration to `application.yml`:

  ```yaml
  spring:
    cloud:
      stream:
        kafka:
          bindings:
            # The binding name must match the consumer function name (e.g., processCustomerSaga-in-0)
            processCustomerSaga-in-0:  # adjust per service
              consumer:
                enableDlq: true
                dlqName: dlq.saga
                dlqProducerProperties:
                  configuration:
                    key.serializer: org.apache.kafka.common.serialization.StringSerializer
                    value.serializer: org.springframework.kafka.support.serializer.JsonSerializer
          binder:
            configuration:
              # Retry configuration — applies to ALL consumer bindings in this service
              spring.kafka.consumer.properties.retry.backoff.ms: 1000   # start at 1s
              spring.kafka.consumer.properties.retry.backoff.multiplier: 2.0  # exponential
              spring.kafka.consumer.properties.retry.backoff.max.ms: 8000  # max 8s
              spring.kafka.consumer.properties.retry.maxAttempts: 6      # 1 initial + 5 retries
  ```

- [ ] **1.2** The binding names per service are:
  - `customer-service`: `processCustomerSaga-in-0`
  - `vehicle-service`: `processVehicleSaga-in-0`
  - `realestate-service`: `processRealEstateSaga-in-0`
  - `insurance-service`: `processInsuranceSaga-in-0`
  - `estimation-service`: `processEstimationSaga-in-0`

### Step 2: Create DLQ Consumer for Poison-Pill Dead Letters

- [ ] **2.1** Currently, deserialization failures are caught and silently skipped — the message disappears. To route poison pills to DLQ, the deserialization must FAIL (throw exception) rather than being caught.

- [ ] **2.2** Update ALL SAGA consumers to NOT catch deserialization failures silently. Instead, let the exception propagate so Spring Cloud Stream routes it to DLQ:

  **Change from:**
  ```java
  try {
      envelope = jsonMapper.readValue(message, EventEnvelope.class);
  } catch (Exception e) {
      log.error("Failed to deserialize SAGA message — skipping (poison pill): {}", e.getMessage(), e);
      return;  // <-- SILENTLY LOSES THE MESSAGE
  }
  ```

  **Change to:**
  ```java
  try {
      envelope = jsonMapper.readValue(message, EventEnvelope.class);
  } catch (Exception e) {
      log.error("Failed to deserialize SAGA message — routing to DLQ: {}", e.getMessage(), e);
      throw new RuntimeException("Deserialization failed — routing to DLQ", e);
  }
  ```

- [ ] **2.3** Services to update (find the exact file and line):
  - `services/estimation-service/.../EstimationSagaConsumer.java` — `processEstimationSaga()` bean
  - `services/insurance-service/.../InsuranceSagaConsumer.java` — `processInsuranceSaga()` bean
  - `services/customer-service/.../CustomerSagaConsumer.java` — `processCustomerSaga()` bean
  - `services/vehicle-service/.../VehicleSagaConsumer.java` — `processVehicleSaga()` bean
  - `services/realestate-service/.../RealEstateSagaConsumer.java` — `processRealEstateSaga()` bean

### Step 3: Create DLQ Monitor/Admin Notification Service

- [ ] **3.1** Create `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/DlqMonitor.java`:

  ```java
  package com.insurancemanagementsystem.common.messaging;

  import lombok.extern.slf4j.Slf4j;
  import org.apache.kafka.clients.consumer.ConsumerRecord;
  import org.springframework.context.annotation.Bean;
  import org.springframework.kafka.annotation.KafkaListener;
  import org.springframework.kafka.support.KafkaHeaders;
  import org.springframework.messaging.handler.annotation.Header;
  import org.springframework.stereotype.Component;

  import java.util.function.Consumer;

  /**
   * Monitors the dlq.saga topic and logs dead-lettered messages for admin review.
   * <p>
   * This is a diagnostic consumer — it does NOT reprocess or retry.
   * Admin intervention is required to investigate and potentially replay
   * DLQ messages after fixing the root cause.
   */
  @Component
  @Slf4j
  public class DlqMonitor {

      /**
       * Consumes messages from dlq.saga and logs them with all available context.
       * Each message includes original topic, partition, offset, and exception details
       * in Kafka headers (populated by DeadLetterPublishingRecoverer).
       */
      @KafkaListener(
          topics = "dlq.saga",
          groupId = "dlq-monitor-group",
          autoStartup = "true"
      )
      public void consume(ConsumerRecord<String, String> record) {
          log.error("""
              ========================================
              DLQ MESSAGE RECEIVED — ADMIN ACTION REQUIRED
              Topic: {}
              Partition: {}
              Offset: {}
              Key: {}
              Original Topic: {}
              Original Partition: {}
              Original Offset: {}
              Exception Message: {}
              Exception Stacktrace: {}
              Payload: {}
              ========================================\
              """,
              record.topic(),
              record.partition(),
              record.offset(),
              record.key(),
              header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
              header(record, KafkaHeaders.DLT_ORIGINAL_PARTITION),
              header(record, KafkaHeaders.DLT_ORIGINAL_OFFSET),
              header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
              header(record, KafkaHeaders.DLT_EXCEPTION_STACKTRACE),
              record.value()
          );
      }

      private String header(ConsumerRecord<String, String> record, String key) {
          var h = record.headers().lastHeader(key);
          return h != null ? new String(h.value()) : "N/A";
      }
  }
  ```

- [ ] **3.2** This consumer is for monitoring only — it reads from `dlq.saga` and logs with high visibility (`ERROR` level, delimited format). It does NOT attempt to reprocess — that requires admin investigation.

- [ ] **3.3** Register this component in `CommonPersistenceAutoConfiguration` or let it be auto-scanned. Since it's in `common-message`, services that include the module will auto-detect it via `@Component` scan.

### Step 4: Create DLQ Admin API (Optional — Future Enhancement)

- [ ] **4.1** Create `DlqAdminController.java` in the estimation service (or a separate admin service) with endpoints:
  - `GET /api/admin/dlq/messages` — list dead-lettered messages
  - `POST /api/admin/dlq/replay/{offset}` — replay a specific message to the original topic
  - `DELETE /api/admin/dlq/purge` — purge all DLQ messages (after acknowledged review)

- [ ] **4.2** This is optional for this phase. Document as a future enhancement. The priority is logging and monitoring.

### Step 5: Configure Retry with Exponential Backoff at Binder Level

- [ ] **5.1** Verify the retry configuration works by creating a test:

  In each service's `application.yml`, the retry configuration section from Step 1.1 should be:
  ```yaml
  spring:
    cloud:
      stream:
        kafka:
          bindings:
            processXxxSaga-in-0:
              consumer:
                enableDlq: true
                dlqName: dlq.saga
  ```

- [ ] **5.2** The exponential backoff retry is handled by Spring Kafka's `DefaultErrorHandler` (Spring Kafka 3.x / Spring Boot 4.x). Configure via properties:

  ```yaml
  spring:
    kafka:
      consumer:
        properties:
          retry.backoff.ms: 1000
          retry.backoff.multiplier: 2.0
          retry.backoff.max.ms: 8000
      listener:
        # Retry exhausted → route to DLQ (not configured per-binding)
  ```

- [ ] **5.3** For Spring Boot 4.x, the retry/DLQ configuration may use `spring.kafka.retry` properties or a `CommonErrorHandler` bean. Create a shared configuration bean:

  ```java
  @Bean
  public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
      DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
              template,
              (record, ex) -> new TopicPartition("dlq.saga", record.partition())
      );
      return new DefaultErrorHandler(recoverer, new ExponentialBackOff(1000L, 2.0, 8000L, 5));
  }
  ```

- [ ] **5.4** This bean should go in:
  - Either a new `common/common-message/.../config/KafkaErrorHandlerConfig.java` (shared)
  - Or in each service's own config class

  **Decision:** Put it in `common-message` so all services inherit it. The `KafkaTemplate` dependency is satisfied because every service using Spring Cloud Stream with Kafka binder has a `KafkaTemplate` auto-configured.

### Step 6: Verify

- [ ] **6.1** After completing Subtask 2 (infrastructure), verify `dlq.saga` topic exists:
  ```bash
  docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic dlq.saga
  ```

- [ ] **6.2** Build all services: `.\gradlew.bat build`

- [ ] **6.3** Start the full stack and publish a malformed message to `estimation.saga`:
  ```bash
  echo 'not-valid-json' | docker exec -i kafka kafka-console-producer --bootstrap-server localhost:9092 --topic estimation.saga
  ```

- [ ] **6.4** Verify the malformed message lands in `dlq.saga`:
  ```bash
  docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic dlq.saga --from-beginning --max-messages 1
  ```

- [ ] **6.5** Run integration tests: `.\gradlew.bat test`

---

## Files to Create

| File | Purpose |
|------|---------|
| `common/common-message/src/main/java/.../messaging/DlqMonitor.java` | DLQ consumer that logs dead letters |
| `common/common-message/src/main/java/.../config/KafkaErrorHandlerConfig.java` | Shared error handler with DLQ routing |
| `services/estimation-service/src/main/java/.../admin/DlqAdminController.java` | (Optional) Admin API for DLQ management |

## Files to Modify

| File | Change |
|------|--------|
| `services/estimation-service/.../EstimationSagaConsumer.java` | Remove silent poison-pill skip; let exceptions propagate |
| `services/insurance-service/.../InsuranceSagaConsumer.java` | Same |
| `services/customer-service/.../CustomerSagaConsumer.java` | Same |
| `services/vehicle-service/.../VehicleSagaConsumer.java` | Same |
| `services/realestate-service/.../RealEstateSagaConsumer.java` | Same |
| `services/*/application.yml` (5 services) | Add DLQ + retry config to Kafka binder |

## Retry Backoff Table

| Attempt | Delay | Cumulative |
|---------|-------|------------|
| 1 (initial) | 0s | 0s |
| 2 | 1s | 1s |
| 3 | 2s | 3s |
| 4 | 4s | 7s |
| 5 | 8s | 15s |
| 6 (exhausted → DLQ) | — | 15s |

## Dependencies
- Subtask 2 (Message Infrastructure) — `dlq.saga` topic must exist
- Subtask 3 (Common Library) — `common-message` must have `DlqMonitor` and error handler

## Completion Criteria
- [ ] `dlq.saga` topic exists with correct configuration
- [ ] All 5 SAGA consumers are configured with `enableDlq: true`
- [ ] Poison-pill messages (deserialization failures) are routed to DLQ (not silently lost)
- [ ] Retry with exponential backoff (1s, 2s, 4s, 8s) is configured
- [ ] After 5 retries, messages land in `dlq.saga`
- [ ] `DlqMonitor` consumer logs dead-lettered messages prominently
- [ ] Common `KafkaErrorHandlerConfig` bean is shared across services
- [ ] `.\gradlew.bat build` passes for all modules
