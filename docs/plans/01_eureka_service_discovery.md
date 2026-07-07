# Plan 01: Eureka Service Discovery Server

> **Status:** Completed
> **Branch:** `phase4-api-gateway`
> **Depends on:** None (independent — first plan in sequence)
> **Blocks:** Plan 02 (API Gateway Core), Plan 07 (Infrastructure Integration)

## Objective

Create a standalone Netflix Eureka Service Discovery server and register all 6 existing microservices as Eureka clients. This enables the API Gateway to use `lb://` load-balanced routing in Plan 02.

## Files to Read Before Starting

| File | Purpose |
|------|---------|
| `docs/outlines/01_SYSTEM_ARCHITECTURE.md` | Tech stack, port allocation, service list |
| `docs/outlines/07_PROJECT_STRUCTURE.md` | Directory layout, build order |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Ports (Eureka: `8761`), .env.template conventions |
| `docs/outlines/12_DEVELOPER_COMMANDS.md` | Gradle commands, service names |
| `settings.gradle.kts` | Multi-module include structure |
| `services/customer-service/build.gradle.kts` | Reference for plugin versions, dependency patterns, BOM versions |
| `services/customer-service/src/main/resources/application.yml` | Pattern for service config, tracing, Kafka settings |
| `common/common-web/src/main/resources/application-common.yml` | Shared config imported by all services |
| `.env.template` | Environment variable conventions |

## Technical Context (Inline)

### Versions (from existing project)
- **Java:** 25 (toolchain)
- **Spring Boot:** 4.0.6
- **Spring Cloud BOM:** `2025.1.2`
- **Gradle:** wrapper (existing `gradlew.bat`)
- **Group:** `com.insurancemanagementsystem`

### Eureka Server port
- **Eureka Server:** `8761` (standard convention, not in port allocation table yet but add it)

### Eureka Client Registration
All 6 existing services must register with Eureka. Each service gets:
- Dependency: `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client`
- Config in `application.yml`: `eureka.client.serviceUrl.defaultZone: http://localhost:8761/eureka/`
- Config: `eureka.instance.preferIpAddress: true` (needed for Docker bridge networking)

### Architectural Constraint
- Eureka server is **standalone** (single instance for dev — no HA cluster needed yet)
- Eureka dashboard available at `http://localhost:8761`
- The Gateway will use `lb://<spring.application.name>` for routing (Plan 02)

### Directory Pattern
Following `services/customer-service/` structure:
```
services/eureka-server/
├── build.gradle.kts
└── src/
    └── main/
        ├── java/com/insurancemanagementsystem/eureka/
        │   └── EurekaServerApplication.java
        └── resources/
            └── application.yml
```

### Spring Boot 4 / Java 25 Convention
- Main class: `static void main(String[] args)` — omit `public` modifier (Java 21+ relaxed main)
- Package naming: `com.insurancemanagementsystem.eureka`

---

## Steps

### Step 1: Create Eureka Server directory structure

```
services/eureka-server/
```

Create the directory tree. The existing `services/api-gateway/` stub already exists with Eclipse files — leave it alone; it will be replaced in Plan 02.

### Step 2: Create `services/eureka-server/build.gradle.kts`

Model after `services/customer-service/build.gradle.kts` but with Eureka Server dependencies instead of JPA/Kafka/Stream.

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
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-zipkin")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
- `spring-cloud-starter-netflix-eureka-server` is the only Eureka dependency needed on server side
- Actuator + Zipkin included for observability (consistent with other services)
- No JPA, no Kafka, no database — Eureka is purely in-memory
- Same BOM version as all other services (`2025.1.2`)
- **No `common:common-web` dependency** — Eureka server doesn't need MVC/OpenAPI

### Step 3: Create `services/eureka-server/src/main/resources/application.yml`

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server
  profiles:
    active: dev
  config:
    import: classpath:application-common.yml

eureka:
  client:
    registerWithEureka: false
    fetchRegistry: false
  server:
    waitTimeInMsWhenSyncEmpty: 0
    enableSelfPreservation: false   # dev only — disable self-preservation to avoid stale entries

logging:
  level:
    com.netflix.eureka: INFO
    com.netflix.discovery: INFO
