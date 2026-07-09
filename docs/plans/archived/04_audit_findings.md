# Audit Report: String Dependencies on Insurance Name Values

**Plan:** 04 — Insurance String Dependency Audit
**Date:** 2026-07-09
**Branch:** feat/insurance-human-readable-names
**Status:** Complete

---

## 1. Executive Summary

An exhaustive search of the entire codebase (backend Java, frontend TypeScript, SQL, and configuration files) was conducted to find any code that performs string matching or comparison against insurance `name` values. Such code would break now that display names have changed from technical codes (TRAFFIC, CASCO, DASK, HEALTH, LIFE) to human-readable Turkish names.

**Result: Zero findings in production code.** No production code paths match on insurance `name` strings. All production logic uses `insuranceId` (UUID), `typeId` (Integer), or the `code` field for lookups and branching.

**Result: 4 test files contain hardcoded name strings that will require updates.** All are already covered by Plan 03 (`03_insurance_tests_update.md`).

---

## 2. Search Methodology

| Search | Scope | Pattern(s) | Result |
|--------|-------|------------|--------|
| **A** | Java production code (`**/src/main/**/*.java`) | `"TRAFFIC"`, `"CASCO"`, `"DASK"`, `"HEALTH"`, `"LIFE"` | Clean — 0 matches |
| **B** | Java test code (`**/src/test/**/*.java`) | Same as A | 4 files with matches (see Section 4) |
| **C** | SQL files (`infra/sql/**/*.sql`, excluding `insurance_db/init.sql`) | `TRAFFIC|CASCO|DASK|HEALTH|LIFE` | Clean — only `insurance_db/init.sql` (excluded by scope) |
| **D** | Config files (`**/*.yml`, `**/*.yaml`, `**/*.properties`, `**/*.json`) | `TRAFFIC|CASCO|DASK` | Clean — 0 matches |
| **E** | Frontend TypeScript/TSX (`frontend-next/src/**/*.{ts,tsx}`) | `"TRAFFIC"`, `"CASCO"`, `"DASK"`, `"HEALTH"`, `"LIFE"` | Clean — 0 matches |
| **F** | Estimation form (`estimation-form.tsx`) | Manual code review for type-based branching | Verified — uses `selectedInsuranceTypeId` (numeric), not name strings |
| **G** | `code` field usage (`**/*.java`, `**/*.{ts,tsx}`) | `getCode()`, `.code(`, `"code"`, `.code` | Field exists and is used properly in production code |
## 3. Findings: Production Code

**No findings.** All production code paths are clean:

- The **insurance service** uses `insuranceId` (UUID) for lookups, `typeId` (Integer) for filtering, and `code` (String) as a unique identifier field -- never the `name` field for matching/branching.
- The **estimation service** fetches insurance by UUID from `InsuranceServiceClient` and stores `insuranceId` (UUID) on the `Estimation` entity -- never uses insurance `name` for logic.
- The **frontend** uses `selectedInsuranceTypeId` (numeric) for step-3 branching in the estimation form, `insurance.id` (UUID) for API calls, and `insurance.name` only for display (dropdown labels, detail cards).
- No **configuration files** (YAML, properties, JSON) reference insurance name strings.
- No **SQL files** outside of `insurance_db/init.sql` reference insurance name strings. The `init.sql` is the seed data source and is handled by Plan 02.

---

## 4. Findings: Test Code

### 4.1 Insurance Service Tests

| File | Line(s) | Issue | Severity | Covered by Plan 03? |
|------|---------|-------|----------|---------------------|
| `.../InsuranceServiceApplicationTests.java` | 73-77 | `InsuranceType` constructor uses old names (`"TRAFFIC"`, `"CASCO"`, `"DASK"`, `"HEALTH"`, `"LIFE"`) | HIGH -- test will mis-seed DB | Yes (Part A1) |
| `.../InsuranceServiceApplicationTests.java` | 230-234 | `.jsonPath("$.data[0].name").isEqualTo("TRAFFIC")` asserts on old type name strings | HIGH -- assertion will fail | Yes (Part A2) |
| `.../InsuranceServiceApplicationTests.java` | 114-118 | `Insurance.builder()` missing `.code()` -- new non-nullable field | MEDIUM -- Hibernate constraint violation | Yes (Part A4) |
| `.../InsuranceSagaConsumerTest.java` | 95 | `InsuranceType(1, "TRAFFIC")` uses old name | HIGH -- test will mis-seed DB | Yes (Part B1) |
| `.../InsuranceSagaConsumerTest.java` | 97-101 | `Insurance.builder()` missing `.code()` -- new non-nullable field | MEDIUM -- Hibernate constraint violation | Yes (Part B2) |
| `.../InsuranceServiceTest.java` | 74-83 | `createInsurance()` helper `Insurance.builder()` missing `.code()` | LOW -- Mockito unit test, no DB | Yes (Part C1) |
| `.../InsuranceServiceTest.java` | 212-216 | `inactiveInsurance` `Insurance.builder()` missing `.code()` | LOW -- Mockito unit test, no DB | Yes (Part C2) |

