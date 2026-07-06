# Plan: Fix 12 — Extract Shared SagaEvent & isDuplicateSagaEvent to Common Module

## Objective

Eliminate the **high-severity DRY violation** where `SagaEvent.java`, `SagaEventRepository.java`, and `isDuplicateSagaEvent()` are duplicated identically across 3 services (estimation, customer, insurance). Extract the shared code into the `common` module so there is a single source of truth.

## Current State

### What's duplicated

| Artifact | estimation-service | customer-service | insurance-service | Lines |
|----------|-------------------|-----------------|-------------------|-------|
| `SagaEvent.java` | ✅ identical | ✅ identical | ✅ identical | 44 each |
| `SagaEventRepository.java` | ✅ identical | ✅ identical | ✅ identical | 14 each |
| `isDuplicateSagaEvent()` | ✅ in `EstimationSagaConsumer` | ✅ in `CustomerSagaConsumer` | ✅ in `InsuranceSagaConsumer` | ~13 each |

**Total duplication:** 3 × (44 + 14 + 13) = ~213 lines of identical code across 3 services.

### OutboxEvent duplication (post-Plan 11)

After Plan 11 adds outbox to customer-service and insurance-service, `OutboxEvent.java` and `OutboxEventRepository.java` will also be duplicated 3×. This plan should handle them too.

---

## Cross-Service Analysis

| Service | Has SagaEvent? | Has OutboxEvent? | Uses isDuplicateSagaEvent()? |
|---------|---------------|-----------------|------------------------------|
| **estimation-service** | ✅ | ✅ | ✅ (DB-backed) |
| **customer-service** | ✅ (added in Plan 06) | ✅ (added in Plan 11) | ✅ (DB-backed, migrated in Plan 06) |
| **insurance-service** | ✅ (added in Plan 06) | ✅ (added in Plan 11) | ✅ (DB-backed, migrated in Plan 06) |

All 3 services use the same entities with the same table names (`saga_events`, `outbox_events`). Each service has its own database, so the table name collision doesn't exist.

---

## Context Files to Read First

### Existing entities (to be moved)
1. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/SagaEvent.java`** (44 lines) — template
2. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/OutboxEvent.java`** (70 lines) — template
3. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/SagaEventRepository.java`** (14 lines) — template
4. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/OutboxEventRepository.java`** (20 lines) — template

### Consumers that use isDuplicateSagaEvent()
5. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`** — `isDuplicateSagaEvent()` at lines 42-54
6. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerSagaConsumer.java`** — identical method at lines 34-46
7. **`services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`** — identical method at lines 38-50

### Common module structure
8. **`common/common-message/build.gradle.kts`** — current dependencies (needs `spring-data-jpa` added)
9. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/`** — existing package structure

### Build files for entity scanning
10. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/EstimationServiceApplication.java`** — `@EntityScan` / `@EnableJpaRepositories` config
11. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/CustomerServiceApplication.java`** — same
12. **`services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/InsuranceServiceApplication.java`** — same

---

## Design Decision

### Where to put shared entities

The `common-message` module currently contains event schemas and `MessagePublisher`. It's a lightweight JAR. Adding JPA entities to it requires adding `spring-data-jpa` (pulls in Hibernate, etc.) as a transitive dependency. This is acceptable — all 3 services already depend on `spring-data-jpa`.

**Alternative:** Create a new `common-entity` module. Overkill for 2 entities.

### Implementation strategy

1. Move `SagaEvent.java` and `OutboxEvent.java` to `common-message/src/main/java/.../common/entity/`
2. Move `SagaEventRepository.java` and `OutboxEventRepository.java` to `common-message/src/main/java/.../common/repository/`
3. Add `spring-data-jpa` dependency to `common-message/build.gradle.kts`
4. Add `isDuplicateSagaEvent()` as a `default` method on `SagaEventRepository`
5. Delete the per-service copies
6. Update imports in all 3 services' consumers and services

### Why default method on repository?

```java
@Repository
public interface SagaEventRepository extends JpaRepository<SagaEvent, UUID> {
    boolean existsBySagaIdAndEventType(UUID sagaId, String eventType);
    Optional<SagaEvent> findBySagaIdAndEventType(UUID sagaId, String eventType);

