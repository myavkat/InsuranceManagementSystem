# Plan 09: Fix Real Estate Service — Add City and Customer Names to Response DTO

## Objective

The frontend expects `cityName` and `customerName` on `RealEstateResponse` (see `frontend/src/lib/api/realestate.ts` lines 56-57: `cityName?: string; customerName?: string`), but the Java backend DTO has neither field. The `RealEstateService.toResponse()` method resolves local reference names (construction type, luxury class, usage type) but never looks up city or customer names from their respective services.

This plan adds `cityName` and `customerName` to the `RealEstateResponse` DTO and wires up synchronous REST calls to reference-data-service (for city names) and customer-service (for customer names).

## Files to Read First

| File | Reason |
|------|--------|
| `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/dto/RealEstateResponse.java` | Current DTO — you will add two fields and two parameters to `fromEntity()` |
| `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java` | Current service — you will modify `toResponse()`, `findAll()`, and `findById()` |
| `services/realestate-service/src/main/resources/application.yml` | Current config — you will add service URL properties |
| `services/realestate-service/build.gradle.kts` | Current dependencies — verify no changes needed |
| `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/dto/CustomerResponse.java` | Reference — shows the JSON shape returned by `GET /api/customers/{id}` |
| `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/dto/CityResponse.java` | Reference — shows the JSON shape returned by `GET /api/reference-data/cities` |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Lombok order, Java 21+ conventions |
| `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` | Service specs — confirms customer and reference-data endpoints |

## Key Technical Context