### 4.2 Estimation Service Tests

| File | Line(s) | Issue | Severity | Covered by Plan 03? |
|------|---------|-------|----------|---------------------|
| `.../EstimationServiceApplicationTests.java` | 75 | Mock returns `InsuranceInfo(..., "TRAFFIC", ...)` with old name | MEDIUM -- mock value semantically incorrect | Yes (Part D, File 3) |
| `.../EstimationServiceTest.java` | 102, 137, 159 | Mock returns `InsuranceInfo(..., "TRAFFIC", ...)` with old name | MEDIUM -- mock value semantically incorrect | Yes (Part D, File 1) |
| `.../EstimationServiceIntegrationTest.java` | 129 | Mock returns `InsuranceInfo(..., "TRAFFIC", ...)` with old name | MEDIUM -- mock value semantically incorrect | Yes (Part D, File 2) |
| `.../EstimationServiceIntegrationTest.java` | 214 | Mock returns `InsuranceInfo(..., "DASK", ...)` with old name | MEDIUM -- mock value semantically incorrect | Yes (Part D, File 2) |
| `.../SagaE2ETest.java` | 131 | Mock returns `InsuranceInfo(..., "TRAFFIC", ...)` with old name | MEDIUM -- mock value semantically incorrect | Yes (Part D, File 4) |

### 4.3 Cross-Reference Against Plan 03

All test code findings are already documented and addressed by Plan `03_insurance_tests_update.md`:

- **Part A** (InsuranceServiceApplicationTests): Covers old type names in seed data and assertions -- HIGH severity items covered.
- **Part B** (InsuranceSagaConsumerTest): Covers old type name in seed data and missing `.code()` -- HIGH and MEDIUM items covered.
- **Part C** (InsuranceServiceTest): Covers missing `.code()` -- LOW items covered.
- **Part D** (estimation-service tests): Covers all 4 estimation test files' old mock names -- MEDIUM items covered.

**No additional test files were found beyond those listed in Plan 03.** The audit confirms Plan 03 is comprehensive.

---

## 5. Clean Bill of Health Items

The following areas were searched and confirmed to have **zero** string-matching dependencies on insurance name values:

- **Production Java code** (`src/main/**/*.java`): No matches for `"TRAFFIC"`, `"CASCO"`, `"DASK"`, `"HEALTH"`, or `"LIFE"` as string literals for comparison, branching, switch cases, or map lookups.
- **Frontend TypeScript code** (`frontend-next/src/**/*.{ts,tsx}`): No matches for any old insurance name string in any context.
- **Estimation form** (`estimation-form.tsx`): Verified that all step-3 type-based branching uses `selectedInsuranceTypeId === N` (numeric) -- lines 123-126, 325, 379, 433. Insurance `name` is used for display only (lines 301, 442, 473). Insurance `id` (UUID) is used for API lookups and routing.
- **SQL files outside `insurance_db/init.sql`**: No other SQL scripts reference insurance name values.
- **Configuration files** (`*.yml`, `*.yaml`, `*.properties`, `*.json`): No routing rules, mappings, or configuration values reference insurance names by string.

---

## 6. Code Field Usage Baseline (Search G)

The `code` field on the `Insurance` entity is already implemented:

| Location | Usage | Purpose |
|----------|-------|---------|
| `Insurance.java:28-29` | `@Column(nullable = false, unique = true, length = 50) private String code;` | Entity field |
| `InsuranceRequest.java:20` | `private String code;` (optional) | Optional input field |
| `InsuranceResponse.java:20` | `.code(insurance.getCode())` | API response field |
| `InsuranceService.java:85-91` | Auto-generates `code` from `name` if not provided | Service logic |

The `code` field is distinct from `name` -- it serves as a stable unique identifier that will NOT change when display names are localized. This is the correct field for any code-based matching going forward.

---

## 7. Recommendations

1. **No production code changes needed.** All production logic correctly uses UUID, typeId, or code for matching -- never name strings.

2. **Proceed with Plan 03 for test updates.** All test findings are already documented and addressed in Plan `03_insurance_tests_update.md`.

3. **Future-proofing guidance:** Any new code that needs to match or identify an insurance product or type should use:
   - `insuranceId` (UUID) for entity identity
   - `typeId` (Integer) for type-based branching
   - `code` field (String) for any code-based matching or display of a stable short identifier
   - Never use `name` for programmatic logic -- it is a display-only field that may change
