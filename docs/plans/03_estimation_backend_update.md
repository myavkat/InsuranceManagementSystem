# Plan 03: Estimation Backend — Entity, DTOs, Service, Controller, and New Client

## Objective

Update all estimation-service backend code to use `insuranceId` (UUID) instead of `insuranceTypeId` (Integer). Create a new `InsuranceServiceClient` to fetch insurance details from the insurance service. Update the service logic so insurance type is derived from the insurance product rather than stored directly.

## Dependencies

- **Plan 02 (Common Events Update) MUST be completed first.** This plan's code will not compile until `EstimationRequestedEvent` and `PremiumCalculatedEvent` use `insuranceId`.

## Files to Read Before Starting

- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java`
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationRequest.java`
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationResponse.java`
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/controller/EstimationController.java`
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/EstimationRepository.java`
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/client/CustomerServiceClient.java` — reference for client pattern
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/client/VehicleServiceClient.java` — reference for client pattern
- `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` — Insurance Service endpoints section
- `docs/outlines/10_JAVA_CONVENTIONS.md` — Lombok order, datetime conventions

## Technical Context

### Why we need an InsuranceServiceClient

The estimation service currently uses `insuranceTypeId` directly for two purposes:
1. **Validation:** Type 1 (Vehicle) requires `vehicleId`, Type 2 (Real Estate) requires `realEstateId`.
2. **Display:** The `findById` method resolves the type ID to a human-readable name using a hardcoded `INSURANCE_TYPE_NAMES` map.

After this change, the estimation only stores `insuranceId`. To validate and display, it must call the insurance service to get the insurance product details (which include `typeId` and `name`).

### Insurance Service API

From `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md`:
- `GET /api/insurances/{id}` — get insurance by ID, returns Insurance with `typeId`, `name`, `basePremium`, etc.

### Client pattern to follow (VERIFIED — read the actual files)

The existing clients use this exact pattern:
```java
package com.insurancemanagementsystem.estimation.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class CustomerServiceClient {

    private final RestClient restClient;

    public CustomerServiceClient(@Value("${estimation.customer-service-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    // Method calls: restClient.get().uri("/api/...", id).retrieve().body(Map.class);
    // Response is ApiResponse<T> envelope: { success, message, data: {...}, timestamp }
    // Access data via: Map<String, Object> data = (Map<String, Object>) response.get("data");
}
```

Key conventions:
- `@Component` + `@Slf4j` (NOT `@RequiredArgsConstructor` — constructor takes `@Value` directly)
- `RestClient.create(baseUrl)` (NOT `RestClient.Builder`)
- Base URL property: `estimation.xxx-service-url` (matches existing naming)
- Response parsing: unwrap `ApiResponse` envelope via `.get("data")`
- Error handling: try-catch, log warning, return null

### Validation logic changes

Current logic in `EstimationService.create()`:
```java
if (typeId == 1 && request.getVehicleId() == null) → error
if (typeId == 2 && request.getRealEstateId() == null) → error
```

New logic:
```java
// Call insurance service to get the insurance product
InsuranceResponse insurance = insuranceServiceClient.getInsurance(request.getInsuranceId());
int typeId = insurance.getTypeId();
// Then same validation using typeId
```

### Display logic changes

Current logic in `EstimationService.findById()`:
```java
String insuranceTypeName = INSURANCE_TYPE_NAMES.get(estimation.getInsuranceTypeId());
```

New logic:
```java
InsuranceResponse insurance = insuranceServiceClient.getInsurance(estimation.getInsuranceId());
String insuranceTypeName = insurance.getTypeName(); // or resolve from type ID
String insuranceName = insurance.getName();
```

### InsuranceTypeName resolution

The `INSURANCE_TYPE_NAMES` static map in `EstimationService` can remain as a fallback, but the primary resolution should come from the insurance service. After the change, the `EstimationResponse` should carry both `insuranceId`, `insuranceName`, and `insuranceTypeName`.

## Steps

### Step 1: Create InsuranceServiceClient

Create a new file at:
`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/client/InsuranceServiceClient.java`

Follow the EXACT pattern from `CustomerServiceClient.java` (already read and verified above):

```java
package com.insurancemanagementsystem.estimation.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class InsuranceServiceClient {

    private final RestClient restClient;

    public InsuranceServiceClient(@Value("${estimation.insurance-service-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    /**
     * Fetches insurance details from the insurance service.
     * Returns null if the insuranceId is null or the insurance is not found.
     */
    public InsuranceInfo getInsurance(UUID insuranceId) {
        if (insuranceId == null) {
            return null;
        }
        try {
            var response = restClient.get()
                    .uri("/api/insurances/{id}", insuranceId)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) {
                return null;
            }

            UUID id = insuranceId; // Use the input — the response data.id is a String, avoid parsing
            String name = (String) data.get("name");
            Integer typeId = data.get("typeId") != null ? ((Number) data.get("typeId")).intValue() : null;
            String typeName = (String) data.get("typeName");

            return new InsuranceInfo(id, name, typeId, typeName);
        } catch (Exception e) {
            log.warn("Failed to fetch insurance info for insuranceId={}: {}", insuranceId, e.getMessage());
            return null;
        }
    }

    /**
     * Lightweight DTO for insurance information needed by the estimation service.
     */
    public record InsuranceInfo(UUID id, String name, Integer typeId, String typeName) {}
}
```

Note the property name: `${estimation.insurance-service-url}` — follows the same naming convention as `${estimation.customer-service-url}` and `${estimation.vehicle-service-url}`.

### Step 2: Update Estimation.java entity

Open `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java`.

**Change:**
```java
@Column(name = "insurance_type_id")
private Integer insuranceTypeId;
```
**To:**
```java
@Column(name = "insurance_id")
private UUID insuranceId;
```

No other changes needed. The entity has no JPA relationships — it only stores the raw ID.

### Step 3: Update EstimationRequest.java DTO

Open `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationRequest.java`.

**Change:**
```java
@NotNull(message = "Insurance type ID is required")
private Integer insuranceTypeId;
```
**To:**
```java
@NotNull(message = "Insurance ID is required")
private UUID insuranceId;
```

### Step 4: Update EstimationResponse.java DTO

Open `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationResponse.java`.

**A. Change the field:**
```java
private Integer insuranceTypeId;
```
**To:**
```java
private UUID insuranceId;
```

**B. Add new fields** (after `insuranceId`/`insuranceTypeName`):
```java
private String insuranceName;
private String insuranceTypeName;
```
(Keep `insuranceTypeName` — it's already there.)

**C. Update `fromEntity(Estimation)` static method:**
Change `.insuranceTypeId(estimation.getInsuranceTypeId())` to `.insuranceId(estimation.getInsuranceId())`.

**D. Update `fromEntity(Estimation, String, String, String, String, String)` static method:**
- Add a new parameter `String insuranceName` (before `String insuranceTypeName`)
- Change `.insuranceTypeId(estimation.getInsuranceTypeId())` to `.insuranceId(estimation.getInsuranceId())`
- Add `.insuranceName(insuranceName)` in the builder chain

The updated method signature should be:
```java
public static EstimationResponse fromEntity(Estimation estimation,
                                              String customerName,
                                              String customerNationalId,
                                              String vehiclePlate,
                                              String vehicleChassisNumber,
                                              String insuranceName,
                                              String insuranceTypeName)
```

### Step 5: Update EstimationService.java

Open `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`.

**A. Add the new client dependency:**
Add `private final InsuranceServiceClient insuranceServiceClient;` to the fields. Lombok's `@RequiredArgsConstructor` will handle constructor injection.

**B. Update `findById()` method:**

Current:
```java
String insuranceTypeName = INSURANCE_TYPE_NAMES.get(estimation.getInsuranceTypeId());

return EstimationResponse.fromEntity(estimation,
        customerName,
        customerNationalId,
        vehiclePlate,
        vehicleChassisNumber,
        insuranceTypeName);
```

New:
```java
String insuranceName = null;
String insuranceTypeName = null;
if (estimation.getInsuranceId() != null) {
    try {
        InsuranceServiceClient.InsuranceInfo info = insuranceServiceClient.getInsurance(estimation.getInsuranceId());
        insuranceName = info.name();
        insuranceTypeName = info.typeName() != null ? info.typeName() : INSURANCE_TYPE_NAMES.get(info.typeId());
    } catch (Exception e) {
        log.warn("Failed to fetch insurance info for insuranceId={}: {}", estimation.getInsuranceId(), e.getMessage());
        // Fall back to null — the response DTO handles null display names gracefully
    }
}

return EstimationResponse.fromEntity(estimation,
        customerName,
        customerNationalId,
        vehiclePlate,
        vehicleChassisNumber,
        insuranceName,
        insuranceTypeName);
```

**C. Update `create()` method:**

Current validation:
```java
Integer typeId = request.getInsuranceTypeId();
if (typeId == null) {
    throw new IllegalArgumentException("insuranceTypeId is required");
}
```

New validation:
```java
UUID insuranceId = request.getInsuranceId();
if (insuranceId == null) {
    throw new IllegalArgumentException("insuranceId is required");
}

// Fetch insurance to determine its type for validation
InsuranceServiceClient.InsuranceInfo info;
try {
    info = insuranceServiceClient.getInsurance(insuranceId);
} catch (Exception e) {
    throw new IllegalArgumentException("Insurance not found or unavailable: " + insuranceId, e);
}
Integer typeId = info.typeId();
```

Then keep the existing type-based validation logic (typeId 1 → vehicleId required, typeId 2 → realEstateId required).

**D. Update the Estimation builder in `create()`:**
Change:
```java
.insuranceTypeId(request.getInsuranceTypeId())
```
To:
```java
.insuranceId(request.getInsuranceId())
```

**E. Update the `EstimationRequestedEvent` builder in `create()`:**
Change:
```java
.insuranceTypeId(request.getInsuranceTypeId())
```
To:
```java
.insuranceId(request.getInsuranceId())
```

**F. The `INSURANCE_TYPE_NAMES` static map** can remain as a fallback for resolving type names. Do not delete it.

### Step 6: Update EstimationSagaConsumer.java (if needed)

Open `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`.

Search for any reference to `insuranceTypeId`. The `handlePremiumCalculated` method receives `PremiumCalculatedEvent` — the event now has `insuranceId` instead of `insuranceTypeId`. If the consumer code references `event.getInsuranceTypeId()`, change it to `event.getInsuranceId()`.

**Check carefully:** Currently the consumer just stores the premium and breakdown on the estimation; it may not even use `insuranceTypeId` from the event. Only change if the code references it.

### Step 7: Update application.yml

Open `services/estimation-service/src/main/resources/application.yml`.

Find the existing `estimation.customer-service-url` and `estimation.vehicle-service-url` properties. Add the insurance service URL property in the same location, following the same naming pattern:

```yaml
estimation:
  insurance-service-url: ${INSURANCE_SERVICE_URL:http://localhost:8084}
```

(This should be added alongside the existing `customer-service-url` and `vehicle-service-url` properties — search for them to find the exact location.)

### Step 8: Update .env.template (if needed)

From `AGENTS.md`: "Every ${ENV_VAR:default} placeholder referenced in any application.yml MUST have a corresponding entry in .env.template."

Open `.env.template` at the repository root.

Add:
```
INSURANCE_SERVICE_URL=http://localhost:8084
```

### Step 9: Verify compilation

```bash
cd services/estimation-service && ./gradlew compileJava
```

Fix any compilation errors. Common issues:
- `getInsuranceTypeId()` no longer exists → use `getInsuranceId()`
- `setInsuranceTypeId()` no longer exists → use `setInsuranceId()`

## Acceptance Criteria

- [ ] `InsuranceServiceClient.java` created following existing client pattern
- [ ] `Estimation.java` uses `insurance_id` / `insuranceId` (UUID) instead of `insurance_type_id` / `insuranceTypeId` (Integer)
- [ ] `EstimationRequest.java` uses `insuranceId` (UUID) with `@NotNull`
- [ ] `EstimationResponse.java` uses `insuranceId`, adds `insuranceName`, keeps `insuranceTypeName`
- [ ] `EstimationService.create()` fetches insurance to derive type for validation
- [ ] `EstimationService.findById()` fetches insurance to resolve names
- [ ] `EstimationRequestedEvent` is built with `.insuranceId()` instead of `.insuranceTypeId()`
- [ ] `application.yml` has insurance service URL config
- [ ] `.env.template` has `INSURANCE_SERVICE_URL`
- [ ] `./gradlew compileJava` passes in `services/estimation-service`
