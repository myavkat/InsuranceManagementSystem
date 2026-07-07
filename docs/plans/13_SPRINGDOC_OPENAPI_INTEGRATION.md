# Springdoc OpenAPI Integration — All 6 Microservices

## Status: PLANNING COMPLETE
## Branch: add-springdoc-and-archive-plans
## Parent: None (standalone)

---

## Objective

Add Springdoc OpenAPI 3.x (Swagger UI) to all 6 microservices by placing a single dependency and a configuration class in the shared `common-web` library. Configuration properties go into the already-shared `application-common.yml`. Each service inherits everything automatically — no per-service code changes needed.

## Context Anchors

Before writing any code, read these outline files for architecture context:

| Outline | Why |
|---------|-----|
| `docs/outlines/01_SYSTEM_ARCHITECTURE.md` | Tech stack, microservice breakdown, port allocation |
| `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` | Per-service entities, endpoints, controllers |
| `docs/outlines/07_PROJECT_STRUCTURE.md` | Directory layout, build order, shared module conventions |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Java 21+, Lombok order, Jackson 2/3 migration notes |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Jackson 2/3 conflict — `bootRun` is broken, verification must use Docker |

## Files to Read Before Starting

Study these exact files before making any changes:

| File | What to look at |
|------|-----------------|
| `common/common-web/build.gradle.kts` | Current dependencies, `java-library` plugin usage, dependency ordering |
| `common/common-web/src/main/resources/application-common.yml` | Existing `management.tracing.*` and `logging.*` config — append springdoc after `logging:` block |
| `common/common-web/src/main/java/com/insurancemanagementsystem/common/web/dto/ApiResponse.java` | The standard API response envelope — Swagger will auto-document this |
| `common/common-web/src/main/java/com/insurancemanagementsystem/common/web/exception/GlobalExceptionHandler.java` | Exception handlers — Swagger auto-documents error responses from `@ExceptionHandler` methods |
| `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/CustomerServiceApplication.java` | `@SpringBootApplication` with `scanBasePackages` — confirms `common.web` is scanned |
| `services/customer-service/src/main/resources/application.yml` | Confirms `spring.config.import: classpath:application-common.yml` — all 6 services do this |
| `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/controller/CustomerController.java` | Representative controller — `@RestController`, `@RequestMapping("/api/customers")`, returns `ResponseEntity<ApiResponse<T>>` |
| `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/controller/VehicleController.java` | Most complex controller — multiple endpoints including reference data lookups |
| `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/EstimationServiceApplication.java` | Has `@EnableScheduling` in addition to standard annotations |

## Current State

### What exists
- 6 microservices: customer (8081), vehicle (8082), realestate (8083), insurance (8084), estimation (8085), reference-data (8086)
- All services share `common-web` via `implementation(project(":common:common-web"))`
- All services import `application-common.yml` via `spring.config.import: classpath:application-common.yml`
- All service `@SpringBootApplication` classes scan `"com.insurancemanagementsystem.common.web"`
- Controllers use `@RestController` + `@RequestMapping("/api/<resource>")` and return `ResponseEntity<ApiResponse<T>>`
- Jackson 2 (`com.fasterxml.jackson.*`) is declared in every service's `build.gradle.kts` to satisfy Kafka
- Jackson 3 (`tools.jackson.*`) is declared in `common-web/build.gradle.kts`

### What does NOT exist
- Zero OpenAPI/Swagger/Springdoc dependencies anywhere
- Zero `@Tag`, `@Operation`, `@Schema`, or any Swagger/OpenAPI annotations in any Java file
- No API Gateway (commented out in `settings.gradle.kts`)

### Known constraint
- `bootRun` is broken due to Jackson 2/3 classpath conflict (`NoClassDefFoundError: com/fasterxml/jackson/databind/JavaType`). Build verification must use `./gradlew build`, and runtime verification must use Docker Compose.

---

## Implementation Steps

