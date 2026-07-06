# Subtask 4: End-to-End SAGA Tests

## Status: NOT STARTED
## Parent: `07_PHASE2_MASTER_PLAN.md`
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Create integration tests that spin up all services (or at minimum their messaging layers) with Testcontainers (PostgreSQL × N, Kafka). Tests cover:
1. Happy path: create estimation → validate customer + vehicle → calculate premium → complete
2. Idempotency: publish duplicate events → verify no side effects
3. Timeout: estimation with no response → verify `EstimationFailed` published within timeout window
4. DLQ: poison message → verify it lands in `dlq.saga`

## Files to Read Before Starting

1. `services/estimation-service/src/test/java/.../EstimationServiceIntegrationTest.java` — existing integration test pattern
2. `services/insurance-service/src/test/java/.../saga/InsuranceSagaConsumerTest.java` — existing SAGA test with @EmbeddedKafka
3. `services/customer-service/src/test/java/.../saga/CustomerSagaConsumerTest.java` — existing SAGA test
4. `services/vehicle-service/src/test/java/.../saga/VehicleSagaConsumerTest.java` — existing SAGA test
5. `services/estimation-service/.../EstimationSagaConsumer.java` — the full consumer for reference
6. `services/insurance-service/.../InsuranceSagaConsumer.java` — premium calculation logic
7. `docs/outlines/03_SAGA_PATTERN.md` — full event flow, event catalog
8. `docs/outlines/11_TESTING_CONVENTIONS.md` — RestTestClient, assertion rules, test types
9. `docs/outlines/13_ENVIRONMENT_QUIRKS.md` — Testcontainers on Windows config
10. `AGENTS.md` — SAGA consumer rules (transaction boundaries, atomic dedup)

## Current State

- Each service has individual SAGA consumer tests using `@EmbeddedKafka` + `PostgreSQLContainer`
- No cross-service end-to-end test exists
- No test spins up multiple services simultaneously
- The infrastructure to do this exists (Testcontainers Kafka + PostgreSQL available in all build files)

## Test Architecture Decision

There are two approaches:

**Approach A: Full multi-service E2E** — spin up all 5 SAGA-participating services in one test. Very realistic but resource-intensive and slow.

**Approach B: Estimation-centric integration** — spin up estimation-service with real Kafka + PostgreSQL. Manually publish events that other services would produce (CustomerValidated, VehicleValidated, PremiumCalculated) using KafkaTemplate. This tests the estimation service's full SAGA lifecycle (the coordinator) without needing all services.

**Decision: Approach B** for the primary test suite (pragmatic, maintainable). Approach A as a single smoke test to validate cross-service wiring.

---

## Implementation Steps

### Step 1: Create E2E Test Module

- [ ] **1.1** Create directory: `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/e2e/`

- [ ] **1.2** Create `SagaE2ETest.java` — the main end-to-end test file.

### Step 2: Happy Path Test

- [ ] **2.1** Test: Full SAGA happy path from `EstimationRequested` to `COMPLETED`

  ```java
  @SpringBootTest(webEnvironment = RANDOM_PORT)
  @Testcontainers
  @AutoConfigureRestTestClient
  class SagaE2ETest {

      @Container
      static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

      @Container
      static KafkaContainer kafka = new KafkaContainer(
              DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

      @DynamicPropertySource
      static void configure(DynamicPropertyRegistry registry) {
          registry.add("spring.datasource.url", postgres::getJdbcUrl);
          registry.add("spring.datasource.username", postgres::getUsername);
          registry.add("spring.datasource.password", postgres::getPassword);
          registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
          registry.add("spring.cloud.stream.kafka.binder.brokers", kafka::getBootstrapServers);
          // Use shorter timeout for tests
          registry.add("estimation.saga.timeout-minutes", () -> 1);
          registry.add("estimation.saga.poll-interval-ms", () -> 5000);
      }

      @Autowired
      private RestTestClient client;

      @Autowired
      private KafkaTemplate<String, String> kafkaTemplate;

      @Autowired
      private EstimationRepository estimationRepository;

      @Autowired
      private SagaEventRepository sagaEventRepository;

      @Autowired
      private JsonMapper jsonMapper;

      // ... test methods
  }
  ```

