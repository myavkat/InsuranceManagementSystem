# Plan 06: Insurance Service Tests Update

## Objective

Update all test files in the insurance service that reference `insuranceTypeId` on SAGA events. After Plan 04, the insurance service SAGA consumer uses `insuranceId` instead — tests must match.

## Dependencies

- **Plan 04 (Insurance SAGA Consumer Update) MUST be completed first.** Tests will not compile until the consumer code uses `insuranceId`.

## Files to Read Before Starting

- `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/saga/InsuranceSagaConsumerTest.java`
- `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/service/InsuranceServiceTest.java`
- `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/controller/InsuranceControllerTest.java`
- `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/InsuranceServiceApplicationTests.java`
- `docs/outlines/11_TESTING_CONVENTIONS.md` — Spring Boot 4 testing rules

## Technical Context

### What changed in the SAGA consumer (Plan 04)

1. `calculatePremium()` now extracts `insuranceId` (UUID) from `EstimationRequestedEvent` instead of `insuranceTypeId` (Integer)
2. Insurance lookup now uses `insuranceRepository.findById(insuranceId)` instead of `insuranceRepository.findByTypeId(typeId, ...)`
3. `PremiumCalculatedEvent` is built with `.insuranceId(...)` instead of `.insuranceTypeId(...)`

### Key change for tests

All tests that build `EstimationRequestedEvent` objects for the SAGA consumer will need to use `.insuranceId(UUID)` instead of `.insuranceTypeId(1)`.

Tests that verify the `PremiumCalculatedEvent` output will need to assert on `getInsuranceId()` instead of `getInsuranceTypeId()`.

## Steps

### Step 1: Update InsuranceSagaConsumerTest.java

Open `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/saga/InsuranceSagaConsumerTest.java`.

**A. Find all `EstimationRequestedEvent` builder calls.**

Search for `.insuranceTypeId(` in this file. Every occurrence should be replaced with `.insuranceId(` and the value changed from an integer (like `1`) to a UUID.

Example:
```java
EstimationRequestedEvent estimationEvent = EstimationRequestedEvent.builder()
        .customerId(customerId)
        .vehicleId(vehicleId)
        .insuranceTypeId(1)    // OLD — integer
        .build();
```
Change to:
```java
UUID insuranceId = UUID.randomUUID();  // Add at top of test or use a field

EstimationRequestedEvent estimationEvent = EstimationRequestedEvent.builder()
        .customerId(customerId)
        .vehicleId(vehicleId)
        .insuranceId(insuranceId)    // NEW — UUID
        .build();
```

**B. Find all `PremiumCalculatedEvent` builder calls.**

Search for `.insuranceTypeId(` in this file for premium events. Replace with `.insuranceId(...)`.

**C. Find assertions on `getInsuranceTypeId()`.**

Replace with `getInsuranceId()`.

**D. Update mock setups for `insuranceRepository`.**

The test likely mocks `insuranceRepository.findByTypeId(...)`. This needs to change to `insuranceRepository.findById(...)`.

Search for `findByTypeId` in the test file. Replace with `findById`:
```java
when(insuranceRepository.findByTypeId(eq(1), any(Pageable.class)))  // OLD
    .thenReturn(...)
```
Change to:
```java
when(insuranceRepository.findById(eq(insuranceId)))  // NEW
    .thenReturn(Optional.of(someInsurance))
```

**E. Check for unused imports.**

After changes, the test may no longer need:
- `org.springframework.data.domain.Pageable` (if `findByTypeId` was the only user)
- `org.springframework.data.domain.Page` or `PageImpl`

Remove unused imports.

### Step 2: Update InsuranceServiceTest.java

Open `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/service/InsuranceServiceTest.java`.

Search for `insuranceTypeId`. This file tests the `InsuranceService` class (CRUD operations for insurance products), not the SAGA consumer. It may reference `insuranceTypeId` in the context of `Insurance.typeId` (which is NOT changing) — or it may reference it in test data that happens to match the old field name.

**Only change references that are about Estimation's old FK.** If a test creates an `EstimationRequestedEvent` or references the `insuranceTypeId` field on estimation-related classes, update it. If it references `Insurance.typeId` or `InsuranceRequest.typeId`, leave it alone — those are not changing.

Most likely this file does NOT need changes. Read it, search for `insuranceTypeId`, and verify each match before changing.

### Step 3: Update InsuranceControllerTest.java

Open `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/controller/InsuranceControllerTest.java`.

Same guidance as Step 2 — only update if the file references estimation-related `insuranceTypeId`. The insurance controller deals with `InsuranceResponse.typeId` which is NOT changing.

Most likely no changes needed.

### Step 4: Update InsuranceServiceApplicationTests.java

Open `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/InsuranceServiceApplicationTests.java`.

Search for `insuranceTypeId`. This is a context-load test; it probably doesn't reference it. If it does, update accordingly.

Most likely no changes needed.

### Step 5: Run the tests

```bash
cd services/insurance-service && ./gradlew test
```

If tests fail, read the error messages carefully. Common issues:
- `insuranceRepository.findByTypeId()` mock not updated → change to `findById()`
- `Pageable` import removed but still needed elsewhere → check before removing
- Type mismatch on `.insuranceTypeId(1)` vs `.insuranceId(UUID)` → fix the builder call

## Acceptance Criteria

- [x] `InsuranceSagaConsumerTest` — all tests pass
- [x] `InsuranceServiceTest` — all tests pass (or verified no changes needed)
- [x] `InsuranceControllerTest` — all tests pass (or verified no changes needed)
- [x] `InsuranceServiceApplicationTests` — all tests pass
- [x] Zero references to `insuranceTypeId` on `EstimationRequestedEvent` or `PremiumCalculatedEvent` remain in insurance-service tests
- [x] `./gradlew test` passes in `services/insurance-service`
