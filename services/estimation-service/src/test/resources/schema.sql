-- Test schema matching entity definitions
-- Uses TEXT for details instead of JSONB to avoid Hibernate JSONB type issues

CREATE TABLE IF NOT EXISTS estimations (
    id UUID PRIMARY KEY,
    saga_id UUID NOT NULL UNIQUE,
    customer_id UUID,
    vehicle_id UUID,
    real_estate_id UUID,
    insurance_type_id INTEGER,
    company_id UUID,
    status VARCHAR(20) NOT NULL,
    premium DECIMAL(12,2),
    details TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS saga_events (
    id UUID PRIMARY KEY,
    saga_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    received_at TIMESTAMP,
    UNIQUE (saga_id, event_type)
);
