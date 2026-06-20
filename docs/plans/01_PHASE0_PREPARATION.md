# Plan: Phase 0 — Preparation & Base Setup

**Task:** `docs/tasks/01_PHASE0_PREPARATION.md`

---

## 1. Create Monorepo Structure

- [ ] Create root directory layout:
  ```
  services/            # All microservices
  services/auth-service/
  services/customer-service/
  services/vehicle-service/
  services/realestate-service/
  services/insurance-service/
  services/estimation-service/
  services/reference-data-service/
  services/api-gateway/
  services/reference-skeleton/
  frontend-next/       # Next.js SSR application
  infra/               # Docker, SQL, IaC
  infra/docker/
  infra/sql/
  infra/k8s/
  common/              # Shared libraries
  common/common-message/
  common/common-test/
  ```
- [ ] For each service under `services/`, create standard Java directory tree:
  ```
  services/<name>/
    build.gradle.kts
    settings.gradle.kts
    src/main/java/com/insurancemanagementsystem/<service>/
    src/main/resources/
    src/test/java/com/insurancemanagementsystem/<service>/
    Dockerfile
  ```
- [ ] Create root `settings.gradle.kts` including all service subprojects (optional unified build).
- [ ] Create root `.env` template file with placeholders for DB passwords, broker addresses, etc.

---

## 2. Docker Dev Environment Setup

- [ ] Create `infra/docker/docker-compose.yml` with:
  - [ ] **PostgreSQL × 8 services**: `auth-db`, `customer-db`, `vehicle-db`, `realestate-db`, `insurance-db`, `estimation-db`, `reference-data-db`, `gateway-db`
    - Each with: image `postgres:16`, named volume, health check, init script volume mount (`./infra/sql/<db>/init.sql`), distinct ports (5432-5439).
  - [ ] **Zookeeper**: image `confluentinc/cp-zookeeper:latest`, port 2181.
  - [ ] **Kafka**: image `confluentinc/cp-kafka:latest`, port 9092, depends on Zookeeper.
  - [ ] **RabbitMQ**: image `rabbitmq:3-management-alpine`, ports 5672 (AMQP) + 15672 (management UI).
  - [ ] **Redis**: image `redis:7-alpine`, port 6379 (for Gateway rate limiting).
  - [ ] Shared network `insurance-net`, `.env` file for configurable credentials.
  - [ ] Health checks for all services, `depends_on` with condition checks.
- [ ] Create `infra/docker/.env` with default ports and credentials.
- [ ] Verify startup: `docker compose up -d` — all 12 containers healthy.
- [ ] Create `infra/docker/docker-compose.override.yml` for local dev overrides (hot-reload volumes, debug ports).

---

## 3. Initialize Next.js Project

- [ ] Run `npx create-next-app@latest frontend-next --typescript --tailwind --eslint --app --src-dir --import-alias "@/*" --use-npm`.
- [ ] Run `npx shadcn@latest init` inside `frontend-next/` (default config, `components.json` created).
- [ ] Install runtime deps: `npm install zustand @tanstack/react-query zod react-hook-form @hookform/resolvers`.
- [ ] Install shadcn UI primitives: `npx shadcn@latest add button input card dialog table select badge skeleton`.
- [ ] Create `.env.local` with:
  ```
  GATEWAY_URL=http://localhost:8080
  NEXT_PUBLIC_GATEWAY_URL=http://localhost:8080
  ```
- [ ] Update `next.config.ts` to allow images from Gateway domain.
- [ ] Verify: `npm run dev` starts without errors on `localhost:3000`.

---

## 4. Database Schema Design

