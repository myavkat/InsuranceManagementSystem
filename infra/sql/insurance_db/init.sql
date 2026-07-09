CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS insurance_types (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS insurances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    type_id INT NOT NULL REFERENCES insurance_types(id),
    base_premium DECIMAL(12,2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_insurances_type ON insurances(type_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_insurances_code ON insurances(code);

-- Seed insurance types (asset categories — determines which asset to link in estimation)
INSERT INTO insurance_types (id, name) VALUES
(1, 'Vehicle'), (2, 'Real Estate'), (3, 'Health'), (4, 'Life')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- Seed insurance products
-- Clean up old seed rows by name before re-inserting (idempotent across restarts)
DELETE FROM insurances WHERE code IN (
    'TRAFFIC', 'CASCO', 'DASK', 'HEALTH', 'LIFE'
);

INSERT INTO insurances (name, code, description, type_id, base_premium, is_active) VALUES
('Trafik Sigortası', 'TRAFFIC', 'Zorunlu trafik sigortası — yasal olarak yaptırılması gereken temel araç sigortası', 1, 1250.00, TRUE),
('Kasko', 'CASCO', 'Kapsamlı kasko sigortası — aracınızı kaza, çalınma ve doğal afetlere karşı güvence altına alır', 1, 3500.00, TRUE),
('DASK (Doğal Afet Sigortası)', 'DASK', 'Deprem ve doğal afet kaynaklı bina hasarlarına karşı zorunlu konut sigortası', 2, 450.00, TRUE),
('Tamamlayıcı Sağlık Sigortası', 'HEALTH', 'Özel hastanelerde tamamlayıcı sağlık hizmetlerinden indirimli yararlanma imkanı sunar', 3, 2800.00, TRUE),
('Hayat Sigortası', 'LIFE', 'Vefat ve maluliyet durumlarına karşı finansal güvence sağlayan hayat sigortası', 4, 1500.00, TRUE);

-- Migration: drop insurance_companies table and remove company_id FK (multi-company concept removed)
ALTER TABLE insurances DROP COLUMN IF EXISTS company_id;
DROP INDEX IF EXISTS idx_insurances_company;
DROP TABLE IF EXISTS insurance_companies;

CREATE TABLE IF NOT EXISTS saga_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(saga_id, event_type)
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID,
    topic VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PUBLISHING','PUBLISHED','FAILED')),
    retry_count INT DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events(status, created_at);

-- Saga aggregation state for insurance premium calculation correlation
-- Replaces the former in-memory ConcurrentHashMap in SagaAggregationStore.
-- All payload columns are nullable because events can arrive out of order.
CREATE TABLE IF NOT EXISTS saga_aggregations (
    saga_id UUID PRIMARY KEY,
    estimation_request_payload JSONB,
    customer_validated_payload JSONB,
    vehicle_validated_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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

-- Add CHECK constraint for factor_value range 0.00-1.00 (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_risk_factor_value'
    ) THEN
        ALTER TABLE risk_factors ADD CONSTRAINT chk_risk_factor_value
        CHECK (factor_value >= 0.00 AND factor_value <= 1.00);
    END IF;
END $$;

-- Seed default risk factors for each insurance product
-- Clean up old seed data first (idempotent)
DELETE FROM risk_factor_history WHERE insurance_id IN (SELECT id FROM insurances WHERE code IN ('TRAFFIC','CASCO','DASK','HEALTH','LIFE'));
DELETE FROM risk_factors WHERE insurance_id IN (SELECT id FROM insurances WHERE code IN ('TRAFFIC','CASCO','DASK','HEALTH','LIFE'));

-- Helper: insert risk factor via DO block (inserts only if insurance name exists)
DO $$
DECLARE
    ins_id UUID;
BEGIN
    -- TRAFFIC (Vehicle type) — vehicle factors + shared factors
    SELECT id INTO ins_id FROM insurances WHERE code = 'TRAFFIC';
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
    SELECT id INTO ins_id FROM insurances WHERE code = 'CASCO';
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
    SELECT id INTO ins_id FROM insurances WHERE code = 'DASK';
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
    SELECT id INTO ins_id FROM insurances WHERE code = 'HEALTH';
    IF FOUND THEN
        INSERT INTO risk_factors (insurance_id, factor_name, factor_value) VALUES
        (ins_id, 'customerAge', 0.50),
        (ins_id, 'profession', 0.50),
        (ins_id, 'city', 0.50)
        ON CONFLICT (insurance_id, factor_name) DO UPDATE SET factor_value = EXCLUDED.factor_value;
    END IF;

    -- LIFE (Life type) — shared factors only
    SELECT id INTO ins_id FROM insurances WHERE code = 'LIFE';
    IF FOUND THEN
        INSERT INTO risk_factors (insurance_id, factor_name, factor_value) VALUES
        (ins_id, 'customerAge', 0.50),
        (ins_id, 'profession', 0.50),
        (ins_id, 'city', 0.50)
        ON CONFLICT (insurance_id, factor_name) DO UPDATE SET factor_value = EXCLUDED.factor_value;
    END IF;
END $$;
