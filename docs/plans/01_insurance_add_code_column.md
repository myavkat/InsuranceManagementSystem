# Plan 01: Add `code` Column to Insurances Table

## Objective

Add a stable, immutable `code` column to the `insurances` table and Java entity. This column holds the internal identifier (e.g., `TRAFFIC`, `CASCO`) separate from the human-readable display `name`. This decoupling allows seed data idempotency and internal references to remain stable even when display names change.

## Dependency

None — this is the first plan. Execute before Plans 02, 03, and 04.

## Files to Read First

- `infra/sql/insurance_db/init.sql` — current schema (see lines 1–18 for CREATE TABLE insurances)
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java` — entity definition
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceResponse.java` — response DTO
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceRequest.java` — request DTO
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/service/InsuranceService.java` — service layer (see `create()` method around line 71 and `update()` around line 99)
- `docs/outlines/10_JAVA_CONVENTIONS.md` — Java conventions (Lombok order, datetime)
- `AGENTS.md` — Schema DDL rule: "Every column addition needs both paths: fresh-db (CREATE) and existing-db (ALTER)"

## Technical Context

- **Database:** PostgreSQL 16, schema managed by `init.sql` mounted to `/docker-entrypoint-initdb.d/`
- **Table creation:** Uses `CREATE TABLE IF NOT EXISTS` — on restart, DDL is skipped but the ALTER migration block at the bottom of `init.sql` handles existing DBs
- **AGENTS.md rule:** Every column addition to an existing table MUST have both:
  1. The column in `CREATE TABLE IF NOT EXISTS` (for fresh databases)
  2. An `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` in the migration block (for existing databases)
- **Entity convention:** `@Data @Builder @NoArgsConstructor @AllArgsConstructor` on entities (Lombok), `java.time.Instant` for timestamps
- **The `code` column** must be `VARCHAR(50) UNIQUE NOT NULL`
- **Existing rows:** On migration, any existing rows with NULL `code` must be backfilled before the NOT NULL constraint is added

## Steps

### Step 1: Add `code` column to CREATE TABLE statement

Open `infra/sql/insurance_db/init.sql`.

In the `CREATE TABLE IF NOT EXISTS insurances` block (around line 8), add the `code` column **after the `name` column**:

```sql
CREATE TABLE IF NOT EXISTS insurances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,          -- <-- ADD THIS LINE
    description TEXT,
    ...
);
```

Also add a unique constraint index (alongside the existing `idx_insurances_type`):

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_insurances_code ON insurances(code);
```

### Step 2: Add migration block at the bottom of init.sql

At the bottom of `init.sql` (before the final `DO $$` block for risk factors, around line 109–118), add a new migration section:

```sql
-- ============================================================
-- Migration: add code column to insurances
-- ============================================================
DO $$
BEGIN
    -- 1. Add column (nullable initially so existing rows don't fail)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'insurances' AND column_name = 'code'
    ) THEN
        ALTER TABLE insurances ADD COLUMN code VARCHAR(50);
    END IF;

    -- 2. Backfill NULL codes with a slug derived from name
    UPDATE insurances SET code = UPPER(REGEXP_REPLACE(TRIM(name), '\s+', '_', 'g'))
    WHERE code IS NULL;

    -- 3. Add NOT NULL constraint
    ALTER TABLE insurances ALTER COLUMN code SET NOT NULL;

    -- 4. Add unique constraint (idempotent)
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_insurances_code'
    ) THEN
        ALTER TABLE insurances ADD CONSTRAINT uq_insurances_code UNIQUE (code);
    END IF;
END $$;
```

### Step 3: Add `code` field to the Insurance entity

Open `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java`.

Add the `code` field **after the `name` field** (after line 26):

```java
@Column(nullable = false, unique = true, length = 50)
private String code;
```

The full field block should look like:
```java
@Column(nullable = false, length = 100)
private String name;

@Column(nullable = false, unique = true, length = 50)
private String code;

@Column(columnDefinition = "TEXT")
private String description;
```

⚠️ **Do NOT add `code` to the `@Builder`** — the builder is already configured at class level. Lombok's `@Builder` on the class includes all fields automatically.

### Step 4: Add `code` to InsuranceResponse DTO

Open `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceResponse.java`.

Add the `code` field **after `name`**:

```java
private UUID id;
private String name;
private String code;          // <-- ADD THIS
private String description;
...
```

In the `fromEntity()` static factory method, add the mapping:

```java
.name(insurance.getName())
.code(insurance.getCode())    // <-- ADD THIS
.description(insurance.getDescription())
```

### Step 5: Add `code` handling to InsuranceService.create()

Open `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/service/InsuranceService.java`.

In the `create()` method (around line 71), add code auto-generation logic. After the duplicate name check (around line 81), add:

```java
// Generate code from name if not explicitly provided
String code = request.getCode() != null && !request.getCode().isBlank()
        ? request.getCode().trim().toUpperCase()
        : request.getName().trim().toUpperCase().replaceAll("\\s+", "_");
```

Then update the Insurance builder to include `.code(code)`:

```java
Insurance insurance = Insurance.builder()
        .name(request.getName().trim())
        .code(code)                                    // <-- ADD THIS
        .description(request.getDescription())
        .typeId(request.getTypeId())
        .basePremium(request.getBasePremium())
        .isActive(request.getIsActive() != null ? request.getIsActive() : true)
        .build();
```

### Step 6: Add optional `code` field to InsuranceRequest DTO

Open `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceRequest.java`.

Add an optional `code` field **after `name`**:

```java
@NotBlank(message = "Insurance name is required")
private String name;

private String code;    // <-- ADD THIS (optional — auto-generated if not set)

private String description;
```

⚠️ Do NOT add `@NotBlank` on `code` — it's optional. The service auto-generates it from `name` when not provided.

## Acceptance Criteria

- [x] `code VARCHAR(50) NOT NULL` column exists in `CREATE TABLE IF NOT EXISTS insurances`
- [x] Migration block handles existing DBs: adds column, backfills nulls, adds NOT NULL + UNIQUE
- [x] `Insurance` entity has `code` field with `@Column(nullable = false, unique = true, length = 50)`
- [x] `InsuranceResponse` exposes `code` in API responses
- [x] `InsuranceRequest` accepts optional `code` (auto-generated from `name` when not provided)
- [x] `InsuranceService.create()` populates `code` on new insurances
- [x] Docker Compose down/up cycle works (fresh DB path)
- [x] Docker Compose restart (without down) works (existing DB migration path)
