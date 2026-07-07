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

-- Seed insurance types
INSERT INTO insurance_types (id, name) VALUES
(1, 'TRAFFIC'), (2, 'CASCO'), (3, 'DASK'), (4, 'HEALTH'), (5, 'LIFE')
ON CONFLICT (id) DO NOTHING;

-- Seed insurance products (single-provider system — one product per type)
INSERT INTO insurances (name, description, type_id, base_premium, is_active) VALUES
('Zorunlu Trafik Sigortası', 'Legal required traffic insurance', 1, 1250.00, TRUE),
('Kapsamlı Kasko', 'Full comprehensive insurance', 2, 3500.00, TRUE),
('Doğal Afet Sigortası (DASK)', 'Earthquake insurance', 3, 450.00, TRUE),
('Tamamlayıcı Sağlık Sigortası', 'Complementary health insurance', 4, 2800.00, TRUE),
('Hayat Sigortası', 'Life insurance', 5, 1500.00, TRUE);

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
