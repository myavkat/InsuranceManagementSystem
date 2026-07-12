# Plan 07: Fix Vehicle Service — Add Customer Name to Response DTO

## Objective

The frontend expects `customerName` on `VehicleResponse` (see `frontend/src/lib/api/vehicles.ts` line 59: `customerName?: string`), but the Java backend DTO has no such field. The `VehicleService.toResponse()` method resolves local reference names (brand, model, engine, etc.) but never looks up the customer name from customer-service. As a result, the vehicle list and detail pages show `"—"` for the Customer column.

This plan adds `customerName` to the `VehicleResponse` DTO and wires up a synchronous REST call to customer-service to resolve the customer ID to a display name.

## Files to Read First

| File | Reason |
|------|--------|
| `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/dto/VehicleResponse.java` | Current DTO — you will add a field and a parameter to `fromEntity()` |
| `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java` | Current service — you will modify `toResponse()`, `findAll()`, and `findById()` |
| `services/vehicle-service/src/main/resources/application.yml` | Current config — you will add the customer-service URL property |
| `services/vehicle-service/build.gradle.kts` | Current dependencies — verify no changes needed |
| `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/dto/CustomerResponse.java` | Reference — shows the JSON shape returned by `GET /api/customers/{id}` |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Lombok order, Java 21+ conventions, datetime conventions |
| `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` | Customer service spec — confirms endpoint `GET /api/customers/{id}` |

## Key Technical Context

- **Project uses Java 25, Spring Boot 4.0.6, Spring Cloud 2025.1.2.**
- **Lombok convention** (from `docs/outlines/10_JAVA_CONVENTIONS.md`): annotation order is `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, then JPA annotations.
- **No Feign clients exist** anywhere in the project. Use Spring 6+ `RestClient` (not the older `RestTemplate`).
- **Customer service endpoint**: `GET /api/customers/{id}` returns `ApiResponse<CustomerResponse>`. The `CustomerResponse` has `id`, `firstName`, `lastName`, `nationalId`, `email`, `phone`, `birthDate`, `address`, `cityId`, `professionId`, `createdAt`, `updatedAt`.
- **The customer's `firstName` and `lastName` must be concatenated** into a single display name (e.g., `"John Doe"`).
- **The API response is wrapped** in the standard `ApiResponse<T>` envelope (`success`, `message`, `data`, `timestamp`). The actual customer data is inside the `data` field.
- **customerId can be null** on Vehicle entities — handle this gracefully (return `null` for `customerName`).
- **For the list endpoint**, many vehicles may share the same customerId. To avoid N+1 REST calls, collect all unique non-null customer IDs from the page, fetch each once, and build a lookup map before mapping entities to DTOs.
- **RestClient is built into Spring Boot 3.2+** (`org.springframework.web.client.RestClient`). No extra dependency needed.
- **Jackson 3 notes** (from `docs/outlines/10_JAVA_CONVENTIONS.md`): annotations stay at `com.fasterxml.jackson.annotation.*`. Only programmatic API classes (`ObjectMapper`, `JsonNode`) need `tools.jackson.databind.*`. Your code won't need to parse JSON manually — RestClient handles it.

## Files to Create

1. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/client/CustomerServiceClient.java`

## Files to Modify

1. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/dto/VehicleResponse.java`
2. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java`
3. `services/vehicle-service/src/main/resources/application.yml`

## Steps

### Step 1: Add `customerServiceUrl` configuration property

Open `services/vehicle-service/src/main/resources/application.yml`.

Add the following under the existing `vehicle:` block (which currently has `outbox:` settings):

```yaml
vehicle:
  outbox:
    poll-interval-ms: 1000
    batch-size: 10
    max-retries: 3
    failed-ttl-minutes: 60
  customer-service-url: ${CUSTOMER_SERVICE_URL:http://localhost:8081}
```

The `${CUSTOMER_SERVICE_URL:...}` pattern reads from env var with a localhost fallback for dev.

### Step 2: Create `CustomerServiceClient` component

Create the file `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/client/CustomerServiceClient.java`.

