# Plan 01: Backend — Entity, Enum, and DDL Changes

**Status:** Completed
**Depends on:** None
**Blocks:** Plan 02, Plan 03, Plan 04

---

## Objective

Extend the `Estimation` entity's status enum and add new columns (`start_date`, `end_date`) to support the offer/payment status flow. Update the SQL DDL and the response DTO to match. No behavioral changes to status transitions — those come in Plan 02.

---

## Files to Read First (before writing any code)

| # | File Path | Purpose |
|---|-----------|---------|
| 1 | `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java` | The entity you will modify |
| 2 | `infra/sql/estimation_db/init.sql` | The DDL you will add migration statements to |
| 3 | `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationResponse.java` | The response DTO you will extend |
| 4 | `docs/outlines/10_JAVA_CONVENTIONS.md` | Java conventions: Lombok order, Instant vs LocalDate, datetime conventions |
| 5 | `AGENTS.md` | SAGA consumer rules, DB state safety rules (especially "Schema DDL must include migration path") |

---

## Steps

### Step 1: Extend the Status Enum

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java`

Add three new status values to the inner `Status` enum. The current enum is:

```java
public enum Status {
    STARTED, COMPLETED, REJECTED
}
```

Change it to:

```java
public enum Status {
    STARTED, WAITING_APPROVAL, PAYMENT_WAITING, ACTIVE, COMPLETED, REJECTED
}
```

Keep both `COMPLETED` and the new `WAITING_APPROVAL` for now — existing data may have `COMPLETED` rows. Do NOT remove any existing enum values; just add the new ones.

After this change, verify:
- [ ] Enum compiles
- [ ] All existing usages of `Estimation.Status.STARTED`, `.COMPLETED`, `.REJECTED` still resolve correctly (they will — Java enum values are additive)

### Step 2: Add start_date and end_date Columns to the Entity

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java`

Add two new fields to the `Estimation` class, BEFORE the `createdAt` field (to group temporal fields together):

```java
@Column(name = "start_date")
private Instant startDate;

@Column(name = "end_date")
private Instant endDate;
```

Placement: insert these lines between the `details` field and the `createdAt` field (around current line 66).

Do NOT add them to `@PrePersist` or `@PreUpdate` — these are set explicitly by business logic, not automatically on save.

### Step 3: Update the SQL DDL (CRITICAL — follow AGENTS.md rules)

**File:** `infra/sql/estimation_db/init.sql`

You must make THREE changes. The AGENTS.md rule "Schema DDL must include migration path" requires both a fresh-DB path (CREATE TABLE) and an existing-DB path (ALTER TABLE).

#### 3a: Update the CHECK constraint for the status column

In the `CREATE TABLE IF NOT EXISTS estimations` block (line 11), change:

```sql
status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'COMPLETED', 'REJECTED')),
```

to:

```sql
status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'WAITING_APPROVAL', 'PAYMENT_WAITING', 'ACTIVE', 'COMPLETED', 'REJECTED')),
```

This handles the fresh-DB case.

For the existing-DB case (where the table already exists and has the old CHECK constraint), add these ALTER TABLE statements at the end of the file (after the existing migration lines, around line 41):

```sql
-- Migration: add WAITING_APPROVAL, PAYMENT_WAITING, ACTIVE to status CHECK constraint
-- Note: PostgreSQL does not support ALTER TABLE ... ALTER COLUMN ... DROP CONSTRAINT
-- for CHECK constraints directly by name unless they were named at creation.
-- Since the original CHECK constraint is unnamed, we drop and re-add it.
-- If the table doesn't exist yet (fresh DB), the CREATE TABLE above already has the new values.
-- This migration only runs when the table already exists with the old constraint.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'estimations'
    ) THEN
        ALTER TABLE estimations DROP CONSTRAINT IF EXISTS estimations_status_check;
        ALTER TABLE estimations ADD CONSTRAINT estimations_status_check
            CHECK (status IN ('STARTED', 'WAITING_APPROVAL', 'PAYMENT_WAITING', 'ACTIVE', 'COMPLETED', 'REJECTED'));
    END IF;
END $$;
```

