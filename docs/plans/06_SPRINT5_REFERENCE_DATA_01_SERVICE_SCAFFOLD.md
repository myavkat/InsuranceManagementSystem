# Plan: Sprint 5 — Reference Data Service Scaffold & Build Configuration

## Context Files (Read Before Starting)

| File | Purpose |
|------|---------|
| `docs/outlines/01_SYSTEM_ARCHITECTURE.md` | Tech stack, architectural rules, microservice breakdown |
| `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` | Reference Data Service spec (entities, endpoints) |
| `docs/outlines/07_PROJECT_STRUCTURE.md` | Directory layout, build order, service conventions |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Java 25 relaxed main, Instant/LocalDate, Lombok order |
| `docs/outlines/12_DEVELOPER_COMMANDS.md` | Build & run commands |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Port allocation, defaults, IntelliJ quirks |
| `services/customer-service/build.gradle.kts` | Reference build file (dependency pattern) |
| `services/customer-service/src/main/java/.../CustomerServiceApplication.java` | Main class pattern (scanBasePackages, EntityScan) |
| `services/reference-skeleton/src/main/resources/application.yml` | Reference application config |
| `settings.gradle.kts` | Root settings (must edit to include new service) |
| `infra/docker/docker-compose.yml` | Reference DB already defined as `reference-data-db` on port 5438 |
| `infra/docker/.env` | DB credentials: `ims_user` / `ims_password` |

## Conventions to Apply (from AGENTS.md + Outlines)

- **Java 25** relaxed main: `static void main(String[] args)` (no `public`)
- **Lombok order:** `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, then JPA annotations
- **Timestamps:** `java.time.Instant` for `createdAt`/`updatedAt`; `LocalDate` for date-only fields
- **Jackson 3:** Annotations stay at `com.fasterxml.jackson.annotation.*`; `ObjectMapper` is `tools.jackson.databind.ObjectMapper`
- **Spring Boot 4:** `@EntityScan` is `org.springframework.boot.persistence.autoconfigure.EntityScan`
- **API response envelope:** `ApiResponse<T>` with `success`, `message`, `data`, `timestamp`
- **Spring Boot MVC** (`@RestController`, not `@Controller`)
- **Gradle** build, JAR packaging, JUnit 5 + Testcontainers
- **No auto-attribution** in commits — never include `Co-Authored-By`

## Implementation Steps

### Step 1: Create Directory Structure

Create the following package directories under `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/`:

```
referencedata/
├── config/
├── controller/
├── dto/
├── entity/
├── exception/
├── repository/
└── service/
```

Also create `services/reference-data-service/src/test/java/com/insurancemanagementsystem/referencedata/`.

### Step 2: Create `build.gradle.kts`

- [ ] Create `services/reference-data-service/build.gradle.kts`

**Pattern:** Copy from `services/customer-service/build.gradle.kts` with these changes:
- Include `common:common-message` and `common:common-web` as project dependencies
- Include all standard Spring Boot starters: web, webmvc, data-jpa, validation
- Include spring-cloud-stream + binder-kafka for domain event publishing
- **NO RabbitMQ/AMQP** — this service uses Kafka exclusively, matching all other production services
- Use **Spring Boot 4.0.6**, Spring Cloud **2025.1.2**, Testcontainers **2.0.5**
- Java toolchain: **25**
- PostgreSQL runtime dependency, Lombok
- JaCoCo plugin for code coverage
- JUnit 5 test dependencies (matching customer-service pattern exactly)

### Step 3: Create `settings.gradle.kts`

- [ ] Create `services/reference-data-service/settings.gradle.kts`

```kotlin
rootProject.name = "reference-data-service"
```

### Step 4: Create `application.yml`

- [ ] Create `services/reference-data-service/src/main/resources/application.yml`

**Configuration:**
- `server.port: 8086` (next available after existing services: customer=8081, vehicle=8082, realestate=8083, insurance=8084, estimation=8085)
- `spring.application.name: reference-data-service`
- `spring.profiles.active: dev`
- **Datasource:** `jdbc:postgresql://localhost:5438/reference_data_db`, user `ims_user`, password `ims_password`
- **JPA:** `ddl-auto: validate`, PostgreSQL dialect, format SQL
- **Kafka:** bootstrap servers `localhost:9092`, consumer group `reference-data-service-group`, trusted packages `com.insurancemanagementsystem.*`
- **Spring Cloud Stream:** default binder kafka, dynamic destinations `reference-data.events`
- **Logging:** MDC pattern with traceId/sagaId, DEBUG level for `com.insurancemanagementsystem`

**No RabbitMQ config** — this service follows the Kafka-only architecture of all other production services (`customer-service`, `vehicle-service`, `realestate-service`, `insurance-service`, `estimation-service`).

### Step 5: Create Main Application Class

- [ ] Create `ReferenceDataServiceApplication.java`

Package: `com.insurancemanagementsystem.referencedata`

```java
@SpringBootApplication(scanBasePackages = {
    "com.insurancemanagementsystem.referencedata",
    "com.insurancemanagementsystem.common.messaging",
    "com.insurancemanagementsystem.common.config"
})
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.referencedata.entity",
    "com.insurancemanagementsystem.common.entity"
})
public class ReferenceDataServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(ReferenceDataServiceApplication.class, args);
    }
}
```

### Step 6: Create `Dockerfile`

- [ ] Create `services/reference-data-service/Dockerfile`

Copy from `services/reference-skeleton/Dockerfile`, change port to `8086`.

### Step 7: Register in Root `settings.gradle.kts`

- [ ] Edit `settings.gradle.kts` — uncomment `include("services:reference-data-service")` (line 15)

### Step 8: Verify Build

- [ ] Run: `.\gradlew.bat :services:reference-data-service:build`
- [ ] Confirm compilation succeeds (tests may fail — no test classes yet, that's expected)
- [ ] If build fails, check: all imports resolve, dependency versions are correct, application.yml is valid YAML

### Step 9: Verify Docker Compose DB

- [ ] Confirm `infra/docker/docker-compose.yml` already defines `reference-data-db` on port 5438
- [ ] Confirm `infra/docker/docker-compose.override.yml` maps port 5438
- [ ] Confirm `infra/sql/reference_data_db/init.sql` exists with cities/professions DDL and seed data
- [ ] No changes needed to infra files (they are pre-configured)

## Deliverables (this plan)

- [ ] `build.gradle.kts` — compiles, all dependencies resolve
- [ ] `settings.gradle.kts` — root project name set
- [ ] `application.yml` — all config properties defined
- [ ] `ReferenceDataServiceApplication.java` — main class boots Spring context
- [ ] `Dockerfile` — container build instructions
- [ ] Root `settings.gradle.kts` — service included in multi-project build
- [ ] `.\gradlew.bat :services:reference-data-service:build` passes
