# Plan 02: API Gateway Core Setup & Routing

> **Status:** Not started
> **Branch:** `phase4-api-gateway`
> **Depends on:** Plan 01 (Eureka Service Discovery) — Eureka server must exist before Gateway can use `lb://` routing
> **Blocks:** Plan 03 (JWT Filter), Plan 04 (Rate Limiting), Plan 05 (Security/Hardening), Plan 07 (Infrastructure)

## Objective

Create the Spring Cloud Gateway service with build configuration, main class, route definitions for all 7 microservices, Eureka client registration, and a basic Gateway configuration class. This is the single entry point for all external API requests.

## Files to Read Before Starting

| File | Purpose |
|------|---------|
| `docs/outlines/01_SYSTEM_ARCHITECTURE.md` | Architecture, Gateway's role, service list |
| `docs/outlines/06_API_GATEWAY_AUTH.md` | Route table, auth requirements per route, filter chain order |
| `docs/outlines/07_PROJECT_STRUCTURE.md` | Directory layout, ports (Gateway: `8080`) |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Lombok order, Java 21+ relaxed main, datetime convention |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Jackson 2/3 conflict detail, shared config, port allocation |
| `settings.gradle.kts` | Current include structure (api-gateway currently commented out) |
| `services/customer-service/build.gradle.kts` | Plugin versions, BOM versions, dependency patterns |
| `services/customer-service/src/main/resources/application.yml` | Service config patterns |
| `common/common-web/src/main/java/.../dto/ApiResponse.java` | Standard API response envelope — Gateway MUST replicate this pattern |
| `common/common-web/src/main/java/.../exception/GlobalExceptionHandler.java` | MVC-style error handler (reference only — Gateway needs WebFlux equivalent) |

## Technical Context (Inline)

### CRITICAL: Gateway is Reactive, NOT MVC
Spring Cloud Gateway is built on **Spring WebFlux** (reactive). This means:
- **DO NOT** add `spring-boot-starter-web` or `spring-boot-starter-webmvc` — they conflict with WebFlux
- **DO NOT** depend on `common:common-web` — it pulls in `spring-boot-starter-web` (MVC)
- Filters implement `org.springframework.cloud.gateway.filter.GlobalFilter` (reactive)
- Error handling uses `org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler`
- CORS is configured via `org.springframework.web.reactive.config.CorsRegistry` or `application.yml`
- Package: `com.insurancemanagementsystem.gateway`

### Routes (from `06_API_GATEWAY_AUTH.md`)

| Route Path | Target Service (`spring.application.name`) | Auth Required |
|------------|---------------------------------------------|---------------|
| `/api/auth/**` | `auth-service` | No (except `/validate`) |
| `/api/customers/**` | `customer-service` | Yes |
| `/api/vehicles/**` | `vehicle-service` | Yes |
| `/api/real-estate/**` | `realestate-service` | Yes |
| `/api/insurances/**` | `insurance-service` | Yes |
| `/api/estimations/**` | `estimation-service` | Yes |
| `/api/reference-data/**` | `reference-data-service` | No (or minimal) |

**Routing semantics:**
- Gateway receives `/api/customers/{id}`
- Strip `/api` prefix (using `StripPrefix=1` or `RewritePath` filter)
- Forward as `/customers/{id}` to `lb://customer-service`
- Each service's controllers use paths like `@GetMapping("/customers")` (NO `/api` prefix in service)

### Filter Chain Order (from `06_API_GATEWAY_AUTH.md`)
1. Rate Limiter (token bucket per client IP or user ID)
2. JWT Authentication Filter (extract, validate, inject headers)
3. Route Filter (forward to service with modified path)
4. Response Filter (standardize errors, add CORS headers)

### Spring Cloud Gateway Route Configuration Pattern
Routes are defined either via `application.yml` (`spring.cloud.gateway.routes`) or via a `@Bean RouteLocator` in Java config. Use **YAML configuration** for route definitions (declarative, easy to read) and **Java config** for programmatic filters.

### Existing stub
The `services/api-gateway/` directory exists with only Eclipse `.project`/`.settings` files. These can be ignored/overwritten.

### Gateway Port
`8080` (from `13_ENVIRONMENT_QUIRKS.md` port allocation table)

