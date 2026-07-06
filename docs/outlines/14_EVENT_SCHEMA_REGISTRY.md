# Event Schema Registry

## Envelope Structure

All events travel through the message broker wrapped in a common `EventEnvelope`.

**Class:** `com.insurancemanagementsystem.common.event.EventEnvelope`
**Serialized with:** Jackson 3 (`tools.jackson.databind.json.JsonMapper`)

```json
{
  "sagaId": "uuid",
  "eventType": "CustomerValidated",
  "timestamp": "2026-07-06T12:00:00Z",
  "traceId": "uuid",
  "payload": { }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sagaId` | UUID | Yes | Correlation ID linking all events in a SAGA flow |
| `eventType` | String | Yes | Discriminator — maps to a concrete `BaseEvent` subclass |
| `timestamp` | Instant | Yes | ISO-8601 instant when the envelope was created |
| `traceId` | UUID | Yes | End-to-end trace ID, propagated from the triggering event |
| `payload` | Object | Yes | The typed event POJO, serialized inline |

---

## SAGA Events

All SAGA events flow through the `estimation.saga` Kafka topic unless otherwise specified.

---

### EstimationRequestedEvent

**Class:** `com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent`
**EventType constant:** `EventConstants.ESTIMATION_REQUESTED` = `"EstimationRequested"`
**Producer:** Estimation Service
**Consumers:** Customer Service, Vehicle Service, RealEstate Service, Insurance Service

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `customerId` | UUID | Yes | Customer being quoted |
| `vehicleId` | UUID | No | Vehicle for traffic insurance (null for property insurance) |
| `realEstateId` | UUID | No | Real estate for property insurance (null for traffic insurance) |
| `insuranceTypeId` | Integer | Yes | Type of insurance from reference data (TRAFFIC, CASCO, DASK, etc.) |
| `companyId` | UUID | Yes | Insurance company |

---

### CustomerValidatedEvent

**Class:** `com.insurancemanagementsystem.common.event.saga.CustomerValidatedEvent`
**EventType constant:** `EventConstants.CUSTOMER_VALIDATED` = `"CustomerValidated"`
**Producer:** Customer Service
**Consumers:** Insurance Service, Estimation Service

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `customerId` | UUID | Yes | Validated customer ID |
| `firstName` | String | Yes | Customer's first name |
| `lastName` | String | Yes | Customer's last name |

---

### CustomerInvalidatedEvent

**Class:** `com.insurancemanagementsystem.common.event.saga.CustomerInvalidatedEvent`
**EventType constant:** `EventConstants.CUSTOMER_INVALIDATED` = `"CustomerInvalidated"`
**Producer:** Customer Service
**Consumers:** Estimation Service

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `customerId` | UUID | Yes | Invalid customer ID |
| `reason` | String | Yes | Reason for invalidation (e.g., "Customer not found or inactive") |

---

### VehicleValidatedEvent

**Class:** `com.insurancemanagementsystem.common.event.saga.VehicleValidatedEvent`
**EventType constant:** `EventConstants.VEHICLE_VALIDATED` = `"VehicleValidated"`
**Producer:** Vehicle Service
**Consumers:** Insurance Service, Estimation Service

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `vehicleId` | UUID | No | Validated vehicle ID (null if no vehicle in the estimation request) |
| `plate` | String | No | Vehicle plate number |
| `brand` | String | No | Vehicle brand name (resolved from `carBrandId`) |
| `model` | String | No | Vehicle model name (resolved from `carModelId`) |

---

### VehicleInvalidatedEvent

**Class:** `com.insurancemanagementsystem.common.event.saga.VehicleInvalidatedEvent`
**EventType constant:** `EventConstants.VEHICLE_INVALIDATED` = `"VehicleInvalidated"`
**Producer:** Vehicle Service
**Consumers:** Estimation Service

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `vehicleId` | UUID | Yes | Invalid vehicle ID |
| `reason` | String | Yes | Reason for invalidation (e.g., "Vehicle not found") |

---

### RealEstateValidatedEvent

