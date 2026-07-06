CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS car_brands (
    id INT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS car_models (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    brand_id INT NOT NULL REFERENCES car_brands(id)
);

CREATE TABLE IF NOT EXISTS car_engines (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    volume DECIMAL(4,1),
    power INT
);

CREATE TABLE IF NOT EXISTS car_fuel_types (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS car_types (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS car_packages (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS vehicles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plate VARCHAR(20) UNIQUE NOT NULL,
    chassis_number VARCHAR(50),
    license_first_date DATE,
    car_brand_id INT NOT NULL REFERENCES car_brands(id),
    car_model_id INT NOT NULL REFERENCES car_models(id),
    car_engine_id INT NOT NULL REFERENCES car_engines(id),
    car_fuel_type_id INT NOT NULL REFERENCES car_fuel_types(id),
    car_type_id INT NOT NULL REFERENCES car_types(id),
    car_package_id INT NOT NULL REFERENCES car_packages(id),
    customer_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vehicles_plate ON vehicles(plate);
CREATE INDEX IF NOT EXISTS idx_vehicles_chassis ON vehicles(chassis_number);
CREATE INDEX IF NOT EXISTS idx_vehicles_customer ON vehicles(customer_id);

-- Seed car brands
INSERT INTO car_brands (id, name) VALUES 
(1, 'Toyota'), (2, 'BMW'), (3, 'Mercedes'), (4, 'Renault'), (5, 'Fiat'),
(6, 'Ford'), (7, 'Honda'), (8, 'Hyundai'), (9, 'Volkswagen'), (10, 'Audi')
ON CONFLICT (id) DO NOTHING;

-- Seed car models
INSERT INTO car_models (id, name, brand_id) VALUES 
(1, 'Corolla', 1), (2, 'Camry', 1), (3, 'RAV4', 1),
(4, '3 Series', 2), (5, '5 Series', 2), (6, 'X5', 2),
(7, 'C-Class', 3), (8, 'E-Class', 3), (9, 'S-Class', 3),
(10, 'Clio', 4), (11, 'Megane', 4), (12, 'Egea', 5),
(13, 'Focus', 6), (14, 'Civic', 7), (15, 'i20', 8),
(16, 'Passat', 9), (17, 'Golf', 9), (18, 'A4', 10), (19, 'A6', 10)
ON CONFLICT (id) DO NOTHING;

-- Seed car engines
INSERT INTO car_engines (id, name, volume, power) VALUES 
(1, '1.2L', 1.2, 75), (2, '1.4L', 1.4, 100), (3, '1.6L', 1.6, 130),
(4, '2.0L', 2.0, 170), (5, '3.0L', 3.0, 250), (6, '1.5L Diesel', 1.5, 110),
(7, '2.0L Diesel', 2.0, 150), (8, 'Electric 100kW', 0.0, 136)
ON CONFLICT (id) DO NOTHING;

-- Seed fuel types
INSERT INTO car_fuel_types (id, name) VALUES 
(1, 'Gasoline'), (2, 'Diesel'), (3, 'Electric'), (4, 'Hybrid'), (5, 'LPG')
ON CONFLICT (id) DO NOTHING;

-- Seed car types
INSERT INTO car_types (id, name) VALUES 
(1, 'Sedan'), (2, 'Hatchback'), (3, 'SUV'), (4, 'Coupe'),
(5, 'Convertible'), (6, 'Minivan'), (7, 'Pickup')
ON CONFLICT (id) DO NOTHING;

-- Seed car packages
INSERT INTO car_packages (id, name) VALUES
(1, 'Base'), (2, 'Comfort'), (3, 'Luxury'), (4, 'Sport')
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
