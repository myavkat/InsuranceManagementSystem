CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS real_estate_construction_types (
    id INT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS real_estate_luxury_classes (
    id INT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS real_estate_usage_types (
    id INT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS real_estates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    address TEXT NOT NULL,
    city_id INT NOT NULL,
    district VARCHAR(100),
    square_meters DECIMAL(10,2),
    construction_year INT,
    construction_type_id INT REFERENCES real_estate_construction_types(id),
    luxury_class_id INT REFERENCES real_estate_luxury_classes(id),
    usage_type_id INT REFERENCES real_estate_usage_types(id),
    customer_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_real_estates_customer ON real_estates(customer_id);

-- Seed construction types
INSERT INTO real_estate_construction_types (id, name) VALUES 
(1, 'Reinforced Concrete'), (2, 'Steel'), (3, 'Masonry'), (4, 'Wood'), (5, 'Prefabricated')
ON CONFLICT (id) DO NOTHING;

-- Seed luxury classes
INSERT INTO real_estate_luxury_classes (id, name) VALUES 
(1, 'Luxury'), (2, 'High'), (3, 'Middle'), (4, 'Low'), (5, 'Slum')
ON CONFLICT (id) DO NOTHING;

-- Seed usage types
INSERT INTO real_estate_usage_types (id, name) VALUES
(1, 'Residential'), (2, 'Commercial'), (3, 'Industrial'), (4, 'Agricultural')
ON CONFLICT (id) DO NOTHING;

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
