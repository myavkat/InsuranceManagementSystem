# 00 — Overview: Remove Multi-Company Concept from Insurance & Estimation Services

## Goal

Refocus the system from a **multi-company insurance brokerage** to a **single-provider insurance premium estimator**. The system itself is the sole insurance provider — it does not compare or aggregate across multiple companies. The `InsuranceCompany` entity, the `companyId` foreign key, and all company-related API endpoints are being removed.

## What Changes

- The `InsuranceCompany` entity, table, repository, DTOs, and REST endpoints are deleted.
- The `Insurance` entity loses its `companyId` foreign key — it represents a product offered by this single system.
- The `Estimation` entity loses its `companyId` column — customers no longer select a company when requesting an estimation.
- Five event POJOs in `common-message` lose their `companyId` field.
- The SAGA premium calculation in insurance-service looks up insurance products by `typeId` only (since there is only one provider).

## What Does NOT Change

- `InsuranceType` entity (TRAFFIC, CASCO, DASK, HEALTH, LIFE) — product types are still valid for a single provider.
- Core risk-calculation formulas in `InsuranceSagaConsumer.calculatePremium()` — only the insurance lookup changes, not the math.
- customer-service, vehicle-service, realestate-service, reference-data-service — zero changes.
- API Gateway, service discovery, infrastructure/deployment config — zero changes.
- Legacy monolith (`backend/`) — zero changes.

## Plan Files & Implementation Order

Build order matters: `common-message` is a dependency of all services, so its changes must happen first. Insurance-service depends on common-message. Estimation-service depends on both.

| Order | File | What It Covers |
|-------|------|----------------|
| 1 | `01-insurance-service-remove-company.md` | common-message event POJO changes + insurance-service: entity, DTO, controller, service, repository, SAGA consumer, event publisher, SQL init script, all tests |
| 2 | `02-estimation-service-remove-company.md` | estimation-service: entity, DTO, service, SQL init script, all tests |

Each file is fully self-contained — the implementing agent needs to read only that one file.

## Cross-Service Contract Changes

The following event schemas change (defined in `common-message`, consumed by both services):

### EstimationRequestedEvent — BEFORE
```json
{
  "customerId": "uuid", "vehicleId": "uuid|null", "realEstateId": "uuid|null",
  "insuranceTypeId": 1, "companyId": "uuid"
}
```
### EstimationRequestedEvent — AFTER
```json
{
  "customerId": "uuid", "vehicleId": "uuid|null", "realEstateId": "uuid|null",
  "insuranceTypeId": 1
}
```

### PremiumCalculatedEvent — BEFORE
```json
{
  "premium": 1250.00, "breakdown": {"basePremium": 1250.00, "riskFactor": 1.00},
  "insuranceTypeId": 1, "companyId": "uuid", "customerId": "uuid", "vehicleId": "uuid|null"
}
```
### PremiumCalculatedEvent — AFTER
```json
{
  "premium": 1250.00, "breakdown": {"basePremium": 1250.00, "riskFactor": 1.00},
  "insuranceTypeId": 1, "customerId": "uuid", "vehicleId": "uuid|null"
}
```

### InsuranceCreatedEvent / InsuranceUpdatedEvent — BEFORE
```json
{ "insuranceId": "uuid", "typeId": 1, "companyId": "uuid", "name": "..." }
```
### InsuranceCreatedEvent / InsuranceUpdatedEvent — AFTER
```json
{ "insuranceId": "uuid", "typeId": 1, "name": "..." }
```

### InsuranceDeletedEvent — BEFORE
```json
{ "insuranceId": "uuid", "typeId": 1, "companyId": "uuid" }
```
### InsuranceDeletedEvent — AFTER
```json
{ "insuranceId": "uuid", "typeId": 1 }
```

## Verification Checklist

After ALL plan files are implemented:

- [ ] `grep -ri "companyid\|company_id\|insurancecompany\|insurance_company" services/insurance-service/src/` returns zero results
- [ ] `grep -ri "companyid\|company_id\|insurancecompany\|insurance_company" services/estimation-service/src/` returns zero results
- [ ] `grep -ri "companyid\|company_id\|insurancecompany\|insurance_company" common/common-message/src/` returns zero results
- [ ] `grep -ri "companyid\|company_id\|insurancecompany\|insurance_company" infra/sql/insurance_db/ infra/sql/estimation_db/` returns zero results
- [ ] `./gradlew :common-message:compileJava :common-message:test` passes
- [ ] `./gradlew :insurance-service:compileJava :insurance-service:test` passes
- [ ] `./gradlew :estimation-service:compileJava :estimation-service:test` passes
