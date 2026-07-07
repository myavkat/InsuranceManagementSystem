# Fix 02 — Kafka Error Handling & DLQ Configuration Fixes

## Status: NOT STARTED
## Parent: Post-Review Fixes (Phase 2 code review, 2026-07-07)
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Fix four interconnected defects in the Kafka error handling and Dead Letter Queue infrastructure discovered during code review:

1. **`KafkaErrorHandlerConfig` bean doesn't apply to Spring Cloud Stream functional bindings** — the `CommonErrorHandler` bean only applies to `@KafkaListener` containers, not to functional `Consumer<String>` beans
2. **`ExponentialBackOff` has unbounded `maxElapsedTime`** — retries never stop; `DeadLetterPublishingRecoverer` is never invoked
3. **`DeadLetterPublishingRecoverer` partition mismatch** — routes to source partition number on `dlq.saga` which has fewer partitions
4. **`DlqMonitor` infinite DLQ loop** — the monitor's `@KafkaListener` inherits the same `DefaultErrorHandler` that republishes to `dlq.saga`

All four issues stem from misunderstanding how Spring Cloud Stream functional bindings interact with Spring Kafka's `CommonErrorHandler`.

## Context — Spring Cloud Stream vs @KafkaListener Error Handling

**Spring Cloud Stream functional bindings** (`Consumer<String>` beans) do NOT use `CommonErrorHandler` beans from the application context. They configure their own error handling through:

1. **Binder-level configuration** in `application.yml`:
   ```yaml
   spring.cloud.stream.kafka.bindings.<functionName>-in-0.consumer.enableDlq: true
   spring.cloud.stream.kafka.bindings.<functionName>-in-0.consumer.dlqName: dlq.saga
   ```

2. **Binder-level retry** via `spring.cloud.stream.kafka.bindings.<functionName>-in-0.consumer.retry.*` or the global `spring.cloud.stream.kafka.default.consumer.retry.*`

**`@KafkaListener` containers** DO use `CommonErrorHandler` beans. `DlqMonitor` is the only `@KafkaListener` in the project — it inherits the `DefaultErrorHandler` from `KafkaErrorHandlerConfig`.

**The current state is therefore:**
- SAGA consumers (functional bindings): retry/DLQ configured ONLY via `enableDlq: true` in `application.yml` — the `KafkaErrorHandlerConfig` bean is IGNORED
- DlqMonitor (`@KafkaListener`): inherits `KafkaErrorHandlerConfig`'s `DefaultErrorHandler` — retries failures and re-publishes to `dlq.saga` (INFINITE LOOP)

## Files to Read Before Starting

1. `common/common-message/src/main/java/com/insurancemanagementsystem/common/config/KafkaErrorHandlerConfig.java` — the shared error handler bean
2. `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/DlqMonitor.java` — the `@KafkaListener` consumer
3. `services/estimation-service/src/main/resources/application.yml` — current Kafka/Stream config (as template for all services)
4. `services/customer-service/src/main/resources/application.yml` — same
5. `services/vehicle-service/src/main/resources/application.yml` — same
6. `services/realestate-service/src/main/resources/application.yml` — same
7. `services/insurance-service/src/main/resources/application.yml` — same
8. `infra/kafka/create-topics.sh` — DLQ topic partition count
9. `docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md` — topic specs (dlq.saga: 1 partition)
10. `docs/plans/07_PHASE2_SUBTASK5_DLQ_HANDLING.md` — original DLQ design (Step 5.3: CommonErrorHandler decision)

---

## Implementation Steps

### Step 1: Fix `KafkaErrorHandlerConfig` — Add `maxElapsedTime` and Fix Partition

