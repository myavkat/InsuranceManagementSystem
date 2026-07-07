# Fix 05 — Dead Code & Library Cleanup

## Status: NOT STARTED
## Parent: Post-Review Fixes (Phase 2 code review, 2026-07-07)
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Resolve the dead-code abstractions discovered during code review. Three new classes were built in Subtask 3 as "canonical" shared abstractions but were never adopted by any service — they have zero callers. Additionally, the `common-test` module has base test classes with zero adopters.

The goal is to either:
1. **Adopt** the abstraction in at least one service (proving it works and establishing the pattern), OR
2. **Remove** the dead code (reducing maintenance surface and avoiding confusion for future developers)

## Context — What Exists and Why It's Dead

### `MessageListener<T>` (100 lines)

Abstract base class that bundles deserialization, MDC setup, dedup checking, and Micrometer Observation instrumentation. Designed to be extended by SAGA consumers instead of writing manual `Consumer<String>` lambdas.

**Zero subclasses.** All 5 SAGA consumers use `@Configuration` classes with `@Bean` methods returning raw `Consumer<String>` lambdas.

**Inconsistency:** `MessageListener` silently swallows poison-pill deserialization failures (`log.error(...); return;`), while the actual SAGA consumers throw `RuntimeException` to route to DLQ. If someone uses `MessageListener`, poison pills are silently lost.

### `SagaContext` (38 lines)

`AutoCloseable` wrapper for MDC context management — sets `sagaId`/`traceId` in MDC, clears on close. Designed for try-with-resources.

**Zero callers.** All consumers manage MDC inline with `MDC.put(...)` / `MDC.clear()` in try-finally blocks.

### `OutboxMessagePublisher` (60 lines)

Convenience class for publishing events via the transactional outbox. Wraps `OutboxEventRepository.save()` + JSON serialization.

**Zero callers.** All SAGA consumers inline their own `buildOutboxEvent()` + `outboxEventRepository.save()` pattern. `EstimationService.saveOutboxEvent()` does the same thing with its own private method.

### `AbstractIntegrationTest` + `AbstractKafkaIntegrationTest`

Base test classes providing shared PostgreSQL (and Kafka) Testcontainers via `@Container` + `@DynamicPropertySource`.

**Zero adopters.** 15+ test classes across services configure `PostgreSQLContainer` individually with the same 8-10 line pattern.

---

## Decision Framework

For each dead-code item, apply this decision matrix:

| Item | Lines | Has runtime bug? | Used by tests? | Consistent with running code? | Decision |
|------|-------|-----------------|---------------|-------------------------------|----------|
| `MessageListener` | 100 | Yes (no transaction, silent poison-pill) | No | No (different poison-pill behavior) | **Remove** — broken and inconsistent |
| `SagaContext` | 38 | No | No | Partially (MDC keys match) | **Remove** — minimal value over inline MDC |
| `OutboxMessagePublisher` | 60 | No (but no null guard on traceId) | No | Yes (wraps existing pattern) | **Keep + adopt** — has value if adopted |
| `AbstractIntegrationTest` | 30 | No | No | Yes (matches conventions) | **Keep + adopt** — migrate 1-2 test classes |
| `AbstractKafkaIntegrationTest` | 25 | No | No | Yes | **Keep + adopt** — migrate the E2E test |

---

## Files to Read Before Starting

