# Plan 03: Update Tests for Human-Readable Insurance Names

## Objective

Update all test files that reference old technical-code insurance names or old insurance type names. This plan fixes two categories of stale test data:

1. **Insurance type names** — Tests that were never updated after the `insurance_types` restructure (Plan archived: `01-seed-data-restructure.md`) and still use 5 types with old names ("TRAFFIC", "CASCO", "DASK", "HEALTH", "LIFE"). The schema now has 4 types: "Vehicle", "Real Estate", "Health", "Life".
2. **Insurance product names** — Tests that hardcode old technical codes ("TRAFFIC", "DASK") as `InsuranceInfo.name`. These should use the new human-readable names from Plan 02.

## Dependency

**Plans 01 and 02 must be completed first** — the `code` column and human-readable seed data must be in place before tests can be validated.

## Files to Read First

- `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/InsuranceServiceApplicationTests.java` — the full file (245 lines)
- `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/saga/InsuranceSagaConsumerTest.java` — lines 86–102 (setUp method)
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/EstimationServiceTest.java` — lines 95–165 (mock setup)
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceIntegrationTest.java` — lines 125–220
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceApplicationTests.java` — lines 70–80
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/e2e/SagaE2ETest.java` — lines 125–140
- `docs/outlines/11_TESTING_CONVENTIONS.md` — testing conventions (Spring Boot 4, RestTestClient)
- `docs/outlines/10_JAVA_CONVENTIONS.md` — Java conventions

## Technical Context

- Spring Boot 4 testing with `@SpringBootTest`, `@AutoConfigureRestTestClient`, `RestTestClient`
- Testcontainers with `PostgreSQLContainer("postgres:16-alpine")` and `ConfluentKafkaContainer`
- Tests use `ddl-auto: create-drop` — entities are mapped to DB schema directly, no init.sql involved
- Entity class: `InsuranceType(id INT, name VARCHAR(50))` — simple lookup entity
- The `InsuranceServiceClient.InsuranceInfo` record is `(UUID id, String name, Integer typeId, String typeName)` — used in estimation service mocks
- When creating `Insurance` entities in tests, the new `code` field (from Plan 01) MUST be populated since it's `nullable = false`

## Part A: Fix `InsuranceServiceApplicationTests.java` (insurance-service)

### A1: Fix the `@BeforeEach` seed data

Open `InsuranceServiceApplicationTests.java`.

**Current (lines 72–78):**
```java
insuranceTypeRepository.saveAll(List.of(
        new InsuranceType(1, "TRAFFIC"),
        new InsuranceType(2, "CASCO"),
        new InsuranceType(3, "DASK"),
        new InsuranceType(4, "HEALTH"),
        new InsuranceType(5, "LIFE")
));
```

**Replace with (4 types matching current schema):**
```java
insuranceTypeRepository.saveAll(List.of(
        new InsuranceType(1, "Vehicle"),
        new InsuranceType(2, "Real Estate"),
        new InsuranceType(3, "Health"),
        new InsuranceType(4, "Life")
));
```

### A2: Fix `getTypes_returnsSeedData` test

**Current (lines 223–234):**
```java
@Test
void getTypes_returnsSeedData() {
    restTestClient.get().uri("/api/insurances/types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.data.length()").isEqualTo(5)
            .jsonPath("$.data[0].name").isEqualTo("TRAFFIC")
            .jsonPath("$.data[1].name").isEqualTo("CASCO")
            .jsonPath("$.data[2].name").isEqualTo("DASK")
            .jsonPath("$.data[3].name").isEqualTo("HEALTH")
            .jsonPath("$.data[4].name").isEqualTo("LIFE");
}
```

**Replace with:**
```java
@Test
void getTypes_returnsSeedData() {
    restTestClient.get().uri("/api/insurances/types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.data.length()").isEqualTo(4)
            .jsonPath("$.data[0].name").isEqualTo("Vehicle")
            .jsonPath("$.data[1].name").isEqualTo("Real Estate")
            .jsonPath("$.data[2].name").isEqualTo("Health")
            .jsonPath("$.data[3].name").isEqualTo("Life");
}
```

### A3: Update `createValidInsuranceRequest` helper (if needed)

The helper method at line 237 creates `InsuranceRequest` with `.name("TestInsurance")` and `.typeId(1)`. This is fine — it uses a generic test name, not a seed technical code. **No change needed.**

