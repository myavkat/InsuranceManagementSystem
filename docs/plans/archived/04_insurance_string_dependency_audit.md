# Plan 04: Audit String Dependencies on Insurance Names

## Objective

Audit the entire codebase (frontend and backend) for any logic that does string comparison or matching against insurance `name` values — specifically places that would break or behave incorrectly now that display names have changed from technical codes (TRAFFIC, CASCO, DASK, HEALTH, LIFE) to human-readable Turkish names. **Flag** any such dependencies; fix those that are in scope.

## Dependency

Independent — can run in parallel with Plans 01–03. However, any fixes resulting from audit findings should be applied after Plans 01–02 are complete to avoid conflicts.

## Files to Read First

- None required before starting — this is a pure search/audit task.
- Reference `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` if API endpoint details are needed.
- Reference `docs/outlines/14_EVENT_SCHEMA_REGISTRY.md` if event payload fields need verification.

## Step 1: Search backend for insurance name string literals

Run the following grep searches across the entire repository (excluding `node_modules/`, `docs/plans/archived/`, `docs/tasks/archived/`, and `infra/sql/` which is handled by Plan 02):

### Search A: Java hardcoded insurance name strings

**Pattern:** `"TRAFFIC"`, `"CASCO"`, `"DASK"`, `"HEALTH"`, `"LIFE"`

Restrict to production code: `**/src/main/**/*.java`

**What to look for:**
- String comparison: `equals("TRAFFIC")`, `equalsIgnoreCase("CASCO")`, `.name().equals(...)`
- Switch cases: `case "TRAFFIC":`
- Map lookups: `map.get("DASK")`
- Conditional branching based on insurance name

**Expected finding:** From the pre-analysis, there should be **zero** production code that matches on insurance name strings. The estimation form's step 3 branching uses `selectedInsuranceTypeId === 1` (type ID, not name). The backend uses `insuranceId` (UUID) for lookups.

If any match is found in production code:
1. Flag it in the report below
2. Recommend fix: use `code` field or UUID `id` instead of name string comparison

### Search B: Test files with old name assertions

**Pattern:** `"TRAFFIC"`, `"CASCO"`, `"DASK"`, `"HEALTH"`, `"LIFE"`

Restrict to test code: `**/src/test/**/*.java`

List every file found. Cross-reference against Plan 03 — the test files listed in Plan 03 should already be covered. Any additional files found here need to be added to Plan 03 or flagged.

### Search C: SQL files (outside init.sql)

**Pattern:** `TRAFFIC|CASCO|DASK|HEALTH|LIFE`

Restrict to: `infra/sql/**/*.sql` (excluding `insurance_db/init.sql`)

Check if other database init scripts reference insurance names. Expected: none (other services don't have insurance seed data).

### Search D: Configuration files

**Pattern:** `TRAFFIC|CASCO|DASK`

Search in: `**/*.yml`, `**/*.yaml`, `**/*.properties`, `**/*.json` (excluding `node_modules/` and build output)

Check if any configuration maps or routing rules reference insurance names by string.

## Step 2: Search frontend for insurance name string matching

### Search E: TypeScript/JavaScript hardcoded insurance name strings

**Pattern:** `"TRAFFIC"`, `"CASCO"`, `"DASK"`, `"HEALTH"`, `"LIFE"`, `'TRAFFIC'`, `'CASCO'`, `'DASK'`, `'HEALTH'`, `'LIFE'`

Restrict to: `frontend/src/**/*.{ts,tsx}`

**What to look for:**
- String comparison: `name === "TRAFFIC"`, `name == "CASCO"`
- Conditional rendering: `{insurance.name === "TRAFFIC" ? ... : ...}`
- Filter/group-by on name string
- Hardcoded display fallbacks: `insurance.name || "TRAFFIC"`

**Expected finding (from pre-analysis):** The frontend does NOT do string matching on insurance names. It uses:
- `insurance.typeId` for logic (step 3 branching in estimation form: `selectedInsuranceTypeId === 1`)
- `insurance.name` for display only (rendered in dropdowns, lists, detail pages)
- `insurance.id` (UUID) for API lookups and routing

If any match is found:
1. Flag it in the report below
2. Recommend fix: use `typeId` (numeric) for type-based logic, `insuranceId` for entity lookups, and `code` field (now available in API response) for any code-based matching

### Search F: Frontend estimation/insurance type-based logic audit

Open `frontend/src/components/features/estimations/estimation-form.tsx`.

Verify that lines 123–126 use `selectedInsuranceTypeId` (numeric) for step 3 branching:
```typescript
const canProceedStep3 =
    (selectedInsuranceTypeId === 1 && watchedVehicleId !== "") ||    // Vehicle type
    (selectedInsuranceTypeId === 2 && watchedRealEstateId !== "") || // Real Estate type
    (selectedInsuranceTypeId === 3 || selectedInsuranceTypeId === 4); // Health/Life — no asset needed
```

This is correct — it uses type ID, not name. **No change needed.**

Also verify that lines 325, 379, 433 use the same pattern (`selectedInsuranceTypeId === 1`, etc.) for conditional rendering of asset selectors.

## Step 3: Search for any `code`-based matching already in use

### Search G: Existing `code` field usage

**Pattern:** `getCode()`, `.code(`, `"code"`, `\.code`

Search in: `**/*.java`, `**/*.ts`, `**/*.tsx` (excluding `node_modules/`, `docs/`)

This establishes baseline: before Plan 01, there should be zero references to an insurance `code` field. If any exist, they were leftover from a prior attempt.

## Step 4: Compile audit report

Create a summary with these sections:

### Findings: Production code

| File | Line | Issue | Severity | Recommendation |
|------|------|-------|----------|----------------|
| (list each finding) | | | HIGH/MEDIUM/LOW | |

### Findings: Test code

| File | Line | Issue | Covered by Plan 03? |
|------|------|-------|---------------------|
| (list each finding) | | | Yes/No |

### Clean bill of health items

List the areas searched that had **zero** findings:
- Production Java code: no insurance name string matching
- Frontend TypeScript: no insurance name string matching
- SQL (outside insurance_db): no insurance name references
- Config files: no insurance name references

## Acceptance Criteria

- [x] All production code paths that match on insurance `name` string values are identified and flagged
- [x] All frontend code paths that match on insurance `name` string values are identified and flagged
- [x] The estimation form type-based branching is confirmed to use `typeId` (numeric), not `name` (string) — safe
- [x] Any found dependencies are documented with a recommended fix (use `code`, `id`, or `typeId`)
- [x] Test files with hardcoded name strings are cross-referenced against Plan 03 coverage
- [x] A summary report is written to `docs/plans/04_audit_findings.md` (or appended to this plan as a completed checklist section)
