# Microservices AGENTS.md

## Overview
Spring Boot 4 microservices using Java 25, Spring Data JPA (Hibernate), and PostgreSQL. Services communicate via Kafka using SAGA choreography.

## Workflow Commands
Building and Testing:
1. Format the code before committing (run spotlessApply, do not bother running spotlessCheck):
   ```bash
   ./gradlew format
   ```
2. Run the tests. This can take a long time so you may prefer to run individual tests:
   ```bash
   ./gradlew test
   ```
3. To run only a single test:
   ```bash
   ./gradlew :services:<service-name>:test --offline --tests "<package.ClassName>"
   ```
4. Build the project:
   ```bash
   ./gradlew clean build
   ```

## Microservice Conventions
- **Spring Boot MVC**: Use `@RestController`, not `@Controller`.
- **API Response Envelope**: Standard `ApiResponse<T>` with `success`, `message`, `data`, `timestamp`.
- **Java 25 Relaxed main**: The `public` modifier on `main(String[])` is no longer required.
- **Datetime Convention**: `java.time.Instant` for timestamps (`createdAt`, `updatedAt`), `java.time.LocalDate` for date-only fields (`birthDate`).
- **Lombok Convention**: Order: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, then JPA annotations.
- **Jackson 3 Usage**: Annotations stay at `com.fasterxml.jackson.annotation`. Only programmatic API classes (`ObjectMapper`, `JsonNode`) need the `tools.jackson` import migration.

## SAGA Consumer Rules
- **Transaction boundaries**: Every SAGA consumer handler that performs more than one database write MUST wrap all writes in a single `TransactionTemplate.executeWithoutResult()`. Never rely on implicit transactions.
- **Atomic dedup**: ALWAYS use `SagaEventRepository.tryInsertDedup()` for idempotency. Never use `existsBySagaIdAndEventType()` followed by `save()`.
- **Dedup requires transaction**: Every call to `tryInsertDedup()` MUST be wrapped in `transactionTemplate.executeWithoutResult()`.
- **In-memory state discipline**: Any in-memory state that must stay consistent with a DB transaction MUST be mutated only AFTER the DB transaction commits (use `TransactionSynchronization.afterCommit()`).

## Outbox & Messaging Rules
- **Check send results**: Always check the boolean return value of `StreamBridge.send()`. Throw an exception if `false` so the outbox retry mechanism handles it.
- **JSON via ObjectMapper only**: Never build JSON strings via concatenation. Always use `jsonMapper.writeValueAsString()`.
- **Binder-level error handling**: Functional `Consumer<String>` beans require binder-level configuration in `application.yml`. `CommonErrorHandler` beans apply ONLY to `@KafkaListener` methods. Never configure both.
- **DLQ consumer must not re-route to the same DLQ**: Use a no-retry, no-DLQ error handler for DLQ consumers to prevent infinite loops.

## DB State Safety Rules
- **Aggregation/correlation store locking**: Any DB-backed store performing read-modify-write cycles MUST acquire a pessimistic lock via `@Lock(PESSIMISTIC_WRITE)` / `SELECT FOR UPDATE`.
- **Event table TTL**: Every table storing transient event data MUST have a cleanup mechanism.
- **Schema DDL must include migration path**: When adding a column, include both `CREATE TABLE IF NOT EXISTS` and `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`.

## Scheduled & Background Task Rules
- **Top-level exception handler required**: Every method invoked by `ScheduledExecutorService` MUST have a top-level try-catch to prevent silent cancellation of future executions.
- **Graceful shutdown**: Every `ScheduledExecutorService` MUST call `awaitTermination()` after `shutdown()` in its `@PreDestroy` method.

## Testing Conventions (Spring Boot 4)
- Always use `RestTestClient`, never `TestRestTemplate` or raw `RestTemplate`.
- Use `@WebMvcTest` for slice tests and `@SpringBootTest(webEnvironment = RANDOM_PORT)` for integration tests.
- Use `@JdbcTypeCode(SqlTypes.JSON)` instead of `@Column(columnDefinition = "JSONB")` for Hibernate 6+ JSON mapping in tests.
- Use module-specific imports for Testcontainers 2.x (e.g., `org.testcontainers.postgresql.PostgreSQLContainer`).
- HTTP Response Assertions: `.jsonPath()`. Domain-Level Assertions: AssertJ `assertThat()`.

## Environment Quirks
- **Testcontainers on Windows**: Add `docker.host=npipe:////./pipe/dockerDesktopLinuxEngine` to `~/.testcontainers.properties` if auto-detection fails.
- **Jackson 2 / Jackson 3 Classpath Conflict**: `bootRun` fails due to Spring Kafka pulling Jackson 2. Use Docker-based startup or `gradlew test` for local development.
