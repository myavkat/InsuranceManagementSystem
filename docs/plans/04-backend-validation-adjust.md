# Plan 04: Adjust Backend Estimation Validation for Non-Asset Insurance Types

## Objective

Update the `EstimationService.create()` validation so that Health and Life insurance types don't require a `vehicleId` or `realEstateId`. Currently, the backend unconditionally rejects requests where both are null, but Health/Life insurances don't involve assets.

## Dependencies

- [ ] Plan 01 (`01-seed-data-restructure.md`) — needs the new insurance type IDs (Health=3, Life=4)

## Files to Read First

- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java` — the service with the validation
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationRequest.java` — the request DTO
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java` — entity for context
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/EstimationRequestedEvent.java` — the event published to Kafka

## Technical Context

- **Current validation** (line 57-59 of `EstimationService.java`):
  ```java
  if (request.getVehicleId() == null && request.getRealEstateId() == null) {
      throw new IllegalArgumentException("Either vehicleId or realEstateId must be provided");
  }
  ```
- **New type IDs** after Plan 01: Vehicle=1, Real Estate=2, Health=3, Life=4
- **Rule**: Vehicle type → requires vehicleId, Real Estate type → requires realEstateId, Health/Life → requires neither
- The `insuranceTypeId` is available in the request — use it to determine validation logic
- The `EstimationRequestedEvent` published to Kafka also carries `insuranceTypeId`, so the SAGA consumers can also make decisions based on type
- **SAGA flow**: Vehicle Saga Consumer currently publishes empty valid if no vehicleId. For Health/Life, there won't be a vehicleId at all. This is already handled because the Vehicle Saga Consumer checks if vehicleId is null and publishes a valid event in that case. Same for RealEstate Saga Consumer.
- **SagaAggregationStore**: In the Insurance Saga Consumer, the store waits for ESTIMATION_REQUESTED + CUSTOMER_VALIDATED + VEHICLE_VALIDATED. For Health/Life, the Vehicle Saga Consumer still publishes a VehicleValidatedEvent (empty), so the aggregation still works. But for Real Estate, the same applies.

## Steps

### Step 1: Update EstimationService.create() validation

Open `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`.

**Replace** the rigid validation (lines 57-59):
```java
if (request.getVehicleId() == null && request.getRealEstateId() == null) {
    throw new IllegalArgumentException("Either vehicleId or realEstateId must be provided");
}
```

**With** type-aware validation:
```java
// Validate asset linkage based on insurance type
Integer typeId = request.getInsuranceTypeId();
if (typeId == null) {
    throw new IllegalArgumentException("insuranceTypeId is required");
}

// Type 1 = Vehicle → vehicleId required
if (typeId == 1 && request.getVehicleId() == null) {
    throw new IllegalArgumentException("vehicleId is required for Vehicle-type insurance");
}

// Type 2 = Real Estate → realEstateId required
if (typeId == 2 && request.getRealEstateId() == null) {
    throw new IllegalArgumentException("realEstateId is required for Real Estate-type insurance");
}

// Type 3 = Health, Type 4 = Life → no asset required (nothing to validate)
```

This uses integer literals (1, 2, 3, 4) matching the seed data defined in Plan 01. These are fixed IDs from seed data — they won't change.

### Step 2: Ensure the SAGA flow handles missing assets correctly

Verify that the Vehicle Saga Consumer (in vehicle-service) and Real Estate Saga Consumer (in realestate-service) already handle the case where no asset ID is provided. Open:
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/config/VehicleSagaConsumer.java`
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/config/RealEstateSagaConsumer.java`

Check the `handleEstimationRequested` method — it should already publish a `VehicleValidatedEvent` (vehicleId=null, with empty data) when the vehicleId is not provided. This was the existing behavior for "optional asset" — confirm it's still working.

**If the consumers DON'T already handle null vehicleId/realEstateId**, add the guard:
```java
// In VehicleSagaConsumer.handleEstimationRequested():
if (event.getVehicleId() == null) {
    // No vehicle to validate — publish empty valid event immediately
    VehicleValidatedEvent validEvent = VehicleValidatedEvent.builder()
            .vehicleId(null)
            .plate(null)
            .build();
    EventEnvelope envelope = validEvent.toEnvelope(sagaId, traceId);
    outboxEventRepository.save(buildOutboxEvent(sagaId, envelope, EventConstants.ESTIMATION_SAGA));
    return;
}
```

Same pattern for Real Estate saga consumer.

### Step 3: Verify the Insurance Saga Consumer aggregation

Open `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/SagaAggregationStore.java`.

Check the `storeAndCheckReady` method — it should be waiting for exactly 3 events: ESTIMATION_REQUESTED, CUSTOMER_VALIDATED, VEHICLE_VALIDATED. For Health/Life (no vehicle), the Vehicle Saga Consumer still publishes a VehicleValidatedEvent (with null vehicleId), so the count should still reach 3. Confirm this logic is correct.

**If the aggregation store currently checks for non-null vehicle payload**, it needs to be loosened to accept null vehicle data as a valid "vehicle validated" marker.

### Step 4: No changes to EstimationRequest DTO

The `EstimationRequest.java` DTO already has `vehicleId` and `realEstateId` as optional (`UUID vehicleId`, `UUID realEstateId` — nullable). No changes needed. The `EstimationRequestedEvent` also has these as nullable fields.

### Step 5: No changes to common-message events

The `EstimationRequestedEvent`, `CustomerValidatedEvent`, `VehicleValidatedEvent`, and `RealEstateValidatedEvent` in `common-message` already support null asset IDs. No changes needed.

## Acceptance Criteria

- [ ] Creating an estimation with Vehicle type (1) and no vehicleId → rejected with clear error
- [ ] Creating an estimation with Real Estate type (2) and no realEstateId → rejected with clear error
- [ ] Creating an estimation with Health type (3) and no assets → accepted, proceeds through SAGA
- [ ] Creating an estimation with Life type (4) and no assets → accepted, proceeds through SAGA
- [ ] Creating an estimation with Vehicle type AND vehicleId → accepted (existing behavior preserved)
- [ ] Creating an estimation with Real Estate type AND realEstateId → accepted (existing behavior preserved)
