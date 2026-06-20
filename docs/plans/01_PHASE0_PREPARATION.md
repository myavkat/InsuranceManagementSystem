# Plan: Phase 0 — Preparation & Base Setup

**Task:** `docs/tasks/01_PHASE0_PREPARATION.md`

---

## 1. Create Monorepo Structure

- [x] Create root directory layout:
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
- [x] For each service under `services/`, create standard Java directory tree:
  ```
  services/<name>/
    build.gradle.kts
    settings.gradle.kts
    src/main/java/com/insurancemanagementsystem/<service>/
    src/main/resources/
    src/test/java/com/insurancemanagementsystem/<service>/
    Dockerfile
  ```
- [x] Create root `settings.gradle.kts` including all service subprojects (optional unified build).
- [x] Create root `.env` template file with placeholders for DB passwords, broker addresses, etc.

---

## 2. Docker Dev Environment Setup

- [x] Create `infra/docker/docker-compose.yml` with:
  - [x] **PostgreSQL × 8 services**: `auth-db`, `customer-db`, `vehicle-db`, `realestate-db`, `insurance-db`, `estimation-db`, `reference-data-db`, `gateway-db`
    - Each with: image `postgres:16`, named volume, health check, init script volume mount (`./infra/sql/<db>/init.sql`), distinct ports (5432-5439).
  - [x] **Zookeeper**: image `confluentinc/cp-zookeeper:latest`, port 2181.
  - [x] **Kafka**: image `confluentinc/cp-kafka:latest`, port 9092, depends on Zookeeper.
  - [x] **RabbitMQ**: image `rabbitmq:3-management-alpine`, ports 5672 (AMQP) + 15672 (management UI).
  - [x] **Redis**: image `redis:7-alpine`, port 6379 (for Gateway rate limiting).
  - [x] Shared network `insurance-net`, `.env` file for configurable credentials.
  - [x] Health checks for all services, `depends_on` with condition checks.
- [x] Create `infra/docker/.env` with default ports and credentials.
- [ ] Verify startup: `docker compose up -d` — all 12 containers healthy.
- [x] Create `infra/docker/docker-compose.override.yml` for local dev overrides (hot-reload volumes, debug ports).

---

## 3. Initialize Next.js Project

- [x] Run `npx create-next-app@latest frontend-next --typescript --tailwind --eslint --app --src-dir --import-alias "@/*" --use-npm`.
- [x] Run `npx shadcn@latest init` inside `frontend-next/` (default config, `components.json` created).
- [x] Install runtime deps: `npm install zustand @tanstack/react-query zod react-hook-form @hookform/resolvers`.
- [x] Install shadcn UI primitives: `npx shadcn@latest add button input card dialog table select badge skeleton`.
- [x] Create `.env.local` with:
  ```
  GATEWAY_URL=http://localhost:8080
  NEXT_PUBLIC_GATEWAY_URL=http://localhost:8080
  ```
- [x] Update `next.config.ts` to allow images from Gateway domain.
- [ ] Verify: `npm run dev` starts without errors on `localhost:3000`.

---

## 4. Database Schema Design

- [x] Create `infra/sql/auth_db/init.sql`:
  - [x] Table `users`, `roles`, `user_roles`, `refresh_tokens` with indexes and seeds.

- [x] Create `infra/sql/customer_db/init.sql`:
  - [x] Table `customers` with indexes on `national_id`, `last_name`, `email`.

- [x] Create `infra/sql/vehicle_db/init.sql`:
  - [x] Tables: `car_brands`, `car_models`, `car_engines`, `car_fuel_types`, `car_types`, `car_packages`, `vehicles` with indexes and full seed data.

- [x] Create `infra/sql/realestate_db/init.sql`:
  - [x] Tables: `real_estate_construction_types`, `real_estate_luxury_classes`, `real_estate_usage_types`, `real_estates` with seeds.

- [x] Create `infra/sql/insurance_db/init.sql`:
  - [x] Tables: `insurance_types`, `insurance_companies`, `insurances` with indexes and seed data.

- [x] Create `infra/sql/estimation_db/init.sql`:
  - [x] Tables: `estimations`, `saga_events` with indexes.

- [x] Create `infra/sql/reference_data_db/init.sql`:
  - [x] Tables: `cities` (81 Turkish cities), `professions` (35 professions) with seeds.

- [x] Create `infra/sql/gateway_db/init.sql`:
  - [x] Table `rate_limits` with indexes.

---

## 5. Define Event Schemas (common-message module)

- [x] Create `common/common-message/build.gradle.kts` with:
  - [x] Java + Spring Boot dependency management.
  - [x] Dependencies: `jackson-databind`, `jackson-datatype-jsr310`, `lombok`, `validation-api`.
- [x] Create `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventEnvelope.java`:
  - [x] Fields: `sagaId` (UUID), `eventType` (String), `timestamp` (Instant), `traceId` (UUID), `payload` (Object/JsonNode).