1. `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/MessageListener.java` — to be removed
2. `common/common-message/src/main/java/com/insurancemanagementsystem/common/util/SagaContext.java` — to be removed
3. `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/OutboxMessagePublisher.java` — to be kept and adopted
4. `common/common-test/src/main/java/com/insurancemanagementsystem/common/test/AbstractIntegrationTest.java` — to be adopted
5. `common/common-test/src/main/java/com/insurancemanagementsystem/common/test/AbstractKafkaIntegrationTest.java` — to be adopted
6. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java` — candidate to adopt OutboxMessagePublisher
7. `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/e2e/SagaE2ETest.java` — candidate to adopt AbstractKafkaIntegrationTest
8. `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerSagaConsumer.java` — reference for buildOutboxEvent pattern
9. `docs/plans/07_PHASE2_SUBTASK3_COMMON_LIBRARY.md` — original design for these abstractions

---

## Implementation Steps

### Step 1: REMOVE `MessageListener` — Broken and Inconsistent

- [ ] **1.1** Delete the file:
  ```
  common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/MessageListener.java
  ```

  **Reason:**
  - Has a confirmed runtime bug: `tryInsertDedup()` called without transaction (will throw `IllegalTransactionStateException`)
  - Poison-pill behavior is inconsistent with all 5 actual consumers (silent skip vs. throw-to-DLQ)
  - Zero subclasses — removing it breaks nothing
  - The Observation instrumentation it provides is unused; existing consumers have no Micrometer spans anyway
  - If a future task wants a shared consumer abstraction, it should be designed AFTER fixing the transaction and DLQ patterns, and adopted by at least one service before merging

- [ ] **1.2** Verify no imports reference `MessageListener`:
  ```bash
  grep -r "MessageListener" --include="*.java" --include="*.md" --include="*.yml" --include="*.kts"
  ```
  Expected: only the file itself and plan documents. No production code references.

- [ ] **1.3** If any plan documents reference `MessageListener`, update them to note it was removed (add a strikethrough or "REMOVED" note).

### Step 2: REMOVE `SagaContext` — Minimal Utility Value

- [ ] **2.1** Delete the file:
  ```
  common/common-message/src/main/java/com/insurancemanagementsystem/common/util/SagaContext.java
  ```

  **Reason:**
  - Zero callers — removing it breaks nothing
  - Its value over inline `MDC.put()`/`MDC.remove()` is marginal (saves 2 lines per call site)
  - Having both `SagaContext` and inline MDC creates ambiguity about which pattern to use
  - MDC is inherently thread-local — the `AutoCloseable` pattern adds ceremony without thread-safety benefit

- [ ] **2.2** Verify no imports reference `SagaContext`:
  ```bash
  grep -r "SagaContext" --include="*.java" --include="*.md" --include="*.yml" --include="*.kts"
  ```
  Expected: only the file itself and plan documents.

### Step 3: KEEP `OutboxMessagePublisher` + Adopt in EstimationService

`OutboxMessagePublisher` wraps a common pattern (serialize event → save OutboxEvent) that is duplicated in 6+ places across the codebase. It has value if adopted.

- [ ] **3.1** Read the current `EstimationService.saveOutboxEvent()` method (private, ~20 lines). It duplicates exactly what `OutboxMessagePublisher.publish()` does.

- [ ] **3.2** Refactor `EstimationService` to inject and use `OutboxMessagePublisher`:

  **Add field:**
  ```java
  private final OutboxMessagePublisher outboxMessagePublisher;
  ```

  **Replace `saveOutboxEvent()` method:**
  ```java
  // REMOVE the private saveOutboxEvent() method entirely
  // REPLACE the call in create() with:

  outboxMessagePublisher.publish(event, sagaId, traceId, EventConstants.ESTIMATION_SAGA);
  ```

  **Updated `create()` method (relevant section):**
  ```java
  @Transactional
  public EstimationResponse create(EstimationRequest request) {
      if (request.getVehicleId() == null && request.getRealEstateId() == null) {
          throw new IllegalArgumentException("Either vehicleId or realEstateId must be provided");
      }

      UUID sagaId = CorrelationIdGenerator.generateSagaId();
      UUID traceId = CorrelationIdGenerator.generateTraceId();

      Estimation estimation = Estimation.builder()
              .sagaId(sagaId)
              .customerId(request.getCustomerId())
              .vehicleId(request.getVehicleId())
              .realEstateId(request.getRealEstateId())
              .insuranceTypeId(request.getInsuranceTypeId())
              .companyId(request.getCompanyId())
              .traceId(traceId)
              .status(Estimation.Status.STARTED)
              .build();

      estimation = estimationRepository.save(estimation);
      log.info("Created estimation id={} with sagaId={}, traceId={}", estimation.getId(), sagaId, traceId);

      // Publish via shared outbox publisher (replaces inline saveOutboxEvent)
      EstimationRequestedEvent event = EstimationRequestedEvent.builder()
              .customerId(request.getCustomerId())
              .vehicleId(request.getVehicleId())
              .realEstateId(request.getRealEstateId())
              .insuranceTypeId(request.getInsuranceTypeId())
              .companyId(request.getCompanyId())
              .build();

      outboxMessagePublisher.publish(event, sagaId, traceId, EventConstants.ESTIMATION_SAGA);

      return EstimationResponse.fromEntity(estimation);
  }
  ```

  Note: Both `estimationRepository.save()` and `outboxMessagePublisher.publish()` (which calls `outboxEventRepository.save()`) happen inside the same `@Transactional` method — atomicity is preserved ✅.

- [ ] **3.3** Do NOT refactor the other SAGA consumers to use `OutboxMessagePublisher` in this task. One adopter is sufficient to prove the abstraction works. The remaining consumers can be migrated in a follow-up DRY pass.

### Step 4: ADOPT `AbstractKafkaIntegrationTest` in E2E Test

- [ ] **4.1** Refactor `SagaE2ETest.java` to extend `AbstractKafkaIntegrationTest`:

  **Before:**
  ```java
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  @AutoConfigureRestTestClient
  @Testcontainers
  class SagaE2ETest {

      @Container
      static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
              .withDatabaseName("test_estimation_e2e_db")
              .withUsername("test")
              .withPassword("test");

      @Container
      static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
              DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

      @DynamicPropertySource
      static void configureProperties(DynamicPropertyRegistry registry) {
          registry.add("spring.datasource.url", postgres::getJdbcUrl);
          registry.add("spring.datasource.username", postgres::getUsername);
          registry.add("spring.datasource.password", postgres::getPassword);
          registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
          registry.add("spring.cloud.stream.kafka.binder.brokers", kafka::getBootstrapServers);
          // ... additional properties
      }
  ```

  **After:**
  ```java
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  @AutoConfigureRestTestClient
  class SagaE2ETest extends AbstractKafkaIntegrationTest {

      @DynamicPropertySource
      static void configureAdditionalProperties(DynamicPropertyRegistry registry) {
          // Base class already provides: datasource, kafka bootstrap-servers, binder brokers
          registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
          registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "*");
          registry.add("spring.cloud.stream.kafka.binder.configuration.auto.create.topics.enable", () -> true);
          registry.add("spring.kafka.producer.properties.auto.create.topics.enable", () -> true);
          registry.add("estimation.outbox.poll-interval-ms", () -> "600000");
          registry.add("estimation.saga.poll-interval-ms", () -> "600000");
      }
  ```

  **Remove:**
  - `@Testcontainers` annotation (inherited from base class)
  - `@Container` fields for postgres and kafka
  - `configureProperties` method (replaced by `configureAdditionalProperties`)

  **Note:** The base class `AbstractIntegrationTest` uses the default database name `testdb`. The E2E test previously used `test_estimation_e2e_db`. If this matters, override via property. For test isolation (each test class gets its own DB), the default `testdb` is fine because Testcontainers creates a fresh container per JVM.

- [ ] **4.2** Add the `common-test` dependency to `estimation-service/build.gradle.kts` if not already present:

  ```kotlin
  testImplementation(project(":common:common-test"))
  ```

- [ ] **4.3** Verify the E2E test still passes after refactoring:
  ```bash
  .\gradlew.bat :services:estimation-service:test --tests "*SagaE2ETest*"
  ```

### Step 5: Migrate ONE More Test to Prove AbstractIntegrationTest Pattern

- [ ] **5.1** Pick one simple test class that currently sets up `PostgreSQLContainer` individually. A good candidate is `CustomerSagaConsumerTest.java` or `EstimationSagaConsumerTest.java`.

- [ ] **5.2** Refactor it to extend `AbstractIntegrationTest`, following the same pattern as Step 4.

- [ ] **5.3** Verify the test passes. This proves the base class pattern works for non-E2E tests too.

### Step 6: Clean Up Dead E2E Test Helpers

- [ ] **6.1** In `SagaE2ETest.java`, three helper methods are defined but never called:
  - `createTestConsumer()` (lines 213-221)
  - `pollForRecords()` (lines 226-234)
  - `decodeKafkaMessage()` (lines 240-247)

- [ ] **6.2** Check if any test method invokes these helpers. If they're unused:
  - **Option A:** Delete them (clean removal)
  - **Option B:** Wire them into the DLQ test `malformedJson_doesNotBlockConsumer` to verify the poison message ACTUALLY lands in `dlq.saga` (adds test coverage)

  **Recommendation: Option B** — it adds real value. The DLQ test currently only verifies the consumer survives a poison pill; it doesn't verify the message was delivered to DLQ.

  If implementing Option B, the test steps:
  1. Create a test consumer subscribed to `dlq.saga` using `createTestConsumer()`
  2. Publish malformed JSON to `estimation.saga`
  3. Poll `dlq.saga` using `pollForRecords()` with timeout
  4. Verify a record appears in `dlq.saga` with `DLT_ORIGINAL_TOPIC` header = `estimation.saga`
  5. Verify the payload matches the original malformed message using `decodeKafkaMessage()`

  If not implementing Option B (scope/time), at minimum delete the dead helpers to avoid confusion. **Mark this decision in Step 6.3.**

- [ ] **6.3** Decision: __________ (fill in: "Option A — delete helpers" or "Option B — wire into DLQ test")

### Step 7: Build and Verify

- [ ] **7.1** Full build after removals:
  ```bash
  .\gradlew.bat build
  ```

- [ ] **7.2** Verify no compilation errors from deleting `MessageListener` and `SagaContext`:
  ```bash
  .\gradlew.bat :common:common-message:build
  ```

- [ ] **7.3** Run the estimation service tests (affected by OutboxMessagePublisher adoption and base class refactoring):
  ```bash
  .\gradlew.bat :services:estimation-service:test
  ```

- [ ] **7.4** Run full test suite:
  ```bash
  .\gradlew.bat test
  ```

---

## Files to Delete

| File | Reason |
|------|--------|
| `common/common-message/src/main/java/.../messaging/MessageListener.java` | Broken (no transaction, inconsistent poison-pill), zero subclasses |
| `common/common-message/src/main/java/.../util/SagaContext.java` | Zero callers, minimal value over inline MDC |

## Files to Modify

| File | Change |
|------|--------|
| `services/estimation-service/.../service/EstimationService.java` | Inject `OutboxMessagePublisher`, replace `saveOutboxEvent()` with `outboxMessagePublisher.publish()` |
| `services/estimation-service/src/test/java/.../e2e/SagaE2ETest.java` | Extend `AbstractKafkaIntegrationTest`, remove duplicate container setup |
| `services/estimation-service/build.gradle.kts` | Add `testImplementation(project(":common:common-test"))` if not present |
| One additional test class (TBD) | Extend `AbstractIntegrationTest` to prove the pattern |

## Files to Optionally Modify

| File | Change |
|------|--------|
| `services/estimation-service/src/test/java/.../e2e/SagaE2ETest.java` | Wire dead helpers into DLQ test OR delete them (Step 6 decision) |

---

## Dependencies

- None (standalone fix)
- Note: `OutboxMessagePublisher` adoption requires `EstimationService` refactoring — ensure Fix 01 (transaction boundaries) is applied first or concurrently to avoid conflicts

## Completion Criteria

- [ ] `MessageListener.java` deleted — no compilation errors, no broken imports
- [ ] `SagaContext.java` deleted — no compilation errors, no broken imports
- [ ] `EstimationService` uses `OutboxMessagePublisher.publish()` instead of private `saveOutboxEvent()`
- [ ] `SagaE2ETest` extends `AbstractKafkaIntegrationTest` — duplicate container setup removed
- [ ] At least one additional test class extends `AbstractIntegrationTest`
- [ ] Dead E2E test helpers either wired into DLQ test or deleted
- [ ] `.\gradlew.bat build` passes for all modules
- [ ] `.\gradlew.bat test` passes — zero regressions