This class is a Spring `@Component` that wraps a `RestClient` and provides a method to fetch a customer name by ID. The RestClient calls `GET http://<baseUrl>/api/customers/{id}` and extracts the `data.firstName` and `data.lastName` from the `ApiResponse<CustomerResponse>` envelope.

Exact implementation:

```java
package com.insurancemanagementsystem.vehicle.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class CustomerServiceClient {

    private final RestClient restClient;

    public CustomerServiceClient(@Value("${vehicle.customer-service-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    /**
     * Fetches the full display name (firstName + lastName) for a customer.
     * Returns null if the customerId is null or the customer is not found.
     */
    public String getCustomerName(UUID customerId) {
        if (customerId == null) {
            return null;
        }
        try {
            // The API returns ApiResponse<CustomerResponse>.
            // Navigate to the 'data' node to get the customer fields.
            var response = restClient.get()
                    .uri("/api/customers/{id}", customerId)
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

            String firstName = (String) data.get("firstName");
            String lastName = (String) data.get("lastName");

            if (firstName != null && lastName != null) {
                return firstName + " " + lastName;
            } else if (firstName != null) {
                return firstName;
            } else if (lastName != null) {
                return lastName;
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch customer name for customerId={}: {}", customerId, e.getMessage());
            return null;
        }
    }
}
```

**Why `Map.class` instead of a typed DTO**: To avoid creating a duplicate `CustomerResponse` class in vehicle-service. The JSON path `response.data.firstName` / `response.data.lastName` is accessed via Map navigation. If you prefer a typed approach, you could create a local DTO record:

```java
// Alternative typed approach (optional, either works):
// record CustomerDto(String firstName, String lastName) {}
// Then use: restClient.get()...body(new ParameterizedTypeReference<ApiResponse<CustomerDto>>() {})
```

But the Map approach is simpler and sufficient for extracting just two fields.

### Step 3: Add `customerName` field to `VehicleResponse` DTO

Open `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/dto/VehicleResponse.java`.

**3a.** Add the new field after `customerId` (line 37):

```java
private UUID customerId;
private String customerName;   // <-- ADD THIS LINE
private Instant createdAt;
```

**3b.** Modify the `fromEntity()` static method to accept a `customerName` parameter. Add `String customerName` as the LAST parameter in the method signature:

Change the method signature from:
```java
public static VehicleResponse fromEntity(Vehicle vehicle,
                                          String brandName,
                                          String modelName,
                                          String engineName,
                                          BigDecimal engineVolume,
                                          Integer enginePower,
                                          String fuelTypeName,
                                          String typeName,
                                          String packageName) {
```

To:
```java
public static VehicleResponse fromEntity(Vehicle vehicle,
                                          String brandName,
                                          String modelName,
                                          String engineName,
                                          BigDecimal engineVolume,
                                          Integer enginePower,
                                          String fuelTypeName,
                                          String typeName,
                                          String packageName,
                                          String customerName) {
```

**3c.** In the builder chain inside `fromEntity()`, add the customerName setter. Add this line after `.customerId(vehicle.getCustomerId())` (around line 69):

```java
.customerId(vehicle.getCustomerId())
.customerName(customerName)    // <-- ADD THIS LINE
.createdAt(vehicle.getCreatedAt())
```

### Step 4: Modify `VehicleService.toResponse()` to accept and pass customer name

Open `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java`.

**4a.** Inject the new `CustomerServiceClient` by adding it to the constructor dependencies. Add this field:

```java
private final CustomerServiceClient customerServiceClient;
```

