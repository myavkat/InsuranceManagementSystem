# Fix 03 — Event Schema & Producer Alignment

## Status: NOT STARTED
## Parent: Post-Review Fixes (Phase 2 code review, 2026-07-07)
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Fix the disconnect between event POJO field additions (Subtask 1) and the domain event publishers that produce those events. The plan added fields to 8 domain events but the corresponding publishers were never updated — they publish `null` for all new fields.

Additionally, `InsuranceDeletedEvent` was created (class, constant, schema registry entry) but `InsuranceEventPublisher` has no `publishInsuranceDeleted()` method — the event can never be produced.

## Context — What Fields Were Added

Per `docs/plans/07_PHASE2_SUBTASK1_EVENT_SCHEMAS.md` Step 3:

| Event Class | Fields Added | Purpose |
|------------|-------------|---------|
| `CustomerCreatedEvent` | `firstName`, `lastName` | Audit/analytics completeness |
| `CustomerUpdatedEvent` | `firstName`, `lastName` | Same |
| `VehicleCreatedEvent` | `customerId`, `carBrandId` | Downstream cache invalidation |
| `VehicleUpdatedEvent` | `customerId`, `carBrandId` | Same |
| `RealEstateCreatedEvent` | `address`, `cityId`, `customerId` | Audit/analytics |
| `RealEstateUpdatedEvent` | `address`, `cityId`, `customerId` | Same |
| `InsuranceCreatedEvent` | `name` | Audit completeness |
| `InsuranceUpdatedEvent` | `name` | Same |
| `InsuranceDeletedEvent` | (entire class) | New event type for insurance deletion |

All 8 existing events (4 Created + 4 Updated) are published by their respective `*EventPublisher` classes, but the publishers call `.build()` without setting the new fields.

## Files to Read Before Starting

