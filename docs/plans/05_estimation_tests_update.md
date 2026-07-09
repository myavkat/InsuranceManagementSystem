# Plan 05: Estimation Service Tests Update

## Objective

Update all test files in the estimation service to use `insuranceId` (UUID) instead of `insuranceTypeId` (Integer). Ensure all tests pass after the FK relationship change.

## Dependencies

- **Plan 03 (Estimation Backend Update) MUST be completed first.** Tests will not compile until the production code uses `insuranceId`.

## Files to Read Before Starting

All of these must be read before making changes:
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/EstimationServiceTest.java`
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/controller/EstimationControllerTest.java`
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java`
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/entity/EstimationTest.java`
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceIntegrationTest.java`
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/e2e/SagaE2ETest.java`
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceApplicationTests.java`
- `docs/outlines/11_TESTING_CONVENTIONS.md` — Spring Boot 4 testing rules

## Technical Context

### What changed in production code

1. `Estimation.insuranceTypeId` (Integer) → `Estimation.insuranceId` (UUID)
2. `EstimationRequest.insuranceTypeId` (Integer) → `EstimationRequest.insuranceId` (UUID)
3. `EstimationResponse.insuranceTypeId` (Integer) → `EstimationResponse.insuranceId` (UUID)
4. `EstimationResponse` has two new fields: `insuranceName` and `insuranceTypeName` (but `insuranceTypeName` already existed)
5. `EstimationService` now depends on `InsuranceServiceClient` (new mock needed)
6. `EstimationRequestedEvent.insuranceTypeId` → `insuranceId`
7. `PremiumCalculatedEvent.insuranceTypeId` → `insuranceId`

### Test conventions

From `docs/outlines/11_TESTING_CONVENTIONS.md`:
- Slice tests use `@WebMvcTest` with `@MockitoBean`
- `RestTestClient` for controller tests (not `MockMvc` directly)
- AssertJ assertions preferred
- Use `MockitoExtension` for unit tests

### Common patterns in existing tests

The test files use these patterns for test data:
```java
private final Integer insuranceTypeId = 1;
// ...
request.setInsuranceTypeId(insuranceTypeId);
// ...
.insuranceTypeId(insuranceTypeId)
```

These all need to change to UUID.

## Steps

### Step 1: Update EstimationServiceTest.java

Open `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/EstimationServiceTest.java`.

**A. Add new mock:**
```java
@Mock
private InsuranceServiceClient insuranceServiceClient;
```

**B. Change test data fields:**
```java
private final UUID insuranceId = UUID.randomUUID();
```
Replace `private final Integer insuranceTypeId = 1;` with the above.

**C. Update `createValidRequest()` helper:**
```java
request.setInsuranceTypeId(insuranceTypeId);  // OLD
request.setInsuranceId(insuranceId);           // NEW
```

**D. Update `createSampleEntity()` helper:**
```java
.insuranceTypeId(insuranceTypeId)  // OLD
.insuranceId(insuranceId)          // NEW
```

**E. Update every test method that uses `insuranceTypeId`:**
- Replace `assertThat(response.getInsuranceTypeId()).isEqualTo(insuranceTypeId)` with `assertThat(response.getInsuranceId()).isEqualTo(insuranceId)`
- Replace `.setInsuranceTypeId(...)` with `.setInsuranceId(...)`

**F. The `create_withValidRequest` test** needs to mock the InsuranceServiceClient:
```java
when(insuranceServiceClient.getInsurance(insuranceId))
    .thenReturn(new InsuranceServiceClient.InsuranceInfo(insuranceId, "TRAFFIC", 1, "Vehicle"));
```

(Adjust based on the actual record/class name defined in InsuranceServiceClient.)

**G. The `findById` tests** also need the mock since `findById` now calls `insuranceServiceClient.getInsurance()`:
```java
when(insuranceServiceClient.getInsurance(any(UUID.class)))
    .thenReturn(new InsuranceServiceClient.InsuranceInfo(insuranceId, "TRAFFIC", 1, "Vehicle"));
```

Make sure every test that calls `estimationService.findById()` or `estimationService.create()` has the insurance client mock set up.

### Step 2: Update EstimationControllerTest.java

Open `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/controller/EstimationControllerTest.java`.

**A. Change test data:**
```java
private final UUID insuranceId = UUID.randomUUID();
```
Replace `private final Integer insuranceTypeId = 1;`.

**B. Update `createSampleResponse()` helper:**
```java
.insuranceTypeId(insuranceTypeId)  // OLD
.insuranceId(insuranceId)          // NEW
```

**C. Update request building in tests:**
```java
request.setInsuranceTypeId(insuranceTypeId);  // OLD
request.setInsuranceId(insuranceId);           // NEW
```

**D. Update `create_WithBothVehicleAndRealEstateNull_Returns400` test:**
```java
request.setInsuranceTypeId(insuranceTypeId);  // OLD
request.setInsuranceId(insuranceId);           // NEW
```

**E. The `EstimationService` mock in controller tests doesn't need `InsuranceServiceClient` mocked separately** — the controller test mocks `EstimationService` directly, so the internal client is never called. No `@MockitoBean` for `InsuranceServiceClient` is needed in controller tests.

### Step 3: Update EstimationSagaConsumerTest.java

Open `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumerTest.java`.

**A. Search for any references to `insuranceTypeId`.** The consumer test builds `PremiumCalculatedEvent` objects. Find all `.insuranceTypeId(...)` calls and replace with `.insuranceId(someUUID)`.

Example:
```java
PremiumCalculatedEvent event = PremiumCalculatedEvent.builder()
        .premium(premium)
        .breakdown(Map.of("base", new BigDecimal("1500.00")))
        .insuranceTypeId(1)  // OLD
        .build();
