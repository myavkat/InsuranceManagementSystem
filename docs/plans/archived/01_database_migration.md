# Plan 01: Database Migration — Estimation FK from insurance_type to insurance

## Objective

Change the `estimations` table column `insurance_type_id` (INT) to `insurance_id` (UUID). Write both a `CREATE TABLE` update and an `ALTER TABLE` migration so existing databases are migrated without data loss.

## Dependencies

- **None.** This plan can be executed independently and first.

## Files to Read Before Starting

- `infra/sql/estimation_db/init.sql` — current schema
- `infra/sql/insurance_db/init.sql` — to understand the `insurances` table structure that `insurance_id` references
- `AGENTS.md` — DB State Safety Rules section (specifically "Schema DDL must include migration path")
- `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` — Estimation Service section

## Technical Context

### Key facts
- The `estimations` table lives in `estimation_db`.
- The `insurances` table lives in `insurance_db` (separate PostgreSQL database).
- Because they are in different databases, a real foreign key constraint (`REFERENCES insurances(id)`) **cannot** be enforced at the database level. The `insurance_id` column will be a plain UUID with no DB-level FK constraint.
- The column type changes from `INT` to `UUID`.
- Existing data will have `NULL` in the new column after migration (old `insurance_type_id` values are integers, can't be converted to insurance UUIDs automatically).

### Migration pattern from AGENTS.md
> "Schema DDL must include migration path: When adding a column to an existing table, the SQL init script MUST include an `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` statement in addition to the `CREATE TABLE IF NOT EXISTS` column definition."

In this case we are **replacing** a column, not adding one. Follow this approach:
1. In the `CREATE TABLE IF NOT EXISTS` block: replace `insurance_type_id INT` with `insurance_id UUID`.
2. Add an `ALTER TABLE` migration block that:
   - Adds `insurance_id UUID` if it doesn't exist (for existing DBs)
   - Does NOT drop `insurance_type_id` — keep it as a deprecated column for rollback safety

## Steps

### Step 1: Update the CREATE TABLE statement

Open `infra/sql/estimation_db/init.sql`.

Find this line inside the `CREATE TABLE IF NOT EXISTS estimations (...)` block:
```sql
insurance_type_id INT,
```

Replace it with:
```sql
insurance_id UUID,
```

The new column list should be:
```sql
CREATE TABLE IF NOT EXISTS estimations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID UNIQUE NOT NULL,
    customer_id UUID,
    vehicle_id UUID,
    real_estate_id UUID,
    insurance_id UUID,
    trace_id UUID,
    status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'COMPLETED', 'REJECTED')),
    premium DECIMAL(12,2),
    details JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Step 2: Add ALTER TABLE migration block

At the end of the file (after the existing migration blocks, before the `outbox_events` table or at the very end), add a new migration block:

```sql
-- Migration: replace insurance_type_id with insurance_id (FK changed from insurance_type to insurance)
ALTER TABLE estimations ADD COLUMN IF NOT EXISTS insurance_id UUID;
-- Note: insurance_type_id is intentionally kept as a deprecated column for rollback safety.
-- It will be removed in a future cleanup migration after all services are confirmed stable.
```

### Step 3: Verify the file

Read the complete `infra/sql/estimation_db/init.sql` and verify:
- The `CREATE TABLE IF NOT EXISTS estimations` block has `insurance_id UUID` instead of `insurance_type_id INT`.
- The new `ALTER TABLE` migration is present at the end of the file.
- No syntax errors (matching parentheses, semicolons).

## Acceptance Criteria

- [x] `CREATE TABLE IF NOT EXISTS estimations` uses `insurance_id UUID` (not `insurance_type_id INT`)
- [x] An `ALTER TABLE estimations ADD COLUMN IF NOT EXISTS insurance_id UUID;` migration exists
- [x] The old `insurance_type_id` column is NOT dropped (kept for rollback safety)
- [x] File parses without SQL syntax errors