### Error Response Envelope
The Gateway must replicate the `ApiResponse` envelope for error responses (`401`, `429`, etc.) without depending on `common-web`. Define a lightweight DTO:
```json
{
  "success": false,
  "message": "Unauthorized",
  "data": null,
  "timestamp": "2026-07-08T12:00:00Z"
}
```

### version metadata
- Spring Boot: 4.0.6, Spring Cloud BOM: 2025.1.2, Java: 25

---

## Steps

### Step 1: Clean up existing api-gateway stub

The `services/api-gateway/` directory has Eclipse files (`.project`, `.settings/`) from the stub phase. Remove the `.project` and `.settings` directory:

```
services/api-gateway/.project        → DELETE
services/api-gateway/.settings/      → DELETE (directory)
```

These are Eclipse IDE artifacts from when this was an empty stub. The new project structure will be Gradle-based.

### Step 2: Create directory structure

Create the following tree under `services/api-gateway/`:

```
services/api-gateway/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── java/com/insurancemanagementsystem/gateway/
    │   │   ├── GatewayApplication.java
    │   │   ├── config/
    │   │   │   └── GatewayConfig.java
    │   │   └── dto/
    │   │       └── ErrorResponse.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/insurancemanagementsystem/gateway/
            └── (empty for now — tests go in Plan 06)
```

### Step 3: Create `build.gradle.kts`

```kotlin
plugins {
    java
    jacoco
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
    // Gateway (WebFlux-based — NO spring-boot-starter-web)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")

    // Observability (consistent with other services)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-zipkin")

    // Redis (for rate limiting — Plan 04)
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // JWT parsing (for JWT filter — Plan 03)
    // io.jsonwebtoken:jjwt-api + jjwt-impl + jjwt-jackson
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.2")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

Key decisions:
- `spring-cloud-starter-gateway` — the core Gateway dependency (brings in WebFlux)
- **No** `spring-boot-starter-web` — Gateway is reactive
- **No** `common:common-web` dependency — that module pulls in Spring MVC
- **No** `common:common-message` — Gateway doesn't produce/consume Kafka events
- `spring-boot-starter-data-redis-reactive` — needed for reactive Redis rate limiter (Plan 04)
- `jjwt` (0.12.6) — JJWT library for JWT parsing/validation (Plan 03)
- `spring-cloud-starter-circuitbreaker-reactor-resilience4j` — circuit breaker for route resilience
- `reactor-test` — for testing reactive filters in Plan 06

### Step 4: Create `application.yml`

```yaml
server:
  port: ${GATEWAY_PORT:8080}

spring:
  application:
    name: api-gateway
  profiles:
    active: dev
  config:
    import: classpath:application-common.yml
  cloud:
    gateway:
      routes:
        # Auth Service — public endpoints (login, register, refresh)
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: false

        # Customer Service — authenticated
        - id: customer-service
          uri: lb://customer-service
          predicates:
            - Path=/api/customers/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: true

        # Vehicle Service — authenticated
        - id: vehicle-service
          uri: lb://vehicle-service
          predicates:
            - Path=/api/vehicles/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: true

        # RealEstate Service — authenticated
        - id: realestate-service
          uri: lb://realestate-service
          predicates:
            - Path=/api/real-estate/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: true

        # Insurance Service — authenticated
        - id: insurance-service
          uri: lb://insurance-service
          predicates:
            - Path=/api/insurances/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: true

        # Estimation Service — authenticated
        - id: estimation-service
          uri: lb://estimation-service
          predicates:
            - Path=/api/estimations/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: true

        # Reference Data Service — public (read-only lookup data)
        - id: reference-data-service
          uri: lb://reference-data-service
          predicates:
            - Path=/api/reference-data/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: false

      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials

      # Global timeouts
      httpclient:
        connect-timeout: 30000    # 30s connect
        response-timeout: 60s     # 60s read from downstream

# Eureka Client
eureka:
  client:
    serviceUrl:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
  instance:
    preferIpAddress: true

# Redis (rate limiting — Plan 04, config here for readiness)
spring.data.redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}

# Request size limit
spring.codec:
  max-in-memory-size: 10MB

