# Plan 13-01: Auth Service — Project Scaffolding

**Objective:** Create the Gradle build file, register the module in the multi-project build, create `application.yml`, and create the Spring Boot application main class for the auth service.

**Depends on:** None (this is the first plan).

**Estimated files to create:** 3
**Estimated files to modify:** 1

---

## Files to Read First

Before writing any code, open these files to understand the patterns:

| File | Why |
|------|-----|
| `services/reference-data-service/build.gradle.kts` | The template to copy for the auth service build file |
| `settings.gradle.kts` | See the existing include structure and the commented-out auth-service line |
| `services/reference-data-service/src/main/resources/application.yml` | Template for auth service application.yml |
| `services/reference-data-service/src/main/java/.../ReferenceDataServiceApplication.java` | Template for the main class |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Port 8087 for auth-service, Jackson conflict note |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Java 21+ relaxed main method convention |
| `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` | Section 1 (Auth Service) — confirms no SAGA events |
| `common/common-web/src/main/resources/application-common.yml` | Shared config imported by all services |
| `.env.template` | Verify AUTH_DB_* vars are present |

---

## Steps

### Step 1: Create directory structure

Create these directories (they don't exist yet):

```
services/auth-service/src/main/java/com/insurancemanagementsystem/auth/
services/auth-service/src/main/resources/
services/auth-service/src/test/java/com/insurancemanagementsystem/auth/
```

Use: `mkdir -p services/auth-service/src/main/java/com/insurancemanagementsystem/auth` and similar for test + resources.

### Step 2: Create `build.gradle.kts`

**File:** `services/auth-service/build.gradle.kts`

Copy the entire content from `services/reference-data-service/build.gradle.kts` and make these changes:

1. Keep the same `plugins`, `group`, `version`, `java` toolchain, `configurations`, `repositories` blocks — no changes needed.
2. In the `dependencies` block:
   - Keep: `implementation(project(":common:common-message"))`
   - Keep: `implementation(project(":common:common-web"))`
   - Keep: `spring-boot-starter-web`, `spring-boot-starter-webmvc`, `spring-boot-starter-json`
   - **Add:** `implementation("org.springframework.boot:spring-boot-starter-security")` — needed for BCrypt
   - Keep: `spring-boot-starter-data-jpa`
   - Keep: `com.fasterxml.jackson.core:jackson-databind` (Jackson 2 for Kafka compat)
   - Keep: `spring-boot-starter-validation`
   - Keep: `spring-cloud-stream`, `spring-cloud-stream-binder-kafka` — included for consistency with other services, even though auth-service has no SAGA consumers
   - Keep: `spring-cloud-starter-netflix-eureka-client`
   - **Add these JWT dependencies** after the Eureka line:
     ```
     implementation("io.jsonwebtoken:jjwt-api:0.12.6")
     runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
     runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
     ```
   - Keep: `compileOnly("org.projectlombok:lombok")` and `annotationProcessor`
   - Keep: `runtimeOnly("org.postgresql:postgresql")`
   - In the `testImplementation` block: **remove** `spring-kafka-test`, `testcontainers-kafka` (auth-service has no Kafka consumers). Keep the rest (spring-boot-starter-test, restclient, webmvc-test, data-jpa-test, testcontainers, testcontainers-postgresql, testcontainers-junit-jupiter).
3. Keep the `dependencyManagement` and `tasks.withType<Test>` blocks unchanged.

### Step 3: Uncomment auth-service in `settings.gradle.kts`

**File:** `settings.gradle.kts`

Find the line:
```
// include("services:auth-service")
```
Change it to:
```
include("services:auth-service")
```

Remove the `// ` prefix only. Do not change any other lines.

### Step 4: Create `application.yml`

**File:** `services/auth-service/src/main/resources/application.yml`

Use `services/reference-data-service/src/main/resources/application.yml` as the template, with these specific values:

```yaml
server:
  port: 8087

spring:
  application:
    name: auth-service
  profiles:
    active: dev
  config:
    import: classpath:application-common.yml
  datasource:
    url: ${AUTH_DB_URL:jdbc:postgresql://localhost:5432/auth_db}
    username: ${AUTH_DB_USERNAME:ims_user}
    password: ${AUTH_DB_PASSWORD:ims_password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: auth-service-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "com.insurancemanagementsystem.*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  cloud:
    stream:
      default-binder: kafka
      kafka:
        binder:
          brokers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
          configuration:
            auto.create.topics.enable: false

eureka:
  client:
    serviceUrl:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
  instance:
    preferIpAddress: true

logging:
  level:
    com.insurancemanagementsystem: DEBUG
```

Key differences from reference-data-service:
- Port: `8087` (not 8086)
- Application name: `auth-service`
- Datasource URL uses `${AUTH_DB_URL}` env var (already in `.env.template`)
- No `spring.cloud.stream.bindings` block — auth-service has no Kafka bindings
- No `OUTBOX_POLL_INTERVAL_MS` — auth-service has no outbox

### Step 5: Create Application main class

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/AuthServiceApplication.java`

Follow the pattern from `ReferenceDataServiceApplication.java` but adapt package names:

```java
package com.insurancemanagementsystem.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = {
    "com.insurancemanagementsystem.auth",
    "com.insurancemanagementsystem.common.messaging",
    "com.insurancemanagementsystem.common.config",
    "com.insurancemanagementsystem.common.web"
})
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.auth.entity",
    "com.insurancemanagementsystem.common.entity"
})
public class AuthServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```

Note: `static void main` (no `public`) — follows the Java 21+ relaxed main convention from `docs/outlines/10_JAVA_CONVENTIONS.md`.

### Step 6: Verify `.env.template` coverage

Open `.env.template`. Confirm these three lines exist:
```
AUTH_DB_URL=jdbc:postgresql://localhost:5432/auth_db
AUTH_DB_USERNAME=ims_user
AUTH_DB_PASSWORD=ims_password
```
They should already be present (they were confirmed in the task file at line 26). If any are missing, add them. Do not modify existing lines.

---

## Acceptance Criteria

- [x] `services/auth-service/build.gradle.kts` exists with correct dependencies including jjwt and spring-boot-starter-security
- [x] `settings.gradle.kts` has `include("services:auth-service")` uncommented (no `//` prefix)
- [x] `services/auth-service/src/main/resources/application.yml` exists with port 8087, auth-db datasource, Eureka client
- [x] `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/AuthServiceApplication.java` exists with correct `@SpringBootApplication` and `@EntityScan`
- [x] `.env.template` contains `AUTH_DB_URL`, `AUTH_DB_USERNAME`, `AUTH_DB_PASSWORD`
- [x] Running `./gradlew projects` from repo root lists `services:auth-service`