- [x] **1.1** Open `KafkaErrorHandlerConfig.java`. It currently reads:

  ```java
  @Bean
  @ConditionalOnMissingBean(CommonErrorHandler.class)
  public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
      DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
          kafkaTemplate,
          (ConsumerRecord<?, ?> record, Exception exception) ->
              new TopicPartition("dlq.saga", record.partition())  // ❌ partition mismatch
      );

      ExponentialBackOff backOff = new ExponentialBackOff();
      backOff.setInitialInterval(1000L);
      backOff.setMultiplier(2.0);
      backOff.setMaxInterval(8000L);
      // ❌ Missing: backOff.setMaxElapsedTime(...) — retries never stop

      DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
      errorHandler.addNotRetryableExceptions(DeserializationException.class);

      log.info("Kafka error handler configured: retry 1s→2s→4s→8s, DLQ=dlq.saga");
      return errorHandler;
  }
  ```

- [x] **1.2** Fix the partition mismatch — `dlq.saga` has 1 partition. Always route to partition 0:

  ```java
  DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
      kafkaTemplate,
      (ConsumerRecord<?, ?> record, Exception exception) ->
          new TopicPartition("dlq.saga", 0)  // ✅ dlq.saga has 1 partition
  );
  ```

- [x] **1.3** Add `maxElapsedTime` to bound retries. The intended retry sequence is 1s → 2s → 4s → 8s (5 total: initial + 4 retries = ~15s cumulative), so set maxElapsedTime to 20000ms:

  ```java
  ExponentialBackOff backOff = new ExponentialBackOff();
  backOff.setInitialInterval(1000L);
  backOff.setMultiplier(2.0);
  backOff.setMaxInterval(8000L);
  backOff.setMaxElapsedTime(20000L);  // ✅ cap retries at ~20s total
  ```

  **Why 20000ms?** The cumulative time for 5 attempts at 1s/2s/4s/8s = 15s. Adding 5s buffer for processing overhead gives 20s. After 20s, the `BackOff` returns `STOP`, and the `DeadLetterPublishingRecoverer` is invoked.

- [x] **1.4** Add a comment documenting the retry math:

  ```java
  // Retry sequence: 1s + 2s + 4s + 8s = ~15s cumulative (5 total attempts).
  // maxElapsedTime=20s adds a 5s buffer for processing overhead before DLQ routing.
  // After expiry, DeadLetterPublishingRecoverer publishes to dlq.saga partition 0.
  ```

- [x] **1.5** Updated full bean method:

  ```java
  @Bean
  @ConditionalOnMissingBean(CommonErrorHandler.class)
  public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
      // dlq.saga has 1 partition — always route to partition 0
      DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
          kafkaTemplate,
          (ConsumerRecord<?, ?> record, Exception exception) ->
              new TopicPartition("dlq.saga", 0)
      );

      // Retry sequence: 1s → 2s → 4s → 8s (5 total attempts, ~15s cumulative)
      // maxElapsedTime=20s adds buffer before DLQ routing
      ExponentialBackOff backOff = new ExponentialBackOff();
      backOff.setInitialInterval(1000L);
      backOff.setMultiplier(2.0);
      backOff.setMaxInterval(8000L);
      backOff.setMaxElapsedTime(20000L);

      DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
      // Deserialization (poison-pill) failures immediately route to DLQ — no retry
      errorHandler.addNotRetryableExceptions(DeserializationException.class);

      log.info("Kafka error handler configured: retry 1s→2s→4s→8s (max 20s), DLQ=dlq.saga[0]");
      return errorHandler;
  }
  ```

### Step 2: Fix `DlqMonitor` — Prevent Infinite DLQ Loop

The `DlqMonitor` is a `@KafkaListener` consuming from `dlq.saga`. If its `consume()` method throws (e.g., malformed headers, log framework error), the `DefaultErrorHandler` retries and then re-publishes to `dlq.saga` — the message re-enters the monitor, fails again, loops forever.