### A4: Update any Insurance builder in tests to include `code`

Since the `Insurance` entity now has a non-nullable `code` field (from Plan 01), any test that creates an `Insurance` entity with `.build()` must also set `.code(...)`.

Search for `Insurance.builder()` in this test file. The `listInsurances` test (line 114) uses:
```java
Insurance insurance = Insurance.builder()
        .name("TestInsurance")
        .typeId(1)
        .basePremium(new BigDecimal("1000"))
        .build();
```

Add `.code("TESTINSURANCE")` to the builder:
```java
Insurance insurance = Insurance.builder()
        .name("TestInsurance")
        .code("TESTINSURANCE")
        .typeId(1)
        .basePremium(new BigDecimal("1000"))
        .build();
```

## Part B: Fix `InsuranceSagaConsumerTest.java` (insurance-service)

### B1: Fix InsuranceType seed in setUp()

Open `InsuranceSagaConsumerTest.java`.

**Current (line 95):**
```java
insuranceTypeRepository.save(new InsuranceType(1, "TRAFFIC"));
```

**Replace with:**
```java
insuranceTypeRepository.save(new InsuranceType(1, "Vehicle"));
```

### B2: Update Insurance builder in setUp()

**Current (lines 97–102):**
```java
Insurance savedInsurance = insuranceRepository.save(Insurance.builder()
        .name("Traffic Insurance")
        .typeId(1)
        .basePremium(BigDecimal.valueOf(1000))
        .build());
```