    /**
     * Atomically inserts a dedup marker.
     * @return true if this event was already processed (duplicate), false if new.
     */
    default boolean tryInsertDedup(UUID sagaId, String eventType) {
        SagaEvent dedup = SagaEvent.builder()
                .sagaId(sagaId)
                .eventType(eventType)
                .build();
        try {
            save(dedup);
            return false;
        } catch (DataIntegrityViolationException e) {
            return true;
        }
    }
}
```

This eliminates the identical `isDuplicateSagaEvent()` method from all 3 consumers. Callers use:

```java
if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
    return; // duplicate — skip
}
```

### @EntityScan configuration

Each service's main application class must include the shared entity package in its scan:

```java
@SpringBootApplication
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.estimation.entity",
    "com.insurancemanagementsystem.common.entity"    // ← added
})
@EnableJpaRepositories(basePackages = {
    "com.insurancemanagementsystem.estimation.repository",
    "com.insurancemanagementsystem.common.repository"  // ← added
})
public class EstimationServiceApplication { ... }
```

Same pattern for customer-service and insurance-service.

**Actually**, Spring Boot auto-scans from the application class package downward. Since `common.entity` and `common.repository` are in a different top-level package (`com.insurancemanagementsystem.common.*` vs `com.insurancemanagementsystem.estimation.*`), explicit `@EntityScan` and `@EnableJpaRepositories` are needed.

---

## Files to Create (in common-message)

### 1. `common/common-message/src/main/java/com/insurancemanagementsystem/common/entity/SagaEvent.java`

Copy from existing estimation-service version. Package: `com.insurancemanagementsystem.common.entity`.

### 2. `common/common-message/src/main/java/com/insurancemanagementsystem/common/entity/OutboxEvent.java`

Copy from existing estimation-service version (post-Plan 09, with `PUBLISHED` status). Package: `com.insurancemanagementsystem.common.entity`.

### 3. `common/common-message/src/main/java/com/insurancemanagementsystem/common/repository/SagaEventRepository.java`

Copy from existing + add `tryInsertDedup()` default method. Package: `com.insurancemanagementsystem.common.repository`.

### 4. `common/common-message/src/main/java/com/insurancemanagementsystem/common/repository/OutboxEventRepository.java`

Copy from existing estimation-service version (post-Plan 09, with updated query methods). Package: `com.insurancemanagementsystem.common.repository`.

## Files to Modify

### 5. `common/common-message/build.gradle.kts`

Add dependency:
```kotlin
api("org.springframework.boot:spring-boot-starter-data-jpa")
```

Use `api` (not `implementation`) so downstream services inherit the JPA dependency transitively.

### 6. Estimation-service — delete per-service copies

Delete:
- `services/estimation-service/src/main/java/.../estimation/entity/SagaEvent.java`
- `services/estimation-service/src/main/java/.../estimation/entity/OutboxEvent.java`
- `services/estimation-service/src/main/java/.../estimation/repository/SagaEventRepository.java`
- `services/estimation-service/src/main/java/.../estimation/repository/OutboxEventRepository.java`

### 7. Estimation-service — update all imports

In every file that imports the deleted entities/repos:
- `EstimationSagaConsumer.java` — update imports, replace `isDuplicateSagaEvent()` with `sagaEventRepository.tryInsertDedup()`
- `EstimationService.java` — update `OutboxEvent` / `OutboxEventRepository` imports
- `SagaTimeoutService.java` — same
- `OutboxProcessor.java` — same
- `OutboxRelay.java` — same
- `OutboxEventSerializer.java` — same

### 8. Estimation-service — update `EstimationServiceApplication.java`

Add `@EntityScan` and `@EnableJpaRepositories` with both local and shared packages:

```java
@EntityScan(basePackages = {
    "com.insurancemanagementsystem.estimation.entity",
    "com.insurancemanagementsystem.common.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.insurancemanagementsystem.estimation.repository",
    "com.insurancemanagementsystem.common.repository"
})
```

### 9. Customer-service — same as #6, #7, #8

Delete per-service SagaEvent.java, OutboxEvent.java, repos.
Update imports in `CustomerSagaConsumer.java`.
Update `CustomerServiceApplication.java` with entity scan.
Replace `isDuplicateSagaEvent()` with `sagaEventRepository.tryInsertDedup()`.

### 10. Insurance-service — same

Delete per-service copies.
Update imports in `InsuranceSagaConsumer.java`.
Update `InsuranceServiceApplication.java`.
Replace `isDuplicateSagaEvent()` with `sagaEventRepository.tryInsertDedup()`.

### 11. Test files — update imports

Each service's test files import SagaEvent, OutboxEvent, or their repositories. Update imports from per-service package to common package.

Files to update:
- `estimation-service`: `EstimationSagaConsumerTest.java`, `EstimationServiceTest.java`, `SagaTimeoutServiceTest.java`, `OutboxRelayTest.java`, `OutboxProcessorTest.java`, `EstimationServiceIntegrationTest.java`
- `customer-service`: `CustomerSagaConsumerTest.java`, `OutboxProcessorTest.java`
- `insurance-service`: `InsuranceSagaConsumerTest.java`, `OutboxProcessorTest.java`

---

## Verification

```bash
# 1. Build common-message (must succeed first)
.\gradlew.bat :common:common-message:build