### Step 1 — Add Springdoc dependency to `common-web`

**File:** `common/common-web/build.gradle.kts`

Insert one line in the `dependencies` block, after the existing `tools.jackson.core:jackson-databind` line:

```kotlin
    implementation("tools.jackson.core:jackson-databind")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("jakarta.persistence:jakarta.persistence-api")
```

**Why version 3.0.3:** Springdoc 3.x is compatible with Spring Boot 4.x. Version 3.0.3 targets Spring Boot 4.0.5; this project uses 4.0.6. Do NOT use Springdoc 2.x — it targets Spring Boot 3.x only and will fail.

**Why `common-web` and not each service:** Every service declares `implementation(project(":common:common-web"))`. Adding the dependency here makes it transitively available to all 6 services — no per-service build file changes needed.

**Why `implementation` not `api`:** Services do not expose Springdoc types in their own public API. `implementation` is the correct scope.

---

### Step 2 — Append Springdoc properties to `application-common.yml`

**File:** `common/common-web/src/main/resources/application-common.yml`

Append this block at the end of the file (after the existing `logging:` block):

```yaml
# ------------------------------------------------------------
# Springdoc OpenAPI (Swagger UI) — shared across all services
# ------------------------------------------------------------
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    tryItOutEnabled: true
    display-request-duration: true
    syntax-highlight:
      theme: monokai
  cache:
    disabled: true
```

Property explanations (so you know what each one does):

| Property | Value | Why |
|---|---|---|
| `api-docs.enabled` | `true` | Enables the OpenAPI JSON endpoint (default, explicit for clarity) |
| `api-docs.path` | `/v3/api-docs` | Standard path — keep it so API Gateway routing works later |
| `swagger-ui.enabled` | `true` | Enables the Swagger UI HTML page |
| `swagger-ui.path` | `/swagger-ui.html` | Standard path recognized by tooling |
| `swagger-ui.tryItOutEnabled` | `true` | Pre-opens the "Try it out" button so developers can send requests immediately |
| `swagger-ui.display-request-duration` | `true` | Shows response time in the UI |
| `swagger-ui.syntax-highlight.theme` | `monokai` | Readable code blocks |
| `cache.disabled` | `true` | Disables caching in dev — controller/DTO changes reflect immediately |

All 6 services already import this file via their `spring.config.import: classpath:application-common.yml` — no per-service config changes needed.

---

### Step 3 — Create `OpenAPIConfig.java`

**New file:** `common/common-web/src/main/java/com/insurancemanagementsystem/common/web/config/OpenAPIConfig.java`

Create the `config` subdirectory if it doesn't exist:
```
common/common-web/src/main/java/com/insurancemanagementsystem/common/web/config/
```

**Why a Java config class (not just YAML):**
- The `Info` object (title, description, version, contact, license) cannot be fully configured via YAML properties
- The JWT Bearer security scheme needs a `Components` object with `SecurityScheme` definitions
- The server URL for "Try it out" mode must be dynamic per service/port — YAML can't resolve `${server.port}` per service

**Why this class is auto-discovered:** Every service's `@SpringBootApplication` includes `"com.insurancemanagementsystem.common.web"` in `scanBasePackages`. The `OpenAPIConfig` class lives in `com.insurancemanagementsystem.common.web.config` which is a sub-package — Spring component scanning will find and register its `@Bean`.

**Full source:**