- [ ] Create `infra/sql/auth_db/init.sql`:
  - [ ] Table `users` (id UUID PK, username VARCHAR UNIQUE, email VARCHAR UNIQUE, password_hash VARCHAR, enabled BOOLEAN, account_non_locked BOOLEAN, failed_attempts INT, lock_time TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP).
  - [ ] Table `roles` (id UUID PK, name VARCHAR UNIQUE).
  - [ ] Table `user_roles` (user_id UUID FK, role_id UUID FK, PK composite).
  - [ ] Table `refresh_tokens` (id UUID PK, user_id UUID FK, token_hash VARCHAR, expires_at TIMESTAMP, created_at TIMESTAMP, revoked BOOLEAN).
  - [ ] Indexes on `users.username`, `users.email`, `refresh_tokens.token_hash`.
  - [ ] Seed: admin user (bcrypt hash placeholder), roles `ADMIN`, `AGENT`, `CUSTOMER`.

- [ ] Create `infra/sql/customer_db/init.sql`:
  - [ ] Table `customers` (id UUID PK, first_name VARCHAR, last_name VARCHAR, national_id VARCHAR UNIQUE, email VARCHAR, phone VARCHAR, birth_date DATE, address TEXT, city_id INT, profession_id INT, created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP NULL).
  - [ ] Indexes on `customers.national_id`, `customers.last_name`, `customers.email`.

- [ ] Create `infra/sql/vehicle_db/init.sql`:
  - [ ] Table `car_brands` (id INT PK, name VARCHAR UNIQUE).
  - [ ] Table `car_models` (id INT PK, name VARCHAR, brand_id INT FK → car_brands).
  - [ ] Table `car_engines` (id INT PK, name VARCHAR, volume DECIMAL, power INT).
  - [ ] Table `car_fuel_types` (id INT PK, name VARCHAR).
  - [ ] Table `car_types` (id INT PK, name VARCHAR).
  - [ ] Table `car_packages` (id INT PK, name VARCHAR).
  - [ ] Table `vehicles` (id UUID PK, plate VARCHAR UNIQUE, chassis_number VARCHAR, license_first_date DATE, car_brand_id INT FK, car_model_id INT FK, car_engine_id INT FK, car_fuel_type_id INT FK, car_type_id INT FK, car_package_id INT FK, customer_id UUID, created_at TIMESTAMP, updated_at TIMESTAMP).
  - [ ] Indexes on `vehicles.plate`, `vehicles.chassis_number`, `vehicles.customer_id`.
  - [ ] Seed: sample car brands (Toyota, BMW, Mercedes, Renault, Fiat, Ford, Honda, Hyundai, Volkswagen, Audi), models per brand, engines, fuel types (Gasoline, Diesel, Electric, Hybrid, LPG), types (Sedan, Hatchback, SUV, Coupe, Convertible, Minivan, Pickup), packages (Base, Comfort, Luxury, Sport).

- [ ] Create `infra/sql/realestate_db/init.sql`:
  - [ ] Table `real_estate_construction_types` (id INT PK, name VARCHAR).
  - [ ] Table `real_estate_luxury_classes` (id INT PK, name VARCHAR).
  - [ ] Table `real_estate_usage_types` (id INT PK, name VARCHAR).
  - [ ] Table `real_estates` (id UUID PK, address TEXT, city_id INT, district VARCHAR, square_meters DECIMAL, construction_year INT, construction_type_id INT FK, luxury_class_id INT FK, usage_type_id INT FK, customer_id UUID, created_at TIMESTAMP, updated_at TIMESTAMP).
  - [ ] Indexes on `real_estates.customer_id`.
  - [ ] Seed: construction types (Reinforced Concrete, Steel, Masonry, Wood, Prefabricated), luxury classes (Luxury, High, Middle, Low, Slum), usage types (Residential, Commercial, Industrial, Agricultural).

- [ ] Create `infra/sql/insurance_db/init.sql`:
  - [ ] Table `insurance_types` (id INT PK, name VARCHAR UNIQUE).
  - [ ] Table `insurance_companies` (id UUID PK, name VARCHAR, rating DECIMAL, is_active BOOLEAN).
  - [ ] Table `insurances` (id UUID PK, name VARCHAR, description TEXT, type_id INT FK, company_id UUID FK, base_premium DECIMAL, is_active BOOLEAN, created_at TIMESTAMP, updated_at TIMESTAMP).
  - [ ] Indexes on `insurances.type_id`, `insurances.company_id`.
  - [ ] Seed: insurance types (TRAFFIC, CASCO, DASK, HEALTH, LIFE), 3-4 sample companies, 2-3 products per type.

