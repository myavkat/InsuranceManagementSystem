# Sub-Plan 1: Insurance Service — Scaffold, Domain & Database

**Parent Plan:** `docs/plans/03_SPRINT2_INSURANCE_SERVICE.md`
**Checklist items:** 1.1 through 1.9

---

## Context Files to Read

Before implementing, Read these files for exact patterns:
- `services/customer-service/build.gradle.kts` — exact Gradle dependency template
- `services/customer-service/src/main/resources/application.yml` — YAML config template
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/CustomerServiceApplication.java` — main class pattern
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/entity/Customer.java` — entity pattern (Lombok, JPA, audit fields)
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/repository/CustomerRepository.java` — repository pattern
- `infra/sql/insurance_db/init.sql` — exact DDL schema (ALREADY EXISTS)
- `settings.gradle.kts` (root) — service inclusion pattern

---

## Step 1.1: Uncomment service in root settings.gradle.kts

**File:** `settings.gradle.kts` (repo root)

Change line 13 from:
```
// include("services:insurance-service")
```
to:
```
include("services:insurance-service")
```

---

## Step 1.2: Create build.gradle.kts

**File to CREATE:** `services/insurance-service/build.gradle.kts`

**Pattern:** Mirror `services/customer-service/build.gradle.kts` EXACTLY — change only coordinates where needed.

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.insurancemanagementsystem"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common:common-message"))
    implementation(project(":common:common-web"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.cloud:spring-cloud-stream")
    implementation("org.springframework.cloud:spring-cloud-stream-binder-kafka")
    implementation("org.springframework.cloud:spring-cloud-stream-binder-rabbit")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.2")
        mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

---

## Step 1.3: Create settings.gradle.kts

**File to CREATE:** `services/insurance-service/settings.gradle.kts`

```kotlin
rootProject.name = "insurance-service"
```

---

## Step 1.4: Create Dockerfile

**File to CREATE:** `services/insurance-service/Dockerfile`

```dockerfile
FROM eclipse-temurin:25-jre-alpine
COPY build/libs/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

---

## Step 1.5: Create package directories and main application class

**Directories to CREATE (under `services/insurance-service/src/`):**
```
main/java/com/insurancemanagementsystem/insurance/
├── entity/
├── repository/
├── service/
├── controller/
├── dto/
├── config/
└── exception/

main/resources/

test/java/com/insurancemanagementsystem/insurance/
```

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/InsuranceServiceApplication.java`

```java
package com.insurancemanagementsystem.insurance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.insurancemanagementsystem.insurance", "com.insurancemanagementsystem.common.web"})
public class InsuranceServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(InsuranceServiceApplication.class, args);
    }
}
```

**Key notes:**
- `scanBasePackages` must include `com.insurancemanagementsystem.common.web` for `GlobalExceptionHandler` and `ApiResponse`
- `static void main` (not `public static void main`) — Java 21+ convention per `10_JAVA_CONVENTIONS.md`
- Package base: `com.insurancemanagementsystem.insurance`

---

## Step 1.6: Create application.yml

**File to CREATE:** `services/insurance-service/src/main/resources/application.yml`

```yaml
server:
  port: 8084

spring:
  application:
    name: insurance-service
  profiles:
    active: dev
  datasource:
    url: jdbc:postgresql://localhost:5436/insurance_db
    username: ims_user
    password: ims_password
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  kafka:
    consumer:
      group-id: insurance-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.insurancemanagementsystem.*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [%X{traceId:-},%X{sagaId:-}] - %msg%n"
  level:
    com.insurancemanagementsystem: DEBUG
```

**Port allocations reference:**
| Service | DB Port | App Port |
|---------|---------|----------|
| customer-service | 5433 | 8081 |
| insurance-service | 5436 | 8084 |

---

## Step 1.7: Create Entity Classes

### 1.7a: InsuranceType entity

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/InsuranceType.java`

```java
package com.insurancemanagementsystem.insurance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "insurance_types")
public class InsuranceType {

    @Id
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;
}
```

**Notes:**
- `InsuranceType` uses `Integer` id (not UUID) — matches `init.sql`: `id INT PRIMARY KEY`
- Seed data in `init.sql` has: TRAFFIC(1), CASCO(2), DASK(3), HEALTH(4), LIFE(5)
- No `createdAt`/`updatedAt` — this is a reference/lookup table

### 1.7b: InsuranceCompany entity

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/InsuranceCompany.java`

```java
package com.insurancemanagementsystem.insurance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "insurance_companies")
public class InsuranceCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
```

### 1.7c: Insurance entity

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java`

