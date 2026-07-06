# Plan: Sprint 4 — Vehicle & RealEstate — Step 6: RealEstate Service Skeleton + Domain Layer

## Objective
Create the `services/realestate-service/` module with Gradle build config, application configuration, Spring Boot main class, 4 JPA entities, 4 JPA repositories, update the SQL init script with infrastructure tables, and register in `settings.gradle.kts`.

## Context Files to Read First
1. **`services/customer-service/build.gradle.kts`** — Exact build config template (copy verbatim)
2. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/CustomerServiceApplication.java`** — Application main class pattern
3. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java`** — Entity pattern (Lombok, @PrePersist/@PreUpdate, Instant timestamps)
4. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/EstimationRepository.java`** — Repository pattern
5. **`services/estimation-service/src/main/resources/application.yml`** — application.yml pattern
6. **`infra/sql/realestate_db/init.sql`** — Existing DB schema (tables and seed data to match)
7. **`infra/sql/estimation_db/init.sql`** — Source for saga_events + outbox_events DDL to copy (lines 18-44)
8. **`settings.gradle.kts`** — Uncomment `include("services:realestate-service")`
9. **`docs/outlines/10_JAVA_CONVENTIONS.md`** — Java conventions

## Files to Create

### 1. `services/realestate-service/build.gradle.kts`

Copy EXACTLY from `services/customer-service/build.gradle.kts`. Identical across all services.

### 2. `services/realestate-service/src/main/resources/application.yml`

Follow the estimation-service application.yml pattern. Key values:
- **server.port**: `8083`
- **spring.application.name**: `realestate-service`
- **spring.datasource.url**: `jdbc:postgresql://localhost:5435/realestate_db`
- **spring.jpa.hibernate.ddl-auto**: `validate`
- **spring.cloud.stream.bindings**: `processRealEstateSaga-in-0` → destination `estimation.saga`, group `realestate-service-group`
- **spring.cloud.stream.dynamicDestinations**: `estimation.saga`
- **spring.kafka.consumer.group-id**: `realestate-service-group`
- Custom app config:
```yaml
realestate:
  outbox:
    poll-interval-ms: 1000
    batch-size: 10
    max-retries: 3
    failed-ttl-minutes: 60

outbox:
  poll-interval-ms: ${realestate.outbox.poll-interval-ms:1000}
  max-retries: ${realestate.outbox.max-retries:3}
  failed-ttl-minutes: ${realestate.outbox.failed-ttl-minutes:60}
```

### 3. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/RealEstateServiceApplication.java`

```java
package com.insurancemanagementsystem.realestate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = {
    "com.insurancemanagementsystem.realestate",
    "com.insurancemanagementsystem.common.web",
    "com.insurancemanagementsystem.common.messaging",
    "com.insurancemanagementsystem.common.config"
})
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.realestate.entity",
    "com.insurancemanagementsystem.common.entity"
})
public class RealEstateServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(RealEstateServiceApplication.class, args);
    }
}
```

### 4. Entities — Package: `com.insurancemanagementsystem.realestate.entity`

**4a. `RealEstate.java`** — `@Table(name = "real_estates")`
- `@Id @GeneratedValue(strategy = GenerationType.UUID)` — `UUID id`
- `@Column(name = "address", columnDefinition = "TEXT", nullable = false)` — `String address`
- `@Column(name = "city_id", nullable = false)` — `Integer cityId`
- `@Column(name = "district", length = 100)` — `String district`
- `@Column(name = "square_meters", precision = 10, scale = 2)` — `BigDecimal squareMeters`
- `@Column(name = "construction_year")` — `Integer constructionYear`
- `@Column(name = "construction_type_id")` — `Integer constructionTypeId`
- `@Column(name = "luxury_class_id")` — `Integer luxuryClassId`
- `@Column(name = "usage_type_id")` — `Integer usageTypeId`
- `@Column(name = "customer_id")` — `UUID customerId`
- `@Column(name = "created_at", updatable = false)` — `Instant createdAt`
- `@Column(name = "updated_at")` — `Instant updatedAt`
- `@PrePersist` / `@PreUpdate` for timestamps

**4b. `RealEstateConstructionType.java`** — `@Table(name = "real_estate_construction_types")`
- `@Id` — `Integer id` (IDENTITY)
- `@Column(name = "name", length = 100, unique = true, nullable = false)` — `String name`

**4c. `RealEstateLuxuryClass.java`** — `@Table(name = "real_estate_luxury_classes")`
- `@Id` — `Integer id` (IDENTITY)
- `@Column(name = "name", length = 100, unique = true, nullable = false)` — `String name`

**4d. `RealEstateUsageType.java`** — `@Table(name = "real_estate_usage_types")`
- `@Id` — `Integer id` (IDENTITY)
- `@Column(name = "name", length = 100, unique = true, nullable = false)` — `String name`

### 5. Repositories — Package: `com.insurancemanagementsystem.realestate.repository`

- `RealEstateRepository extends JpaRepository<RealEstate, UUID>` — add `Page<RealEstate> findByCustomerId(UUID customerId, Pageable pageable)`
- `RealEstateConstructionTypeRepository extends JpaRepository<RealEstateConstructionType, Integer>`
- `RealEstateLuxuryClassRepository extends JpaRepository<RealEstateLuxuryClass, Integer>`
- `RealEstateUsageTypeRepository extends JpaRepository<RealEstateUsageType, Integer>`

### 6. `infra/sql/realestate_db/init.sql` — Append Infrastructure Tables

Copy the `saga_events` and `outbox_events` table DDL from `infra/sql/estimation_db/init.sql` (lines 18-44) and append to the END of `infra/sql/realestate_db/init.sql`. See Step 2 plan for exact SQL.

### 7. `settings.gradle.kts` — Register Module

Find the commented line `// include("services:realestate-service")` and uncomment it.

## Verification

```bash
.\gradlew.bat :services:realestate-service:compileJava
```

## Files Written
- `services/realestate-service/build.gradle.kts` ✅
- `services/realestate-service/src/main/resources/application.yml` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/RealEstateServiceApplication.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/entity/RealEstate.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/entity/RealEstateConstructionType.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/entity/RealEstateLuxuryClass.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/entity/RealEstateUsageType.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/repository/RealEstateRepository.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/repository/RealEstateConstructionTypeRepository.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/repository/RealEstateLuxuryClassRepository.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/repository/RealEstateUsageTypeRepository.java` ✅
- `infra/sql/realestate_db/init.sql` ✅ (modified — saga_events + outbox_events appended)
- `settings.gradle.kts` ✅ (modified — realestate-service uncommented)
