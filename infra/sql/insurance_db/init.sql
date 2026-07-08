CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS insurance_types (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS insurances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type_id INT NOT NULL REFERENCES insurance_types(id),
    base_premium DECIMAL(12,2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_insurances_type ON insurances(type_id);

-- Seed insurance types (asset categories — determines which asset to link in estimation)
INSERT INTO insurance_types (id, name) VALUES
(1, 'Vehicle'), (2, 'Real Estate'), (3, 'Health'), (4, 'Life')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- Seed insurance products
-- Clean up old seed rows by name before re-inserting (idempotent across restarts)
DELETE FROM insurances WHERE name IN (
    'TRAFFIC', 'CASCO', 'DASK', 'HEALTH', 'LIFE',
    'Zorunlu Trafik Sigortası', 'Kapsamlı Kasko', 'Doğal Afet Sigortası (DASK)',
    'Tamamlayıcı Sağlık Sigortası', 'Hayat Sigortası'
);

INSERT INTO insurances (name, description, type_id, base_premium, is_active) VALUES
('TRAFFIC', 'Mandatory traffic insurance', 1, 1250.00, TRUE),
('CASCO', 'Comprehensive auto insurance', 1, 3500.00, TRUE),
('DASK', 'Earthquake insurance for real estate', 2, 450.00, TRUE),
('HEALTH', 'Complementary health insurance', 3, 2800.00, TRUE),
('LIFE', 'Life insurance', 4, 1500.00, TRUE);

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