- [ ] **2.2** Happy path test steps:
  1. Create estimation via REST API: `POST /api/estimations` with customerId, vehicleId, insuranceTypeId, companyId
  2. Verify estimation is created with status `STARTED`
  3. Verify `EstimationRequested` outbox event is published to `estimation.saga` topic
  4. Simulate Customer Service: publish `CustomerValidatedEvent` wrapped in `EventEnvelope` to `estimation.saga` with same sagaId
  5. Simulate Vehicle Service: publish `VehicleValidatedEvent` to `estimation.saga` with same sagaId
  6. Simulate Insurance Service: publish `PremiumCalculatedEvent` to `estimation.saga` with same sagaId
  7. Wait for outbox relay to process and consumer to handle (poll with Awaitility)
  8. Verify estimation status is `COMPLETED`
  9. Verify estimation.premium matches the published premium

- [ ] **2.3** Publish helper method:
  ```java
  private void publishSagaEvent(UUID sagaId, UUID traceId, BaseEvent event) {
      EventEnvelope envelope = event.toEnvelope(sagaId, traceId);
      String json = jsonMapper.writeValueAsString(envelope);
      kafkaTemplate.send(EventConstants.ESTIMATION_SAGA, sagaId.toString(), json);
  }
  ```

### Step 3: Idempotency Test

- [ ] **3.1** Test: Duplicate events do not cause side effects

  Steps:
  1. Create estimation (gets sagaId)
  2. Publish `CustomerValidatedEvent` with sagaId (first time)
  3. Wait for consumer to process
  4. Publish `CustomerValidatedEvent` with same sagaId AGAIN (duplicate)
  5. Publish `VehicleValidatedEvent` with sagaId
  6. Publish `PremiumCalculatedEvent` with sagaId
  7. Wait for COMPLETED
  8. Verify estimation is COMPLETED (only one transition occurred)
  9. Verify `saga_events` table has exactly one row for each event type (no duplicate dedup markers causing errors)

- [ ] **3.2** Also test duplicate `PremiumCalculatedEvent`:
  1. After estimation is COMPLETED, publish another `PremiumCalculatedEvent` with different premium
  2. Verify estimation still has the first premium value (duplicate was skipped)
  3. Verify status is still COMPLETED

### Step 4: Timeout Test

- [ ] **4.1** Test: Estimation times out when no response is received

  Steps:
  1. Create estimation with very short timeout (configure `estimation.saga.timeout-minutes=0` or use a small value)
  2. Do NOT publish any response events (no CustomerValidated, VehicleValidated, or PremiumCalculated)
  3. Wait for `SagaTimeoutService` to run (with Awaitility, up to 30 seconds)
  4. Verify estimation status transitions to `REJECTED`
  5. Verify `EstimationFailed` outbox event is published to `estimation.saga` topic
  6. Consume the `EstimationFailed` event from Kafka and verify it has correct sagaId and reason

### Step 5: DLQ Test (Poison Message)

- [ ] **5.1** Test: Malformed message lands in DLQ

  Steps:
  1. Publish a malformed JSON string to `estimation.saga` (not valid EventEnvelope)
  2. Verify the consumer catches the deserialization error and skips (current behavior — logged, not retried)
  3. **OR** if DLQ is enabled (Subtask 5), verify the poison message lands in `dlq.saga` topic
  4. Publish valid messages afterward and verify the consumer is still functional (not blocked by poison pill)

- [ ] **5.2** Note: The current consumer implementation catches deserialization errors and silently returns. For the DLQ test to work properly, Subtask 5 must be completed first. If Subtask 5 is not yet done, this test verifies the current poison-pill-skip behavior.

