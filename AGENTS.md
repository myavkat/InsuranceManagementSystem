# AGENTS.md - InsuranceManagementSystem

## Global Execution Constraints

### Context Precedence
When working on features, prioritize guidelines in this order:
1. Local Constraints (@AGENTS.LOCAL.md — if present in active memory)
2. Project Constraints (This file)
3. Workspace Context & Workflow Handling (`docs/AGENTS.md`)

### Operational Rules
- ALWAYS look into `docs/plans/` for the active feature plan before writing code.
- BEFORE executing any workspace actions, read and adhere to the directory workflow defined in `docs/AGENTS.md`.
- If a model swap occurred, verify state by cross-referencing code against the active plan file.
- DO NOT invent dependencies or refactor out-of-scope modules.
- **Commit message convention:** Use descriptive, topic-based commit headers with conventional commit prefixes (e.g., `feat(scope):`, `fix(scope):`, `docs:`, `test(scope):`, `refactor(scope):`, `chore:`). Never use opaque codenames, section numbers, or ticket IDs alone as the commit subject — the header must describe what changed, not reference external tracking. Commit each logical topic separately (topic-by-topic), not as a batch of unrelated changes.
- **No auto-attribution:** Never include `Co-Authored-By` trailers or any other auto-attribution lines in commit messages. Only the actual human author's identity belongs in commits.
- **Environment variable documentation:** Every `${ENV_VAR:default}` placeholder referenced in any `application.yml` MUST have a corresponding entry in `.env.template` (repo root). The `.env.template` file is the single source of truth for all configurable environment variables. A variable referenced in config but missing from the template silently falls back to its inline default, making it invisible to operators who customize the deployment environment.

### SAGA Consumer Rules
- **Transaction boundaries:** Every SAGA consumer handler that performs more than one database write MUST wrap all writes in a single `TransactionTemplate.executeWithoutResult()` (or `@Transactional`). Never rely on `JpaRepository` implicit transactions — they commit independently and break atomicity between business state and outbox event persistence.
- **Atomic dedup:** ALWAYS use `SagaEventRepository.tryInsertDedup()` for idempotency. Never use `existsBySagaIdAndEventType()` followed by `save()` — it has a TOCTOU race under concurrent Kafka delivery. The `tryInsertDedup()` method (atomic INSERT with UNIQUE-constraint catch) is the single canonical pattern.
- **Dedup requires transaction:** Every call to `SagaEventRepository.tryInsertDedup()` MUST be wrapped in `transactionTemplate.executeWithoutResult()` or an active `@Transactional` context. The underlying `insertDedupMarker()` has `@Transactional(propagation = Propagation.MANDATORY)` — calling it without an active transaction throws `IllegalTransactionStateException`, routing the event to DLQ and losing the dedup marker. This applies to ALL handlers, including log-only ones like `handleEstimationFailed`. The `TransactionTemplate` field already injected into every consumer is the canonical wrapper.
- **In-memory state discipline:** Any in-memory state that must stay consistent with a DB transaction MUST be mutated only AFTER the DB transaction commits (use `TransactionSynchronization.afterCommit()`), or be stored in the DB itself. Never call a destructive in-memory operation (`remove`, `clear`, `retrieve`) inside a `TransactionTemplate` callback — a rollback cannot undo it.

### Outbox & Messaging Rules
- **Check send results:** Always check the boolean return value of `StreamBridge.send()`. A `false` return means the message was NOT accepted by the broker. Throw an exception so the outbox retry mechanism can handle it.
- **JSON via ObjectMapper only:** Never build JSON strings via concatenation (`"{\\"key\\":\\"" + value + "\\"}"`). Always use `jsonMapper.writeValueAsString()`. If serialization can fail, catch and use a properly-escaped fallback.
- **Binder-level error handling for functional bindings:** Spring Cloud Stream functional `Consumer< String >` beans (SAGA consumers) do NOT use `CommonErrorHandler` beans from the application context — they require binder-level configuration in `application.yml` (`spring.cloud.stream.kafka.bindings.<name>-in-0.consumer.enableDlq` and `retry.*`). A `CommonErrorHandler` bean (like `DefaultErrorHandler` with `DeadLetterPublishingRecoverer`) applies ONLY to `@KafkaListener`-annotated methods. Never configure both mechanisms on overlapping bindings — the interaction is untested and can cause double-DLQ or message loss.
- **DLQ consumer must not re-route to the same DLQ:** Any consumer reading from a DLQ topic (e.g., `DlqMonitor` on `dlq.saga`) MUST use a no-retry, no-DLQ error handler. If the DLQ consumer itself throws and its error handler re-publishes to the same DLQ topic, the message loops forever. Use a dedicated `KafkaListenerContainerFactory` with a no-op `CommonErrorHandler` for DLQ consumers.

### Cross-Service Code Sharing
- **Extract before triplicating:** Before adding the same class to a 3rd service module, extract it to `common-message`. If it already exists in 2 services, the next change must be extraction, not copy-paste.
- **Propagate trace context:** All outbound SAGA events must carry the original `traceId` from the triggering `EventEnvelope`. Never generate a fresh `UUID.randomUUID()` for outbound traceId — it breaks end-to-end observability.
- **New abstractions require adoption before completion:** Any new shared class added to `common-message` (listener base classes, context holders, publisher wrappers, test base classes) MUST be adopted by at least one service before the task is marked complete. A library abstraction with zero callers is dead code — it creates a misleading API surface, rots silently (no tests exercise it), and confuses future developers about which pattern to use. If no service will adopt the abstraction in the current phase, don't add it yet.
- **Event field additions require producer updates:** When adding fields to event POJOs in `common-message`, the plan checklist MUST include a step to update all producers (event publishers in each service) that publish those event types. A new field with no producer populating it silently publishes `null` to every downstream consumer.