```

Key decisions:
- `registerWithEureka: false` + `fetchRegistry: false` — this IS the server, it doesn't register with itself
- `enableSelfPreservation: false` — development-only setting to avoid stale instance warnings
- `waitTimeInMsWhenSyncEmpty: 0` — don't wait for peer sync in standalone mode
- Imports `application-common.yml` for consistent logging MDC pattern and Zipkin config

### Step 4: Create `EurekaServerApplication.java`

**File:** `services/eureka-server/src/main/java/com/insurancemanagementsystem/eureka/EurekaServerApplication.java`

```java
package com.insurancemanagementsystem.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

Following the Java 21+ relaxed `main` convention (no `public`).

### Step 5: Register Eureka Server in `settings.gradle.kts`

**File:** `settings.gradle.kts` (edit existing)

Add the include line for eureka-server. Place it near the other service includes (near the `api-gateway` commented-out line):

```kotlin
include("services:eureka-server")
```

The current file has:
```kotlin
// include("services:api-gateway")
```

Add BEFORE that line:
```kotlin
include("services:eureka-server")
```

### Step 6: Add Eureka Client to all 6 existing services

For each of these services, do TWO things:

**Services to update:**
1. `services/customer-service`
2. `services/vehicle-service`
3. `services/realestate-service`
4. `services/insurance-service`
5. `services/estimation-service`
6. `services/reference-data-service`

#### 6a. Add Eureka Client dependency to each service's `build.gradle.kts`

In each service's `build.gradle.kts`, add this line in the `dependencies` block (alongside the other `spring-cloud` dependencies):

```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
```

**Placement:** Add it near the existing `spring-cloud` dependencies. For customer-service, it would go after:
```kotlin
implementation("org.springframework.cloud:spring-cloud-stream-binder-kafka")
```

This exact line goes in all 6 services.

#### 6b. Add Eureka Client configuration to each service's `application.yml`

In each service's `application.yml`, add under the `spring:` section (at the same level as `spring.application.name`):

```yaml
eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
  instance:
    preferIpAddress: true
```

**IMPORTANT:** Add this block with the SAME indentation as `spring:` (top-level key). Do not nest it under `spring:`.

**Location:** Add after the `spring:` block and before `logging:`.

Example for customer-service — the file would have:
```yaml
spring:
  application:
    name: customer-service
  # ... rest of spring config ...

eureka:                    # <-- NEW: add this block
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
  instance:
    preferIpAddress: true

logging:                   # existing
  level:
    com.insurancemanagementsystem: DEBUG
```

### Step 7: Verify build compiles

From repo root, run:

```bash
.\gradlew.bat :services:eureka-server:build
```

And spot-check one service:

```bash
.\gradlew.bat :services:customer-service:build
```

### Step 8: Test Eureka Server startup

Start infra (if not running):

```bash
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml up -d
```

Then start Eureka:

```bash
.\gradlew.bat :services:eureka-server:bootRun
```

Verify dashboard is accessible at `http://localhost:8761`.

Then start one service (e.g., customer-service) and verify it appears in the Eureka dashboard:

```bash
.\gradlew.bat :services:customer-service:bootRun
```

Check `http://localhost:8761` — customer-service should appear as a registered instance.

### Step 9: Update `.env.template`

Add the following to `.env.template` (in the appropriate section):

```properties
# Eureka Service Discovery
EUREKA_SERVER_URL=http://localhost:8761/eureka/
```

Place this in a logical location (e.g., near the API Gateway section).

---

## Acceptance Criteria

- [x] `services/eureka-server/` directory exists with `build.gradle.kts`
- [x] `.\gradlew.bat :services:eureka-server:build` compiles successfully
- [x] `settings.gradle.kts` includes `services:eureka-server`
- [x] Eureka dashboard accessible at `http://localhost:8761`
- [x] All 6 services have `spring-cloud-starter-netflix-eureka-client` dependency added
- [x] All 6 services have `eureka.client.serviceUrl.defaultZone` configured
- [x] All 6 services still compile after changes (`.\gradlew.bat :services:<name>:build`)
- [x] At least one service registers with Eureka and appears in dashboard when both are running
- [x] `.env.template` has `EUREKA_SERVER_URL` entry
