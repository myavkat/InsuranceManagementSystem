# Fix 04 — Database Schema Migration & Event Table Cleanup

## Status: NOT STARTED
## Parent: Post-Review Fixes (Phase 2 code review, 2026-07-07)
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Fix two database-related defects discovered during code review:

1. **`trace_id` column has no migration path** — `CREATE TABLE IF NOT EXISTS` doesn't add the column to existing databases. With `ddl-auto: validate`, Hibernate fails on restart.
2. **`saga_events` table has no TTL/cleanup** — AGENTS.md requires: *"Every table that stores transient event data (dedup markers, aggregation state, outbox events) MUST have a cleanup mechanism."* Kafka topics have retention; outbox has TTL; `saga_events` has nothing.

## Context — The Migration Problem

The `Estimation` entity gained a `traceId` field (Subtask 6). The `init.sql` script was updated:

```sql
CREATE TABLE IF NOT EXISTS estimations (
    -- ... existing columns ...
    trace_id UUID,        -- ✅ NEW COLUMN
    -- ...
);
```

`CREATE TABLE IF NOT EXISTS` only creates the table if it doesn't exist. If the table ALREADY exists (from a prior `docker compose up`), the statement is skipped entirely — the `trace_id` column is never added.

Services use `ddl-auto: validate` (standard for production). Hibernate compares the entity fields against the live schema and fails with:

```
Schema-validation: missing column [trace_id] in table [estimations]
```

The only recovery path today is dropping the PostgreSQL volume (`docker compose down -v`), which destroys ALL data.

## Context — The Cleanup Problem

The `saga_events` table stores dedup markers — one row per `(saga_id, event_type)` pair. This is transient data: once a SAGA completes (or times out), the dedup markers serve no further purpose. They grow unboundedly.

Contrast with other transient tables:
- **Kafka topics:** 7-day or 30-day retention (configured in `create-topics.sh`)
- **outbox_events:** `cleanupEvents()` in `OutboxProcessor` deletes `PUBLISHED` events older than 5 minutes and `FAILED` events older than `failed-ttl-minutes` (60 min)
- **saga_aggregations** (insurance-service): consumed immediately (retrieve → delete in same transaction)
- **saga_events:** NO CLEANUP — rows accumulate forever

AGENTS.md: *"A table without cleanup is a slow disk-exhaustion bug."*

## Files to Read Before Starting

