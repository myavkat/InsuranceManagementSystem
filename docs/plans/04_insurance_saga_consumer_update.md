# Plan 04: Insurance Service SAGA Consumer — Use insuranceId Instead of typeId for Lookup

## Objective

Update the insurance service's SAGA consumer to look up insurance by `insuranceId` (UUID) instead of `insuranceTypeId` (Integer) when calculating premiums. Also update the `PremiumCalculatedEvent` builder to include `insuranceId`.

## Dependencies

- **Plan 02 (Common Events Update) MUST be completed first.** The `EstimationRequestedEvent` and `PremiumCalculatedEvent` classes must already have `insuranceId` instead of `insuranceTypeId`.

## Files to Read Before Starting

- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/InsuranceRepository.java`
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java`
- `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` — Insurance Service section
- `docs/outlines/14_EVENT_SCHEMA_REGISTRY.md` — PremiumCalculatedEvent section
- `AGENTS.md` — SAGA Consumer Rules section

## Technical Context

### Current behavior

The `InsuranceSagaConsumer.calculatePremium()` method currently:
1. Extracts `insuranceTypeId` from `EstimationRequestedEvent`
2. Looks up insurance by `typeId` using `insuranceRepository.findByTypeId(insuranceTypeId, Pageable.unpaged())`
3. Picks the first active insurance of that type

This is wrong for the new design — the estimation now references a **specific insurance product** (e.g., "CASCO" vs "TRAFFIC"), not just a type (e.g., "Vehicle").

### New behavior

1. Extract `insuranceId` from `EstimationRequestedEvent`
2. Look up insurance by `id` directly using `insuranceRepository.findById(insuranceId)`
3. If not found or inactive, publish `CalculationFailed`

### SAGA Consumer Rules (from AGENTS.md)
- **Transaction boundaries:** The `calculatePremium` method is called from within `transactionTemplate.executeWithoutResult()`, so its DB operations are already wrapped.
- **Check send results:** The method publishes via outbox (not `StreamBridge.send()`), so the send-result rule doesn't directly apply here.
- **JSON via ObjectMapper only:** Already followed — the code uses `jsonMapper.writeValueAsString()`.

### InsuranceRepository methods

The repository likely has `findByTypeId(Integer typeId, Pageable pageable)`. We'll use the standard `findById(UUID id)` inherited from `JpaRepository` instead.

## Steps

### Step 1: Update handleEstimationRequested method (if needed)

Open `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`.

Look at `handleEstimationRequested()`. It stores the raw `EventEnvelope` in the aggregation store. No change needed here — the event envelope already contains the updated `EstimationRequestedEvent` with `insuranceId`.

### Step 2: Update calculatePremium method — extraction

Find this code block in `calculatePremium()`:
```java
EstimationRequestedEvent estimationEvent = jsonMapper.convertValue(
        state.getEstimationRequest().getPayload(), EstimationRequestedEvent.class);
UUID customerId = estimationEvent.getCustomerId();
UUID vehicleId = estimationEvent.getVehicleId();
Integer insuranceTypeId = estimationEvent.getInsuranceTypeId();
```

**Change the last line to:**
```java
UUID insuranceId = estimationEvent.getInsuranceId();
```

Also update any log messages that reference `insuranceTypeId` → `insuranceId`.

### Step 3: Update calculatePremium method — insurance lookup

Find this code block:
```java
// Look up insurance by typeId — use only active ones for estimation
Optional<Insurance> insuranceOpt = insuranceRepository
        .findByTypeId(insuranceTypeId, Pageable.unpaged())
        .getContent().stream()
        .filter(Insurance::getIsActive)
        .findFirst();

if (insuranceOpt.isEmpty()) {
    publishCalculationFailed(sagaId, traceId,
            "No active insurance found for typeId=" + insuranceTypeId);
    return;
}
```

**Replace with:**
```java
// Look up insurance by ID directly
Optional<Insurance> insuranceOpt = insuranceRepository.findById(insuranceId);

if (insuranceOpt.isEmpty() || !insuranceOpt.get().getIsActive()) {
    publishCalculationFailed(sagaId, traceId,
            "No active insurance found for insuranceId=" + insuranceId);
    return;
}
```

Also remove the unused import for `org.springframework.data.domain.Pageable` if it's no longer needed. Check if `Pageable` is used elsewhere in the file before removing.

### Step 4: Update calculatePremium method — PremiumCalculatedEvent builder

Find this code block:
```java
PremiumCalculatedEvent premiumEvent = PremiumCalculatedEvent.builder()
        .premium(totalPremium)
        .breakdown(breakdown)
        .insuranceTypeId(insuranceTypeId)
        .customerId(customerId)
        .vehicleId(vehicleId)
        .build();
```

**Change `.insuranceTypeId(insuranceTypeId)` to `.insuranceId(insuranceId)`.**

Also update the log line:
```java
log.info("Premium calculated for sagaId={}: premium={}, typeId={}",
        sagaId, totalPremium, insuranceTypeId);
```
**Change to:**
```java
log.info("Premium calculated for sagaId={}: premium={}, insuranceId={}",
        sagaId, totalPremium, insuranceId);
```

### Step 5: Remove unused import

If `Pageable` is no longer used anywhere in the file, remove:
```java
import org.springframework.data.domain.Pageable;
```

Check carefully — it might be used in other methods. If it's only used in the old `findByTypeId` call (which you just removed), it's safe to delete.

### Step 6: Verify compilation

```bash
cd services/insurance-service && ./gradlew compileJava
```

## Acceptance Criteria

- [x] `insuranceTypeId` variable extracted from `EstimationRequestedEvent` replaced with `insuranceId`
- [x] Insurance lookup uses `insuranceRepository.findById(insuranceId)` instead of `findByTypeId(typeId, ...)`
- [x] Inactive insurance check is `insuranceOpt.isEmpty() || !insuranceOpt.get().getIsActive()`
- [x] `PremiumCalculatedEvent` built with `.insuranceId(insuranceId)` instead of `.insuranceTypeId(...)`
- [x] Log messages reference `insuranceId` instead of `typeId`
- [x] `./gradlew compileJava` passes in `services/insurance-service`