### Step 6: Failure Path Tests

- [ ] **6.1** Test: `CustomerInvalidated` → estimation REJECTED
  1. Create estimation
  2. Publish `CustomerInvalidatedEvent` with sagaId
  3. Wait for consumer
  4. Verify estimation status is `REJECTED`
  5. Verify `EstimationFailed` outbox event is published
  6. Consume `EstimationFailed` from Kafka and verify payload

- [ ] **6.2** Test: `VehicleInvalidated` → estimation REJECTED (same pattern)

- [ ] **6.3** Test: `CalculationFailed` → estimation REJECTED (same pattern)

### Step 7: Cross-Service Smoke Test (Optional — Approach A)

- [ ] **7.1** Create `SagaCrossServiceSmokeTest.java` that spins up estimation-service + customer-service (or at minimum, their Kafka consumers/producers)

- [ ] **7.2** This test depends on having the customer-service on the classpath (add test dependency if needed)

- [ ] **7.3** If cross-service classpath setup is too complex, document it as a manual test or defer to CI pipeline setup

### Step 8: Verify

- [ ] **8.1** Run the E2E tests: `.\gradlew.bat :services:estimation-service:test --tests "*SagaE2ETest*"`
- [ ] **8.2** Verify all tests pass
- [ ] **8.3** Run full estimation service test suite to ensure no regressions: `.\gradlew.bat :services:estimation-service:test`

---

## Files to Create

| File | Purpose |
|------|---------|
| `services/estimation-service/src/test/java/.../estimation/e2e/SagaE2ETest.java` | Main E2E test class |
| `services/estimation-service/src/test/java/.../estimation/e2e/SagaCrossServiceSmokeTest.java` | Optional cross-service test |

## Files to Modify

| File | Change |
|------|--------|
| `services/estimation-service/build.gradle.kts` | Add `spring-kafka-test` test dependency if not present; add `awaitility` test dependency |

## Key Libraries for Tests

Add to `services/estimation-service/build.gradle.kts`:
```kotlin
testImplementation("org.awaitility:awaitility")
testImplementation("org.springframework.kafka:spring-kafka-test")
```

## Test Patterns to Follow

### Awaitility for Async Verification
```java
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

await().atMost(30, SECONDS).untilAsserted(() -> {
    Estimation estimation = estimationRepository.findBySagaId(sagaId).orElseThrow();
    assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.COMPLETED);
});
```

### Publishing Events via KafkaTemplate
```java
@Autowired
private KafkaTemplate<String, String> kafkaTemplate;

// In test:
EventEnvelope envelope = event.toEnvelope(sagaId, traceId);
String json = jsonMapper.writeValueAsString(envelope);
kafkaTemplate.send(EventConstants.ESTIMATION_SAGA, sagaId.toString(), json);
```

### Consuming Events from Kafka
```java
Consumer<String, String> consumer = createConsumer("test-group");
consumer.subscribe(List.of(EventConstants.ESTIMATION_SAGA));
// poll for records, find the one with matching sagaId
```

## Dependencies
- Subtask 1 (Event Schemas) — event POJOs used in test
- Subtask 2 (Message Infrastructure) — topics must be provisioned (or auto-created)
- Subtask 3 (Common Library) — test base classes
- Subtask 5 (DLQ) — for DLQ test section
- Subtask 6 (Distributed Tracing) — for traceId propagation test

## Completion Criteria
- [ ] Happy path test passes: estimation created → COMPLETED with premium
- [ ] Idempotency test passes: duplicate events do not cause side effects
- [ ] Timeout test passes: stale estimation transitions to REJECTED with EstimationFailed
- [ ] Failure path tests pass: *Invalidated and CalculationFailed → REJECTED
- [ ] DLQ/poison message test passes
- [ ] All tests run green in `.\gradlew.bat :services:estimation-service:test`