- [x] Create base abstract class `BaseEvent.java` with serialization support.
- [x] Create SAGA event POJOs in `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/`:
  - [x] `EstimationRequestedEvent` — customerId, vehicleId/realEstateId, insuranceTypeId, companyId.
  - [x] `CustomerValidatedEvent` — customerId, firstName, lastName.
  - [x] `CustomerInvalidatedEvent` — customerId, reason.
  - [x] `VehicleValidatedEvent` — vehicleId, plate, brand, model.
  - [x] `VehicleInvalidatedEvent` — vehicleId, reason.
  - [x] `PremiumCalculatedEvent` — premium amount, breakdown (Map<String, BigDecimal>), insuranceTypeId.
  - [x] `CalculationFailedEvent` — reason.
  - [x] `EstimationFailedEvent` — original sagaId, reason, failedStep.
- [x] Create domain event POJOs in `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/`:
  - [x] `CustomerCreatedEvent`, `CustomerUpdatedEvent` — customerId, nationalId, email.
  - [x] `VehicleCreatedEvent`, `VehicleUpdatedEvent`, `VehicleDeletedEvent` — vehicleId, plate.
  - [x] `RealEstateCreatedEvent`, `RealEstateUpdatedEvent`, `RealEstateDeletedEvent` — realEstateId.
  - [x] `InsuranceCreatedEvent`, `InsuranceUpdatedEvent` — insuranceId, typeId, companyId.
  - [x] `ReferenceDataChangedEvent` — entityType, changeType.
- [x] Create `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventConstants.java`:
  - [x] Topic name constants: `ESTIMATION_SAGA`, `CUSTOMER_EVENTS`, `VEHICLE_EVENTS`, `REALESTATE_EVENTS`, `INSURANCE_EVENTS`, `REFERENCE_DATA_EVENTS`.
  - [x] Event type name constants matching each event class.
- [x] Create serialization/deserialization unit tests for every event type.
- [ ] Create `common/common-message/build.gradle.kts` artifact publishing config (Maven local or composite build).

---

## 6. Build Reference Skeleton Service

- [x] Copy `services/reference-skeleton/` structure from template, or create fresh:
  - [x] `build.gradle.kts` with:
    - Spring Boot 4.0.6, Java 25.
    - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `lombok`, `postgresql`, `testcontainers`, `spring-cloud-stream`, `spring-cloud-stream-binder-kafka`, `spring-cloud-stream-binder-rabbit`.
    - `io.spring.dependency-management` plugin.
- [x] Standardized API response envelope:
  - [x] `ApiResponse.java` — generic class with `success`, `message`, `data`, `timestamp`.
  - [ ] `SuccessResponse.java`, `ErrorResponse.java` convenience builders (built into ApiResponse static methods).
- [x] Global error handler:
  - [x] `GlobalExceptionHandler.java` — `@ControllerAdvice` handling `MethodArgumentNotValidException`, `EntityNotFoundException`, generic `Exception` → returns standardized `ApiResponse`.
- [x] Sample entity + JPA repository + service + controller:
  - [x] `SampleEntity.java` (id, name, createdAt, updatedAt) with `@Entity`, `@Table`, `@Data`, Lombok.
  - [x] `SampleRepository.java` extending `JpaRepository`.
  - [x] `SampleService.java` with CRUD methods.
  - [x] `SampleController.java` with `@RestController`, `@RequestMapping("/api/samples")`, full CRUD endpoints.
- [x] Kafka binder integration:
  - [x] `application.yml` with Kafka configuration (bootstrap-servers, consumer/producer settings, trusted packages).
  - [x] Sample `MessagePublisher` bean using `StreamBridge`.
  - [x] Sample event consumer `@Bean` `Consumer<String>` with logging.
- [x] RabbitMQ binder integration:
  - [x] `application.yml` with RabbitMQ configuration.
  - [ ] Sample RPC publisher/reply pattern.
- [x] `application.yml` with:
  - [x] Datasource: `jdbc:postgresql://localhost:5432/skeleton_db`.
  - [x] JPA: `hibernate.ddl-auto=validate`, `show-sql=true`.
  - [x] Server port: 9999 (template, override per service).
  - [x] Kafka/RabbitMQ config (point to localhost).
  - [x] Logging: structured JSON with `traceId`, `sagaId` pattern.
- [x] `Dockerfile` — multi-stage build: Gradle build → JRE 25 runtime.
- [x] Integration test with Testcontainers:
  - [x] `@SpringBootTest` + `@Testcontainers` PostgreSQL + Kafka.
  - [ ] Test: create entity via REST → verify in DB → verify event published.
- [ ] Verify: `./gradlew build` passes, `./gradlew test` passes.

---

## Verification

- [ ] `docker compose up -d` — all 12 containers healthy (requires Docker).
- [ ] All 8 `init.sql` scripts apply without errors (verified on disk).
- [ ] `frontend-next` → `npm run dev` starts on `localhost:3000` (requires manual check).
- [ ] `reference-skeleton` → `./gradlew bootRun` starts on port 9999 (requires Gradle wrapper).
- [ ] `common-message` → `./gradlew build` + `./gradlew test` passes (requires Gradle wrapper).
- [x] Monorepo structure committed with `docs/` plans, outlines, stories, tasks.