logging:
  level:
    com.insurancemanagementsystem.gateway: DEBUG
    org.springframework.cloud.gateway: INFO
```

Key decisions:
- `StripPrefix=1` strips `/api` from the path before forwarding (e.g., `/api/customers/123` → `/customers/123`)
- `lb://` prefix uses Eureka service discovery for load-balanced routing
- `metadata.auth-required` — custom metadata consumed by JWT filter in Plan 03 to decide whether to enforce auth
- `default-filters` with `DedupeResponseHeader` — prevents duplicate CORS headers from downstream services
- Connect timeout: 30s, Response timeout: 60s (from task spec)
- `max-in-memory-size: 10MB` — request payload limit (from task spec)
- Redis config included now so it's ready for Plan 04
- `auth-service` route — auth-service doesn't exist yet (stub), but route is defined so Gateway starts without errors. The auth-service just won't be reachable until Plan for Auth Service is implemented.

### Step 5: Create `ErrorResponse.java` DTO

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/dto/ErrorResponse.java`

```java
package com.insurancemanagementsystem.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private boolean success;
    private String message;
    private Object data;
    private Instant timestamp;

    public static ErrorResponse of(String message) {
        return ErrorResponse.builder()
                .success(false)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static ErrorResponse of(String message, Object data) {
        return ErrorResponse.builder()
                .success(false)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }
}
```

This replicates the `ApiResponse` contract from `common-web` but as a lightweight DTO without the MVC dependency.

**Jackson annotations note:** Uses `com.fasterxml.jackson.annotation.JsonInclude` — unchanged in Jackson 3 per `10_JAVA_CONVENTIONS.md`.

### Step 6: Create `GatewayConfig.java`

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/config/GatewayConfig.java`

This is a **skeleton** config class. Plans 03, 04, 05 will add beans to it.

```java
package com.insurancemanagementsystem.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class GatewayConfig {

    /**
     * CORS configuration — allows frontend origins.
     * Plan 05 will enhance this with production domain configuration.
     */
    @Bean
    public WebFluxConfigurer corsConfigurer() {
        return new WebFluxConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:3000")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
```

Key decisions:
- Uses `WebFluxConfigurer` (reactive), NOT `WebMvcConfigurer` (MVC)
- `allowedOrigins("http://localhost:3000")` — Next.js dev server
- This is a skeleton; Plan 05 will add production origins, global error handler, logging filter

### Step 7: Create `GatewayApplication.java`

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/GatewayApplication.java`

```java
package com.insurancemanagementsystem.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApplication {
    static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

Key decisions:
- **No `@EnableEurekaClient`** — not needed in Spring Cloud 2022+; auto-detected from classpath
- **No `@EnableDiscoveryClient`** — same reason; Spring Boot auto-configuration handles it
- **No `scanBasePackages`** — Gateway doesn't depend on common-web or common-message
- Java 21+ relaxed main (no `public`)

### Step 8: Register API Gateway in `settings.gradle.kts`

**File:** `settings.gradle.kts`

Uncomment the existing line:
```kotlin
// include("services:api-gateway")
```

Change to:
```kotlin
include("services:api-gateway")
```

The file currently has this line commented out near the bottom of the `include()` block.

### Step 9: Build & Verify

```bash
.\gradlew.bat :services:api-gateway:build
```

Ensure compilation succeeds. The `build` task runs compilation + tests (tests are empty for now, so it should pass if compilation succeeds).

---

## Acceptance Criteria

- [ ] `services/api-gateway/build.gradle.kts` exists with correct dependencies
- [ ] `services/api-gateway/src/main/resources/application.yml` defines all 7 routes
- [ ] `GatewayApplication.java` compiles without errors
- [ ] `ErrorResponse.java` exists with `success()`, `error()` static factory methods
- [ ] `GatewayConfig.java` has CORS bean skeleton
- [ ] `.\gradlew.bat :services:api-gateway:build` passes
- [ ] `settings.gradle.kts` includes `services:api-gateway` (uncommented)
- [ ] No `spring-boot-starter-web` or `spring-boot-starter-webmvc` in Gateway dependencies
- [ ] No `common:common-web` or `common:common-message` in Gateway dependencies