#### 3b: Add start_date column migration

Add after the insurance_id migration line (around line 39):

```sql
ALTER TABLE estimations ADD COLUMN IF NOT EXISTS start_date TIMESTAMP;
```

#### 3c: Add end_date column migration

Add after the start_date migration:

```sql
ALTER TABLE estimations ADD COLUMN IF NOT EXISTS end_date TIMESTAMP;
```

**Why both CREATE and ALTER paths:** As stated in AGENTS.md, `CREATE TABLE IF NOT EXISTS` is skipped if the table already exists from a prior deployment. Without the ALTER, Hibernate's `ddl-auto: validate` fails on restart because the entity expects columns that don't exist in the live schema.

### Step 4: Update the EstimationResponse DTO

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationResponse.java`

Add two new fields to the DTO:

```java
private Instant startDate;
private Instant endDate;
```

Add them after the `details` field (around line 33) and before `createdAt` to match the entity field order.

#### Step 4a: Update the two-parameter `fromEntity` method (the one used for list queries)

In the `fromEntity(Estimation estimation)` method (around line 41-55), add these two lines inside the builder:

```java
.startDate(estimation.getStartDate())
.endDate(estimation.getEndDate())
```

Add them after `.details(estimation.getDetails())` and before `.createdAt(estimation.getCreatedAt())`.

#### Step 4b: Update the enriched `fromEntity` method (the one used for detail queries)

In the `fromEntity(Estimation estimation, String customerName, ...)` method (around line 61-87), add the same two lines in the same position.

### Step 5: Verify Everything Compiles

Run the build for the estimation service to confirm no compilation errors:

```bash
cd services/estimation-service && ../gradlew compileJava
```

### Step 6: Update the SagaTimeoutService (compatibility fix)

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/SagaTimeoutService.java`

The `SagaTimeoutService` currently queries for `Estimation.Status.STARTED` estimations to time out. With the new statuses, we need to consider: should WAITING_APPROVAL estimations also time out? Yes — if an offer is never accepted, it should eventually time out.

Find the repository call that queries by status (it uses `Estimation.Status.STARTED`). Change it to query for both `STARTED` and `WAITING_APPROVAL`:

If the repository method is `findByStatusAndCreatedAtBefore(Estimation.Status status, Instant createdAt)`, you'll need to either:
- Call it twice (once for STARTED, once for WAITING_APPROVAL) and merge the lists, OR
- Add a new repository method `findByStatusInAndCreatedAtBefore(List<Estimation.Status> statuses, Instant createdAt)`

The simpler approach: call the existing method twice and merge:

```java
List<Estimation> startedEstimations = estimationRepository
        .findByStatusAndCreatedAtBefore(Estimation.Status.STARTED, cutoff);
List<Estimation> waitingApprovalEstimations = estimationRepository
        .findByStatusAndCreatedAtBefore(Estimation.Status.WAITING_APPROVAL, cutoff);

List<Estimation> expiredEstimations = new ArrayList<>();
expiredEstimations.addAll(startedEstimations);
expiredEstimations.addAll(waitingApprovalEstimations);
```

Replace the existing single `findByStatusAndCreatedAtBefore` call with this pattern.

---

## Acceptance Criteria

- [x] `Estimation.Status` enum has all six values: `STARTED, WAITING_APPROVAL, PAYMENT_WAITING, ACTIVE, COMPLETED, REJECTED`
- [x] No existing enum values removed
- [x] `Estimation` entity has `startDate` (Instant) and `endDate` (Instant) fields with proper `@Column` annotations
- [x] `infra/sql/estimation_db/init.sql` has: updated CHECK constraint in CREATE TABLE, ALTER TABLE to drop/re-add constraint for existing DBs, ALTER TABLE ADD COLUMN IF NOT EXISTS for start_date and end_date
- [x] `EstimationResponse` DTO has `startDate` and `endDate` fields, populated in both `fromEntity` overloads
- [x] Estimation service compiles successfully
- [x] `SagaTimeoutService` checks both STARTED and WAITING_APPROVAL for timeout
