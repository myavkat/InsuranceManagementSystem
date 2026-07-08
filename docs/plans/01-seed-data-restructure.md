# Plan 01: Restructure Insurance Types & Products Seed Data

## Objective

Reshape the `insurance_types` and `insurances` seed data so that:
- **Insurance Types** = asset categories: `Vehicle`, `Real Estate`, `Health`, `Life` (4 types)
- **Insurance Products** = specific insurance names: `TRAFFIC`, `CASCO`, `DASK`, `HEALTH`, `LIFE` (5 products)
- The type now answers "what asset does this insurance cover?" and drives step 3 behavior in the estimation wizard

## Files to Read First

- `infra/sql/insurance_db/init.sql` — current schema and seed data
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/InsuranceType.java` — entity definition
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java` — entity definition

## Technical Context

- PostgreSQL database `insurance_db`, schema managed by `init.sql` mounted to `/docker-entrypoint-initdb.d/` in Docker
- `insurance_types` uses manually-assigned INT PK (no auto-increment)
- `insurances` uses UUID PK (`uuid_generate_v4()`)
- Table creation uses `CREATE TABLE IF NOT EXISTS` — on restart, DDL is skipped but seed INSERT statements still run
- `alter table` statements at the bottom of `init.sql` handle migrations for existing DBs (both fresh-db and existing-db paths)
- **AGENTS.md rule**: "Schema DDL must include migration path" — every column/constraint change needs both CREATE (fresh) and ALTER (existing) paths
- Java convention: `@Data @Builder @NoArgsConstructor @AllArgsConstructor` on entities, `java.time.Instant` for timestamps

## Mapping

| Type ID | Type Name | Products |
|---------|-----------|----------|
| 1 | Vehicle | TRAFFIC (id=1 → type 1), CASCO (id=2 → type 1) |
| 2 | Real Estate | DASK (id=3 → type 2) |
| 3 | Health | HEALTH (id=4 → type 3) |
| 4 | Life | LIFE (id=5 → type 4) |

## Steps

### Step 1: ✅ Update insurance_types seed data

Open `infra/sql/insurance_db/init.sql`.

**Replace** the existing seed block:
```sql
-- Seed insurance types
INSERT INTO insurance_types (id, name) VALUES
(1, 'TRAFFIC'), (2, 'CASCO'), (3, 'DASK'), (4, 'HEALTH'), (5, 'LIFE')
ON CONFLICT (id) DO NOTHING;
```

**With**:
```sql
-- Seed insurance types (asset categories — determines which asset to link in estimation)
INSERT INTO insurance_types (id, name) VALUES
(1, 'Vehicle'), (2, 'Real Estate'), (3, 'Health'), (4, 'Life')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;
```

This uses `ON CONFLICT DO UPDATE` so existing DBs get the type names updated (from TRAFFIC→Vehicle, CASCO deleted, DASK→Real Estate, HEALTH→Health, LIFE→Life).

### Step 2: ✅ Add cleanup + new seed for insurances

**Replace** the existing insurance products seed block:
```sql
-- Seed insurance products (single-provider system — one product per type)
INSERT INTO insurances (name, description, type_id, base_premium, is_active) VALUES
('Zorunlu Trafik Sigortası', 'Legal required traffic insurance', 1, 1250.00, TRUE),
('Kapsamlı Kasko', 'Full comprehensive insurance', 2, 3500.00, TRUE),
('Doğal Afet Sigortası (DASK)', 'Earthquake insurance', 3, 450.00, TRUE),
('Tamamlayıcı Sağlık Sigortası', 'Complementary health insurance', 4, 2800.00, TRUE),
('Hayat Sigortası', 'Life insurance', 5, 1500.00, TRUE);
```

**With**:
```sql
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
```

### Step 3: ✅ Handle FK constraint for DELETE

Since `insurances` has `type_id INT NOT NULL REFERENCES insurance_types(id)`, the DELETE is safe because we DELETE from insurances first, then UPDATE insurance_types.

But note: if there are `estimations` rows in a separate DB that reference old `insurance_type_id` values (especially id=5 for LIFE which will now be id=4), those would become orphaned. This is acceptable for dev seed data. The estimation DB is separate and its data is transient.

### Step 4: ✅ Verify the InsuranceType entity

Open `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/InsuranceType.java`.

Verify the entity has only `id` (Integer, `@Id`) and `name` (String, max 50). No changes needed — the entity is just a simple lookup.

### Step 5: ✅ Verify Insurance entity still correct

Open `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java`.

Verify: `typeId` field (Integer) maps to `type_id` column with `@ManyToOne` read-only relationship to `InsuranceType`. No entity changes needed — the schema and seed data changes are sufficient.

## Acceptance Criteria

- [x] `insurance_types` table has exactly 4 rows: Vehicle(1), Real Estate(2), Health(3), Life(4)
- [x] `insurances` table has exactly 5 active products: TRAFFIC(type=1), CASCO(type=1), DASK(type=2), HEALTH(type=3), LIFE(type=4)
- [x] `GET /api/insurances/types` returns the 4 new type names
- [x] `GET /api/insurances` returns the 5 new product names
- [x] Docker Compose down/up cycle produces same clean state (idempotent)
