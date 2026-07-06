# Plan: Sprint 5 — Code Review Fixes (Top 3 Critical)

Fixes three bugs found during code review on `sprint5-reference-data-service` branch.

## Context Files (Read Before Starting)

| File | Purpose |
|------|---------|
| `infra/sql/reference_data_db/init.sql` | **TARGET (Fix 1)** — DB schema: `cities` and `professions` tables, seed data |
| `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/ReferenceDataServiceApplication.java` | **TARGET (Fix 2)** — main class: `scanBasePackages` needs `"common.web"` added |
| `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/exception/GlobalExceptionHandler.java` | **TARGET (Fix 2)** — local copy to DELETE after adding common.web to scan |
| `services/reference-data-service/Dockerfile` | **TARGET (Fix 3)** — rewrite following customer-service pattern |
| `common/common-web/src/main/java/com/insurancemanagementsystem/common/web/exception/GlobalExceptionHandler.java` | **REFERENCE** — the shared handler (5 methods, includes `HttpMessageNotReadableException`) |
| `services/customer-service/Dockerfile` | **REFERENCE** — the correct Dockerfile pattern to copy |
| `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/entity/City.java` | **REFERENCE** — entity declares `@Column(name = "created_at")` and `@Column(name = "updated_at")` |
| `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/entity/Profession.java` | **REFERENCE** — entity declares `@Column(name = "created_at")` and `@Column(name = "updated_at")` |
| `services/reference-data-service/src/main/resources/application.yml` | **REFERENCE** — confirms `ddl-auto: validate` (line 15), and shows Kafka consumer dead config for optional cleanup |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Conventions: Jackson 3, Lombok order, Instant timestamps |
| `docs/outlines/11_TESTING_CONVENTIONS.md` | Testing: import paths, assertion rules |

## Prerequisites

- [x] `sprint5-reference-data-service` branch checked out
- [x] `.\gradlew.bat :services:reference-data-service:build` passes
- [ ] Docker Compose DB `reference-data-db` exists on port 5438 (pre-configured)

---

## Fix 1: Add `created_at` / `updated_at` Columns to `init.sql`

### Problem

The `cities` and `professions` tables in `init.sql` define only `id` and `name` (plus `plate_code` for cities). Both JPA entities (`City.java`, `Profession.java`) declare `created_at` and `updated_at` columns with `@PrePersist` / `@PreUpdate` lifecycle callbacks. The application configures `spring.jpa.hibernate.ddl-auto: validate` (application.yml line 15). At startup, Hibernate's `SchemaValidator` compares entity mappings against the live database schema and throws `SchemaValidationException` because the columns don't exist in the database.

Integration tests pass because they use `create-drop` (auto-creates columns from entity mappings). Production/manual startup against the seed DB will crash.

### Implementation

Edit `infra/sql/reference_data_db/init.sql`. Add `created_at` and `updated_at` columns to both `CREATE TABLE` statements.

#### Step 1.1: Modify the `cities` table

**Current** (lines 1-5):
```sql
CREATE TABLE IF NOT EXISTS cities (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    plate_code VARCHAR(2) NOT NULL
);
```

