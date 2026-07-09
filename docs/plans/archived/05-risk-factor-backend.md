# Plan 05: Risk Factor Backend — Entity, Table, Migration, Repository

## Objective

Create the database layer for risk factors: two new tables (`risk_factors` and `risk_factor_history`), JPA entities, repositories, and SQL migration. This provides persistent storage for per-insurance risk factor values (0.0–1.0) with audit trail.

## Dependencies

- [x] Plan 01 (`01-seed-data-restructure.md`) — references insurance_type IDs in default seed data

## Files to Read First

- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java` — the parent entity
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/InsuranceType.java` — type reference
- `infra/sql/insurance_db/init.sql` — existing schema + migration patterns
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/SagaAggregation.java` — entity pattern to follow (Lombok, annotations)
- `docs/outlines/10_JAVA_CONVENTIONS.md` — Java conventions (timestamps = Instant, Lombok order)

## Technical Context

- **Convention**: Entities use `@Data @Builder @NoArgsConstructor @AllArgsConstructor` with `@Entity` and `@Table`
- **PK pattern**: `@Id @GeneratedValue(strategy = GenerationType.UUID)` for UUID keys
- **Timestamps**: `java.time.Instant` with `@PrePersist`/`@PreUpdate` lifecycle callbacks
- **BigDecimal precision**: Use `@Column(precision = 3, scale = 2)` for 0.00–1.00 values
- **AGENTS.md SQL rule**: Every column added to an existing table needs both `CREATE TABLE IF NOT EXISTS` (fresh DB) and `ALTER TABLE ADD COLUMN IF NOT EXISTS` (existing DB) paths
- **Factor value range**: 0.0 = no risk, 1.0 = highest risk

## Design

### risk_factors table

| Column | Type | Description |
|--------|------|-------------|
| id | UUID PK | Auto-generated |
| insurance_id | UUID FK → insurances.id | Which insurance product |
| factor_name | VARCHAR(50) | e.g. "motorSize", "fuelType", "carAge", "customerAge", "buildingAge" |
| factor_value | DECIMAL(3,2) | 0.00–1.00 |
| created_at | TIMESTAMPTZ | Auto-set |
| updated_at | TIMESTAMPTZ | Auto-updated |

Unique constraint: `(insurance_id, factor_name)` — one value per factor per insurance.

### risk_factor_history table

| Column | Type | Description |
|--------|------|-------------|
| id | UUID PK | Auto-generated |
| risk_factor_id | UUID FK → risk_factors.id | Which factor was changed |
| insurance_id | UUID FK → insurances.id | Denormalized for query convenience |
| factor_name | VARCHAR(50) | Denormalized |
| old_value | DECIMAL(3,2) | Previous value (nullable — first creation) |
| new_value | DECIMAL(3,2) | New value |
| changed_at | TIMESTAMPTZ | When change occurred |

### Default risk factor definitions per insurance type

**Vehicle-type factors** (applied to TRAFFIC and CASCO insurances):
- `motorSize` — engine displacement risk
- `fuelType` — fuel type risk
- `carAge` — vehicle age risk
- `brandRisk` — car brand repair cost risk

**Real Estate-type factors** (applied to DASK insurance):
- `buildingAge` — construction age risk
- `constructionType` — construction material risk
- `luxuryClass` — property value risk
- `floorArea` — square meters risk

**Shared/Customer factors** (applied to ALL insurance products):
- `customerAge` — customer age risk
- `profession` — profession risk category
- `city` — location risk

Each factor is seeded with a default neutral value of `0.50` (middle of range).

## Steps

### Step 1: Create RiskFactor entity

- [x] RiskFactor.java entity created at `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/RiskFactor.java`

Create new file: `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/RiskFactor.java`

```java
package com.insurancemanagementsystem.insurance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_factors", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"insurance_id", "factor_name"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "insurance_id", nullable = false)
    private UUID insuranceId;

    @Column(name = "factor_name", nullable = false, length = 50)
    private String factorName;

    @Column(name = "factor_value", nullable = false, precision = 3, scale = 2)
    private BigDecimal factorValue;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

### Step 2: Create RiskFactorHistory entity

- [x] RiskFactorHistory.java entity created at `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/RiskFactorHistory.java`

Create new file: `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/RiskFactorHistory.java`