```
Change to:
```java
PremiumCalculatedEvent event = PremiumCalculatedEvent.builder()
        .premium(premium)
        .breakdown(Map.of("base", new BigDecimal("1500.00")))
        .insuranceId(UUID.randomUUID())  // NEW
        .build();
```

**B. Do this for every test that builds a `PremiumCalculatedEvent`.** Use grep or search to find all occurrences.

### Step 4: Update EstimationTest.java (entity test)

Open `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/entity/EstimationTest.java`.

Replace all `insuranceTypeId` references with `insuranceId`:
- `.insuranceTypeId(1)` → `.insuranceId(UUID.randomUUID())`
- `getInsuranceTypeId()` → `getInsuranceId()`
- `setInsuranceTypeId(...)` → `setInsuranceId(...)`

### Step 5: Update EstimationServiceIntegrationTest.java

Open `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceIntegrationTest.java`.

**A. Update `createValidRequest()` helper:**
```java
request.setInsuranceTypeId(1);       // OLD
request.setInsuranceId(UUID.randomUUID());  // NEW — but wait...
```

**Important:** The integration test actually calls the real service, which now calls `InsuranceServiceClient.getInsurance()`. Since this is an integration test without a running insurance service, the call will fail.

**Two options:**
1. **Mock the InsuranceServiceClient** with `@MockitoBean` (replacing `@MockBean` in Spring Boot 4).
2. **Use a WireMock or stub** for the insurance service HTTP endpoint.

The simplest approach for now (to match the existing test pattern) is to use `@MockitoBean`:

Add at the top of the class:
```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;
// ...
@MockitoBean
private InsuranceServiceClient insuranceServiceClient;
```

Then in `createValidRequest()` or in a `@BeforeEach`, set up the mock:
```java
when(insuranceServiceClient.getInsurance(any(UUID.class)))
    .thenReturn(new InsuranceServiceClient.InsuranceInfo(UUID.randomUUID(), "TRAFFIC", 1, "Vehicle"));
```

**B. Update `createValidRequest()`:**
```java
request.setInsuranceTypeId(1);            // OLD
request.setInsuranceId(UUID.randomUUID()); // NEW
```

**C. Update `createEstimation_withRealEstateId_publishesEvent()` test:**
```java
request.setInsuranceTypeId(2);            // OLD
request.setInsuranceId(UUID.randomUUID()); // NEW
```

Also update the mock to return typeId=2 for this test:
```java
when(insuranceServiceClient.getInsurance(any(UUID.class)))
    .thenReturn(new InsuranceServiceClient.InsuranceInfo(UUID.randomUUID(), "DASK", 2, "Real Estate"));
```

**D. Update `createEstimation_withoutVehicleOrRealEstate_throwsException()`:**
```java
request.setInsuranceTypeId(1);            // OLD
request.setInsuranceId(UUID.randomUUID()); // NEW
```

### Step 6: Update SagaE2ETest.java (if it references insuranceTypeId)

Open `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/e2e/SagaE2ETest.java`.

Search for `insuranceTypeId`. Replace with `insuranceId` using UUID values.

### Step 7: Update EstimationServiceApplicationTests.java (if it references insuranceTypeId)

Open `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceApplicationTests.java`.

Search for `insuranceTypeId`. Replace with `insuranceId`. This file may not reference it at all — if not, skip.

### Step 8: Run the tests

```bash
cd services/estimation-service && ./gradlew test
```

Fix any test failures by examining the error messages. Common issues:
- Missing `InsuranceServiceClient` mock → add `@Mock` or `@MockitoBean`
- `NullPointerException` on `insuranceServiceClient` → mock not set up
- UUID vs Integer type mismatch → use `UUID.randomUUID()` instead of an integer literal

## Acceptance Criteria

- [ ] `EstimationServiceTest` — all tests pass with `InsuranceServiceClient` mocked
- [ ] `EstimationControllerTest` — all tests pass
- [ ] `EstimationSagaConsumerTest` — all tests pass with `PremiumCalculatedEvent.insuranceId`
- [ ] `EstimationTest` (entity) — all tests pass
- [ ] `EstimationServiceIntegrationTest` — all tests pass (with `@MockitoBean InsuranceServiceClient`)
- [ ] `SagaE2ETest` — all tests pass (if it references insuranceTypeId)
- [ ] Zero references to `insuranceTypeId` remain in any estimation-service test file
- [ ] `./gradlew test` passes in `services/estimation-service`