# 2. Build all 3 services
.\gradlew.bat :services:estimation-service:build
.\gradlew.bat :services:customer-service:build
.\gradlew.bat :services:insurance-service:build

# 3. Run all tests
.\gradlew.bat test
```

---

## Execution Checklist

- [ ] Read context files 1-12
- [ ] Edit `common/common-message/build.gradle.kts` — add `spring-boot-starter-data-jpa` as `api` dependency
- [ ] Create `common/.../common/entity/SagaEvent.java`
- [ ] Create `common/.../common/entity/OutboxEvent.java`
- [ ] Create `common/.../common/repository/SagaEventRepository.java` with `tryInsertDedup()` default method
- [ ] Create `common/.../common/repository/OutboxEventRepository.java`
- [ ] Build `common:common-message`: SUCCESS
- [ ] Delete per-service `SagaEvent.java` (3 files)
- [ ] Delete per-service `OutboxEvent.java` (3 files — or 2 if insurance doesn't have one yet)
- [ ] Delete per-service `SagaEventRepository.java` (3 files)
- [ ] Delete per-service `OutboxEventRepository.java` (3 files)
- [ ] Update imports in estimation-service consumers/services (6+ files)
- [ ] Replace `isDuplicateSagaEvent()` with `sagaEventRepository.tryInsertDedup()` in estimation-service
- [ ] Update `EstimationServiceApplication.java` — add `@EntityScan` + `@EnableJpaRepositories`
- [ ] Update imports in customer-service (2+ files)
- [ ] Replace `isDuplicateSagaEvent()` with `sagaEventRepository.tryInsertDedup()` in customer-service
- [ ] Update `CustomerServiceApplication.java` — add entity scan
- [ ] Update imports in insurance-service (2+ files)
- [ ] Replace `isDuplicateSagaEvent()` with `sagaEventRepository.tryInsertDedup()` in insurance-service
- [ ] Update `InsuranceServiceApplication.java` — add entity scan
- [ ] Update imports in all test files (10+ files)
- [ ] Compile common-message: SUCCESS
- [ ] Compile estimation-service: SUCCESS
- [ ] Compile customer-service: SUCCESS
- [ ] Compile insurance-service: SUCCESS
- [ ] All tests pass across all services

---

## Risk Assessment

- **Risk:** LOW. Pure refactoring — no behavioral change. Entities are byte-for-byte identical. Repos have the same method signatures. The `tryInsertDedup()` default method is functionally identical to the deleted `isDuplicateSagaEvent()`.
- **`@EntityScan` risk:** If a service's entity scan is misconfigured, JPA won't find `SagaEvent` or `OutboxEvent`. Compilation will succeed (entities aren't referenced at compile time for schema generation) but runtime will fail. **Verify with integration tests that load the full context.**
- **Common-message JAR size:** Adding `spring-data-jpa` increases the JAR but all 3 services already have this dependency. Net effect: zero change in overall artifact size.
- **Compile order:** `common:common-message:build` MUST complete before any service compiles. This is the natural build order per `settings.gradle.kts`.

---

## Dependencies

- **Prerequisite: Plan 09** (OutboxRelay fix) — OutboxEvent.Status must include `PUBLISHED`
- **Prerequisite: Plan 10** (ACID gaps) — `OutboxEventSerializer` (or equivalent) must exist
- **Prerequisite: Plan 11** (Dual-write fix) — OutboxEvent entity must be present in all 3 services before extraction