```java
package com.insurancemanagementsystem.common.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Value("${openapi.title:#{null}}")
    private String title;

    @Value("${openapi.description:#{null}}")
    private String description;

    @Value("${openapi.version:0.0.1-SNAPSHOT}")
    private String apiVersion;

    @Value("${openapi.server-url:http://localhost:${server.port:8080}}")
    private String serverUrl;

    @Value("${openapi.server-description:Local development server}")
    private String serverDescription;

    @Bean
    public OpenAPI customOpenAPI() {
        final String resolvedTitle = (title != null) ? title : serviceName + " API";
        final String resolvedDescription = (description != null)
                ? description
                : "REST API for " + serviceName
                + ". This service is part of the Insurance Management System.";

        return new OpenAPI()
                .info(new Info()
                        .title(resolvedTitle)
                        .description(resolvedDescription)
                        .version(apiVersion)
                        .contact(new Contact()
                                .name("Development Team")
                                .email("dev@insurancemanagementsystem.com")
                                .url("https://insurancemanagementsystem.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://insurancemanagementsystem.com/license")))
                .addServersItem(new Server()
                        .url(serverUrl)
                        .description(serverDescription))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(
                                    "JWT Authorization header using the Bearer scheme. "
                                  + "Example: \"Authorization: Bearer {token}\"")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
```

**How per-service titles resolve (no config needed):**

| Service | `spring.application.name` | Auto-generated Swagger title |
|---------|---------------------------|------------------------------|
| customer-service | `customer-service` | `customer-service API` |
| vehicle-service | `vehicle-service` | `vehicle-service API` |
| realestate-service | `realestate-service` | `realestate-service API` |
| insurance-service | `insurance-service` | `insurance-service API` |
| estimation-service | `estimation-service` | `estimation-service API` |
| reference-data-service | `reference-data-service` | `reference-data-service API` |

The server URL resolves to `http://localhost:<service-port>` from each service's `server.port` property (8081-8086). Example for customer-service: `http://localhost:8081`.

**Optional per-service overrides (not required for this plan):**
To give a service a custom title, add to its `application.yml`:
```yaml
openapi:
  title: Customer Service API
  description: Manage customer profiles, search, and validation.
```
Skip this for the initial implementation — the defaults are good enough.

---

### Step 4 — Build and verify compilation

After making the 3 file changes, verify the project compiles:

```bash
./gradlew :common:common-web:build
```

This builds the shared library with the new dependency. If it succeeds, all services will compile too (they inherit the dependency transitively).

**Do NOT run `bootRun`** — it's broken due to the Jackson 2/3 classpath conflict. Use `./gradlew build` for compilation verification.

---

### Step 5 — Runtime verification via Docker Compose

Build the service JARs and start the stack:

```bash
./gradlew :services:customer-service:build
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.services.yml up -d
```

Then verify Swagger UI on each service:

```bash
# 1. Check Swagger UI HTML loads
curl -s http://localhost:8081/swagger-ui.html | head -5
# Expected: HTML containing "Swagger UI"

# 2. Check OpenAPI JSON endpoint returns correct title
curl -s http://localhost:8081/v3/api-docs | jq '.info.title'
# Expected: "customer-service API"

# 3. Repeat for all services (8082-8086)
curl -s http://localhost:8082/v3/api-docs | jq '.info.title'  # vehicle-service API
curl -s http://localhost:8083/v3/api-docs | jq '.info.title'  # realestate-service API
curl -s http://localhost:8084/v3/api-docs | jq '.info.title'  # insurance-service API
curl -s http://localhost:8085/v3/api-docs | jq '.info.title'  # estimation-service API
curl -s http://localhost:8086/v3/api-docs | jq '.info.title'  # reference-data-service API

# 4. Open in browser
# Navigate to http://localhost:8081/swagger-ui.html
# Expected: Swagger UI page with endpoints listed, "Try it out" button enabled,
# "Authorize" button for JWT Bearer token, correct server URL in dropdown
```

---

## What Swagger UI Will Show (No Annotation Changes Needed)

Without adding any `@Tag`, `@Operation`, or `@Schema` annotations:

| Aspect | Behavior |
|--------|----------|
| **Endpoints** | All `@RequestMapping` paths from `@RestController` classes listed |
| **HTTP methods** | GET, POST, PUT, DELETE auto-detected from `@GetMapping`, `@PostMapping`, etc. |
| **Path variables** | Auto-detected from `@PathVariable` parameters |
| **Query parameters** | Auto-detected from `@RequestParam` parameters |
| **Request bodies** | Schema generated from DTO classes — field names and types from POJOs/records |
| **Validation constraints** | `@Valid` / `jakarta.validation` annotations picked up (e.g., `@NotBlank`, `@Size`) |
| **Response schemas** | `ApiResponse<T>` wrapper documented with inner type resolved |
| **Error responses** | `@ExceptionHandler` methods from `GlobalExceptionHandler` auto-documented with status codes |
| **Security** | JWT Bearer scheme with "Authorize" button — paste a token to authenticate requests |

## Optional Enhancement (Follow-Up, Not Required)

Adding `@Tag` to controllers makes the Swagger UI more readable by grouping endpoints into named sections. Example:

```java
// services/customer-service/src/main/java/.../controller/CustomerController.java
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management endpoints")
public class CustomerController {
    // existing code — no method-level changes needed
}
```

This is cosmetic only — it does not affect functionality. Add incrementally in a follow-up pass if desired.

---

## Jackson 2/3 Compatibility Analysis

**Risk: LOW — no action needed.**

Springdoc internally uses `com.fasterxml.jackson.core:jackson-databind` (Jackson 2) for its own JSON serialization. The project already declares `com.fasterxml.jackson.core:jackson-databind` in every service's `build.gradle.kts` (required by Spring Kafka). Springdoc will use this existing Jackson 2 dependency without conflict.

The known Jackson 2/3 issue only affects `bootRun` locally — it does NOT affect:
- Docker Compose deployment (classpaths are isolated per container)
- `@SpringBootTest` integration tests (test runner classpath handles both versions)
- `./gradlew build` (compilation and unit tests)

Springdoc will work correctly in all supported runtime environments.

---

## Files Modified/Created (Summary)

| File | Action | Lines Changed |
|------|--------|---------------|
| `common/common-web/build.gradle.kts` | Add 1 dependency line | +1 |
| `common/common-web/src/main/resources/application-common.yml` | Append springdoc block | +15 |
| `common/common-web/src/main/java/.../common/web/config/OpenAPIConfig.java` | New file | ~70 |
| `infra/docker/docker-compose.services.yml` | Add `OPENAPI_SERVER_URL` per service | +6 |
| `.env.template` | Document `OPENAPI_SERVER_URL` | +3 |

No changes to any `services/*/build.gradle.kts` or `services/*/application.yml` files.

> **Docker note:** Services in Docker run on internal port 8080. The `OPENAPI_SERVER_URL` env var per service overrides the server URL to the correct host-mapped port (8081-8086) so Swagger UI's "Try it out" sends requests to the right place.

---

## Dependencies

None. This plan is standalone and does not depend on any other plan completing first.

## Completion Criteria

- [x] `springdoc-openapi-starter-webmvc-ui:3.0.3` dependency added to `common-web/build.gradle.kts`
- [x] Springdoc YAML config appended to `application-common.yml`
- [x] `OpenAPIConfig.java` created with `@Configuration` and `OpenAPI` bean
- [x] `./gradlew :common:common-web:build` succeeds
- [x] Docker Compose starts successfully with the updated services
- [x] `curl http://localhost:8081/v3/api-docs | jq '.info.title'` returns `"customer-service API"`
- [x] All 6 services return their respective OpenAPI JSON on ports 8081-8086
- [x] Swagger UI loads in browser at `http://localhost:8081/swagger-ui.html` with "Try it out" enabled

## Rollback Plan

If Springdoc causes issues:

1. Remove the dependency line from `common/web/build.gradle.kts`
2. Delete `common/common-web/src/main/java/com/insurancemanagementsystem/common/web/config/OpenAPIConfig.java`
3. Remove the `springdoc:` block from `application-common.yml`
4. Run `./gradlew :common:common-web:build` to confirm clean state

The entire change is contained in 3 files in a single module.
