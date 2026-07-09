CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS estimations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID UNIQUE NOT NULL,
    customer_id UUID,
    vehicle_id UUID,
    real_estate_id UUID,
    insurance_id UUID,
    trace_id UUID,
    status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'WAITING_APPROVAL', 'PAYMENT_WAITING', 'ACTIVE', 'COMPLETED', 'REJECTED')),
    premium DECIMAL(12,2),
    details JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS saga_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(saga_id, event_type)
);

CREATE INDEX IF NOT EXISTS idx_estimations_saga ON estimations(saga_id);
CREATE INDEX IF NOT EXISTS idx_estimations_customer ON estimations(customer_id);
CREATE INDEX IF NOT EXISTS idx_estimations_status ON estimations(status);
CREATE INDEX IF NOT EXISTS idx_estimations_created ON estimations(created_at);

-- Migration: add trace_id column for databases created before Subtask 6
-- (CREATE TABLE IF NOT EXISTS skips the column if the table already exists)
ALTER TABLE estimations ADD COLUMN IF NOT EXISTS trace_id UUID;

-- Migration: remove company_id from estimations (multi-company concept removed)
ALTER TABLE estimations DROP COLUMN IF EXISTS company_id;

-- Migration: replace insurance_type_id with insurance_id (FK changed from insurance_type to insurance)
ALTER TABLE estimations ADD COLUMN IF NOT EXISTS insurance_id UUID;
-- Note: insurance_type_id is intentionally kept as a deprecated column for rollback safety.
-- It will be removed in a future cleanup migration after all services are confirmed stable.

-- Migration: add start_date and end_date columns for offer/payment status flow
ALTER TABLE estimations ADD COLUMN IF NOT EXISTS start_date TIMESTAMP;
ALTER TABLE estimations ADD COLUMN IF NOT EXISTS end_date TIMESTAMP;

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
