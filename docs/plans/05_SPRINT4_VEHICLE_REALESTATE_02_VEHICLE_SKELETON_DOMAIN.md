# Plan: Sprint 4 — Vehicle & RealEstate — Step 2: Vehicle Service Skeleton + Domain Layer

## Objective
Create the `services/vehicle-service/` module with Gradle build config, application configuration, Spring Boot main class, 7 JPA entities, 7 JPA repositories, update the SQL init script with infrastructure tables, and register in `settings.gradle.kts`.

## Context Files to Read First
1. **`services/customer-service/build.gradle.kts`** — Exact build config template (copy verbatim, same deps)
2. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/CustomerServiceApplication.java`** — Application main class pattern
3. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java`** — Entity pattern (Lombok, @PrePersist/@PreUpdate, Instant timestamps)
4. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/EstimationRepository.java`** — Repository pattern
5. **`services/estimation-service/src/main/resources/application.yml`** — application.yml pattern
6. **`infra/sql/vehicle_db/init.sql`** — Existing DB schema (tables and seed data to match)
7. **`infra/sql/estimation_db/init.sql`** — Source for saga_events + outbox_events DDL to copy (lines 18-44)
8. **`settings.gradle.kts`** — Uncomment `include("services:vehicle-service")`
9. **`docs/outlines/10_JAVA_CONVENTIONS.md`** — Java conventions (Lombok order, Instant timestamps)

## Files to Create

### 1. `services/vehicle-service/build.gradle.kts`

Copy EXACTLY from `services/customer-service/build.gradle.kts`. No changes needed — the dependencies, plugins, BOMs, JaCoCo config are identical across all services.

### 2. `services/vehicle-service/src/main/resources/application.yml`

Follow the `estimation-service` application.yml pattern. Key values:
- **server.port**: `8082`
- **spring.application.name**: `vehicle-service`
- **spring.datasource.url**: `jdbc:postgresql://localhost:5434/vehicle_db`
- **spring.jpa.hibernate.ddl-auto**: `validate`
- **spring.cloud.stream.bindings**: `processVehicleSaga-in-0` → destination `estimation.saga`, group `vehicle-service-group`
- **spring.cloud.stream.dynamicDestinations**: `estimation.saga`
- **spring.kafka.consumer.group-id**: `vehicle-service-group`
- Custom app config at bottom:
```yaml
vehicle:
  outbox:
    poll-interval-ms: 1000
    batch-size: 10
    max-retries: 3
    failed-ttl-minutes: 60

outbox:
  poll-interval-ms: ${vehicle.outbox.poll-interval-ms:1000}
  max-retries: ${vehicle.outbox.max-retries:3}
  failed-ttl-minutes: ${vehicle.outbox.failed-ttl-minutes:60}
```

### 3. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/VehicleServiceApplication.java`

Same pattern as `CustomerServiceApplication` but with `vehicle` package:

```java
package com.insurancemanagementsystem.vehicle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = {
    "com.insurancemanagementsystem.vehicle",
    "com.insurancemanagementsystem.common.web",
    "com.insurancemanagementsystem.common.messaging",
    "com.insurancemanagementsystem.common.config"
})
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.vehicle.entity",
    "com.insurancemanagementsystem.common.entity"
})
public class VehicleServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(VehicleServiceApplication.class, args);
    }
}
```

Note: no `@EnableScheduling` — the outbox relay is auto-configured from `common-message`.

### 4. Entities — Package: `com.insurancemanagementsystem.vehicle.entity`

