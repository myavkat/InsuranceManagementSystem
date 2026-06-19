# Task: Sprint 4 — Vehicle & RealEstate Services

## Context Anchors
- Read Blueprint: @docs/outlines/01_SYSTEM_ARCHITECTURE.md
- Read Blueprint: @docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md
- Read Blueprint: @docs/outlines/03_SAGA_PATTERN.md
- Read Story: @docs/stories/05_VEHICLE_MANAGEMENT.md
- Read Story: @docs/stories/06_REAL_ESTATE_MANAGEMENT.md

## Objective
Extract and implement the Vehicle Service and RealEstate Service in parallel. Both follow the same pattern: domain extraction, CRUD, event publishing, and SAGA consumers.

### Subtasks

#### Vehicle Service

1. **Extract Vehicle Domain**
   - Create `services/vehicle-service/` from the reference skeleton.
   - Entities: `Vehicle`, `CarBrand`, `CarModel`, `CarEngine`, `CarFuelType`, `CarType`, `CarPackage`.
   - JPA repositories with cascading lookups (models by brandId, etc.).

2. **Apply Vehicle DB Scripts**
   - Run the `vehicle_db` init SQL against PostgreSQL.
   - Seed car brands, models, engines, fuel types, types, and packages.

3. **Vehicle CRUD APIs**
   - Full CRUD for `Vehicle` (plate, chassisNumber, licenseFirstDate, brand/model/engine/fuel/type/package selections, customerId).
   - Reference endpoints: `GET /api/vehicles/brands`, `GET /api/vehicles/brands/{brandId}/models`, `GET /api/vehicles/engines`, `GET /api/vehicles/fuel-types`, `GET /api/vehicles/types`, `GET /api/vehicles/packages`.
   - Plate format validation (Turkish plate: `XX 1234` or `XX 1234 YY`).
   - Chassis number 17-char alphanumeric validation.

4. **Vehicle Event Integration**
   - Publish domain events to `vehicle.events` on create/update/delete.
   - SAGA consumer: listen for `EstimationRequested` → validate vehicle → publish `VehicleValidated` or `VehicleInvalidated`.
   - Idempotency guard.

#### RealEstate Service

5. **Extract RealEstate Domain**
   - Create `services/realestate-service/` from the reference skeleton.
   - Entities: `RealEstate`, `RealEstateConstructionType`, `RealEstateLuxuryClass`, `RealEstateUsageType`.
   - JPA repositories.

6. **Apply RealEstate DB Scripts**
   - Run the `realestate_db` init SQL.
   - Seed construction types, luxury classes, usage types.

7. **RealEstate CRUD APIs**
   - Full CRUD for `RealEstate` (address, cityId, district, squareMeters, constructionYear, type/class/usage selections, customerId).
   - Reference endpoints: construction-types, luxury-classes, usage-types.
   - Validation: squareMeters > 0, constructionYear not in future.

8. **RealEstate Event Integration**
   - Publish domain events to `realestate.events`.
   - SAGA consumer: listen for `EstimationRequested` → validate real estate → publish `RealEstateValidated` or `RealEstateInvalidated`.
   - Idempotency guard.

### Deliverables
- Vehicle Service with full CRUD + reference data endpoints
- RealEstate Service with full CRUD + reference data endpoints
- Domain events publishing from both services
- SAGA consumers for estimation validation in both services
- Unit tests (≥80% coverage per service)
- Integration tests with Testcontainers