```java
package com.insurancemanagementsystem.insurance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_factor_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactorHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "risk_factor_id", nullable = false)
    private UUID riskFactorId;

    @Column(name = "insurance_id", nullable = false)
    private UUID insuranceId;

    @Column(name = "factor_name", nullable = false, length = 50)
    private String factorName;

    @Column(name = "old_value", precision = 3, scale = 2)
    private BigDecimal oldValue;

    @Column(name = "new_value", nullable = false, precision = 3, scale = 2)
    private BigDecimal newValue;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @PrePersist
    void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }
}
```

### Step 3: Create RiskFactorRepository

- [x] RiskFactorRepository.java created at `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/RiskFactorRepository.java`

Create new file: `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/RiskFactorRepository.java`

```java
package com.insurancemanagementsystem.insurance.repository;

import com.insurancemanagementsystem.insurance.entity.RiskFactor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskFactorRepository extends JpaRepository<RiskFactor, UUID> {
    List<RiskFactor> findByInsuranceId(UUID insuranceId);
    Optional<RiskFactor> findByInsuranceIdAndFactorName(UUID insuranceId, String factorName);
}
```

### Step 4: Create RiskFactorHistoryRepository

- [x] RiskFactorHistoryRepository.java created at `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/RiskFactorHistoryRepository.java`

Create new file: `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/RiskFactorHistoryRepository.java`

```java
package com.insurancemanagementsystem.insurance.repository;

import com.insurancemanagementsystem.insurance.entity.RiskFactorHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RiskFactorHistoryRepository extends JpaRepository<RiskFactorHistory, UUID> {
    Page<RiskFactorHistory> findByInsuranceIdOrderByChangedAtDesc(UUID insuranceId, Pageable pageable);
    Page<RiskFactorHistory> findByRiskFactorIdOrderByChangedAtDesc(UUID riskFactorId, Pageable pageable);
}
```

### Step 5: Add SQL DDL to insurance_db init.sql

- [x] DDL, indexes, and seed data appended to `infra/sql/insurance_db/init.sql`

Open `infra/sql/insurance_db/init.sql`.

**Add** at the end of the file (before the existing saga_events/outbox_events/saga_aggregations block, or after — both are fine):

```sql
-- ============================================================
-- Risk Factors — per-insurance adjustable weight values (0.0-1.0)
-- ============================================================

CREATE TABLE IF NOT EXISTS risk_factors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    insurance_id UUID NOT NULL REFERENCES insurances(id) ON DELETE CASCADE,
    factor_name VARCHAR(50) NOT NULL,
    factor_value DECIMAL(3,2) NOT NULL DEFAULT 0.50,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(insurance_id, factor_name)
);

CREATE INDEX IF NOT EXISTS idx_risk_factors_insurance ON risk_factors(insurance_id);

CREATE TABLE IF NOT EXISTS risk_factor_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    risk_factor_id UUID NOT NULL REFERENCES risk_factors(id) ON DELETE CASCADE,
    insurance_id UUID NOT NULL REFERENCES insurances(id) ON DELETE CASCADE,
    factor_name VARCHAR(50) NOT NULL,
    old_value DECIMAL(3,2),
    new_value DECIMAL(3,2) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_risk_factor_history_insurance ON risk_factor_history(insurance_id);
CREATE INDEX IF NOT EXISTS idx_risk_factor_history_factor ON risk_factor_history(risk_factor_id);

-- Seed default risk factors for each insurance product
-- Clean up old seed data first (idempotent)
DELETE FROM risk_factor_history WHERE insurance_id IN (SELECT id FROM insurances WHERE name IN ('TRAFFIC','CASCO','DASK','HEALTH','LIFE'));
DELETE FROM risk_factors WHERE insurance_id IN (SELECT id FROM insurances WHERE name IN ('TRAFFIC','CASCO','DASK','HEALTH','LIFE'));

-- Helper: insert risk factor via DO block (inserts only if insurance name exists)
DO $$
DECLARE
    ins_id UUID;
BEGIN
    -- TRAFFIC (Vehicle type) — vehicle factors + shared factors
    SELECT id INTO ins_id FROM insurances WHERE name = 'TRAFFIC';
    IF FOUND THEN
        INSERT INTO risk_factors (insurance_id, factor_name, factor_value) VALUES
        (ins_id, 'motorSize', 0.50),
        (ins_id, 'fuelType', 0.50),
        (ins_id, 'carAge', 0.50),
        (ins_id, 'brandRisk', 0.50),
        (ins_id, 'customerAge', 0.50),
        (ins_id, 'profession', 0.50),
        (ins_id, 'city', 0.50)
        ON CONFLICT (insurance_id, factor_name) DO UPDATE SET factor_value = EXCLUDED.factor_value;
    END IF;

    -- CASCO (Vehicle type) — vehicle factors + shared factors
    SELECT id INTO ins_id FROM insurances WHERE name = 'CASCO';
    IF FOUND THEN
        INSERT INTO risk_factors (insurance_id, factor_name, factor_value) VALUES
        (ins_id, 'motorSize', 0.50),
        (ins_id, 'fuelType', 0.50),
        (ins_id, 'carAge', 0.50),
        (ins_id, 'brandRisk', 0.50),
        (ins_id, 'customerAge', 0.50),
        (ins_id, 'profession', 0.50),
        (ins_id, 'city', 0.50)
        ON CONFLICT (insurance_id, factor_name) DO UPDATE SET factor_value = EXCLUDED.factor_value;
    END IF;

    -- DASK (Real Estate type) — real estate factors + shared factors
    SELECT id INTO ins_id FROM insurances WHERE name = 'DASK';
    IF FOUND THEN
        INSERT INTO risk_factors (insurance_id, factor_name, factor_value) VALUES
        (ins_id, 'buildingAge', 0.50),
        (ins_id, 'constructionType', 0.50),
        (ins_id, 'luxuryClass', 0.50),
        (ins_id, 'floorArea', 0.50),
        (ins_id, 'customerAge', 0.50),
        (ins_id, 'profession', 0.50),
        (ins_id, 'city', 0.50)
        ON CONFLICT (insurance_id, factor_name) DO UPDATE SET factor_value = EXCLUDED.factor_value;
    END IF;

    -- HEALTH (Health type) — shared factors only
    SELECT id INTO ins_id FROM insurances WHERE name = 'HEALTH';
    IF FOUND THEN
        INSERT INTO risk_factors (insurance_id, factor_name, factor_value) VALUES
        (ins_id, 'customerAge', 0.50),
        (ins_id, 'profession', 0.50),
        (ins_id, 'city', 0.50)
        ON CONFLICT (insurance_id, factor_name) DO UPDATE SET factor_value = EXCLUDED.factor_value;
    END IF;

    -- LIFE (Life type) — shared factors only
    SELECT id INTO ins_id FROM insurances WHERE name = 'LIFE';
    IF FOUND THEN
        INSERT INTO risk_factors (insurance_id, factor_name, factor_value) VALUES
        (ins_id, 'customerAge', 0.50),
        (ins_id, 'profession', 0.50),
        (ins_id, 'city', 0.50)
        ON CONFLICT (insurance_id, factor_name) DO UPDATE SET factor_value = EXCLUDED.factor_value;
    END IF;
END $$;
```

