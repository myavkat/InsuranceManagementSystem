# Plan: Fix 01 — Extract Common MessagePublisher to shared module

## Objective
Eliminate the triplicated `MessagePublisher` class (identical 19-line copies in customer-service, insurance-service, estimation-service) by creating a single shared `MessagePublisher` in `common/common-message`. Update all 3 services to use it and remove their local copies.

## Why
All 3 services have identical `MessagePublisher` classes (only the package name differs). This is a HIGH DRY violation — any future change must be replicated 3 times.

## Context Files to Read First

Read these files to understand the exact current state before editing:

1. **`common/common-message/build.gradle.kts`** — Current dependencies (needs `spring-cloud-stream` added)
2. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/MessagePublisher.java`** — Reference copy (19 lines)
3. **`services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/MessagePublisher.java`** — Reference copy (19 lines)
4. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/MessagePublisher.java`** — Reference copy (19 lines)
5. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerEventPublisher.java`** — Consumer of MessagePublisher (verify import)
6. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerSagaConsumer.java`** — Consumer of MessagePublisher (verify import)
7. **`services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceEventPublisher.java`** — Consumer of MessagePublisher (verify import)
8. **`services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`** — Consumer of MessagePublisher (verify import)
9. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationEventPublisher.java`** — Consumer of MessagePublisher (verify import)
10. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`** — Consumer of MessagePublisher (verify import)
11. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`** — Verify it does NOT use MessagePublisher directly

### For test file discovery (after main code is done):
12. Glob: `services/*/src/test/**/*MessagePublisher*` — Find all test files to update
13. Glob: `services/*/src/test/**/*.java` — All test files in all services (to check for imports of old MessagePublisher)

## Key Design Decision: Where to put the shared class

**Package:** `com.insurancemanagementsystem.common.messaging.MessagePublisher`

**Module:** `common/common-message` — because it's the messaging domain module that already contains `EventEnvelope`, `EventConstants`, `BaseEvent`, and all event POJOs.

**Dependency concern:** `MessagePublisher` needs `StreamBridge` (from `spring-cloud-stream`). To avoid pulling Spring Cloud Stream as a transitive runtime dependency for all consumers of `common-message`, use **`compileOnly`** scope:

```kotlin
// In common/common-message/build.gradle.kts, add:
compileOnly("org.springframework.cloud:spring-cloud-stream")
// Also add Spring Cloud BOM to dependencyManagement for version management
dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.2")  // ADD THIS
    }
}
```

`compileOnly` means: `common-message` can compile against `StreamBridge`, but the actual `StreamBridge` class is provided at runtime by the service that uses it (all 3 services already have `implementation("org.springframework.cloud:spring-cloud-stream")`).

## Files to Modify (in order)

### STEP 1: Update `common/common-message/build.gradle.kts`

Open the file. Add:
- Spring Cloud BOM to `dependencyManagement.imports`
- `compileOnly("org.springframework.cloud:spring-cloud-stream")` to `dependencies`

**Before** (current):
```kotlin
dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    implementation("tools.jackson.core:jackson-databind")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("jakarta.validation:jakarta.validation-api")
    ...
}
```

**After:**
```kotlin
dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.2")
    }
}

dependencies {
    implementation("tools.jackson.core:jackson-databind")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("jakarta.validation:jakarta.validation-api")
    compileOnly("org.springframework.cloud:spring-cloud-stream")
    ...
}
```

### STEP 2: Create shared MessagePublisher

**Create file:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/MessagePublisher.java`

```java
package com.insurancemanagementsystem.common.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessagePublisher {

    private final StreamBridge streamBridge;

    public void publish(String topic, Object message) {
        log.debug("Publishing message to {}: {}", topic, message);
        streamBridge.send(topic, message);
    }
}
```

This is **exactly identical** to all 3 existing copies, just with the new package.

### STEP 3: Delete MessagePublisher from estimation-service

**Delete:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/MessagePublisher.java`

Then update all files in estimation-service that import the old class:

#### 3a. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationEventPublisher.java`

**Remove old import:**
```java
import com.insurancemanagementsystem.estimation.config.MessagePublisher;
```
**Add new import:**
```java
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
```

#### 3b. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`

**Remove old import:**
```java
import com.insurancemanagementsystem.estimation.config.MessagePublisher;
```
**Add new import:**
```java
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
```

#### 3c. Test files in estimation-service — update imports

All test files that reference `com.insurancemanagementsystem.estimation.config.MessagePublisher` need their imports updated to `com.insurancemanagementsystem.common.messaging.MessagePublisher`. These files include:

- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/MessagePublisherTest.java` — update import AND consider: should this test move to common? DECISION: Keep the test in estimation-service but change the import. It tests the shared class from this service's perspective (verifies StreamBridge integration works in this service's context). The `@Mock` and `@InjectMocks` still work because `@Component` makes it a Spring bean.
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationEventPublisherTest.java` — update import
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/EstimationServiceTest.java` — update import

### STEP 4: Delete MessagePublisher from insurance-service

**Delete:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/MessagePublisher.java`

#### 4a. `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceEventPublisher.java`

**Remove old import:**
```java
import com.insurancemanagementsystem.insurance.config.MessagePublisher;
```
**Add new import:**
```java
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
```

#### 4b. `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`

**Remove old import:**
```java
import com.insurancemanagementsystem.insurance.config.MessagePublisher;
```
**Add new import:**
```java
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
```

#### 4c. Test files in insurance-service

