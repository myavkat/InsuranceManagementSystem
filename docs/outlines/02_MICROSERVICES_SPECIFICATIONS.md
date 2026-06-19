# Microservice Specifications Outline

## Common Conventions (All Services)

- **Spring Boot MVC** (`@RestController`, not `@Controller`)
- **Spring Data JPA** with Hibernate ORM
- **Lombok** (`@Data`, `@AllArgsConstructor`, `@NoArgsConstructor` on entities/DTOs)
- **PostgreSQL** via `spring.datasource.url=jdbc:postgresql://localhost:5432/<service-db>`
- **API response envelope** — standard `ApiResponse<T>` with `success`, `message`, `data`, `timestamp`
- **Gradle** build, JAR packaging, JUnit 5 + Testcontainers for integration tests
- **`application.yml`** configuration per service

---

## 1. Auth Service

**Database:** `auth_db`

**Entities:**
- `User` — id, username, email, password hash, roles, enabled, created_at, updated_at
- `Role` — id, name (`ADMIN`, `AGENT`, `CUSTOMER`)

**Endpoints (via Gateway):**
- `POST /api/auth/register` — register new user
- `POST /api/auth/login` — authenticate, return JWT (access + refresh)
- `POST /api/auth/refresh` — refresh access token
- `POST /api/auth/validate` — validate token (used by Gateway)

**Key Details:**
- BCrypt password hashing
- JWT with configurable expiry (access: 15min, refresh: 7d)
- No SAGA events — synchronous auth only

---

## 2. Customer Service

**Database:** `customer_db`

**Entities:**
- `Customer` — id, firstName, lastName, nationalId (TCKN), email, phone, birthDate, address, cityId, professionId, created_at, updated_at

**Endpoints (via Gateway):**
- `GET /api/customers` — list/search (filter by name, nationalId)
- `GET /api/customers/{id}` — get by ID
- `POST /api/customers` — create
- `PUT /api/customers/{id}` — update
- `DELETE /api/customers/{id}` — soft-delete

**SAGA Consumers:**
- `EstimationRequested` — validate customer existence, publish `CustomerValidated` / `CustomerInvalidated`
- `EstimationFailed` — (no reversible action for read-only validation)

---

## 3. Vehicle Service

**Database:** `vehicle_db`

**Entities:**
- `Vehicle` — id, plate, chassisNumber, licenseFirstDate, carBrandId, carModelId, carEngineId, carFuelTypeId, carTypeId, carPackageId, customerId, created_at, updated_at
- `CarBrand` — id, name
- `CarModel` — id, name, carBrandId
- `CarEngine` — id, name, volume, power
- `CarFuelType` — id, name
- `CarType` — id, name
- `CarPackage` — id, name

**Endpoints (via Gateway):**
- `GET /api/vehicles` — list/search
- `GET /api/vehicles/{id}` — get by ID
- `POST /api/vehicles` — create
- `PUT /api/vehicles/{id}` — update
- `DELETE /api/vehicles/{id}` — delete
- `GET /api/vehicles/brands` — list car brands
- `GET /api/vehicles/brands/{brandId}/models` — list models by brand
- `GET /api/vehicles/engines` — list engines
- `GET /api/vehicles/fuel-types` — list fuel types
- `GET /api/vehicles/types` — list types
- `GET /api/vehicles/packages` — list packages

**SAGA Consumers:**
- `EstimationRequested` — validate vehicle, publish `VehicleValidated` / `VehicleInvalidated`
- `EstimationFailed` — (no reversible action for read-only validation)

---

## 4. RealEstate Service

**Database:** `realestate_db`

**Entities:**
- `RealEstate` — id, address, cityId, district, squareMeters, constructionYear, constructionTypeId, luxuryClassId, usageTypeId, customerId, created_at, updated_at
- `RealEstateConstructionType` — id, name
- `RealEstateLuxuryClass` — id, name
- `RealEstateUsageType` — id, name

**Endpoints (via Gateway):**
- `GET /api/real-estate` — list/search
- `GET /api/real-estate/{id}` — get by ID
- `POST /api/real-estate` — create
- `PUT /api/real-estate/{id}` — update
- `DELETE /api/real-estate/{id}` — delete
- `GET /api/real-estate/construction-types` — list construction types
- `GET /api/real-estate/luxury-classes` — list luxury classes
- `GET /api/real-estate/usage-types` — list usage types

---

## 5. Insurance Service

**Database:** `insurance_db`

**Entities:**
- `Insurance` — id, name, description, typeId, companyId, basePremium, isActive, created_at, updated_at
- `InsuranceType` — id, name (TRAFFIC, CASCO, DASK, HEALTH, LIFE, etc.)
- `InsuranceCompany` — id, name, rating, isActive

**Endpoints (via Gateway):**
- `GET /api/insurances` — list insurance products
- `GET /api/insurances/{id}` — get by ID
- `POST /api/insurances` — create
- `PUT /api/insurances/{id}` — update
- `DELETE /api/insurances/{id}` — soft-delete
- `GET /api/insurances/types` — list insurance types
- `GET /api/insurances/companies` — list insurance companies

**SAGA Consumers:**
- `CustomerValidated` + `VehicleValidated` (same sagaId) — calculate premium, publish `PremiumCalculated` / `CalculationFailed`
- `EstimationFailed` — (no reversible action for calculation)

---

## 6. Estimation Service

**Database:** `estimation_db`

**Entities:**
- `Estimation` — id, sagaId, customerId, vehicleId (nullable), realEstateId (nullable), insuranceTypeId, companyId, status (STARTED, COMPLETED, REJECTED), premium, details, createdAt, updatedAt

**Endpoints (via Gateway):**
- `POST /api/estimations` — create estimation request (starts SAGA)
- `GET /api/estimations/{id}` — get estimation status
- `GET /api/estimations` — list estimations (filter by customer, status, date range)

**SAGA Producers:**
- Publishes `EstimationRequested` to `estimation.saga`

**SAGA Consumers:**
- `PremiumCalculated` — update estimation to `COMPLETED` with premium
- `*Invalidated`, `CalculationFailed` — set estimation `REJECTED`, publish `EstimationFailed`
- Internal timeout scheduler — publishes `EstimationFailed` if no terminal event within window

---

## 7. Reference Data Service

**Database:** `reference_data_db`

**Entities:**
- `City` — id, name, plateCode
- `Profession` — id, name

**Endpoints (via Gateway):**
- `GET /api/reference-data/cities` — list cities
- `GET /api/reference-data/professions` — list professions

**RabbitMQ RPC:**
- Consumes `rpc.reference-data` requests for synchronous lookups by other services