**DDL reference (from `infra/sql/insurance_db/init.sql`):**
```sql
CREATE TABLE IF NOT EXISTS insurances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type_id INT NOT NULL REFERENCES insurance_types(id),
    company_id UUID NOT NULL REFERENCES insurance_companies(id),
    base_premium DECIMAL(12,2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

```java
package com.insurancemanagementsystem.insurance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "insurances")
public class Insurance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "type_id", nullable = false)
    private Integer typeId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "base_premium", precision = 12, scale = 2)
    private BigDecimal basePremium;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Convenience relationship mappings (read-only, no cascading)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", insertable = false, updatable = false)
    private InsuranceType insuranceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", insertable = false, updatable = false)
    private InsuranceCompany insuranceCompany;
}
```

**Key notes:**
- `typeId` is `Integer` (FK to `insurance_types.id` which is INT)
- `companyId` is `UUID` (FK to `insurance_companies.id` which is UUID)
- `@ManyToOne` relationships are read-only (`insertable=false, updatable=false`) — no cascading per architecture rule
- Lombok order: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, then JPA annotations — per `10_JAVA_CONVENTIONS.md`
- Audit fields: `createdAt` (Instant, `updatable=false`), `updatedAt` (Instant) — per `10_JAVA_CONVENTIONS.md`
- `@PrePersist`/`@PreUpdate` auto-manage timestamps
- Soft-delete via `isActive` Boolean (matches DDL: `is_active BOOLEAN DEFAULT TRUE`)

---

## Step 1.8: Create JPA Repositories

### 1.8a: InsuranceTypeRepository

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/InsuranceTypeRepository.java`

```java
package com.insurancemanagementsystem.insurance.repository;

import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InsuranceTypeRepository extends JpaRepository<InsuranceType, Integer> {
}
```

### 1.8b: InsuranceCompanyRepository

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/InsuranceCompanyRepository.java`

```java
package com.insurancemanagementsystem.insurance.repository;

import com.insurancemanagementsystem.insurance.entity.InsuranceCompany;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InsuranceCompanyRepository extends JpaRepository<InsuranceCompany, UUID> {
    Page<InsuranceCompany> findByIsActiveTrue(Pageable pageable);
    Page<InsuranceCompany> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
```

### 1.8c: InsuranceRepository

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/InsuranceRepository.java`

```java
package com.insurancemanagementsystem.insurance.repository;

import com.insurancemanagementsystem.insurance.entity.Insurance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsuranceRepository extends JpaRepository<Insurance, UUID> {

    // Active insurances (not soft-deleted)
    Page<Insurance> findByIsActiveTrue(Pageable pageable);

    // Filter by type
    Page<Insurance> findByTypeIdAndIsActiveTrue(Integer typeId, Pageable pageable);

    // Filter by company
    Page<Insurance> findByCompanyIdAndIsActiveTrue(UUID companyId, Pageable pageable);

    // Filter by type AND company
    Page<Insurance> findByTypeIdAndCompanyIdAndIsActiveTrue(Integer typeId, UUID companyId, Pageable pageable);

    // Search by name
    @Query("SELECT i FROM Insurance i WHERE i.isActive = true AND LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Insurance> searchByName(@Param("search") String search, Pageable pageable);

    // Find by name for uniqueness checks
    Optional<Insurance> findByNameIgnoreCase(String name);
}
```

---

## Step 1.9: Database Verification

**Commands to run:**

```bash
# Start insurance-db container
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml up -d insurance-db

# Wait ~30s for init scripts to run, then verify
docker exec insurance-db psql -U ims_user -d insurance_db -c "\dt"
```

**Expected output:** Three tables: `insurance_types`, `insurance_companies`, `insurances`

**Verify seed data:**
```bash
# Check types (should be 5: TRAFFIC, CASCO, DASK, HEALTH, LIFE)
docker exec insurance-db psql -U ims_user -d insurance_db -c "SELECT * FROM insurance_types;"

# Check companies (should be 4: Anadolu, Ak, Allianz, Groupama)
docker exec insurance-db psql -U ims_user -d insurance_db -c "SELECT id, name, rating FROM insurance_companies;"

# Check products (should be 8 seed products)
docker exec insurance-db psql -U ims_user -d insurance_db -c "SELECT id, name, type_id FROM insurances;"
```

**If container already exists but needs reset:**
```bash
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml down insurance-db
docker volume rm docker_insurance-db-data
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml up -d insurance-db
```

---

## Verification After Step 1

After completing all sub-steps, verify:
1. `.\gradlew.bat :services:insurance-service:compileJava` — compiles without errors
2. `.\gradlew.bat :services:insurance-service:bootRun` — starts and connects to DB
3. Check logs for: `Hibernate: ` lines showing table mapping