- `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/saga/InsuranceSagaConsumerTest.java` — update import (`com.insurancemanagementsystem.insurance.config.MessagePublisher` → `com.insurancemanagementsystem.common.messaging.MessagePublisher`)

### STEP 5: Delete MessagePublisher from customer-service

**Delete:** `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/MessagePublisher.java`

#### 5a. `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerEventPublisher.java`

**Remove old import:**
```java
import com.insurancemanagementsystem.customer.config.MessagePublisher;
```
**Add new import:**
```java
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
```

#### 5b. `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerSagaConsumer.java`

**Remove old import:**
```java
import com.insurancemanagementsystem.customer.config.MessagePublisher;
```
**Add new import:**
```java
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
```

#### 5c. Test files in customer-service

- `services/customer-service/src/test/java/com/insurancemanagementsystem/customer/saga/CustomerSagaConsumerTest.java` — update import (`com.insurancemanagementsystem.customer.config.MessagePublisher` → `com.insurancemanagementsystem.common.messaging.MessagePublisher`)

### STEP 6: Scan remaining test files for old imports

Run a grep across all `services/*/src/test/` for `import.*\.config\.MessagePublisher` to ensure no stale references remain.

---

## Verification

After all changes, verify each service compiles and tests pass:

```bash
# 1. Compile common-message (should now include MessagePublisher)
.\gradlew.bat :common:common-message:compileJava

# 2. Compile estimation-service (should use shared MessagePublisher)
.\gradlew.bat :services:estimation-service:compileJava

# 3. Run estimation-service tests
.\gradlew.bat :services:estimation-service:test

# 4. Compile insurance-service
.\gradlew.bat :services:insurance-service:compileJava

# 5. Run insurance-service tests
.\gradlew.bat :services:insurance-service:test

# 6. Compile customer-service
.\gradlew.bat :services:customer-service:compileJava

# 7. Run customer-service tests
.\gradlew.bat :services:customer-service:test
```

All compilations and tests must pass.

---

## Files Summary

### Created
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/messaging/MessagePublisher.java`

### Modified
- `common/common-message/build.gradle.kts` — add `compileOnly("org.springframework.cloud:spring-cloud-stream")` + Spring Cloud BOM
- `services/estimation-service/src/main/java/.../config/EstimationEventPublisher.java` — update import
- `services/estimation-service/src/main/java/.../service/EstimationService.java` — update import
- `services/estimation-service/src/test/java/.../config/MessagePublisherTest.java` — update import
- `services/estimation-service/src/test/java/.../config/EstimationEventPublisherTest.java` — update import
- `services/estimation-service/src/test/java/.../service/EstimationServiceTest.java` — update import
- `services/insurance-service/src/main/java/.../config/InsuranceEventPublisher.java` — update import
- `services/insurance-service/src/main/java/.../config/InsuranceSagaConsumer.java` — update import
- `services/insurance-service/src/test/java/.../saga/InsuranceSagaConsumerTest.java` — update import
- `services/customer-service/src/main/java/.../config/CustomerEventPublisher.java` — update import
- `services/customer-service/src/main/java/.../config/CustomerSagaConsumer.java` — update import
- `services/customer-service/src/test/java/.../saga/CustomerSagaConsumerTest.java` — update import

### Deleted
- `services/estimation-service/src/main/java/.../config/MessagePublisher.java`
- `services/insurance-service/src/main/java/.../config/MessagePublisher.java`
- `services/customer-service/src/main/java/.../config/MessagePublisher.java`

---

## Important Notes for Implementer

1. **Do NOT change any logic** — only change imports and delete the old local MessagePublisher files.
2. **`@Component` annotation on MessagePublisher** — Spring component scanning will find it because each service's `@SpringBootApplication` includes `com.insurancemanagementsystem` in its base package (either directly or via `scanBasePackages`). For services with explicit `scanBasePackages`, verify `com.insurancemanagementsystem` is covered:
   - `customer-service`: `scanBasePackages` covers `com.insurancemanagementsystem.customer` — BUT `com.insurancemanagementsystem.common.messaging` is a different root. Need to ADD `"com.insurancemanagementsystem.common.messaging"` to scanBasePackages, OR add `@ComponentScan("com.insurancemanagementsystem.common.messaging")`, OR move the scan to a broader package like `com.insurancemanagementsystem`.
   
   Wait — actually let's re-examine. The `common-web` module's classes (like `ApiResponse`, `GlobalExceptionHandler`) are already scanned by estimation-service via `scanBasePackages = {"com.insurancemanagementsystem.estimation", "com.insurancemanagementsystem.common.web"}`. For the new `common.messaging` package, the services need to ALSO scan that package.

   **IMPORTANT ACTION:** For each service, update `scanBasePackages` to include `"com.insurancemanagementsystem.common.messaging"`:
   - `services/estimation-service/.../EstimationServiceApplication.java` — add to `scanBasePackages`
   - `services/insurance-service/.../InsuranceServiceApplication.java` — add to `scanBasePackages`
   - `services/customer-service/.../CustomerServiceApplication.java` — add to `scanBasePackages`

   If any service does NOT use explicit `scanBasePackages`, Spring Boot's default component scan from the application class's package works, but it won't reach `com.insurancemanagementsystem.common.messaging` because that's a different root package. So explicit scan is required.

3. **Test MessagePublisherTest** — The test `MessagePublisherTest` in estimation-service mocks `StreamBridge` and verifies `MessagePublisher.publish()` delegates. This test should still pass after the refactor because `@InjectMocks` will find the shared `MessagePublisher` class (a Spring `@Component`). The import change is all that's needed.