**Replace with (add `.code(...)`:**
```java
Insurance savedInsurance = insuranceRepository.save(Insurance.builder()
        .name("Traffic Insurance")
        .code("TRAFFIC_INSURANCE")
        .typeId(1)
        .basePremium(BigDecimal.valueOf(1000))
        .build());
```

### B3: Search for any other `Insurance.builder()` calls in this file

Run a search for `Insurance.builder()` in this file. If there are more occurrences, add `.code("...")` to every one.

## Part C: Fix `InsuranceServiceTest.java` (insurance-service unit test)

This is a Mockito unit test (`@ExtendWith(MockitoExtension.class)`). No Spring context, no database. Entities are plain POJOs — the non-nullable `code` column constraint is DDL-level only and will NOT cause test failures if `code` is null. However, add `.code(...)` for consistency so these test entities match the entity contract.

### C1: Update `createInsurance()` helper method

Open `InsuranceServiceTest.java`.

**Current (lines 73–84):**
```java
private Insurance createInsurance(UUID id, String name) {
    return Insurance.builder()
            .id(id)
            .name(name)
            .description(TEST_DESCRIPTION)
            .typeId(TEST_TYPE_ID)
            .basePremium(TEST_BASE_PREMIUM)
            .isActive(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
}
```

**Replace with:**
```java
private Insurance createInsurance(UUID id, String name) {
    return Insurance.builder()
            .id(id)
            .name(name)
            .code(name.toUpperCase().replaceAll("\\s+", "_"))
            .description(TEST_DESCRIPTION)
            .typeId(TEST_TYPE_ID)
            .basePremium(TEST_BASE_PREMIUM)
            .isActive(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
}
```

The code is auto-derived from the name — e.g., `"Health Insurance"` → `"HEALTH_INSURANCE"`. This matches the auto-generation logic from Plan 01 Step 5.

### C2: Check `inactiveInsurance` builder (line 212)

Line 212 creates an `Insurance` without the helper:
```java
Insurance inactiveInsurance = Insurance.builder()
        .id(TEST_ID)
        .name(TEST_NAME)
        .isActive(false)
        .build();
```

Add `.code(...)`:
```java
Insurance inactiveInsurance = Insurance.builder()
        .id(TEST_ID)
        .name(TEST_NAME)
        .code(TEST_NAME.toUpperCase().replaceAll("\\s+", "_"))
        .isActive(false)
        .build();
```

## Part D: Fix estimation-service tests

### C1: Update mock `InsuranceInfo` names

The following test files create `InsuranceServiceClient.InsuranceInfo` mock objects. Update the `name` field from old technical codes to new human-readable names. The other fields (`id`, `typeId`, `typeName`) stay the same.

**File 1:** `EstimationServiceTest.java`

Replace all occurrences (3 total at lines 102, 137, 159):
```java
new InsuranceServiceClient.InsuranceInfo(insuranceId, "TRAFFIC", 1, "Vehicle")
```
→
```java
new InsuranceServiceClient.InsuranceInfo(insuranceId, "Trafik Sigortası", 1, "Vehicle")
```

⚠️ **Line-specific:**
- Line 102: `new InsuranceServiceClient.InsuranceInfo(insuranceId, "TRAFFIC", 1, "Vehicle")`
- Line 137: `new InsuranceServiceClient.InsuranceInfo(insuranceId, "TRAFFIC", 1, "Vehicle")`
- Line 159: `new InsuranceServiceClient.InsuranceInfo(insuranceId, "TRAFFIC", 1, "Vehicle")`

**File 2:** `EstimationServiceIntegrationTest.java`

Replace (2 occurrences):
- Line 129: `new InsuranceServiceClient.InsuranceInfo(UUID.randomUUID(), "TRAFFIC", 1, "Vehicle")` → `new InsuranceServiceClient.InsuranceInfo(UUID.randomUUID(), "Trafik Sigortası", 1, "Vehicle")`
- Line 214: `new InsuranceServiceClient.InsuranceInfo(realEstateInsuranceId, "DASK", 2, "Real Estate")` → `new InsuranceServiceClient.InsuranceInfo(realEstateInsuranceId, "DASK (Doğal Afet Sigortası)", 2, "Real Estate")`

**File 3:** `EstimationServiceApplicationTests.java`

Replace (1 occurrence):
- Line 75: `new InsuranceServiceClient.InsuranceInfo(UUID.randomUUID(), "TRAFFIC", 1, "Vehicle")` → `new InsuranceServiceClient.InsuranceInfo(UUID.randomUUID(), "Trafik Sigortası", 1, "Vehicle")`

**File 4:** `SagaE2ETest.java`

Replace (1 occurrence):
- Line 131: `new InsuranceServiceClient.InsuranceInfo(UUID.randomUUID(), "TRAFFIC", 1, "Vehicle")` → `new InsuranceServiceClient.InsuranceInfo(UUID.randomUUID(), "Trafik Sigortası", 1, "Vehicle")`

### C2: Verify no test assertions depend on old insurance name strings

Search for assertions in these test files that directly compare insurance `name` values:
- Pattern: `isEqualTo("TRAFFIC")`, `isEqualTo("CASCO")`, `isEqualTo("DASK")`, `isEqualTo("HEALTH")`, `isEqualTo("LIFE")`
- Pattern: `.getName().isEqualTo(`

From the analysis, estimation tests do NOT assert on `InsuranceInfo.name()` — they only use it as mock data. The `InsuranceServiceApplicationTests` in Part A already handles the only assertions on insurance type names.

If any assertion on insurance product name is found in these files, update the expected value to match the human-readable name from Plan 02.

## Part E: Search for `Insurance.builder()` across ALL test files

Run a project-wide search for `Insurance.builder()` in `**/src/test/**/*.java`. Every builder that doesn't set `.code(...)` will fail with a constraint violation because `code` is `nullable = false`.

For each occurrence found, add an appropriate `.code("...")` value (use a slug of the test name, e.g., `.name("TestInsurance")` → `.code("TESTINSURANCE")`).

This search should cover ALL test files, not just the ones listed above — there may be additional test files not captured by the grep results.

## Acceptance Criteria

- [ ] `InsuranceServiceApplicationTests.getTypes_returnsSeedData` expects 4 types with correct names
- [ ] `InsuranceServiceApplicationTests.@BeforeEach` seeds 4 InsuranceType rows (Vehicle, Real Estate, Health, Life)
- [ ] `InsuranceSagaConsumerTest.setUp` uses `InsuranceType(1, "Vehicle")`
- [ ] `InsuranceServiceTest.createInsurance()` helper sets `.code(...)` on built entities
- [ ] All `Insurance.builder()` calls in test files include `.code(...)`
- [ ] All `InsuranceServiceClient.InsuranceInfo` mocks use human-readable names
- [ ] No test assertion compares against old technical codes "TRAFFIC", "CASCO", "DASK", "HEALTH", "LIFE" as type names or insurance names (they may still appear as `code` values)
- [ ] `./mvnw test -pl services/insurance-service` passes
- [ ] `./mvnw test -pl services/estimation-service` passes