**Replace with:**
```sql
CREATE TABLE IF NOT EXISTS cities (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    plate_code VARCHAR(2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

- `TIMESTAMPTZ` — matches `java.time.Instant` (UTC timestamp with timezone)
- `NOT NULL DEFAULT NOW()` — safe default for existing rows; `@PrePersist` overrides with actual time on first insert

#### Step 1.2: Modify the `professions` table

**Current** (lines 7-10):
```sql
CREATE TABLE IF NOT EXISTS professions (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);
```

**Replace with:**
```sql
CREATE TABLE IF NOT EXISTS professions (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

#### Step 1.3: Verify

- Run: `docker compose -f infra/docker/docker-compose.yml up -d reference-data-db`
- Run: `docker compose -f infra/docker/docker-compose.yml exec reference-data-db psql -U ims_user -d reference_data_db -c "\d cities"` — should show 5 columns: `id`, `name`, `plate_code`, `created_at`, `updated_at`
- Run: `docker compose -f infra/docker/docker-compose.yml exec reference-data-db psql -U ims_user -d reference_data_db -c "\d professions"` — should show 4 columns: `id`, `name`, `created_at`, `updated_at`
- Run: `.\gradlew.bat :services:reference-data-service:bootRun` — app should start without schema validation errors
- Test: `GET http://localhost:8086/api/reference-data/cities` should return cities with `timestamp` in the envelope (not the entity's `created_at`/`updated_at` — those are DB-only audit fields)

---

## Fix 2: Add `"com.insurancemanagementsystem.common.web"` to `scanBasePackages` and Delete Local `GlobalExceptionHandler`

### Problem

Every other production service (`customer-service`, `vehicle-service`, `realestate-service`, `insurance-service`, `estimation-service`) includes `"com.insurancemanagementsystem.common.web"` in `scanBasePackages`. The reference-data-service does not. This means Spring cannot discover the shared `GlobalExceptionHandler` in `common/common-web/` (annotated with `@ControllerAdvice`), forcing the service to maintain its own local copy.

The local copy is **less complete** than the shared version — it is missing the `handleMalformedJson` method (`HttpMessageNotReadableException` handler), which means malformed JSON requests in the reference-data-service return a misleading HTTP 500 instead of HTTP 400 with `"Malformed JSON request body"`.

### Implementation

#### Step 2.1: Edit `ReferenceDataServiceApplication.java`

**Current** (lines 7-11):
```java
@SpringBootApplication(scanBasePackages = {
    "com.insurancemanagementsystem.referencedata",
    "com.insurancemanagementsystem.common.messaging",
    "com.insurancemanagementsystem.common.config"
})
```

**Replace with:**
```java
@SpringBootApplication(scanBasePackages = {
    "com.insurancemanagementsystem.referencedata",
    "com.insurancemanagementsystem.common.messaging",
    "com.insurancemanagementsystem.common.config",
    "com.insurancemanagementsystem.common.web"
})
```

This matches the exact package set used by `customer-service`, `vehicle-service`, `realestate-service`, `insurance-service`, and `estimation-service`.

#### Step 2.2: Delete the local `GlobalExceptionHandler.java`

Delete the file:
```
services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/exception/GlobalExceptionHandler.java
```

The shared handler at `common/common-web/src/main/java/com/insurancemanagementsystem/common/web/exception/GlobalExceptionHandler.java` will now be discovered via component scanning. It handles:
- `EntityNotFoundException` → 404
- `MethodArgumentNotValidException` → 400 (field validation errors)
- `IllegalArgumentException` → 400
- `HttpMessageNotReadableException` → 400 ("Malformed JSON request body") ← **NEW, was missing before**
- `Exception` → 500 (generic fallback)

#### Step 2.3: Update the test class `ReferenceDataControllerTest.java`

The test at `services/reference-data-service/src/test/java/com/insurancemanagementsystem/referencedata/controller/ReferenceDataControllerTest.java` has `@Import(GlobalExceptionHandler.class)` on line 11, which imports the now-deleted local handler. Remove the `@Import` and the associated import statement.

**Current** (lines 5, 11):
```java
import com.insurancemanagementsystem.referencedata.exception.GlobalExceptionHandler;
...
@WebMvcTest(ReferenceDataController.class)
@Import(GlobalExceptionHandler.class)
class ReferenceDataControllerTest {
```

**Replace with:**
```java
@WebMvcTest(ReferenceDataController.class)
class ReferenceDataControllerTest {
```

(The `import` line for `GlobalExceptionHandler` must also be removed.)

#### Step 2.4: Verify

- Run: `.\gradlew.bat :services:reference-data-service:compileJava` — must pass (no more import of local `GlobalExceptionHandler` in production code)
- Run: `.\gradlew.bat :services:reference-data-service:test` — all tests must pass
  - `ReferenceDataControllerTest` should still pass (the shared handler is not on the classpath during `@WebMvcTest`, but the error handler tests use `willThrow()` which MockMvc handles through its own error dispatch)
  - If `shouldHandleServiceException` or `shouldHandleIllegalArgumentException` fail after removing `@Import`, add back `@Import(com.insurancemanagementsystem.common.web.exception.GlobalExceptionHandler.class)` referencing the shared handler. However, in most cases `@WebMvcTest` tests exception mapping through the mock layer and the tests should pass without any explicit handler import — the mock setup (`given(service.getCities()).willThrow(...)`) bypasses the actual handler chain.
- Run: `.\gradlew.bat :services:reference-data-service:test` — confirm all tests pass
- (If tests break): The safe fallback is to add `@Import(com.insurancemanagementsystem.common.web.exception.GlobalExceptionHandler.class)` instead of the local import — still uses the shared handler.

---

## Fix 3: Rewrite Dockerfile Following `customer-service` Pattern

### Problem

The current Dockerfile at `services/reference-data-service/Dockerfile` has two fatal issues:

1. **Missing `common/` modules**: The Dockerfile copies only the service-local `build.gradle.kts`, `settings.gradle.kts`, and `src/`. It never copies `common/common-message/` and `common/common-web/`, which are declared as `project()` dependencies in `build.gradle.kts`. Gradle cannot resolve these dependencies inside the container.

2. **Wrong build context pattern**: The Dockerfile assumes the build context is `services/reference-data-service/`, but `gradle build` inside the container needs the full multi-project structure. The established pattern (used by `customer-service/Dockerfile` and `estimation-service/Dockerfile`) puts the build context at the repository root and copies both `common/` and the specific `services/<name>/` directory.

### Implementation

Replace the entire contents of `services/reference-data-service/Dockerfile`.

#### Current (lines 1-11):
```dockerfile
FROM gradle:8-jdk25 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle build --no-daemon -x test

FROM openjdk:25-jre-slim
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Replace with (following `customer-service/Dockerfile` exactly):
```dockerfile
# Stage 1: Build — uses repo root as context to resolve common:common-message and common:common-web dependencies
FROM gradle:9.3.1-jdk25-ubi10 AS build
WORKDIR /app

# Copy root settings (required for multi-project resolution)
COPY settings.gradle.kts ./

# Copy shared libraries
COPY common/ ./common/

# Copy service source
COPY services/reference-data-service/ ./services/reference-data-service/

# Build the reference-data-service (skip tests for smaller image)
RUN gradle :services:reference-data-service:build -x test --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/services/reference-data-service/build/libs/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Key changes:
- Base image updated from `gradle:8-jdk25` to `gradle:9.3.1-jdk25-ubi10` (matches customer-service)
- Runtime image updated from `openjdk:25-jre-slim` to `eclipse-temurin:25-jre-alpine` (matches customer-service)
- `COPY settings.gradle.kts ./` — copies the ROOT settings (not the service-local one), required for Gradle multi-project resolution
- `COPY common/ ./common/` — copies all shared modules (common-message, common-web)
- `COPY services/reference-data-service/ ./services/reference-data-service/` — copies service source
- `RUN gradle :services:reference-data-service:build -x test --no-daemon` — qualified project path
- `COPY --from=build /app/services/reference-data-service/build/libs/*.jar` — correct JAR location in multi-project structure

#### Build & Verify

The Docker build **must** be run from the repository root with the build context as `.` (the repo root):

```bash
docker build -t reference-data-service -f services/reference-data-service/Dockerfile .
```

If the build context is not the repo root, the `COPY settings.gradle.kts ./` step will fail because there is no root `settings.gradle.kts` in a subdirectory context.

---

## Deliverables (this plan)

- [x] `init.sql` — `cities` table has `created_at` / `updated_at` columns
- [x] `init.sql` — `professions` table has `created_at` / `updated_at` columns
- [x] `ReferenceDataServiceApplication.java` — `scanBasePackages` includes `"com.insurancemanagementsystem.common.web"`
- [x] `GlobalExceptionHandler.java` (local) — deleted
- [x] `ReferenceDataControllerTest.java` — `@Import` and import statement removed (or updated to shared handler if tests require it)
- [x] `Dockerfile` — rewritten following `customer-service/Dockerfile` pattern
- [x] `.\gradlew.bat :services:reference-data-service:build` — full build passes
- [x] `.\gradlew.bat :services:reference-data-service:test` — all tests pass
- [ ] Manual verification: app starts against DB initialized with updated `init.sql`
