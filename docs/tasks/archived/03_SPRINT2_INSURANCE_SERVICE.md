# Task: Sprint 2 — Insurance Service

## Context Anchors
- Read Blueprint: @docs/outlines/01_SYSTEM_ARCHITECTURE.md
- Read Blueprint: @docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md
- Read Blueprint: @docs/outlines/03_SAGA_PATTERN.md
- Read Story: @docs/stories/03_INSURANCE_PRODUCTS.md

## Objective
Extract and implement the Insurance Service — manages insurance products, types, and companies. Implements the premium calculation SAGA consumer.

### Subtasks

1. **Extract Insurance Domain**
   - Create `services/insurance-service/` from the reference skeleton.
   - Entities: `Insurance` (id, name, description, typeId, companyId, basePremium, isActive), `InsuranceType` (id, name), `InsuranceCompany` (id, name, rating, isActive).
   - JPA repositories with standard CRUD and search by name/type/company.

2. **Apply Insurance DB Scripts**
   - Run the `insurance_db` init SQL against PostgreSQL.
   - Seed insurance types (TRAFFIC, CASCO, DASK, HEALTH, LIFE) and sample companies.

3. **Insurance CRUD APIs**
   - `GET /api/insurances` — list with filter by type, company, active status.
   - `GET /api/insurances/{id}` — product detail.
   - `POST /api/insurances` — create product.
   - `PUT /api/insurances/{id}` — update.
   - `DELETE /api/insurances/{id}` — soft-delete.
   - `GET /api/insurances/types` — list types.
   - `GET /api/insurances/companies` — list companies.
   - `POST /api/insurances/companies` — create company.
   - `PUT /api/insurances/companies/{id}` — update company.

4. **Insurance Messaging Integration**
   - Publish `InsuranceCreated`, `InsuranceUpdated` to `insurance.events` (log-compacted, keyed by insuranceId).

5. **Implement Insurance Saga Consumer**
   - Aggregate consumer that listens for both `CustomerValidated` AND `VehicleValidated` (correlated by `sagaId`).
   - Once both events arrive for the same `sagaId`, calculate premium using basePremium + risk factors from customer/vehicle data.
   - Publish `PremiumCalculated` (with premium amount and breakdown) or `CalculationFailed`.
   - Idempotency guard: deduplicate by `(sagaId, eventType)`.

### Deliverables
- Fully functional Insurance Service with CRUD API
- Domain events publishing
- SAGA premium calculation consumer with event correlation
- Unit tests (≥80% coverage)
- Integration tests with Testcontainers
