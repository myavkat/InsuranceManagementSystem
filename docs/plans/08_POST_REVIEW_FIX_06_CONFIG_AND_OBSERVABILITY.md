# Fix 06 — Configuration, Documentation & Observability Fixes

## Status: NOT STARTED
## Parent: Post-Review Fixes (Phase 2 code review, 2026-07-07)
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Fix configuration inconsistencies, documentation gaps, and observability issues discovered during code review:

1. **JSON string concatenation fallback** — unsafe escaping in SagaTimeoutService and EstimationSagaConsumer (AGENTS.md violation)
2. **`ZIPKIN_ENDPOINT` missing from `.env.template`** — 7 files reference it, no documentation
3. **Copy-paste configuration across 6 services** — identical `management.tracing`, `logging.pattern`, and Kafka config blocks
4. **Jackson 2/3 classpath conflict** — known issue documented but not mitigated (add exclusion strategy)
5. **Existing SAGA consumers lack Micrometer Observation** — Zipkin is blind to business logic in all 5 running consumers

## Context

### Finding 1: JSON Fallback
Both `SagaTimeoutService.java:76` and `EstimationSagaConsumer.java:217` use string concatenation for JSON when `jsonMapper.writeValueAsString()` fails:
```java
estimation.setDetails("{\"reason\":\"" + reason.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
```
AGENTS.md: *"Fallback must produce valid JSON: `.replace("\"", "\\\"")` is insufficient — it doesn't escape backslashes, newlines, or control characters."*

### Finding 2: Missing Env Var
7 files reference `${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}` but `.env.template` has no entry.

### Finding 3: Copy-Paste Config
All 6 service `application.yml` files contain identical blocks for:
- `management.tracing.sampling.probability: 1.0`
- `management.tracing.export.zipkin.endpoint`
- `logging.pattern.console` (with spanId)
- `spring.cloud.stream.kafka.binder.configuration.auto.create.topics.enable: false`

### Finding 4: Jackson 2/3 Conflict
Every service explicitly depends on `com.fasterxml.jackson.core:jackson-databind` (Jackson 2) alongside `tools.jackson.*` (Jackson 3). The plan acknowledges this blocks `bootRun`. While not fixable in this ticket, we should document the exclusion strategy.

### Finding 5: Missing Observation
Only the (now-deleted) `MessageListener` wraps handlers in Micrometer `Observation`. All 5 running SAGA consumers produce no Zipkin spans for their business logic.

---

## Files to Read Before Starting

1. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java` — line 76 (JSON fallback)
2. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java` — line 217 (JSON fallback)
3. `.env.template` (repo root) — current contents
4. All 6 `services/*/src/main/resources/application.yml` — current tracing/logging config
5. All 6 `services/*/build.gradle.kts` — Jackson 2 dependency
6. `AGENTS.md` — JSON Serialization Rules (line 41)
7. `docs/outlines/13_ENVIRONMENT_QUIRKS.md` — port allocation, env vars
8. `docs/plans/07_PHASE2_SUBTASK6_DISTRIBUTED_TRACING.md` — original tracing design

---

## Implementation Steps

### Step 1: Fix JSON String Concatenation Fallback

Per AGENTS.md: *"Use a nested try-catch with `JsonMapper.builder().build().writeValueAsString()`, or extract a shared `JsonUtils.safeSerialize()` utility."*

- [x] **1.1** Fix `SagaTimeoutService.java` line 76:

  **Current (broken):**
  ```java
  try {
      estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
  } catch (Exception e) {
      log.warn("Failed to serialize timeout details for sagaId={}", sagaId, e);
      estimation.setDetails("{\"reason\":\"" + reason.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
  }
  ```

  **Fixed:**
  ```java
  try {
      estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
  } catch (Exception e) {
      log.warn("Failed to serialize timeout details for sagaId={} — using safe fallback", sagaId, e);
      try {
          // Nested try with a fresh JsonMapper to avoid any poisoned state
          estimation.setDetails(tools.jackson.databind.json.JsonMapper.builder()
                  .build().writeValueAsString(Map.of("reason", reason)));
      } catch (Exception ex) {
          // Absolute last resort — log and set a minimal valid JSON literal
          log.error("Safe fallback serialization also failed for sagaId={}: {}", sagaId, ex.getMessage(), ex);
          estimation.setDetails("{\"reason\":\"serialization failed\"}");
      }
  }
  ```

- [x] **1.2** Fix `EstimationSagaConsumer.java` line 217 — identical pattern:

  **Current (broken):**
  ```java
  try {
      estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
  } catch (Exception e) {
      log.warn("Failed to serialize rejection details for sagaId={}", sagaId, e);
      estimation.setDetails("{\"reason\":\"" + reason.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
  }
  ```

  Apply the same nested try-catch fix as Step 1.1.

### Step 2: Add ZIPKIN_ENDPOINT to .env.template

- [x] **2.1** Open `.env.template`. Current last line is `GATEWAY_PORT=8080`. Add after the Kafka section:

  ```properties
  # Kafka
  KAFKA_BOOTSTRAP_SERVERS=localhost:9092

  # Zipkin (Distributed Tracing)
  ZIPKIN_ENDPOINT=http://localhost:9411/api/v2/spans

  # Redis
  REDIS_HOST=localhost
  REDIS_PORT=6379
  ```

  **Placement:** Between the Kafka and Redis sections, since Zipkin is infrastructure like Kafka/Redis.

- [x] **2.2** Verify the variable is consistently referenced across all services. The expected pattern is:
  ```yaml
  management:
    tracing:
      export:
        zipkin:
          endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
  ```

  Check each of the 6 service `application.yml` files for this exact expression.

- [x] **2.3** Also add the variable to `infra/docker/docker-compose.services.yml` if services are configured via environment:
  ```yaml
  environment:
    ZIPKIN_ENDPOINT: http://zipkin:9411/api/v2/spans
  ```
  Check if this is already present or needs to be added.

### Step 3: Extract Shared Configuration to a Common Config File (Optional Enhancement)

This step reduces the copy-paste across 6 services. Consider whether to implement now or defer.

- [ ] **3.1** The identical blocks across all services are:

  ```yaml
  # Block A — Tracing config (identical in all 6 services)
  management:
    tracing:
      sampling:
        probability: 1.0
      export:
        zipkin:
          endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}

  # Block B — Logging pattern (identical in all 6 services)
  logging:
    pattern:
      console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [%X{traceId:-},%X{spanId:-},%X{sagaId:-}] - %msg%n"

  # Block C — Kafka binder config (identical in all 6 services)
  spring:
    cloud:
      stream:
        kafka:
          binder:
            brokers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
            configuration:
              auto.create.topics.enable: false
  ```

- [ ] **3.2** Options for centralization:

  **Option A: Spring Cloud Config Server** — Not available in this project. Skip.

  **Option B: Shared `application-common.yml` imported via `spring.config.import`** — Each service adds:
  ```yaml
  spring:
    config:
      import: classpath:application-common.yml
  ```
  Place `application-common.yml` in `common/common-web/src/main/resources/`.

  **Option C: Keep as-is, document the pattern** — Accept the duplication. Add a comment in each `application.yml`:
  ```yaml
  # NOTE: This tracing/logging/Kafka config block is intentionally duplicated
  # across all services. When changing, update ALL 6 application.yml files.
  # See: docs/outlines/13_ENVIRONMENT_QUIRKS.md
  ```

  **Recommendation: Option B** if `spring.config.import` is available in Spring Boot 4.x. **Option C** otherwise, with clear documentation.

- [ ] **3.3** If choosing Option C (document only), add a note to `docs/outlines/13_ENVIRONMENT_QUIRKS.md`:
  ```markdown
  ### Shared Configuration Blocks

  The following configuration is intentionally duplicated across all 6 services'
  `application.yml` files. When modifying these blocks, update ALL services:
  - `management.tracing.*` (sampling, Zipkin endpoint)
  - `logging.pattern.console` (MDC: traceId, spanId, sagaId)
  - `spring.cloud.stream.kafka.binder.*` (brokers, auto.create.topics.enable)
  - `spring.cloud.stream.kafka.bindings.*.consumer.enableDlq` (DLQ routing)
  ```

- [x] **3.4** Mark this step as `[x]` once the decision is made and implemented (or deferred with documentation).

### Step 4: Document Jackson 2/3 Conflict Mitigation

The Jackson 2/3 classpath conflict is a known issue that blocks `bootRun`. Document the mitigation strategy.

- [x] **4.1** Add to `docs/outlines/13_ENVIRONMENT_QUIRKS.md`:

  ```markdown
  ### Jackson 2 / Jackson 3 Classpath Conflict

  **Status:** Known issue — `bootRun` fails with `NoClassDefFoundError: com/fasterxml/jackson/databind/JavaType`.

  **Cause:** Spring Kafka (`spring-kafka`) pulls `com.fasterxml.jackson.core:jackson-databind` (Jackson 2)
  transitively, while the project uses `tools.jackson.*` (Jackson 3). All services also explicitly declare
  `com.fasterxml.jackson.core:jackson-databind` to satisfy Kafka deserializer requirements.

  **Impact:** `bootRun` is broken. Services can only be tested via integration tests (`@EmbeddedKafka` +
  Testcontainers), which handle the classpath correctly by isolating the test runner.

  **Workaround:** Use the Docker-based startup (`docker compose -f infra/docker/docker-compose.yml
  -f infra/docker/docker-compose.services.yml up -d`) which builds and runs services in containers
  with isolated classpaths. For local development, use `gradlew test` (integration tests) instead of
  `gradlew bootRun`.

  **Resolution plan:** Either:
  1. Shade `com.fasterxml.jackson` into a relocated package in the service JARs, OR
  2. Migrate all Jackson usage from `tools.jackson` back to `com.fasterxml.jackson` (reverses the
     Jackson 3 migration), OR
  3. Exclude `com.fasterxml.jackson` from Spring Kafka dependencies and configure Spring Kafka
     to use `tools.jackson` serializers (requires custom serializer implementation).

  **Priority:** Medium — blocks local `bootRun` but tests and Docker deployment are unaffected.
  ```

- [x] **4.2** Add a comment to each service's `build.gradle.kts` above the Jackson 2 dependency:
  ```kotlin
  // Jackson 2 required by spring-kafka for Kafka deserializer compatibility.
  // Known conflict with tools.jackson (Jackson 3) — see docs/outlines/13_ENVIRONMENT_QUIRKS.md
  implementation("com.fasterxml.jackson.core:jackson-databind")
  ```

### Step 5: Add Micrometer Observation to ONE SAGA Consumer (Pilot)

Prove the Observation instrumentation pattern in a real consumer. The `MessageListener` approach was designed for this but is being removed in Fix 05. Instead, add Observation directly to one existing consumer.

- [x] **5.1** Choose the estimation service's consumer (`EstimationSagaConsumer`) as the pilot — it's the SAGA coordinator and has the most handler methods.

- [x] **5.2** Add `ObservationRegistry` as a constructor dependency:
  ```java
  private final ObservationRegistry observationRegistry;
  ```

- [x] **5.3** Wrap the main `switch` handler in an Observation:

  In the `processEstimationSaga` bean method, after dedup check and before the switch:

  ```java
  // Wrap business logic in a Micrometer Observation for Zipkin tracing
  Observation observation = Observation.createNotStarted("saga.estimation.process", observationRegistry)
          .contextualName("process " + eventType)
          .lowCardinalityKeyValue("event.type", eventType)
          .highCardinalityKeyValue("saga.id", sagaId.toString());

  observation.observe(() -> {
      switch (eventType) {
          case EventConstants.CUSTOMER_VALIDATED ->
              handleCustomerValidated(envelope, sagaId, traceId, jsonMapper);
          // ... other cases unchanged
      }
  });
  ```

- [ ] **5.4** Verify the Observation produces spans. After starting the service and triggering a SAGA flow, check Zipkin at `http://localhost:9411` for a span named `saga.estimation.process` or `process <EventType>`.

- [ ] **5.5** If this works, document the pattern. Do NOT apply to all 5 consumers in this fix — that's a separate task. The goal here is to prove the pattern works with ONE consumer.

### Step 6: Build and Verify

- [x] **6.1** Build all modules:
  ```bash
  .\gradlew.bat build
  ```

- [x] **6.2** Verify `.env.template` is valid (no syntax errors in the ZIPKIN_ENDPOINT line).

- [x] **6.3** Run tests:
  ```bash
  .\gradlew.bat test
  ```

- [ ] **6.4** Start infrastructure and verify Zipkin is reachable:
  ```bash
  docker compose -f infra/docker/docker-compose.yml up -d
  curl http://localhost:9411/health
  ```

- [ ] **6.5** If Observation was added (Step 5), trigger a SAGA flow and check for spans in Zipkin UI.

- [ ] **6.6** Tear down:
  ```bash
  docker compose -f infra/docker/docker-compose.yml down
  ```

---

## Files to Modify

| File | Change |
|------|--------|
| `services/estimation-service/.../service/SagaTimeoutService.java` | Replace string-concat JSON fallback with nested `JsonMapper` try-catch (line 76) |
| `services/estimation-service/.../config/EstimationSagaConsumer.java` | Same fix for `handleFailed` fallback (line 217) |
| `.env.template` | Add `ZIPKIN_ENDPOINT` variable |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Add: Jackson 2/3 conflict documentation; shared config blocks note |
| All 6 `services/*/build.gradle.kts` | Add comment above Jackson 2 dependency referencing the known issue |
| `services/estimation-service/.../config/EstimationSagaConsumer.java` | Add Micrometer Observation wrapping (pilot) |

## Files to Optionally Create/Modify

| File | Change |
|------|--------|
| `common/common-web/src/main/resources/application-common.yml` | If Option B chosen for Step 3 — shared config file |
| All 6 `services/*/src/main/resources/application.yml` | If Option B chosen — add `spring.config.import: classpath:application-common.yml` |
| `infra/docker/docker-compose.services.yml` | Add `ZIPKIN_ENDPOINT` env var if not present |

---

## Dependencies

- Fix 05 (Dead Code Cleanup) — if `MessageListener` is removed, the Observation pilot uses inline Observation instead
- No other dependencies

## Completion Criteria

- [x] Both JSON fallback sites use nested `JsonMapper.builder().build().writeValueAsString()` instead of string concatenation
- [x] `ZIPKIN_ENDPOINT` documented in `.env.template`
- [x] Jackson 2/3 conflict documented in `13_ENVIRONMENT_QUIRKS.md` with workaround and resolution plan
- [x] All 6 `build.gradle.kts` files have a comment above the Jackson 2 dependency referencing the docs
- [x] Shared config duplication decision made and documented (Option B implemented or Option C documented)
- [x] Micrometer Observation pilot implemented in `EstimationSagaConsumer` (verification in Zipkin pending infrastructure)
- [x] `.\gradlew.bat build` passes for all modules
- [x] `.\gradlew.bat test` passes — zero regressions