**Class:** `com.insurancemanagementsystem.common.event.saga.RealEstateValidatedEvent`
**EventType constant:** `EventConstants.REAL_ESTATE_VALIDATED` = `"RealEstateValidated"`
**Producer:** RealEstate Service
**Consumers:** Estimation Service

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `realEstateId` | UUID | No | Validated real estate ID (null if no real estate in the estimation request) |
| `address` | String | No | Real estate address |
| `cityId` | Integer | No | City ID from reference data |

---

### RealEstateInvalidatedEvent

**Class:** `com.insurancemanagementsystem.common.event.saga.RealEstateInvalidatedEvent`
**EventType constant:** `EventConstants.REAL_ESTATE_INVALIDATED` = `"RealEstateInvalidated"`
**Producer:** RealEstate Service
**Consumers:** Estimation Service

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `realEstateId` | UUID | Yes | Invalid real estate ID |
| `reason` | String | Yes | Reason for invalidation (e.g., "Real estate not found") |

---

### PremiumCalculatedEvent

**Class:** `com.insurancemanagementsystem.common.event.saga.PremiumCalculatedEvent`
**EventType constant:** `EventConstants.PREMIUM_CALCULATED` = `"PremiumCalculated"`
**Producer:** Insurance Service
**Consumers:** Estimation Service

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `premium` | BigDecimal | Yes | Total calculated premium amount |
| `breakdown` | Map<String, BigDecimal> | No | Itemized breakdown (basePremium, riskFactor, adjustment) |
| `insuranceTypeId` | Integer | Yes | Insurance type from the estimation request |
| `companyId` | UUID | Yes | Insurance company from the estimation request |
| `customerId` | UUID | Yes | Customer from the estimation request |
| `vehicleId` | UUID | No | Vehicle from the estimation request (null for property insurance) |

---

### CalculationFailedEvent

**Class:** `com.insurancemanagementsystem.common.event.saga.CalculationFailedEvent`
**EventType constant:** `EventConstants.CALCULATION_FAILED` = `"CalculationFailed"`
**Producer:** Insurance Service
**Consumers:** Estimation Service

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `reason` | String | Yes | Reason for premium calculation failure (e.g., "No active insurance found") |

---

### EstimationFailedEvent

**Class:** `com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent`
**EventType constant:** `EventConstants.ESTIMATION_FAILED` = `"EstimationFailed"`
**Producer:** Estimation Service
**Consumers:** All SAGA participants (Customer, Vehicle, RealEstate, Insurance)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `originalSagaId` | UUID | Yes | The SAGA ID that failed |
| `reason` | String | Yes | Human-readable failure reason |
| `failedStep` | String | Yes | The step that failed (e.g., "CustomerValidation", "VehicleValidation", "PremiumCalculation", "TIMEOUT") |

---

## Domain Events

Domain events flow through per-service topics (`customer.events`, `vehicle.events`, `realestate.events`, `insurance.events`, `reference-data.events`). They are used for audit, analytics, cache invalidation, and cross-service awareness.

---

### CustomerCreatedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.CustomerCreatedEvent`
**EventType constant:** `EventConstants.CUSTOMER_CREATED` = `"CustomerCreated"`
**Producer:** Customer Service
**Topic:** `customer.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `customerId` | UUID | Yes | New customer ID |
| `nationalId` | String | Yes | Turkish national ID (TCKN, 11 digits) |
| `email` | String | No | Customer email address |
| `firstName` | String | Yes | Customer first name |
| `lastName` | String | Yes | Customer last name |

---

### CustomerUpdatedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.CustomerUpdatedEvent`
**EventType constant:** `EventConstants.CUSTOMER_UPDATED` = `"CustomerUpdated"`
**Producer:** Customer Service
**Topic:** `customer.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `customerId` | UUID | Yes | Updated customer ID |
| `nationalId` | String | Yes | Turkish national ID (TCKN, 11 digits) |
| `email` | String | No | Customer email address |
| `firstName` | String | Yes | Customer first name |
| `lastName` | String | Yes | Customer last name |

---

### CustomerDeletedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.CustomerDeletedEvent`
**EventType constant:** `EventConstants.CUSTOMER_DELETED` = `"CustomerDeleted"`
**Producer:** Customer Service
**Topic:** `customer.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `customerId` | UUID | Yes | Deleted customer ID |
| `nationalId` | String | Yes | Turkish national ID (TCKN, 11 digits) |

---

### VehicleCreatedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.VehicleCreatedEvent`
**EventType constant:** `EventConstants.VEHICLE_CREATED` = `"VehicleCreated"`
**Producer:** Vehicle Service
**Topic:** `vehicle.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `vehicleId` | UUID | Yes | New vehicle ID |
| `plate` | String | Yes | Vehicle plate number |
| `customerId` | UUID | Yes | Owner customer ID |
| `carBrandId` | Integer | Yes | Car brand ID from reference data |

---

### VehicleUpdatedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.VehicleUpdatedEvent`
**EventType constant:** `EventConstants.VEHICLE_UPDATED` = `"VehicleUpdated"`
**Producer:** Vehicle Service
**Topic:** `vehicle.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `vehicleId` | UUID | Yes | Updated vehicle ID |
| `plate` | String | Yes | Vehicle plate number |
| `customerId` | UUID | Yes | Owner customer ID |
| `carBrandId` | Integer | Yes | Car brand ID from reference data |

---

### VehicleDeletedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.VehicleDeletedEvent`
**EventType constant:** `EventConstants.VEHICLE_DELETED` = `"VehicleDeleted"`
**Producer:** Vehicle Service
**Topic:** `vehicle.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `vehicleId` | UUID | Yes | Deleted vehicle ID |
| `plate` | String | Yes | Vehicle plate number |

---

### RealEstateCreatedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.RealEstateCreatedEvent`
**EventType constant:** `EventConstants.REAL_ESTATE_CREATED` = `"RealEstateCreated"`
**Producer:** RealEstate Service
**Topic:** `realestate.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `realEstateId` | UUID | Yes | New real estate ID |
| `address` | String | Yes | Real estate address |
| `cityId` | Integer | Yes | City ID from reference data |
| `customerId` | UUID | Yes | Owner customer ID |

---

### RealEstateUpdatedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.RealEstateUpdatedEvent`
**EventType constant:** `EventConstants.REAL_ESTATE_UPDATED` = `"RealEstateUpdated"`
**Producer:** RealEstate Service
**Topic:** `realestate.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `realEstateId` | UUID | Yes | Updated real estate ID |
| `address` | String | Yes | Real estate address |
| `cityId` | Integer | Yes | City ID from reference data |
| `customerId` | UUID | Yes | Owner customer ID |

---

### RealEstateDeletedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.RealEstateDeletedEvent`
**EventType constant:** `EventConstants.REAL_ESTATE_DELETED` = `"RealEstateDeleted"`
**Producer:** RealEstate Service
**Topic:** `realestate.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `realEstateId` | UUID | Yes | Deleted real estate ID |

---

### InsuranceCreatedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.InsuranceCreatedEvent`
**EventType constant:** `EventConstants.INSURANCE_CREATED` = `"InsuranceCreated"`
**Producer:** Insurance Service
**Topic:** `insurance.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `insuranceId` | UUID | Yes | New insurance product ID |
| `typeId` | Integer | Yes | Insurance type ID (TRAFFIC, CASCO, DASK, etc.) |
| `companyId` | UUID | Yes | Insurance company ID |
| `name` | String | Yes | Insurance product name |

---

### InsuranceUpdatedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.InsuranceUpdatedEvent`
**EventType constant:** `EventConstants.INSURANCE_UPDATED` = `"InsuranceUpdated"`
**Producer:** Insurance Service
**Topic:** `insurance.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `insuranceId` | UUID | Yes | Updated insurance product ID |
| `typeId` | Integer | Yes | Insurance type ID |
| `companyId` | UUID | Yes | Insurance company ID |
| `name` | String | Yes | Insurance product name |

---

### InsuranceDeletedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.InsuranceDeletedEvent`
**EventType constant:** `EventConstants.INSURANCE_DELETED` = `"InsuranceDeleted"`
**Producer:** Insurance Service
**Topic:** `insurance.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `insuranceId` | UUID | Yes | Deleted insurance product ID |
| `typeId` | Integer | Yes | Insurance type ID |
| `companyId` | UUID | Yes | Insurance company ID |

---

### ReferenceDataChangedEvent

**Class:** `com.insurancemanagementsystem.common.event.domain.ReferenceDataChangedEvent`
**EventType constant:** `EventConstants.REFERENCE_DATA_CHANGED` = `"ReferenceDataChanged"`
**Producer:** Reference Data Service
**Topic:** `reference-data.events`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `entityType` | String | Yes | The reference entity that changed (e.g., "City", "Profession", "CarBrand") |
| `changeType` | String | Yes | Type of change (e.g., "CREATED", "UPDATED", "DELETED") |

---

## Event Type Index

| # | Event Class | Constant | Envelope `eventType` |
|---|-------------|----------|---------------------|
| 1 | `EstimationRequestedEvent` | `ESTIMATION_REQUESTED` | `"EstimationRequested"` |
| 2 | `CustomerValidatedEvent` | `CUSTOMER_VALIDATED` | `"CustomerValidated"` |
| 3 | `CustomerInvalidatedEvent` | `CUSTOMER_INVALIDATED` | `"CustomerInvalidated"` |
| 4 | `VehicleValidatedEvent` | `VEHICLE_VALIDATED` | `"VehicleValidated"` |
| 5 | `VehicleInvalidatedEvent` | `VEHICLE_INVALIDATED` | `"VehicleInvalidated"` |
| 6 | `RealEstateValidatedEvent` | `REAL_ESTATE_VALIDATED` | `"RealEstateValidated"` |
| 7 | `RealEstateInvalidatedEvent` | `REAL_ESTATE_INVALIDATED` | `"RealEstateInvalidated"` |
| 8 | `PremiumCalculatedEvent` | `PREMIUM_CALCULATED` | `"PremiumCalculated"` |
| 9 | `CalculationFailedEvent` | `CALCULATION_FAILED` | `"CalculationFailed"` |
| 10 | `EstimationFailedEvent` | `ESTIMATION_FAILED` | `"EstimationFailed"` |
| 11 | `CustomerCreatedEvent` | `CUSTOMER_CREATED` | `"CustomerCreated"` |
| 12 | `CustomerUpdatedEvent` | `CUSTOMER_UPDATED` | `"CustomerUpdated"` |
| 13 | `CustomerDeletedEvent` | `CUSTOMER_DELETED` | `"CustomerDeleted"` |
| 14 | `VehicleCreatedEvent` | `VEHICLE_CREATED` | `"VehicleCreated"` |
| 15 | `VehicleUpdatedEvent` | `VEHICLE_UPDATED` | `"VehicleUpdated"` |
| 16 | `VehicleDeletedEvent` | `VEHICLE_DELETED` | `"VehicleDeleted"` |
| 17 | `RealEstateCreatedEvent` | `REAL_ESTATE_CREATED` | `"RealEstateCreated"` |
| 18 | `RealEstateUpdatedEvent` | `REAL_ESTATE_UPDATED` | `"RealEstateUpdated"` |
| 19 | `RealEstateDeletedEvent` | `REAL_ESTATE_DELETED` | `"RealEstateDeleted"` |
| 20 | `InsuranceCreatedEvent` | `INSURANCE_CREATED` | `"InsuranceCreated"` |
| 21 | `InsuranceUpdatedEvent` | `INSURANCE_UPDATED` | `"InsuranceUpdated"` |
| 22 | `InsuranceDeletedEvent` | `INSURANCE_DELETED` | `"InsuranceDeleted"` |
| 23 | `ReferenceDataChangedEvent` | `REFERENCE_DATA_CHANGED` | `"ReferenceDataChanged"` |