(It goes alongside the other `private final` fields. Lombok `@RequiredArgsConstructor` will include it automatically since it's `final`.)

**4b.** Modify the `toResponse()` helper method. **Do NOT make it call the REST client directly** — that would cause N REST calls per page for the list endpoint. Instead, change `toResponse` to accept a pre-resolved `customerName` parameter.

Change the method from:
```java
private VehicleResponse toResponse(Vehicle vehicle) {
```

To:
```java
private VehicleResponse toResponse(Vehicle vehicle, String customerName) {
```

And update the call to `VehicleResponse.fromEntity()` at the end of `toResponse()` to pass `customerName` as the new last argument:

```java
return VehicleResponse.fromEntity(vehicle,
        brand != null ? brand.getName() : null,
        model != null ? model.getName() : null,
        engine != null ? engine.getName() : null,
        engine != null ? engine.getVolume() : null,
        engine != null ? engine.getPower() : null,
        fuelType != null ? fuelType.getName() : null,
        type != null ? type.getName() : null,
        pkg != null ? pkg.getName() : null,
        customerName);   // <-- ADD THIS ARGUMENT
```

**4c.** Update `findById()` (single entity lookup — one REST call is fine):

Change the body of `findById(UUID id)` from:
```java
Vehicle vehicle = vehicleRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Vehicle not found with id: " + id));
return toResponse(vehicle);
```

To:
```java
Vehicle vehicle = vehicleRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Vehicle not found with id: " + id));
String customerName = customerServiceClient.getCustomerName(vehicle.getCustomerId());
return toResponse(vehicle, customerName);
```

**4d.** Update `findAll()` (list endpoint — must batch-resolve customer names):

Replace the entire `findAll(Pageable pageable)` method:

```java
@Transactional(readOnly = true)
public Page<VehicleResponse> findAll(Pageable pageable) {
    Page<Vehicle> vehiclePage = vehicleRepository.findAll(pageable);

    // Collect unique non-null customer IDs from the page
    java.util.Set<UUID> customerIds = vehiclePage.getContent().stream()
            .map(Vehicle::getCustomerId)
            .filter(id -> id != null)
            .collect(java.util.stream.Collectors.toSet());

    // Resolve each customer name once into a lookup map
    java.util.Map<UUID, String> customerNameMap = new java.util.HashMap<>();
    for (UUID customerId : customerIds) {
        String name = customerServiceClient.getCustomerName(customerId);
        if (name != null) {
            customerNameMap.put(customerId, name);
        }
    }

    // Map entities to DTOs using pre-resolved names
    return vehiclePage.map(vehicle -> {
        String customerName = customerNameMap.get(vehicle.getCustomerId());
        return toResponse(vehicle, customerName);
    });
}
```

**4e.** Update `create()` — the line that calls `toResponse(saved)` at the end of `create()`:

Change from:
```java
return toResponse(saved);
```

To:
```java
String customerName = customerServiceClient.getCustomerName(saved.getCustomerId());
return toResponse(saved, customerName);
```

**4f.** Update `update()` — the line that calls `toResponse(saved)` at the end of `update()`:

Change from:
```java
return toResponse(saved);
```

To:
```java
String customerName = customerServiceClient.getCustomerName(saved.getCustomerId());
return toResponse(saved, customerName);
```

### Step 5: Verify compilation

Run the following from the project root:

```
cd services/vehicle-service && ../gradlew compileJava
```

(Use `../gradlew` because `gradlew` is at the repo root, not in the service directory. Or use the full path from the repo root: `./gradlew :vehicle-service:compileJava`.)

If there are compilation errors, fix them before marking this plan complete.

## Acceptance Criteria

- [x] `VehicleResponse` has a `customerName` field (type: `String`)
- [x] `VehicleResponse.fromEntity()` accepts `customerName` as a parameter and sets it in the builder
- [x] `CustomerServiceClient` exists and successfully calls `GET /api/customers/{id}` to retrieve customer name
- [x] `VehicleService.findAll()` batch-resolves customer names using a lookup map (not N individual calls per entity)
- [x] `VehicleService.findById()` resolves and returns the customer name
- [x] `VehicleService.create()` and `update()` return responses with customer name populated
- [x] When `customerId` is null, `customerName` is returned as null (no NPE)
- [x] When the customer-service call fails (network error, 404), the code logs a warning and returns null for `customerName` instead of throwing
- [x] Code compiles without errors

## Dependencies

- **None.** This plan is self-contained within vehicle-service.
- Plan 08 (vehicle search) touches the same files but is functionally independent — merge conflicts are possible if both plans modify the same lines. If running both, complete this plan first, then apply Plan 08 on top.