- [ ] Create `infra/sql/estimation_db/init.sql`:
  - [ ] Table `estimations` (id UUID PK, saga_id UUID UNIQUE, customer_id UUID, vehicle_id UUID NULL, real_estate_id UUID NULL, insurance_type_id INT, company_id UUID, status VARCHAR CHECK IN [STARTED, COMPLETED, REJECTED], premium DECIMAL NULL, details JSONB NULL, created_at TIMESTAMP, updated_at TIMESTAMP).
  - [ ] Table `saga_events` (id UUID PK, saga_id UUID, event_type VARCHAR, received_at TIMESTAMP, processed BOOLEAN, PRIMARY KEY (saga_id, event_type)).
  - [ ] Indexes on `estimations.saga_id`, `estimations.customer_id`, `estimations.status`, `estimations.created_at`.

- [ ] Create `infra/sql/reference_data_db/init.sql`:
  - [ ] Table `cities` (id INT PK, name VARCHAR, plate_code VARCHAR).
  - [ ] Table `professions` (id INT PK, name VARCHAR).
  - [ ] Indexes on `cities.name`, `professions.name`.
  - [ ] Seed: 81 Turkish cities with plate codes (e.g., `1, 'Adana', '01'` → `81, 'Zonguldak', '67'`), 30+ common professions (Doctor, Engineer, Teacher, Lawyer, Accountant, etc.).

- [ ] Create `infra/sql/gateway_db/init.sql`:
  - [ ] Table `rate_limits` (id UUID PK, ip_address VARCHAR, user_id UUID NULL, endpoint VARCHAR, request_count INT, window_start TIMESTAMP).

---

## 5. Define Event Schemas (common-message module)

- [ ] Create `common/common-message/build.gradle.kts` with:
  - [ ] Java + Spring Boot dependency management.
  - [ ] Dependencies: `jackson-databind`, `jackson-datatype-jsr310`, `lombok`, `validation-api`.
- [ ] Create `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventEnvelope.java`:
  - [ ] Fields: `sagaId` (UUID), `eventType` (String), `timestamp` (Instant), `traceId` (UUID), `payload` (Object/JsonNode).
- [ ] Create base abstract class `BaseEvent.java` with serialization support.
- [ ] Create SAGA event POJOs in `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/`:
  - [ ] `EstimationRequestedEvent` — customerId, vehicleId/realEstateId, insuranceTypeId, companyId.
  - [ ] `CustomerValidatedEvent` — customerId, firstName, lastName.
  - [ ] `CustomerInvalidatedEvent` — customerId, reason.
  - [ ] `VehicleValidatedEvent` — vehicleId, plate, brand, model.
  - [ ] `VehicleInvalidatedEvent` — vehicleId, reason.
  - [ ] `PremiumCalculatedEvent` — premium amount, breakdown (Map<String, BigDecimal>), insuranceTypeId.
  - [ ] `CalculationFailedEvent` — reason.
  - [ ] `EstimationFailedEvent` — original sagaId, reason, failedStep.
- [ ] Create domain event POJOs in `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/`:
  - [ ] `CustomerCreatedEvent`, `CustomerUpdatedEvent` — customerId, nationalId, email.
  - [ ] `VehicleCreatedEvent`, `VehicleUpdatedEvent`, `VehicleDeletedEvent` — vehicleId, plate.
  - [ ] `RealEstateCreatedEvent`, `RealEstateUpdatedEvent`, `RealEstateDeletedEvent` — realEstateId.
  - [ ] `InsuranceCreatedEvent`, `InsuranceUpdatedEvent` — insuranceId, typeId, companyId.
  - [ ] `ReferenceDataChangedEvent` — entityType, changeType.
