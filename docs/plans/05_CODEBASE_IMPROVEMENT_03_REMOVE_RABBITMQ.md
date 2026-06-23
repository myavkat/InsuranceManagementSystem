# Plan: Fix 08 — Remove Unused RabbitMQ Dependencies and Configuration

## Objective

Remove the unused RabbitMQ binder dependency and connection configuration from all active services. RabbitMQ is not used by any service — all messaging uses Kafka. The RabbitMQ config is dead code that adds unnecessary startup overhead, dependency resolution time, and configuration surface area.

## Cross-Service Analysis

| Service | `build.gradle.kts` line | `application.yml` lines | File |
|---------|------------------------|------------------------|------|
| **customer-service** | Line 37: `spring-cloud-stream-binder-rabbit` | Lines 45-48: `spring.rabbitmq` block | ✅ Remove both |
| **insurance-service** | Line 37: `spring-cloud-stream-binder-rabbit` | Lines 43-46: `spring.rabbitmq` block | ✅ Remove both |
| **estimation-service** | Line 37: `spring-cloud-stream-binder-rabbit` | Lines 44-48: `spring.rabbitmq` block | ✅ Remove both |
| **reference-skeleton** | Line 33: `spring-cloud-stream-binder-rabbit` | Lines 40-43: `spring.rabbitmq` block | ⚠️ Template file — keep for now |

**All 3 active services** have the same RabbitMQ binder dependency (same line, same module) and the same RabbitMQ connection config block in `application.yml`. The `reference-skeleton` is a template — leave its config as-is (future services may need RabbitMQ).

## Context Files to Read First

### Build files
1. **`services/estimation-service/build.gradle.kts`** — Line 37: rabbit binder dep
2. **`services/customer-service/build.gradle.kts`** — Line 37: rabbit binder dep
3. **`services/insurance-service/build.gradle.kts`** — Line 37: rabbit binder dep

### Config files
4. **`services/estimation-service/src/main/resources/application.yml`** — Lines 44-48: rabbit config block
5. **`services/customer-service/src/main/resources/application.yml`** — Lines 45-48: rabbit config block
6. **`services/insurance-service/src/main/resources/application.yml`** — Lines 43-46: rabbit config block

## Files to Modify

### Step 1: Remove RabbitMQ binder from `estimation-service/build.gradle.kts`

**BEFORE (line 37):**
```kotlin
    implementation("org.springframework.cloud:spring-cloud-stream-binder-kafka")
    implementation("org.springframework.cloud:spring-cloud-stream-binder-rabbit")
```

**AFTER:**
```kotlin
    implementation("org.springframework.cloud:spring-cloud-stream-binder-kafka")
```

**Action:** Delete the line containing `spring-cloud-stream-binder-rabbit`.

### Step 2: Remove RabbitMQ config from `estimation-service/src/main/resources/application.yml`

**BEFORE (lines 44-48):**
```yaml
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

**AFTER:** Delete lines 44-48.

### Step 3: Remove RabbitMQ binder from `customer-service/build.gradle.kts`

Same as Step 1 — delete the `spring-cloud-stream-binder-rabbit` line.

### Step 4: Remove RabbitMQ config from `customer-service/src/main/resources/application.yml`

Same as Step 2 — delete the `spring.rabbitmq` block.

### Step 5: Remove RabbitMQ binder from `insurance-service/build.gradle.kts`

Same as Step 1 — delete the `spring-cloud-stream-binder-rabbit` line.

### Step 6: Remove RabbitMQ config from `insurance-service/src/main/resources/application.yml`

Same as Step 2 — delete the `spring.rabbitmq` block.

### Do NOT modify
- `services/reference-skeleton/build.gradle.kts` — template file, keep RabbitMQ for future services
- `services/reference-skeleton/src/main/resources/application.yml` — template file, keep RabbitMQ config

## Verification

After removing RabbitMQ from all 3 services, verify each service compiles cleanly:

```bash
# 1. Compile estimation-service
.\gradlew.bat :services:estimation-service:compileJava

# 2. Compile customer-service
.\gradlew.bat :services:customer-service:compileJava

# 3. Compile insurance-service
.\gradlew.bat :services:insurance-service:compileJava

# 4. Run all estimation-service tests
.\gradlew.bat :services:estimation-service:test

# 5. Run all customer-service tests
.\gradlew.bat :services:customer-service:test

# 6. Run all insurance-service tests
.\gradlew.bat :services:insurance-service:test
```

All compilations must succeed. All tests must pass.

## Execution Checklist

- [ ] Read `services/estimation-service/build.gradle.kts` — confirm rabbit line
- [ ] Read `services/estimation-service/src/main/resources/application.yml` — confirm rabbit block
- [ ] Read `services/customer-service/build.gradle.kts` — confirm rabbit line
- [ ] Read `services/customer-service/src/main/resources/application.yml` — confirm rabbit block
- [ ] Read `services/insurance-service/build.gradle.kts` — confirm rabbit line
- [ ] Read `services/insurance-service/src/main/resources/application.yml` — confirm rabbit block
- [ ] Edit estimation-service `build.gradle.kts` — remove rabbit binder
- [ ] Edit estimation-service `application.yml` — remove rabbit block
- [ ] Edit customer-service `build.gradle.kts` — remove rabbit binder
- [ ] Edit customer-service `application.yml` — remove rabbit block
- [ ] Edit insurance-service `build.gradle.kts` — remove rabbit binder
- [ ] Edit insurance-service `application.yml` — remove rabbit block
- [ ] Compile all 3 services — all `BUILD SUCCESSFUL`
- [ ] All tests pass

## Risk Assessment

- **Risk:** VERY LOW. RabbitMQ is unused. The `spring-cloud-stream-binder-rabbit` dependency is only needed if a service configures a RabbitMQ binder. Without the config, the dependency is ignored. Removing it just cleans up dead code.
- **If someone later wants RabbitMQ:** They can re-add the dependency and config. The `reference-skeleton` template still has it as a reference.
