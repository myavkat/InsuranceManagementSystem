# Plan 02: Update Seed Data — Human-Readable Insurance Names

## Objective

Replace the technical codes in `insurances.name` with human-readable Turkish display names. Also set the stable `code` column for each seed record, and update all SQL WHERE clauses in `init.sql` to reference `code` instead of `name` for idempotency. This ensures the display name can change independently of internal references.

## Dependency

**Plan 01 must be completed first** — the `code` column must exist before this plan's seed data can be inserted.

## Files to Read First

- `infra/sql/insurance_db/init.sql` — the entire file (focus on lines 26–191 covering seed data INSERT, DELETE, and risk factor DO blocks)
- `docs/plans/01_insurance_add_code_column.md` — verify what was done in Plan 01 (especially the column name and type)
- `AGENTS.md` — Schema DDL rule, SAGA Consumer Rules

## Name Mapping

Apply the following name translations. The `code` stays as the legacy technical identifier. The `name` becomes human-readable Turkish.

| code | old name | new name (display) | type_id |
|------|----------|-------------------|---------|
| TRAFFIC | TRAFFIC | Trafik Sigortası | 1 (Vehicle) |
| CASCO | CASCO | Kasko | 1 (Vehicle) |
| DASK | DASK | DASK (Doğal Afet Sigortası) | 2 (Real Estate) |
| HEALTH | HEALTH | Tamamlayıcı Sağlık Sigortası | 3 (Health) |
| LIFE | LIFE | Hayat Sigortası | 4 (Life) |

**Note on DASK:** "DASK" is a well-known Turkish acronym (Doğal Afet Sigortaları Kurumu). The display name keeps the recognizable acronym with its expansion in parentheses since end users search for "DASK".

## Steps

### Step 1: Update the DELETE cleanup statement [done]

Open `infra/sql/insurance_db/init.sql`.

Find the cleanup DELETE block (around lines 28–32). Replace the `WHERE name IN (...)` clause with `WHERE code IN (...)`:

**Replace:**
```sql
DELETE FROM insurances WHERE name IN (
    'TRAFFIC', 'CASCO', 'DASK', 'HEALTH', 'LIFE',
    'Zorunlu Trafik Sigortası', 'Kapsamlı Kasko', 'Doğal Afet Sigortası (DASK)',
    'Tamamlayıcı Sağlık Sigortası', 'Hayat Sigortası'
);
```

**With:**
```sql
DELETE FROM insurances WHERE code IN (
    'TRAFFIC', 'CASCO', 'DASK', 'HEALTH', 'LIFE'
);
```

This is cleaner — delete by stable code instead of enumerating every historical name variant.

### Step 2: Update the INSERT seed data [done]

Find the `INSERT INTO insurances` block (around lines 34–39). Replace with human-readable names and explicit codes:

**Replace:**
```sql
INSERT INTO insurances (name, description, type_id, base_premium, is_active) VALUES
('TRAFFIC', 'Mandatory traffic insurance', 1, 1250.00, TRUE),
('CASCO', 'Comprehensive auto insurance', 1, 3500.00, TRUE),
('DASK', 'Earthquake insurance for real estate', 2, 450.00, TRUE),
('HEALTH', 'Complementary health insurance', 3, 2800.00, TRUE),
('LIFE', 'Life insurance', 4, 1500.00, TRUE);
```

**With:**
```sql
INSERT INTO insurances (name, code, description, type_id, base_premium, is_active) VALUES
('Trafik Sigortası', 'TRAFFIC', 'Zorunlu trafik sigortası — yasal olarak yaptırılması gereken temel araç sigortası', 1, 1250.00, TRUE),
('Kasko', 'CASCO', 'Kapsamlı kasko sigortası — aracınızı kaza, çalınma ve doğal afetlere karşı güvence altına alır', 1, 3500.00, TRUE),
('DASK (Doğal Afet Sigortası)', 'DASK', 'Deprem ve doğal afet kaynaklı bina hasarlarına karşı zorunlu konut sigortası', 2, 450.00, TRUE),
('Tamamlayıcı Sağlık Sigortası', 'HEALTH', 'Özel hastanelerde tamamlayıcı sağlık hizmetlerinden indirimli yararlanma imkanı sunar', 3, 2800.00, TRUE),
('Hayat Sigortası', 'LIFE', 'Vefat ve maluliyet durumlarına karşı finansal güvence sağlayan hayat sigortası', 4, 1500.00, TRUE);
```

### Step 3: Update risk factor seed data DELETE [done]

Find the risk factor cleanup block (around line 122). Replace the `WHERE name IN` subquery with `WHERE code IN`:

**Replace:**
```sql
DELETE FROM risk_factor_history WHERE insurance_id IN (SELECT id FROM insurances WHERE name IN ('TRAFFIC','CASCO','DASK','HEALTH','LIFE'));
DELETE FROM risk_factors WHERE insurance_id IN (SELECT id FROM insurances WHERE name IN ('TRAFFIC','CASCO','DASK','HEALTH','LIFE'));
```

**With:**
```sql
DELETE FROM risk_factor_history WHERE insurance_id IN (SELECT id FROM insurances WHERE code IN ('TRAFFIC','CASCO','DASK','HEALTH','LIFE'));
DELETE FROM risk_factors WHERE insurance_id IN (SELECT id FROM insurances WHERE code IN ('TRAFFIC','CASCO','DASK','HEALTH','LIFE'));
```

### Step 4: Update risk factor seed data DO blocks [done]

Find the DO block (around lines 126–191) that seeds risk factors. Replace every `WHERE name = '...'` with `WHERE code = '...'`:

**All occurrences to replace:**
- Line ~131: `WHERE name = 'TRAFFIC'` → `WHERE code = 'TRAFFIC'`
- Line ~145: `WHERE name = 'CASCO'` → `WHERE code = 'CASCO'`
- Line ~159: `WHERE name = 'DASK'` → `WHERE code = 'DASK'`
- Line ~173: `WHERE name = 'HEALTH'` → `WHERE code = 'HEALTH'`
- Line ~183: `WHERE name = 'LIFE'` → `WHERE code = 'LIFE'`

There are exactly 5 such occurrences (one per insurance product in the risk factor DO block). Update all of them.

### Step 5: Verify no other `name`-based insurance lookups remain [done]

Search `infra/sql/insurance_db/init.sql` for any remaining pattern `WHERE.*insurances.*name` or `WHERE name =` that still references old insurance codes. Every insurance lookup in seed data should now use `code`.

Run a manual grep/search for the pattern `'TRAFFIC'` in `init.sql` — after Step 4, the only remaining occurrences should be in the `code IN` lists and the INSERT values, NOT in display-oriented name columns.

## Acceptance Criteria

- [x] `insurances` seed INSERT includes both `name` (human-readable) and `code` (technical identifier) columns
- [x] Insurance names are proper Turkish: "Trafik Sigortası", "Kasko", "DASK (Doğal Afet Sigortası)", "Tamamlayıcı Sağlık Sigortası", "Hayat Sigortası"
- [x] DELETE cleanup uses `WHERE code IN (...)` — stable across future name changes
- [x] Risk factor DELETE uses `WHERE code IN (...)` subquery
- [x] All 5 risk factor DO blocks use `WHERE code = '...'` in the SELECT
- [x] No insurance `name`-based string matching remains in `init.sql`
- [x] Docker Compose down/up produces clean state with human-readable names
- [x] Docker Compose restart (without down) is idempotent — DELETE + INSERT produce same rows