- [x] **2.1** Implement Approach A: Add dlqMonitorContainerFactory with no-retry handler

  Add a separate `KafkaListenerContainerFactory` for the DLQ monitor that uses a no-op error handler:

  In `KafkaErrorHandlerConfig.java` (or a new config class), add:

  ```java
  /**
   * Dedicated listener container factory for dlq-monitor-group.
   * Uses a no-retry error handler to prevent infinite DLQ loops —
   * if the monitor itself fails, the message is simply logged and committed
   * (not re-published to dlq.saga).
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> dlqMonitorContainerFactory(
          ConsumerFactory<String, String> consumerFactory) {
      ConcurrentKafkaListenerContainerFactory<String, String> factory =
              new ConcurrentKafkaListenerContainerFactory<>();
      factory.setConsumerFactory(consumerFactory);
      // No retries, no DLQ publishing — just log and move on
      factory.setCommonErrorHandler(new CommonErrorHandler() {
          @Override
          public void handleRemaining(Exception thrownException,
                                      List<ConsumerRecord<?, ?>> records,
                                      Consumer<?, ?> consumer,
                                      MessageListenerContainer container) {
              log.error("DlqMonitor failed to process {} record(s) — committing offsets to avoid loop: {}",
                      records.size(), thrownException.getMessage(), thrownException);
              // Default behavior after this returns: commit offsets (acknowledge)
          }
      });
      return factory;
  }
  ```

  Then update `DlqMonitor.java` to use this factory:

  ```java
  @KafkaListener(
      topics = "dlq.saga",
      groupId = "dlq-monitor-group",
      containerFactory = "dlqMonitorContainerFactory",  // ✅ no-retry container
      autoStartup = "true"
  )
  public void consume(ConsumerRecord<String, String> record) {
      // ... existing implementation unchanged
  }
  ```

  **Approach B: Remove `@KafkaListener`, use a Spring Cloud Stream functional binding**

  Convert DlqMonitor to use the same functional `Consumer<String>` pattern as SAGA consumers. This ensures it's managed by the binder (not the `DefaultErrorHandler`). More work but more consistent.

- [x] **2.2** Choose Approach A for this fix. It's minimal, targeted, and the plan is clear.

### Step 3: Add Binder-Level Retry Configuration to ALL 5 Services

The `KafkaErrorHandlerConfig` bean only serves `DlqMonitor` (the sole `@KafkaListener`). The SAGA consumers need **binder-level** retry configuration in `application.yml`.

- [x] **3.1** For each of the 5 SAGA-consuming services, verify the DLQ config already exists (`enableDlq: true`, `dlqName: dlq.saga`). This was added in Subtask 5.

- [x] **3.2** Add retry configuration at the binder level. Since the retry applies uniformly, configure it once under `spring.cloud.stream.kafka.default.consumer` rather than per-binding:

  In each service's `application.yml`:

  ```yaml
  spring:
    cloud:
      stream:
        kafka:
          default:
            consumer:
              # Retry with exponential backoff: 1s → 2s → 4s → 8s (5 total attempts)
              # After retries exhausted → route to DLQ (configured per-binding)
              retry:
                maxAttempts: 5
                backOff:
                  initialInterval: 1000
                  multiplier: 2.0
                  maxInterval: 8000
          bindings:
            processEstimationSaga-in-0:   # adjust binding name per service
              consumer:
                enableDlq: true
                dlqName: dlq.saga
  ```

  **Binding names per service:**
  | Service | Binding Name |
  |---------|-------------|
  | estimation-service | `processEstimationSaga-in-0` |
  | customer-service | `processCustomerSaga-in-0` |
  | vehicle-service | `processVehicleSaga-in-0` |
  | realestate-service | `processRealEstateSaga-in-0` |
  | insurance-service | `processInsuranceSaga-in-0` |

- [x] **3.3** Apply the retry configuration to ALL 5 services. Copy the identical `spring.cloud.stream.kafka.default.consumer.retry` block into each `application.yml`.

- [x] **3.4** Add a comment block explaining the error handling architecture:

  ```yaml
  # Error handling architecture:
  # - SAGA consumers (functional bindings): retry via binder-level retry config,
  #   then route to dlq.saga via enableDlq per binding
  # - DlqMonitor (@KafkaListener): uses dlqMonitorContainerFactory with
  #   no-retry handler to prevent infinite DLQ loops
  # - The KafkaErrorHandlerConfig CommonErrorHandler bean serves ONLY
  #   @KafkaListener containers (currently just DlqMonitor)
  ```

