# Plan: Sprint 4 — Vehicle & RealEstate — Step 7: RealEstate Service API Layer

## Objective
Create DTOs (RealEstateRequest, RealEstateResponse), RealEstateService with full CRUD + reference data lookups, and RealEstateController with all 8 REST endpoints.

## Context Files to Read First
1. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/controller/CustomerController.java`** — Controller pattern
2. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/service/CustomerService.java`** — Service pattern
3. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/dto/CustomerRequest.java`** — Request DTO pattern
4. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/dto/CustomerResponse.java`** — Response DTO pattern
5. **`services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/entity/RealEstate.java`** — Entity (from Step 6)
6. **`services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/entity/RealEstateConstructionType.java`** — Reference entity
7. **`services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/repository/RealEstateRepository.java`** — Repository

## Files to Create

### 1. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/dto/RealEstateRequest.java`

```java
package com.insurancemanagementsystem.realestate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealEstateRequest {

    @NotBlank(message = "Address is required")
    private String address;

    @NotNull(message = "City ID is required")
    private Integer cityId;

    private String district;

    @NotNull(message = "Square meters is required")
    @Min(value = 1, message = "Square meters must be positive")
    private BigDecimal squareMeters;

    @PastOrPresent(message = "Construction year cannot be in the future")
    private Integer constructionYear;

    @NotNull(message = "Construction type ID is required")
    private Integer constructionTypeId;

    @NotNull(message = "Luxury class ID is required")
    private Integer luxuryClassId;

    @NotNull(message = "Usage type ID is required")
    private Integer usageTypeId;

    @NotNull(message = "Customer ID is required")
    private UUID customerId;
}
```

Validation: `constructionYear` is validated in the service (compare against current year) rather than via annotation, since there's no built-in `@Max(currentYear)` annotation.

### 2. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/dto/RealEstateResponse.java`

```java
package com.insurancemanagementsystem.realestate.dto;

import com.insurancemanagementsystem.realestate.entity.RealEstate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealEstateResponse {
    private UUID id;
    private String address;
    private Integer cityId;
    private String district;
    private BigDecimal squareMeters;
    private Integer constructionYear;
    private Integer constructionTypeId;
    private String constructionTypeName;
    private Integer luxuryClassId;
    private String luxuryClassName;
    private Integer usageTypeId;
    private String usageTypeName;
    private UUID customerId;
    private Instant createdAt;
    private Instant updatedAt;

    public static RealEstateResponse fromEntity(RealEstate realEstate,
                                                 String constructionTypeName,
                                                 String luxuryClassName,
                                                 String usageTypeName) {
        return RealEstateResponse.builder()
                .id(realEstate.getId())
                .address(realEstate.getAddress())
                .cityId(realEstate.getCityId())
                .district(realEstate.getDistrict())
                .squareMeters(realEstate.getSquareMeters())
                .constructionYear(realEstate.getConstructionYear())
                .constructionTypeId(realEstate.getConstructionTypeId())
                .constructionTypeName(constructionTypeName)
                .luxuryClassId(realEstate.getLuxuryClassId())
                .luxuryClassName(luxuryClassName)
                .usageTypeId(realEstate.getUsageTypeId())
                .usageTypeName(usageTypeName)
                .customerId(realEstate.getCustomerId())
                .createdAt(realEstate.getCreatedAt())
                .updatedAt(realEstate.getUpdatedAt())
                .build();
    }
}
```

### 3. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java`

Follow `CustomerService` pattern exactly. Inject: RealEstateRepository + 3 reference repositories. Methods:
- `findAll(Pageable)` — paginated list with reference data names
- `findById(UUID)` — get by ID, EntityNotFoundException if missing
- `create(RealEstateRequest)` — validate construction year (not in future), validate FK references, build + save
- `update(UUID, RealEstateRequest)` — find existing, update fields, save
- `delete(UUID)` — hard delete
- `getConstructionTypes()` — return all
- `getLuxuryClasses()` — return all
- `getUsageTypes()` — return all

Add `toResponse(RealEstate)` helper method that resolves reference entity names via their repositories (same pattern as VehicleService.toResponse()).

Construction year validation in service: `if (request.getConstructionYear() != null && request.getConstructionYear() > Year.now().getValue()) throw new IllegalArgumentException(...)`

### 4. `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/controller/RealEstateController.java`

```java
@RestController
@RequestMapping("/api/real-estate")
@RequiredArgsConstructor
```

8 endpoints:
- `GET /api/real-estate` — paginated list, `@PageableDefault(sort = "createdAt", direction = DESC)`
- `GET /api/real-estate/{id}` — get by ID
- `POST /api/real-estate` — create, `@Valid @RequestBody`, 201 CREATED
- `PUT /api/real-estate/{id}` — update, `@Valid @RequestBody`
- `DELETE /api/real-estate/{id}` — delete
- `GET /api/real-estate/construction-types` — list construction types
- `GET /api/real-estate/luxury-classes` — list luxury classes
- `GET /api/real-estate/usage-types` — list usage types

All responses wrapped in `ResponseEntity<ApiResponse<T>>`.

## Verification

```bash
.\gradlew.bat :services:realestate-service:compileJava
```

## Files Written
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/dto/RealEstateRequest.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/dto/RealEstateResponse.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/service/RealEstateService.java` ✅
- `services/realestate-service/src/main/java/com/insurancemanagementsystem/realestate/controller/RealEstateController.java` ✅

## Verification

```bash
.\gradlew.bat :services:realestate-service:compileJava
```
- `compileJava` — ✅ SUCCESS