- [ ] Create `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventConstants.java`:
  - [ ] Topic name constants: `ESTIMATION_SAGA`, `CUSTOMER_EVENTS`, `VEHICLE_EVENTS`, `REALESTATE_EVENTS`, `INSURANCE_EVENTS`, `REFERENCE_DATA_EVENTS`.
  - [ ] Event type name constants matching each event class.
- [ ] Create serialization/deserialization unit tests for every event type.
- [ ] Create `common/common-message/build.gradle.kts` artifact publishing config (Maven local or composite build).

---

## 6. Build Reference Skeleton Service

- [ ] Copy `services/reference-skeleton/` structure from template, or create fresh:
  - [ ] `build.gradle.kts` with:
    - Spring Boot 4.0.6, Java 25.
    - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `lombok`, `postgresql`, `testcontainers`, `spring-cloud-stream`, `spring-cloud-stream-binder-kafka`, `spring-cloud-stream-binder-rabbit`.
    - `io.spring.dependency-management` plugin.
- [ ] Standardized API response envelope:
  - [ ] `ApiResponse.java` — generic class with `success`, `message`, `data`, `timestamp`.
  - [ ] `SuccessResponse.java`, `ErrorResponse.java` convenience builders.
- [ ] Global error handler:
  - [ ] `GlobalExceptionHandler.java` — `@ControllerAdvice` handling `MethodArgumentNotValidException`, `EntityNotFoundException`, generic `Exception` → returns standardized `ApiResponse`.
- [ ] Sample entity + JPA repository + service + controller:
  - [ ] `SampleEntity.java` (id, name, createdAt, updatedAt) with `@Entity`, `@Table`, `@Data`, Lombok.
  - [ ] `SampleRepository.java` extending `JpaRepository`.
  - [ ] `SampleService.java` with CRUD methods.
  - [ ] `SampleController.java` with `@RestController`, `@RequestMapping("/api/samples")`, full CRUD endpoints.
- [ ] Kafka binder integration:
  - [ ] `application.yml` with Kafka configuration (bootstrap-servers, consumer/producer settings, trusted packages).
  - [ ] Sample `MessagePublisher` bean using `StreamBridge`.
  - [ ] Sample event consumer `@Bean` `Consumer<EventEnvelope>` with logging.
- [ ] RabbitMQ binder integration:
  - [ ] `application.yml` with RabbitMQ configuration.
  - [ ] Sample RPC publisher/reply pattern.
- [ ] `application.yml` with:
  - [ ] Datasource: `jdbc:postgresql://localhost:5432/skeleton_db`.
  - [ ] JPA: `hibernate.ddl-auto=validate`, `show-sql=true`.
  - [ ] Server port: 9999 (template, override per service).
  - [ ] Kafka/RabbitMQ config (point to localhost).
  - [ ] Logging: structured JSON with `traceId`, `sagaId` pattern.
- [ ] `Dockerfile` — multi-stage build: Gradle build → JRE 25 runtime.
- [ ] Integration test with Testcontainers:
  - [ ] `@SpringBootTest` + `@Testcontainers` PostgreSQL + Kafka + RabbitMQ.
  - [ ] Test: create entity via REST → verify in DB → verify event published.
- [ ] Verify: `./gradlew build` passes, `./gradlew test` passes.

---

## Verification

- [ ] `docker compose up -d` — all 12 containers healthy.
- [ ] Seed data queryable: connect to `reference_data_db` → `SELECT * FROM cities` returns 81 rows.
- [ ] `frontend-next` → `npm run dev` starts on `localhost:3000`.
- [ ] `reference-skeleton` → `./gradlew bootRun` starts on port 9999, REST API responds.
- [ ] All 8 `init.sql` scripts apply without errors.
- [ ] `common-message` → `./gradlew build` + `./gradlew test` passes.
- [ ] Monorepo structure committed with `docs/` plans, outlines, stories, tasks.
