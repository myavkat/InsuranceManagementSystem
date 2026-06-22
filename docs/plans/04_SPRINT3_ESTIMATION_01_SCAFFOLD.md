# Plan: Sprint 3 — Estimation Service — Step 1: Scaffold Infrastructure

## Objective
Create the `services/estimation-service/` module with Gradle build configs, Dockerfile, application configuration, and the Spring Boot application entry point.

## Context Files to Read First
Read these files to understand the exact patterns to follow:

1. **`services/customer-service/build.gradle.kts`** — Exact build config template (dependencies, plugins, JaCoCo)
2. **`services/insurance-service/build.gradle.kts`** — Alternative with JaCoCo coverage verification (>=80%)
3. **`services/customer-service/Dockerfile`** — Docker build pattern with multi-stage for `common/` dependency
4. **`services/customer-service/settings.gradle.kts`** — Service settings.gradle.kts template
5. **`services/customer-service/src/main/resources/application.yml`** — application.yml pattern (Kafka, PostgreSQL, logging)
6. **`services/customer-service/src/main/java/.../CustomerServiceApplication.java`** — Application main class pattern
7. **`infra/docker/docker-compose.override.yml`** — Confirm estimation-db port (5437)
8. **`infra/sql/estimation_db/init.sql`** — DB schema to reference
9. **`docs/outlines/10_JAVA_CONVENTIONS.md`** — Java conventions (Lombok order, Instant timestamps)
10. **`docs/outlines/07_PROJECT_STRUCTURE.md`** — Directory layout convention
11. **`docs/outlines/13_ENVIRONMENT_QUIRKS.md`** — Service port (8085), estimation-db port (5437)

## Files to Create

### 1. `services/estimation-service/build.gradle.kts`

Copy from `services/insurance-service/build.gradle.kts` pattern (has JaCoCo with >=80% coverage). Use `estimation` package name. Dependencies:

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
    implementation(project(":common:common-message"))
    implementation(project(":common:common-web"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.cloud:spring-cloud-stream")
    implementation("org.springframework.cloud:spring-cloud-stream-binder-kafka")
    implementation("org.springframework.cloud:spring-cloud-stream-binder-rabbit")
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
    finalizedBy(tasks.jacocoTestReport)
}

tasks.withType<JacocoReport> {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<JacocoCoverageVerification> {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
```

### 2. `services/estimation-service/settings.gradle.kts`

```kotlin
rootProject.name = "estimation-service"
```

### 3. `services/estimation-service/Dockerfile`

Use `customer-service/Dockerfile` pattern (multi-stage with `common/` lib resolution):

```dockerfile
# Stage 1: Build — uses repo root as context to resolve common:common-message dependency
FROM gradle:9.3.1-jdk25-ubi10 AS build
WORKDIR /app

# Copy root settings (required for multi-project resolution)
COPY settings.gradle.kts ./

# Copy shared libraries
COPY common/ ./common/

# Copy service source
COPY services/estimation-service/ ./services/estimation-service/

# Build the estimation-service (skip tests for smaller image)
RUN gradle :services:estimation-service:build -x test --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/services/estimation-service/build/libs/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 4. `services/estimation-service/src/main/resources/application.yml`

Port: 8085. DB url: `localhost:5437/estimation_db`. Group: `estimation-service-group`.

```yaml
server:
  port: 8085

spring:
  application:
    name: estimation-service
  profiles:
    active: dev
  datasource:
    url: jdbc:postgresql://localhost:5437/estimation_db
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
  cloud:
    stream:
      default-binder: kafka
      dynamicDestinations: estimation.saga
      bindings:
        processEstimationSaga-in-0:
          destination: estimation.saga
          group: estimation-service-group
      kafka:
        binder:
          brokers: localhost:9092
  kafka:
    consumer:
      group-id: estimation-service-group
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

estimation:
  saga:
    timeout-minutes: 5
```

Note the custom `estimation.saga.timeout-minutes: 5` config property at the bottom — the timeout scheduler will use this.

### 5. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/EstimationServiceApplication.java`

Follow `CustomerServiceApplication` pattern with `@SpringBootApplication(scanBasePackages = {...})`. Include `com.insurancemanagementsystem.common.web` in scan for the shared `ApiResponse` and `GlobalExceptionHandler`:

```java
package com.insurancemanagementsystem.estimation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.insurancemanagementsystem.estimation", "com.insurancemanagementsystem.common.web"})
@EnableScheduling
public class EstimationServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(EstimationServiceApplication.class, args);
    }
}
```

**IMPORTANT:** `@EnableScheduling` is required for the timeout scheduler (Step 6).

## Gradle Registration

After creating these files, add estimation-service to the root `settings.gradle.kts`:

```kotlin
include("services:estimation-service")
```

Find the line `// include("services:estimation-service")` and uncomment it (remove `// `).

## Verification

```bash
.\gradlew.bat :services:estimation-service:build -x test
```

The build should succeed (compile, package JAR, JaCoCo report tasks run — tests will be skipped here, no test files exist yet).

## Files Written So Far
- `services/estimation-service/build.gradle.kts` ✅
- `services/estimation-service/settings.gradle.kts` ✅
- `services/estimation-service/Dockerfile` ✅
- `services/estimation-service/src/main/resources/application.yml` ✅
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/EstimationServiceApplication.java` ✅
- Root `settings.gradle.kts` — uncommented `estimation-service` ✅