- **Project uses Java 25, Spring Boot 4.0.6, Spring Cloud 2025.1.2.**
- **Lombok convention** (from `docs/outlines/10_JAVA_CONVENTIONS.md`): annotation order is `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, then JPA annotations.
- **No Feign clients exist** anywhere in the project. Use Spring 6+ `RestClient`.
- **Customer service endpoint**: `GET /api/customers/{id}` returns `ApiResponse<CustomerResponse>`. The `CustomerResponse` has `firstName` and `lastName` — concatenate them.
- **Reference data service endpoint**: `GET /api/reference-data/cities` returns `ApiResponse<List<CityResponse>>` with a 5-minute cache header. Each `CityResponse` has `id`, `name`, `plateCode`. **There is NO single-city-by-ID endpoint.** You have two options:
  - **Option A (chosen)**: Fetch the full cities list once (cached locally in the client) and build a lookup map. The cities list is small (~81 cities for Turkey) and rarely changes.
  - Option B: Add a new `GET /api/reference-data/cities/{id}` endpoint to reference-data-service. This is cleaner but requires changing another service.
- **The API response is wrapped** in `ApiResponse<T>` envelope (`success`, `message`, `data`, `timestamp`). The actual data is inside the `data` field.
- **cityId and customerId can be null** on RealEstate entities — handle gracefully (return `null` for the name).
- **For the list endpoint**, batch-resolve all unique city IDs and customer IDs before mapping, to avoid N+1 REST calls.
- **RestClient is built into Spring Boot 3.2+**. No extra dependency needed.
- **Jackson 3 notes**: annotations stay at `com.fasterxml.jackson.annotation.*`. Only programmatic API classes need `tools.jackson.databind.*`.

## Files to Create

1. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/client/CustomerServiceClient.java`
2. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/client/ReferenceDataServiceClient.java`

## Files to Modify

1. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/dto/RealEstateResponse.java`
2. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java`
3. `services/realestate-service/src/main/resources/application.yml`

## Steps

### Step 1: Add service URL configuration properties

Open `services/realestate-service/src/main/resources/application.yml`.

Add the following under the existing `realestate:` block:

```yaml
realestate:
  outbox:
    poll-interval-ms: 1000
    batch-size: 10
    max-retries: 3
    failed-ttl-minutes: 60
  customer-service-url: ${CUSTOMER_SERVICE_URL:http://localhost:8081}
  reference-data-service-url: ${REFERENCE_DATA_SERVICE_URL:http://localhost:8085}
```

The `${VAR:default}` pattern reads from env var with a localhost fallback for dev.

### Step 2: Create `CustomerServiceClient` component

Create the file `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/client/CustomerServiceClient.java`.

This is the same pattern as the vehicle-service equivalent (see Plan 07, Step 2). It wraps a `RestClient` to fetch customer name by UUID.

```java
package com.insurancemanagementsystem.realestate.client;

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

    public CustomerServiceClient(@Value("${realestate.customer-service-url}") String baseUrl) {
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

### Step 3: Create `ReferenceDataServiceClient` component

Create the file `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/client/ReferenceDataServiceClient.java`.

This client fetches the full cities list from `GET /api/reference-data/cities` and builds a lookup map. Because the cities list is small and rarely changes, cache it locally for 5 minutes.

```java
package com.insurancemanagementsystem.realestate.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ReferenceDataServiceClient {

    private final RestClient restClient;
    private volatile Map<Integer, String> cachedCityNames = Map.of();
    private volatile Instant cacheExpiry = Instant.MIN;

    public ReferenceDataServiceClient(@Value("${realestate.reference-data-service-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    /**
     * Returns the city name for a given city ID, or null if not found.
     * Results are cached for 5 minutes since the cities list rarely changes.
     */
    public String getCityName(Integer cityId) {
        if (cityId == null) {
            return null;
        }
        Map<Integer, String> cityMap = getCityMap();
        return cityMap.get(cityId);
    }

    private Map<Integer, String> getCityMap() {
        // Double-checked locking for thread-safe lazy cache refresh
        if (Instant.now().isBefore(cacheExpiry)) {
            return cachedCityNames;
        }
        synchronized (this) {
            if (Instant.now().isBefore(cacheExpiry)) {
                return cachedCityNames;
            }
            try {
                var response = restClient.get()
                        .uri("/api/reference-data/cities")
                        .retrieve()
                        .body(Map.class);

                if (response == null) {
                    return cachedCityNames; // return stale cache on error
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                if (data == null) {
                    return cachedCityNames;
                }

                Map<Integer, String> newMap = new HashMap<>();
                for (Map<String, Object> city : data) {
                    Integer id = (Integer) city.get("id");
                    String name = (String) city.get("name");
                    if (id != null && name != null) {
                        newMap.put(id, name);
                    }
                }
                cachedCityNames = Collections.unmodifiableMap(newMap);
                cacheExpiry = Instant.now().plusSeconds(300); // 5 minutes
                log.debug("Refreshed city name cache: {} cities", newMap.size());
            } catch (Exception e) {
                log.warn("Failed to fetch cities list: {}. Using {} cached entries.",
                        e.getMessage(), cachedCityNames.size());
                // Extend stale cache by 60 seconds to avoid hammering a failing service
                cacheExpiry = Instant.now().plusSeconds(60);
            }
            return cachedCityNames;
        }
    }
}
```

**Why cache the full list:** The reference-data-service only exposes `GET /api/reference-data/cities` (returns all cities), not a single-city-by-ID endpoint. Fetching the full list (81 cities × ~3 fields = tiny payload) once every 5 minutes is far more efficient than making individual calls, and simpler than modifying the reference-data-service to add a new endpoint.

### Step 4: Add `cityName` and `customerName` fields to `RealEstateResponse` DTO

Open `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/dto/RealEstateResponse.java`.

**4a.** Add the two new fields. Add them after `customerId` (line 30):

```java
private UUID customerId;
private String cityName;        // <-- ADD THIS LINE
private String customerName;    // <-- ADD THIS LINE
private Instant createdAt;
```

**4b.** Modify the `fromEntity()` static method signature to accept the two new name parameters.

Change from:
```java
public static RealEstateResponse fromEntity(RealEstate realEstate,
                                             String constructionTypeName,
                                             String luxuryClassName,
                                             String usageTypeName) {
```

To:
```java
public static RealEstateResponse fromEntity(RealEstate realEstate,
                                             String constructionTypeName,
                                             String luxuryClassName,
                                             String usageTypeName,
                                             String cityName,
                                             String customerName) {
```

**4c.** In the builder chain inside `fromEntity()`, add the new setters after `.customerId(realEstate.getCustomerId())`:

```java
.customerId(realEstate.getCustomerId())
.cityName(cityName)          // <-- ADD THIS LINE
.customerName(customerName)  // <-- ADD THIS LINE
.createdAt(realEstate.getCreatedAt())
```

### Step 5: Modify `RealEstateService` to inject clients and resolve names

Open `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java`.

**5a.** Inject the two new client components by adding these fields alongside the existing `private final` fields:

```java
private final CustomerServiceClient customerServiceClient;
private final ReferenceDataServiceClient referenceDataServiceClient;
```

(Lombok `@RequiredArgsConstructor` will include them automatically since they're `final`.)

**5b.** Modify the `toResponse()` helper to accept pre-resolved names. Change from:

```java
private RealEstateResponse toResponse(RealEstate realEstate) {
    String constructionTypeName = constructionTypeRepository
            .findById(realEstate.getConstructionTypeId())
            .map(RealEstateConstructionType::getName)
            .orElse(null);
    String luxuryClassName = luxuryClassRepository
            .findById(realEstate.getLuxuryClassId())
            .map(RealEstateLuxuryClass::getName)
            .orElse(null);
    String usageTypeName = usageTypeRepository
            .findById(realEstate.getUsageTypeId())
            .map(RealEstateUsageType::getName)
            .orElse(null);

    return RealEstateResponse.fromEntity(realEstate, constructionTypeName, luxuryClassName, usageTypeName);
}
```

To:

```java
private RealEstateResponse toResponse(RealEstate realEstate, String cityName, String customerName) {
    String constructionTypeName = constructionTypeRepository
            .findById(realEstate.getConstructionTypeId())
            .map(RealEstateConstructionType::getName)
            .orElse(null);
    String luxuryClassName = luxuryClassRepository
            .findById(realEstate.getLuxuryClassId())
            .map(RealEstateLuxuryClass::getName)
            .orElse(null);
    String usageTypeName = usageTypeRepository
            .findById(realEstate.getUsageTypeId())
            .map(RealEstateUsageType::getName)
            .orElse(null);

    return RealEstateResponse.fromEntity(realEstate,
            constructionTypeName, luxuryClassName, usageTypeName,
            cityName, customerName);
}
```

**5c.** Update `findById()` to resolve names for a single entity:

Change the body of `findById(UUID id)` from:
```java
RealEstate realEstate = realEstateRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("RealEstate not found with id: " + id));
return toResponse(realEstate);
```

To:
```java
RealEstate realEstate = realEstateRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("RealEstate not found with id: " + id));
String cityName = referenceDataServiceClient.getCityName(realEstate.getCityId());
String customerName = customerServiceClient.getCustomerName(realEstate.getCustomerId());
return toResponse(realEstate, cityName, customerName);
```

**5d.** Update `findAll()` to batch-resolve names for the list endpoint. Replace the entire method:

Current:
```java
@Transactional(readOnly = true)
public Page<RealEstateResponse> findAll(Pageable pageable) {
    return realEstateRepository.findAll(pageable).map(this::toResponse);
}
```

New:
```java
@Transactional(readOnly = true)
public Page<RealEstateResponse> findAll(Pageable pageable) {
    Page<RealEstate> page = realEstateRepository.findAll(pageable);

    // Collect unique non-null city IDs and customer IDs from the page
    java.util.Set<Integer> cityIds = page.getContent().stream()
            .map(RealEstate::getCityId)
            .filter(id -> id != null)
            .collect(java.util.stream.Collectors.toSet());

    java.util.Set<UUID> customerIds = page.getContent().stream()
            .map(RealEstate::getCustomerId)
            .filter(id -> id != null)
            .collect(java.util.stream.Collectors.toSet());

    // Resolve city names (the client already caches the full list)
    java.util.Map<Integer, String> cityNameMap = new java.util.HashMap<>();
    for (Integer cityId : cityIds) {
        String name = referenceDataServiceClient.getCityName(cityId);
        if (name != null) {
            cityNameMap.put(cityId, name);
        }
    }

    // Resolve customer names (one REST call per unique customer)
    java.util.Map<UUID, String> customerNameMap = new java.util.HashMap<>();
    for (UUID customerId : customerIds) {
        String name = customerServiceClient.getCustomerName(customerId);
        if (name != null) {
            customerNameMap.put(customerId, name);
        }
    }

    // Map entities to DTOs using pre-resolved names
    return page.map(realEstate -> {
        String cityName = cityNameMap.get(realEstate.getCityId());
        String customerName = customerNameMap.get(realEstate.getCustomerId());
        return toResponse(realEstate, cityName, customerName);
    });
}
```

**5e.** Update `create()` — the line that calls `toResponse(saved)` at the end:

Change from:
```java
return toResponse(saved);
```

To:
```java
String cityName = referenceDataServiceClient.getCityName(saved.getCityId());
String customerName = customerServiceClient.getCustomerName(saved.getCustomerId());
return toResponse(saved, cityName, customerName);
```

**5f.** Update `update()` — the line that calls `toResponse(saved)` at the end:

Change from:
```java
return toResponse(saved);
```

To:
```java
String cityName = referenceDataServiceClient.getCityName(saved.getCityId());
String customerName = customerServiceClient.getCustomerName(saved.getCustomerId());
return toResponse(saved, cityName, customerName);
```

### Step 6: Verify compilation

Run from the repo root:

```
./gradlew :realestate-service:compileJava
```

If there are compilation errors, fix them before marking this plan complete.

## Acceptance Criteria

- [x] `RealEstateResponse` has `cityName` (String) and `customerName` (String) fields
- [x] `RealEstateResponse.fromEntity()` accepts and sets both new fields
- [x] `CustomerServiceClient` successfully calls `GET /api/customers/{id}` and returns `"firstName lastName"`
- [x] `ReferenceDataServiceClient` fetches the full cities list from `GET /api/reference-data/cities`, builds a lookup map, and caches it for 5 minutes
- [x] `RealEstateService.findAll()` batch-resolves city and customer names using lookup maps
- [x] `RealEstateService.findById()` resolves and returns both city name and customer name
- [x] `RealEstateService.create()` and `update()` return responses with both names populated
- [x] When `cityId` or `customerId` is null, the corresponding name is returned as null (no NPE)
- [x] When an external service call fails (network error, 404), the code logs a warning and returns null for that name instead of throwing
- [x] Code compiles without errors

## Dependencies

- **None.** This plan is self-contained within realestate-service.
- The `CustomerServiceClient` in this plan is a near-copy of the one in Plan 07 (vehicle-service). Per the "Extract before triplicating" rule, if a third service needs the same client, it should be extracted to a shared module. For now, two copies are acceptable.
- Plan 10 (real estate search) touches the same files but is functionally independent. If running both, complete this plan first, then apply Plan 10 on top.