All entities follow the exact Lombok + JPA pattern from `Estimation.java`:
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name = "...")
```

**4a. `Vehicle.java`** — `@Table(name = "vehicles")`
- `@Id @GeneratedValue(strategy = GenerationType.UUID)` — `UUID id`
- `@Column(name = "plate", length = 20, unique = true, nullable = false)` — `String plate`
- `@Column(name = "chassis_number", length = 50)` — `String chassisNumber`
- `@Column(name = "license_first_date")` — `LocalDate licenseFirstDate`
- `@Column(name = "car_brand_id", nullable = false)` — `Integer carBrandId`
- `@Column(name = "car_model_id", nullable = false)` — `Integer carModelId`
- `@Column(name = "car_engine_id", nullable = false)` — `Integer carEngineId`
- `@Column(name = "car_fuel_type_id", nullable = false)` — `Integer carFuelTypeId`
- `@Column(name = "car_type_id", nullable = false)` — `Integer carTypeId`
- `@Column(name = "car_package_id", nullable = false)` — `Integer carPackageId`
- `@Column(name = "customer_id")` — `UUID customerId`
- `@Column(name = "created_at", updatable = false)` — `Instant createdAt`
- `@Column(name = "updated_at")` — `Instant updatedAt`
- `@PrePersist` sets both timestamps to `Instant.now()`
- `@PreUpdate` sets `updatedAt` to `Instant.now()`

IMPORTANT: Use plain FK columns (Integer/UUID) — do NOT use `@ManyToOne` relationships. The SQL schema uses simple foreign key references.

**4b. `CarBrand.java`** — `@Table(name = "car_brands")`
- `@Id` — `Integer id` (use `@GeneratedValue(strategy = GenerationType.IDENTITY)` — these are seeded from SQL)
- `@Column(name = "name", length = 100, unique = true, nullable = false)` — `String name`

**4c. `CarModel.java`** — `@Table(name = "car_models")`
- `@Id` — `Integer id` (IDENTITY)
- `@Column(name = "name", length = 100, nullable = false)` — `String name`
- `@Column(name = "brand_id", nullable = false)` — `Integer brandId` (plain FK, not @ManyToOne)

**4d. `CarEngine.java`** — `@Table(name = "car_engines")`
- `@Id` — `Integer id` (IDENTITY)
- `@Column(name = "name", length = 100)` — `String name`
- `@Column(name = "volume", precision = 4, scale = 1)` — `BigDecimal volume`
- `@Column(name = "power")` — `Integer power`

**4e. `CarFuelType.java`** — `@Table(name = "car_fuel_types")`
- `@Id` — `Integer id` (IDENTITY)
- `@Column(name = "name", length = 50, unique = true, nullable = false)` — `String name`

**4f. `CarType.java`** — `@Table(name = "car_types")`
- `@Id` — `Integer id` (IDENTITY)
- `@Column(name = "name", length = 50, unique = true, nullable = false)` — `String name`

**4g. `CarPackage.java`** — `@Table(name = "car_packages")`
- `@Id` — `Integer id` (IDENTITY)
- `@Column(name = "name", length = 50, unique = true, nullable = false)` — `String name`

### 5. Repositories — Package: `com.insurancemanagementsystem.vehicle.repository`

All extend `JpaRepository<Entity, PK_Type>` with `@Repository` annotation.

- `VehicleRepository extends JpaRepository<Vehicle, UUID>` — add `Optional<Vehicle> findByPlate(String plate)`, `Page<Vehicle> findByCustomerId(UUID customerId, Pageable pageable)`
- `CarBrandRepository extends JpaRepository<CarBrand, Integer>` — no custom methods needed
- `CarModelRepository extends JpaRepository<CarModel, Integer>` — add `List<CarModel> findByBrandId(Integer brandId)`
- `CarEngineRepository extends JpaRepository<CarEngine, Integer>` — no custom methods
- `CarFuelTypeRepository extends JpaRepository<CarFuelType, Integer>` — no custom methods
- `CarTypeRepository extends JpaRepository<CarType, Integer>` — no custom methods
- `CarPackageRepository extends JpaRepository<CarPackage, Integer>` — no custom methods

### 6. `infra/sql/vehicle_db/init.sql` — Append Infrastructure Tables

Copy the `saga_events` and `outbox_events` table DDL from `infra/sql/estimation_db/init.sql` (lines 18-44) and append to the END of `infra/sql/vehicle_db/init.sql`:

```sql
CREATE TABLE IF NOT EXISTS saga_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(saga_id, event_type)
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID,
    topic VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PUBLISHING','PUBLISHED','FAILED')),
    retry_count INT DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events(status, created_at);
```

### 7. `settings.gradle.kts` — Register Module

Find the commented line `// include("services:vehicle-service")` and uncomment it to:
```kotlin
include("services:vehicle-service")
```

## Key Conventions
- Java 25, Spring Boot 4.0.6, Jackson 3 (`tools.jackson.databind.json.JsonMapper`)
- Lombok order: `@Data @Builder @NoArgsConstructor @AllArgsConstructor` before `@Entity`
- `Instant` for timestamps (`createdAt`, `updatedAt`), `LocalDate` for date-only (`licenseFirstDate`)
- `@Column` annotations with snake_case names matching DB columns exactly
- `@GeneratedValue(strategy = GenerationType.UUID)` for domain entities, `GenerationType.IDENTITY` for reference entities with integer PKs
- Reference entities (CarBrand etc.) have no timestamps (they're static lookup data)
- Plain FK columns (Integer/UUID), NOT `@ManyToOne` — the SQL schema uses simple foreign keys
- `static void main(String[] args)` — no `public` (Java 25 relaxed main)

## Verification

```bash
.\gradlew.bat :services:vehicle-service:compileJava
```

Should compile successfully (no tests yet — those come in Step 5).

## Files Written
- `services/vehicle-service/build.gradle.kts` ✅
- `services/vehicle-service/src/main/resources/application.yml` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/VehicleServiceApplication.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/Vehicle.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/CarBrand.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/CarModel.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/CarEngine.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/CarFuelType.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/CarType.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/CarPackage.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/VehicleRepository.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/CarBrandRepository.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/CarModelRepository.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/CarEngineRepository.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/CarFuelTypeRepository.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/CarTypeRepository.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/CarPackageRepository.java` ✅
- `infra/sql/vehicle_db/init.sql` ✅ (modified — saga_events + outbox_events appended)
- `settings.gradle.kts` ✅ (modified — vehicle-service uncommented)
