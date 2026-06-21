CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS insurance_types (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS insurance_companies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    rating DECIMAL(2,1),
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS insurances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type_id INT NOT NULL REFERENCES insurance_types(id),
    company_id UUID NOT NULL REFERENCES insurance_companies(id),
    base_premium DECIMAL(12,2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_insurances_type ON insurances(type_id);
CREATE INDEX IF NOT EXISTS idx_insurances_company ON insurances(company_id);

-- Seed insurance types
INSERT INTO insurance_types (id, name) VALUES 
(1, 'TRAFFIC'), (2, 'CASCO'), (3, 'DASK'), (4, 'HEALTH'), (5, 'LIFE')
ON CONFLICT (id) DO NOTHING;

-- Seed insurance companies
INSERT INTO insurance_companies (id, name, rating, is_active) VALUES 
(uuid_generate_v4(), 'Anadolu Sigorta', 4.5, TRUE),
(uuid_generate_v4(), 'Ak Sigorta', 4.2, TRUE),
(uuid_generate_v4(), 'Allianz', 4.8, TRUE),
(uuid_generate_v4(), 'Groupama', 4.0, TRUE);

-- Seed insurance products
INSERT INTO insurances (name, description, type_id, company_id, base_premium, is_active)
SELECT 'Zorunlu Trafik Sigortası', 'Legal required traffic insurance', 1, id, 1250.00, TRUE FROM insurance_companies WHERE name = 'Anadolu Sigorta';
INSERT INTO insurances (name, description, type_id, company_id, base_premium, is_active)
SELECT 'Kapsamlı Kasko', 'Full comprehensive insurance', 2, id, 3500.00, TRUE FROM insurance_companies WHERE name = 'Anadolu Sigorta';
INSERT INTO insurances (name, description, type_id, company_id, base_premium, is_active)
SELECT 'Doğal Afet Sigortası (DASK)', 'Earthquake insurance', 3, id, 450.00, TRUE FROM insurance_companies WHERE name = 'Anadolu Sigorta';
INSERT INTO insurances (name, description, type_id, company_id, base_premium, is_active)
SELECT 'Tamamlayıcı Sağlık Sigortası', 'Complementary health insurance', 4, id, 2800.00, TRUE FROM insurance_companies WHERE name = 'Anadolu Sigorta';
INSERT INTO insurances (name, description, type_id, company_id, base_premium, is_active)
SELECT 'Zorunlu Trafik Sigortası', 'Legal required traffic insurance', 1, id, 1200.00, TRUE FROM insurance_companies WHERE name = 'Ak Sigorta';
INSERT INTO insurances (name, description, type_id, company_id, base_premium, is_active)
SELECT 'Kasko Sigortası', 'Vehicle comprehensive insurance', 2, id, 3200.00, TRUE FROM insurance_companies WHERE name = 'Ak Sigorta';
INSERT INTO insurances (name, description, type_id, company_id, base_premium, is_active)
SELECT 'Premium Kasko', 'Premium comprehensive insurance', 2, id, 4500.00, TRUE FROM insurance_companies WHERE name = 'Allianz';
INSERT INTO insurances (name, description, type_id, company_id, base_premium, is_active)
SELECT 'Hayat Sigortası', 'Life insurance', 5, id, 1500.00, TRUE FROM insurance_companies WHERE name = 'Allianz';