### DB State Safety Rules
- **Aggregation/correlation store locking:** Any DB-backed store that performs read-modify-write cycles on a shared row (load → mutate field → save) MUST acquire a pessimistic lock on the initial read via `@Lock(PESSIMISTIC_WRITE)` / `SELECT FOR UPDATE`. Plain `findById()` + `save()` loses data under multi-instance concurrency when the entity lacks `@DynamicUpdate` — Hibernate writes all columns, including stale nulls from the pre-mutation snapshot.
- **Event table TTL:** Every table that stores transient event data (dedup markers, aggregation state, outbox events) MUST have a cleanup mechanism — a scheduled DELETE by age, a TTL index, or application-level eviction. Migrations from in-memory stores to DB tables must preserve the original TTL invariant. A table without cleanup is a slow disk-exhaustion bug.
- **Schema DDL must include migration path:** When adding a column to an existing table, the SQL init script MUST include an `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` statement in addition to the `CREATE TABLE IF NOT EXISTS` column definition. The `CREATE TABLE IF NOT EXISTS` statement is skipped if the table already exists from a prior deployment — without the `ALTER TABLE`, Hibernate's `ddl-auto: validate` fails on restart because the entity expects a column that doesn't exist in the live schema. Every column addition needs both paths: fresh-db (CREATE) and existing-db (ALTER).

### Scheduled & Background Task Rules
- **Top-level exception handler required:** Every method invoked by `ScheduledExecutorService.scheduleWithFixedDelay()` (or `scheduleAtFixedRate()`) MUST have a top-level try-catch that logs the error and prevents it from propagating. An unhandled exception reaching the executor silently cancels ALL future executions of that task — the relay or poller dies permanently with no alert. The per-item loop body catching exceptions is NOT sufficient; the repository queries and transaction template calls outside the loop also need coverage.
- **Graceful shutdown:** Every `ScheduledExecutorService` created by application code MUST call `awaitTermination()` after `shutdown()` in its `@PreDestroy` method. Without it, in-flight tasks are interrupted mid-write during pod termination, leaving database rows in indeterminate states (e.g., `PUBLISHING` zombie outbox events).

### JSON Serialization Rules
- **Fallback must produce valid JSON:** When `jsonMapper.writeValueAsString()` fails and a string-construction fallback is used, the fallback MUST produce valid JSON under all inputs. `.replace("\"", "\\\"")` is insufficient — it doesn't escape backslashes, newlines, or control characters. Use a nested try-catch with `JsonMapper.builder().build().writeValueAsString()`, or extract a shared `JsonUtils.safeSerialize()` utility. A fallback that produces malformed JSON silently corrupts the `details` JSONB column.

## Architecture & Convention Index

All technical decisions and conventions live in `docs/outlines/`. Consult the relevant outline before implementing any feature.

### Core Architecture
| Outline | Content |
|---------|---------|
| [`01_SYSTEM_ARCHITECTURE.md`](docs/outlines/01_SYSTEM_ARCHITECTURE.md) | Tech stack, microservice breakdown, communication architecture, key architectural rules |
| [`02_MICROSERVICES_SPECIFICATIONS.md`](docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md) | Per-service entities, endpoints, SAGA consumers |
| [`07_PROJECT_STRUCTURE.md`](docs/outlines/07_PROJECT_STRUCTURE.md) | Directory layout, technology stack per layer, build order |

### Patterns & Communication
| Outline | Content |
|---------|---------|
| [`03_SAGA_PATTERN.md`](docs/outlines/03_SAGA_PATTERN.md) | SAGA choreography flow, event catalog, idempotency, timeout, compensation |
| [`04_MESSAGE_QUEUE_TOPOLOGY.md`](docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md) | Kafka topics, partitions, retention, per-service consumer configuration |
| [`06_API_GATEWAY_AUTH.md`](docs/outlines/06_API_GATEWAY_AUTH.md) | Gateway routing, filter chain, rate limiting, auth service |

### Frontend
| Outline | Content |
|---------|---------|
| [`05_NEXTJS_FRONTEND.md`](docs/outlines/05_NEXTJS_FRONTEND.md) | Next.js App Router architecture, BFF pattern, component structure, data flow |

### Conventions & Operations
| Outline | Content |
|---------|---------|
| [`10_JAVA_CONVENTIONS.md`](docs/outlines/10_JAVA_CONVENTIONS.md) | Java 21+ relaxed main, datetime conventions (Instant/LocalDate), Lombok order, Jackson 3 usage |
| [`11_TESTING_CONVENTIONS.md`](docs/outlines/11_TESTING_CONVENTIONS.md) | Spring Boot 4 testing rules (RestTestClient, slice/integration tests, assertions) |
| [`12_DEVELOPER_COMMANDS.md`](docs/outlines/12_DEVELOPER_COMMANDS.md) | Build, run, test commands for all subsystems |
| [`13_ENVIRONMENT_QUIRKS.md`](docs/outlines/13_ENVIRONMENT_QUIRKS.md) | .gitignore, defaults, IntelliJ quirks, port allocation, Jackson conflict |
| [`14_EVENT_SCHEMA_REGISTRY.md`](docs/outlines/14_EVENT_SCHEMA_REGISTRY.md) | Event schemas, EventType constants, payload fields, producer/consumer index |