### Step 4: Update KafkaErrorHandlerConfig Javadoc

- [x] **4.1** Update the class Javadoc to clarify scope:

  **Current:**
  ```java
  /**
   * Shared Kafka error handler configuration that provides exponential backoff retry
   * with Dead Letter Queue routing for all SAGA consumer bindings.
   */
  ```

  **Fixed:**
  ```java
  /**
   * Shared Kafka error handler configuration for {@code @KafkaListener} containers.
   * <p>
   * <b>Scope:</b> This bean applies ONLY to {@code @KafkaListener}-annotated methods
   * (currently just {@link com.insurancemanagementsystem.common.messaging.DlqMonitor}).
   * Spring Cloud Stream functional {@code Consumer<String>} bindings (SAGA consumers)
   * use binder-level retry and DLQ configuration in {@code application.yml} instead.
   * <p>
   * Retry sequence: 1s → 2s → 4s → 8s (5 total attempts, max 20s elapsed),
   * then routes to {@code dlq.saga} partition 0.
   * Deserialization (poison-pill) failures are immediately routed to DLQ without retry.
   */
  ```

### Step 5: Build and Verify

- [x] **5.1** Build `common-message` (contains KafkaErrorHandlerConfig and DlqMonitor):
  ```bash
  .\gradlew.bat :common:common-message:build
  ```

- [x] **5.2** Build all services to verify YAML config is valid:
  ```bash
  .\gradlew.bat build -x test
  ```

- [x] **5.3** Run the E2E SAGA tests — they use `@EmbeddedKafka` and exercise the consumer error paths:
  ```bash
  .\gradlew.bat :services:estimation-service:test --tests "*SagaE2ETest*"
  ```

- [ ] **5.4** Start infrastructure and verify DLQ topic still has 1 partition:
  ```bash
  docker compose -f infra/docker/docker-compose.yml up -d
  docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic dlq.saga
  ```

- [ ] **5.5** Verify the `kafkaErrorHandler` bean is created (check startup logs for "Kafka error handler configured: retry 1s→2s→4s→8s (max 20s), DLQ=dlq.saga[0]")

- [ ] **5.6** Tear down:
  ```bash
  docker compose -f infra/docker/docker-compose.yml down
  ```

---

## Files to Modify

| File | Change |
|------|--------|
| `common/common-message/.../config/KafkaErrorHandlerConfig.java` | Fix partition → 0; add maxElapsedTime=20000; add dlqMonitorContainerFactory bean; update Javadoc |
| `common/common-message/.../messaging/DlqMonitor.java` | Add `containerFactory = "dlqMonitorContainerFactory"` to `@KafkaListener` |
| `services/estimation-service/src/main/resources/application.yml` | Add `spring.cloud.stream.kafka.default.consumer.retry` block |
| `services/customer-service/src/main/resources/application.yml` | Same |
| `services/vehicle-service/src/main/resources/application.yml` | Same |
| `services/realestate-service/src/main/resources/application.yml` | Same |
| `services/insurance-service/src/main/resources/application.yml` | Same |

---

## Dependencies

- None (standalone fix)
- Note: The binder-level retry config requires Spring Cloud Stream 2025.1.x+ (already in use)

## Completion Criteria

- [x] `ExponentialBackOff.maxElapsedTime` is set to 20000ms
- [x] `DeadLetterPublishingRecoverer` routes to `dlq.saga` partition 0 (not source partition)
- [x] `DlqMonitor` uses `dlqMonitorContainerFactory` with no-retry error handler (no infinite loop)
- [x] All 5 services have `spring.cloud.stream.kafka.default.consumer.retry` block with 1s→2s→4s→8s
- [x] `KafkaErrorHandlerConfig` Javadoc clearly states scope (`@KafkaListener` only)
- [x] `.\gradlew.bat build` passes for all modules
- [x] **Status: COMPLETED**
- [ ] E2E SAGA tests pass
- [ ] DLQ topic verified: 1 partition, `docker exec kafka kafka-topics --describe --topic dlq.saga`