1. `infra/sql/estimation_db/init.sql` — current schema (trace_id at line 11)
2. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java` — entity with traceId field
3. `common/common-message/src/main/java/com/insurancemanagementsystem/common/entity/SagaEvent.java` — the dedup entity
4. `common/common-message/src/main/java/com/insurancemanagementsystem/common/repository/SagaEventRepository.java` — existing repository methods
5. `common/common-message/src/main/java/com/insurancemanagementsystem/common/config/OutboxProcessor.java` — `cleanupEvents()` as reference pattern
6. `AGENTS.md` — DB State Safety Rules: Event table TTL (line 34)
7. `infra/sql/*/init.sql` — check ALL database init scripts for `saga_events` table definitions

---

## Implementation Steps

### Step 1: Add ALTER TABLE Migration for trace_id

- [ ] **1.1** Open `infra/sql/estimation_db/init.sql`. After the `CREATE TABLE IF NOT EXISTS estimations` block, add:

  ```sql
  -- Migration: add trace_id column for databases created before Subtask 6
  -- (CREATE TABLE IF NOT EXISTS skips the column if the table already exists)
  ALTER TABLE estimations ADD COLUMN IF NOT EXISTS trace_id UUID;
  ```

  **Note:** `ADD COLUMN IF NOT EXISTS` is PostgreSQL 9.6+ syntax. The project uses PostgreSQL 16, so this is safe.

  **Placement:** Add this line right after the `CREATE INDEX IF NOT EXISTS` statements (after line 30, before the `outbox_events` table).

  Full context:
  ```sql
  CREATE TABLE IF NOT EXISTS estimations (
      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      saga_id UUID UNIQUE NOT NULL,
      customer_id UUID,
      vehicle_id UUID,
      real_estate_id UUID,
      insurance_type_id INT,
      company_id UUID,
      trace_id UUID,
      status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'COMPLETED', 'REJECTED')),
      premium DECIMAL(12,2),
      details JSONB,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );

  CREATE INDEX IF NOT EXISTS idx_estimations_saga ON estimations(saga_id);
  CREATE INDEX IF NOT EXISTS idx_estimations_customer ON estimations(customer_id);
  CREATE INDEX IF NOT EXISTS idx_estimations_status ON estimations(status);
  CREATE INDEX IF NOT EXISTS idx_estimations_created ON estimations(created_at);

  -- Migration: add trace_id column for databases created before Subtask 6
  ALTER TABLE estimations ADD COLUMN IF NOT EXISTS trace_id UUID;

  CREATE TABLE IF NOT EXISTS saga_events (
      -- ...
  );
  ```

### Step 2: Add TTL Cleanup for saga_events Table

There are two approaches. **Choose Approach A** (application-level scheduled cleanup, consistent with outbox cleanup pattern).

#### Approach A: Application-Level Scheduled Cleanup

Create a scheduled task in the `common-message` module that deletes old dedup markers.

- [ ] **2.1** Add a cleanup method to `SagaEventRepository`:

  ```java
  /**
   * Delete dedup markers older than the specified cutoff.
   * SAGA workflows complete within minutes; dedup markers older than
   * the retention period are no longer needed for idempotency.
   *
   * @param cutoff delete rows with received_at before this time
   * @return number of deleted rows
   */
  @Modifying
  @Query(value = "DELETE FROM saga_events WHERE received_at < :cutoff", nativeQuery = true)
  int deleteByReceivedAtBefore(@Param("cutoff") Instant cutoff);
  ```

  The default retention should match the outbox TTL (60 minutes). This is generous — most SAGAs complete or time out within 5 minutes.

- [ ] **2.2** Create a `SagaEventCleanupService` in `common-message`:

  File: `common/common-message/src/main/java/com/insurancemanagementsystem/common/config/SagaEventCleanupService.java`

  ```java
  package com.insurancemanagementsystem.common.config;

  import com.insurancemanagementsystem.common.repository.SagaEventRepository;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.scheduling.annotation.Scheduled;
  import org.springframework.stereotype.Component;
  import org.springframework.transaction.support.TransactionTemplate;

  import java.time.Instant;
  import java.time.temporal.ChronoUnit;

  /**
   * Scheduled cleanup for the saga_events dedup table.
   * <p>
   * Dedup markers are transient — once a SAGA completes or times out,
   * the markers serve no further purpose. This scheduled task deletes
   * markers older than the configured retention period to prevent
   * unbounded table growth (disk-exhaustion per AGENTS.md).
   * <p>
   * Default retention: 60 minutes (configurable via property).
   * Runs every 10 minutes.
   */
  @Component
  @RequiredArgsConstructor
  @Slf4j
  public class SagaEventCleanupService {

      private final SagaEventRepository sagaEventRepository;
      private final TransactionTemplate transactionTemplate;

      @Value("${saga-event.cleanup.retention-minutes:60}")
      private int retentionMinutes;

      /**
       * Delete dedup markers older than retentionMinutes.
       * Top-level try-catch prevents silent scheduler cancellation
       * on transient database errors (AGENTS.md requirement).
       */
      @Scheduled(fixedDelayString = "${saga-event.cleanup.interval-ms:600000}") // 10 minutes
      public void cleanupOldDedupMarkers() {
          try {
              Instant cutoff = Instant.now().minus(retentionMinutes, ChronoUnit.MINUTES);
              int deleted = transactionTemplate.execute(status ->
                  sagaEventRepository.deleteByReceivedAtBefore(cutoff)
              );
              if (deleted > 0) {
                  log.info("Cleaned up {} saga_events dedup markers older than {} minutes",
                          deleted, retentionMinutes);
              }
          } catch (Exception e) {
              log.error("SagaEventCleanupService.cleanupOldDedupMarkers() failed — "
                      + "scheduler will retry on next tick", e);
              // Do NOT re-throw — AGENTS.md: prevents silent scheduler cancellation
          }
      }
  }
  ```

- [ ] **2.3** Register this service in the component scan. Since it's under `com.insurancemanagementsystem.common.config` and uses `@Component`, and services scan `com.insurancemanagementsystem.common`, it will be auto-detected.

- [ ] **2.4** Optional — add configuration defaults to `CommonPersistenceAutoConfiguration` or document the properties:

  ```properties
  # saga_events dedup marker cleanup (default: 60 min retention, runs every 10 min)
  saga-event.cleanup.retention-minutes=60
  saga-event.cleanup.interval-ms=600000
  ```

#### Approach B: Database-Level TTL (Alternative)

Use a PostgreSQL pg_cron job or a TTL index extension. More robust but requires PostgreSQL extension setup. **Not recommended for this fix** — the application-level approach is simpler and consistent with the existing `OutboxProcessor.cleanupEvents()` pattern.

### Step 3: Verify saga_events Table Exists in ALL Database Init Scripts

- [ ] **3.1** Check every `infra/sql/*/init.sql` file for a `saga_events` table. The table must exist wherever a service has a `SagaEventRepository` bean.

  Expected databases with `saga_events`:
  - `estimation_db` ✅ (confirmed)
  - `customer_db`
  - `vehicle_db`
  - `realestate_db`
  - `insurance_db`
  - `reference_data_db` (if it consumes SAGA events)
  - `auth_db` (probably not)
  - `gateway_db` (probably not)

- [ ] **3.2** For each database that has a `saga_events` table, verify the table definition matches. The schema must have:
  ```sql
  CREATE TABLE IF NOT EXISTS saga_events (
      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      saga_id UUID NOT NULL,
      event_type VARCHAR(50) NOT NULL,
      received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(saga_id, event_type)
  );
  ```

- [ ] **3.3** If any database is missing the `saga_events` table, add it. If any has a different schema, reconcile.

### Step 4: Build and Verify

- [ ] **4.1** Build `common-message` (contains the new repository method and cleanup service):
  ```bash
  .\gradlew.bat :common:common-message:build
  ```

- [ ] **4.2** Run tests:
  ```bash
  .\gradlew.bat :common:common-message:test --tests "*SagaEventRepositoryTest*"
  ```

- [ ] **4.3** Build all services to verify no compilation errors from the new component:
  ```bash
  .\gradlew.bat build -x test
  ```

- [ ] **4.4** Start infrastructure and test the migration:
  ```bash
  docker compose -f infra/docker/docker-compose.yml up -d
  ```

  Verify the `trace_id` column exists:
  ```bash
  docker exec estimation-db psql -U ims_user -d estimation_db -c "\d estimations"
  ```

  Look for `trace_id | uuid` in the output.

- [ ] **4.5** Verify the cleanup service starts without errors (check service logs for "SagaEventCleanupService" at startup).

- [ ] **4.6** Tear down:
  ```bash
  docker compose -f infra/docker/docker-compose.yml down
  ```

---

## Files to Create

| File | Purpose |
|------|---------|
| `common/common-message/src/main/java/.../config/SagaEventCleanupService.java` | Scheduled dedup marker cleanup |

## Files to Modify

| File | Change |
|------|--------|
| `infra/sql/estimation_db/init.sql` | Add `ALTER TABLE estimations ADD COLUMN IF NOT EXISTS trace_id UUID;` |
| `common/common-message/.../repository/SagaEventRepository.java` | Add `deleteByReceivedAtBefore(Instant cutoff)` method |

## Files to Verify (no changes expected, but must confirm)

| File | Verify |
|------|--------|
| `infra/sql/customer_db/init.sql` | saga_events table exists with correct schema |
| `infra/sql/vehicle_db/init.sql` | Same |
| `infra/sql/realestate_db/init.sql` | Same |
| `infra/sql/insurance_db/init.sql` | Same |
| `infra/sql/reference_data_db/init.sql` | Same (if applicable) |

---

## Dependencies

- None (standalone fix)
- The `SagaEventCleanupService` uses `@Scheduled` — ensure `@EnableScheduling` is present in the application configuration (it should already be configured for `OutboxRelay` and `SagaTimeoutService`)

## Completion Criteria

- [ ] `ALTER TABLE estimations ADD COLUMN IF NOT EXISTS trace_id UUID;` added to `estimation_db/init.sql`
- [ ] `SagaEventRepository.deleteByReceivedAtBefore(Instant)` method exists
- [ ] `SagaEventCleanupService` with top-level try-catch and configurable retention
- [ ] All database init.sql files verified to have `saga_events` table with consistent schema
- [ ] `.\gradlew.bat build` passes for all modules
- [ ] `docker compose up` → `trace_id` column present in `estimations` table
- [ ] `docker compose up` → `SagaEventCleanupService` starts without errors