1. `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/config/CustomerEventPublisher.java` — the publisher to update
2. `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/entity/Customer.java` — entity to read fields from
3. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/config/VehicleEventPublisher.java`
4. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/Vehicle.java`
5. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/config/RealEstateEventPublisher.java`
6. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/entity/RealEstate.java`
7. `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceEventPublisher.java`
8. `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java`
9. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/` — event POJOs to verify field names
10. `docs/plans/07_PHASE2_SUBTASK1_EVENT_SCHEMAS.md` — Step 3: Fill Sparse Domain Event Fields

## Implementation Steps

### Step 1: Fix CustomerEventPublisher — Add firstName/lastName

- [x] **1.1** Read `CustomerEventPublisher.java`. Currently:
  ```java
  public void publishCustomerCreated(Customer customer) {
      CustomerCreatedEvent event = CustomerCreatedEvent.builder()
              .customerId(customer.getId())
              .nationalId(customer.getNationalId())
              .email(customer.getEmail())
              .build();  // ❌ missing .firstName() and .lastName()
      // ...
  }
  ```

- [x] **1.2** Read `Customer.java` entity to verify the getter names (`getFirstName()`, `getLastName()`).

- [x] **1.3** Update `publishCustomerCreated`:
  ```java
  public void publishCustomerCreated(Customer customer) {
      CustomerCreatedEvent event = CustomerCreatedEvent.builder()
              .customerId(customer.getId())
              .nationalId(customer.getNationalId())
              .email(customer.getEmail())
              .firstName(customer.getFirstName())
              .lastName(customer.getLastName())
              .build();
      // ... rest unchanged
  }
  ```

- [x] **1.4** Update `publishCustomerUpdated` — same addition of `.firstName(customer.getFirstName()).lastName(customer.getLastName())`.

- [x] **1.5** `publishCustomerDeleted` — `CustomerDeletedEvent` has only `customerId` and `nationalId` (no new fields added). No change needed.

### Step 2: Fix VehicleEventPublisher — Add customerId/carBrandId

- [x] **2.1** Read `VehicleEventPublisher.java`. Currently:
  ```java
  public void publishVehicleCreated(Vehicle vehicle) {
      VehicleCreatedEvent event = VehicleCreatedEvent.builder()
              .vehicleId(vehicle.getId())
              .plate(vehicle.getPlate())
              .build();  // ❌ missing .customerId() and .carBrandId()
      // ...
  }
  ```

- [x] **2.2** Read `Vehicle.java` entity to verify getter names (`getCustomerId()`, `getCarBrandId()`).

- [x] **2.3** Update `publishVehicleCreated`:
  ```java
  public void publishVehicleCreated(Vehicle vehicle) {
      VehicleCreatedEvent event = VehicleCreatedEvent.builder()
              .vehicleId(vehicle.getId())
              .plate(vehicle.getPlate())
              .customerId(vehicle.getCustomerId())
              .carBrandId(vehicle.getCarBrandId())
              .build();
      // ... rest unchanged
  }
  ```

- [x] **2.4** Update `publishVehicleUpdated` — same addition of `.customerId(vehicle.getCustomerId()).carBrandId(vehicle.getCarBrandId())`.

- [x] **2.5** `publishVehicleDeleted` — `VehicleDeletedEvent` has only `vehicleId` and `plate`. No change needed.

### Step 3: Fix RealEstateEventPublisher — Add address/cityId/customerId

- [x] **3.1** Read `RealEstateEventPublisher.java`. Currently:
  ```java
  public void publishRealEstateCreated(RealEstate realEstate) {
      RealEstateCreatedEvent event = RealEstateCreatedEvent.builder()
              .realEstateId(realEstate.getId())
              .build();  // ❌ missing .address(), .cityId(), .customerId()
      // ...
  }
  ```

- [x] **3.2** Read `RealEstate.java` entity to verify getter names (`getAddress()`, `getCityId()`, `getCustomerId()`).

- [x] **3.3** Update `publishRealEstateCreated`:
  ```java
  public void publishRealEstateCreated(RealEstate realEstate) {
      RealEstateCreatedEvent event = RealEstateCreatedEvent.builder()
              .realEstateId(realEstate.getId())
              .address(realEstate.getAddress())
              .cityId(realEstate.getCityId())
              .customerId(realEstate.getCustomerId())
              .build();
      // ... rest unchanged
  }
  ```

- [x] **3.4** Update `publishRealEstateUpdated` — same addition.

- [x] **3.5** `publishRealEstateDeleted` — `RealEstateDeletedEvent` has only `realEstateId`. No change needed.

### Step 4: Fix InsuranceEventPublisher — Add name + Add publishInsuranceDeleted

- [x] **4.1** Read `InsuranceEventPublisher.java`. Currently:
  ```java
  public void publishInsuranceCreated(Insurance insurance) {
      InsuranceCreatedEvent event = InsuranceCreatedEvent.builder()
              .insuranceId(insurance.getId())
              .typeId(insurance.getTypeId())
              .companyId(insurance.getCompanyId())
              .build();  // ❌ missing .name()
      // ...
  }
  ```

- [x] **4.2** Read `Insurance.java` entity to verify getter name (`getName()`).

- [x] **4.3** Update `publishInsuranceCreated`:
  ```java
  public void publishInsuranceCreated(Insurance insurance) {
      InsuranceCreatedEvent event = InsuranceCreatedEvent.builder()
              .insuranceId(insurance.getId())
              .typeId(insurance.getTypeId())
              .companyId(insurance.getCompanyId())
              .name(insurance.getName())
              .build();
      // ... rest unchanged
  }
  ```

- [x] **4.4** Update `publishInsuranceUpdated` — same addition of `.name(insurance.getName())`.

- [x] **4.5** Add the missing `publishInsuranceDeleted` method:

  ```java
  public void publishInsuranceDeleted(Insurance insurance) {
      InsuranceDeletedEvent event = InsuranceDeletedEvent.builder()
              .insuranceId(insurance.getId())
              .typeId(insurance.getTypeId())
              .companyId(insurance.getCompanyId())
              .build();

      EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
      messagePublisher.publish(EventConstants.INSURANCE_EVENTS, envelope);
      log.info("Published InsuranceDeleted event for insurance id: {}", insurance.getId());
  }
  ```

- [x] **4.6** Find where `Insurance` entities are deleted in the insurance-service and add a call to `insuranceEventPublisher.publishInsuranceDeleted(insurance)`.

  Search for: `insuranceRepository.delete` or `insuranceService.delete` in the insurance-service.

  Important: The outbox pattern requires the event publish to be in the SAME transaction as the delete. If the existing delete flow does not use the outbox, the call should still be added for eventual consistency (domain events are fire-and-forget).

### Step 5: Verify Event Envelope traceId Propagation

- [ ] **5.1** Note: All domain event publishers currently use `UUID.randomUUID()` as the traceId in `event.toEnvelope(null, UUID.randomUUID())`. Per AGENTS.md: *"All outbound SAGA events must carry the original traceId from the triggering EventEnvelope."*

  Domain events (`*Created`, `*Updated`, `*Deleted`) are NOT SAGA events — they're fire-and-forget cache-invalidation/audit events. The AGENTS.md rule specifically says "SAGA events." Domain events with `null` sagaId and random traceId are acceptable for now. A future improvement could propagate the HTTP request's trace context.

### Step 6: Build and Verify

- [x] **6.1** Build all affected services:
  ```bash
  .\gradlew.bat :services:customer-service:build
  .\gradlew.bat :services:vehicle-service:build
  .\gradlew.bat :services:realestate-service:build
  .\gradlew.bat :services:insurance-service:build
  ```

- [x] **6.2** Run tests for affected services:
  ```bash
  .\gradlew.bat :services:customer-service:test
  .\gradlew.bat :services:vehicle-service:test
  .\gradlew.bat :services:realestate-service:test
  .\gradlew.bat :services:insurance-service:test
  ```

- [x] **6.3** Run the serialization tests to verify the new fields survive JSON round-trip:
  ```bash
  .\gradlew.bat :common:common-message:test --tests "*EventSerializationTest*"
  ```

- [x] **6.4** Run the full test suite:
  ```bash
  .\gradlew.bat test
  ```

---

## Files to Modify

| File | Change |
|------|--------|
| `services/customer-service/.../config/CustomerEventPublisher.java` | Add `.firstName()`, `.lastName()` to `publishCustomerCreated` and `publishCustomerUpdated` |
| `services/vehicle-service/.../config/VehicleEventPublisher.java` | Add `.customerId()`, `.carBrandId()` to `publishVehicleCreated` and `publishVehicleUpdated` |
| `services/realestate-service/.../config/RealEstateEventPublisher.java` | Add `.address()`, `.cityId()`, `.customerId()` to `publishRealEstateCreated` and `publishRealEstateUpdated` |
| `services/insurance-service/.../config/InsuranceEventPublisher.java` | Add `.name()` to `publishInsuranceCreated` and `publishInsuranceUpdated`; add new `publishInsuranceDeleted` method |
| `services/insurance-service/.../service/InsuranceService.java` (or wherever delete is handled) | Call `insuranceEventPublisher.publishInsuranceDeleted()` on insurance deletion |

---

## Context Reference: Entity Field Verification

For each entity, verify the getter returns a value before adding it to the event. All entities use Lombok `@Data` or `@Getter`, so getters exist if the field exists.

| Entity | Fields Available |
|--------|-----------------|
| `Customer` | `id`, `nationalId`, `email`, `firstName`, `lastName`, `createdAt`, `updatedAt`, `deletedAt` |
| `Vehicle` | `id`, `plate`, `customerId`, `carBrandId`, `carModelId`, `createdAt`, `updatedAt`, `deletedAt` |
| `RealEstate` | `id`, `address`, `cityId`, `customerId`, `createdAt`, `updatedAt`, `deletedAt` |
| `Insurance` | `id`, `typeId`, `companyId`, `name`, `basePremium`, `isActive`, `createdAt`, `updatedAt` |

## Dependencies

- None (standalone fix)

## Completion Criteria

- [x] All 8 `*Created`/`*Updated` event builders include the new fields with values from the entity
- [x] `InsuranceEventPublisher` has `publishInsuranceDeleted()` method
- [x] Insurance deletion flow calls `publishInsuranceDeleted()`
- [x] `.\gradlew.bat build` passes for all affected services
- [x] `EventSerializationTest` passes (verifies field round-trip)
- [x] No compilation errors related to missing getter methods
- [x] **Status: COMPLETED**