### Step 6: Add ALTER TABLE migration path

- [x] Not needed — new tables, `CREATE TABLE IF NOT EXISTS` covers both fresh-DB and existing-DB paths

Since `risk_factors` and `risk_factor_history` are new tables (not adding columns to existing tables), the `CREATE TABLE IF NOT EXISTS` pattern covers both paths. No ALTER TABLE statements needed — this is a fresh table addition.

### Step 7: Add CHECK constraint for value range

- [x] CHECK constraint `chk_risk_factor_value` added via DO block in `init.sql`

Optionally add a CHECK constraint to enforce the 0.00–1.00 range at the DB level:

```sql
-- Add CHECK constraint (fresh-DB and existing-DB paths)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_risk_factor_value'
    ) THEN
        ALTER TABLE risk_factors ADD CONSTRAINT chk_risk_factor_value
        CHECK (factor_value >= 0.00 AND factor_value <= 1.00);
    END IF;
END $$;
```

This uses a DO block to conditionally add the constraint — safe for both new and existing DBs.

## Acceptance Criteria

- [x] `risk_factors` table exists with columns: id, insurance_id, factor_name, factor_value, created_at, updated_at
- [x] `risk_factor_history` table exists with columns: id, risk_factor_id, insurance_id, factor_name, old_value, new_value, changed_at
- [x] Unique constraint on (insurance_id, factor_name) prevents duplicates
- [x] CHECK constraint enforces factor_value BETWEEN 0.00 AND 1.00
- [x] Seed data creates 7 factors for TRAFFIC, 7 for CASCO, 7 for DASK, 3 for HEALTH, 3 for LIFE (29 total rows)
- [x] All factor values seeded at 0.50 (neutral)
- [x] RiskFactor JPA entity compiles and maps correctly
- [x] RiskFactorHistory JPA entity compiles and maps correctly
- [x] Both repositories work — can query by insuranceId
- [x] Docker restart is idempotent (DELETE + INSERT pattern ensures clean state)
