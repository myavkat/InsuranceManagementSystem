# Sub-Plan 2: Insurance Service — CRUD API

**Parent Plan:** `docs/plans/03_SPRINT2_INSURANCE_SERVICE.md`
**Checklist items:** 2.1 through 2.4
**Prerequisite:** Sub-plan 1 (Scaffold & Domain) must be COMPLETE — entities, repositories, application.yml, and database MUST exist.

---

## Context Files to Read

Before implementing, Read these files for exact patterns and existing code:
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/dto/CustomerRequest.java` — DTO pattern with Jakarta validation
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/dto/CustomerResponse.java` — response DTO with `fromEntity` factory
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/controller/CustomerController.java` — controller structure, ApiResponse wrapping
- `services/customer-service/src/main/java/com/insurancemanagementsystem/customer/service/CustomerService.java` — service layer pattern
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java` — YOUR entity (created in Sub-plan 1)
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/InsuranceCompany.java` — YOUR entity
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/InsuranceType.java` — YOUR entity
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/InsuranceRepository.java` — YOUR repository
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/InsuranceCompanyRepository.java` — YOUR repository
- `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/InsuranceTypeRepository.java` — YOUR repository
- `common/common-web/src/main/java/com/insurancemanagementsystem/common/web/dto/ApiResponse.java` — standard envelope
- `infra/sql/insurance_db/init.sql` — DDL schema reference

---

## Architecture Context

### Entities Summary

**InsuranceType** — `insurance_types` table:
- `id` (Integer, PK)
- `name` (String, unique, NOT NULL)
- No timestamps — lookup table

**InsuranceCompany** — `insurance_companies` table:
- `id` (UUID, PK, auto-generated)
- `name` (String, NOT NULL)
- `rating` (BigDecimal, precision=2 scale=1)
- `isActive` (Boolean, default TRUE)
- No timestamps

**Insurance** — `insurances` table:
- `id` (UUID, PK, auto-generated)
- `name` (String, NOT NULL)
- `description` (TEXT)
- `typeId` (Integer, FK → insurance_types.id)
- `companyId` (UUID, FK → insurance_companies.id)
- `basePremium` (BigDecimal, precision=12 scale=2)
- `isActive` (Boolean, default TRUE) — soft-delete flag
- `createdAt` (Instant, updatable=false)
- `updatedAt` (Instant)
- Read-only ManyToOne: `insuranceType`, `insuranceCompany`

### API Response Envelope

All endpoints must wrap responses in `ApiResponse<T>` from `com.insurancemanagementsystem.common.web.dto.ApiResponse`.
Key factory methods:
- `ApiResponse.success(T data)` — returns `{ success: true, message: "Operation successful", data: ..., timestamp }`
- `ApiResponse.success(String message, T data)` — custom success message
- `ApiResponse.error(String message)` — `{ success: false, message: ..., timestamp }`

---

## Required Endpoints (Story & Spec Aligned)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/insurances` | List insurance products (filterable by typeId, companyId, search) |
| GET | `/api/insurances/{id}` | Get single insurance product detail |
| POST | `/api/insurances` | Create insurance product |
| PUT | `/api/insurances/{id}` | Update insurance product |
| DELETE | `/api/insurances/{id}` | Soft-delete (set isActive=false) |
| GET | `/api/insurances/types` | List insurance types |
| GET | `/api/insurances/companies` | List insurance companies (active only) |
| POST | `/api/insurances/companies` | Create insurance company |
| PUT | `/api/insurances/companies/{id}` | Update insurance company |

---

## Step 2.1: Create DTOs

### 2.1a: InsuranceRequest

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceRequest.java`

```java
package com.insurancemanagementsystem.insurance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceRequest {

    @NotBlank(message = "Insurance name is required")
    private String name;

    private String description;

    @NotNull(message = "Insurance type ID is required")
    private Integer typeId;

    @NotNull(message = "Company ID is required")
    private UUID companyId;

    @NotNull(message = "Base premium is required")
    @DecimalMin(value = "0.01", message = "Base premium must be positive")
    private BigDecimal basePremium;

    private Boolean isActive;
}
```

### 2.1b: InsuranceResponse

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceResponse.java`

```java
package com.insurancemanagementsystem.insurance.dto;

import com.insurancemanagementsystem.insurance.entity.Insurance;
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
public class InsuranceResponse {
    private UUID id;
    private String name;
    private String description;
    private Integer typeId;
    private String typeName;
    private UUID companyId;
    private String companyName;
    private BigDecimal companyRating;
    private BigDecimal basePremium;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static InsuranceResponse fromEntity(Insurance insurance) {
        return InsuranceResponse.builder()
                .id(insurance.getId())
                .name(insurance.getName())
                .description(insurance.getDescription())
                .typeId(insurance.getTypeId())
                .typeName(insurance.getInsuranceType() != null ? insurance.getInsuranceType().getName() : null)
                .companyId(insurance.getCompanyId())
                .companyName(insurance.getInsuranceCompany() != null ? insurance.getInsuranceCompany().getName() : null)
                .companyRating(insurance.getInsuranceCompany() != null ? insurance.getInsuranceCompany().getRating() : null)
                .basePremium(insurance.getBasePremium())
                .isActive(insurance.getIsActive())
                .createdAt(insurance.getCreatedAt())
                .updatedAt(insurance.getUpdatedAt())
                .build();
    }
}
```

### 2.1c: InsuranceCompanyRequest

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceCompanyRequest.java`

```java
package com.insurancemanagementsystem.insurance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceCompanyRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    @NotNull(message = "Rating is required")
    @DecimalMin(value = "0.0", message = "Rating must be between 0.0 and 5.0")
    @DecimalMax(value = "5.0", message = "Rating must be between 0.0 and 5.0")
    private BigDecimal rating;

    private Boolean isActive;
}
```

### 2.1d: InsuranceCompanyResponse

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceCompanyResponse.java`

```java
package com.insurancemanagementsystem.insurance.dto;

import com.insurancemanagementsystem.insurance.entity.InsuranceCompany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceCompanyResponse {
    private UUID id;
    private String name;
    private BigDecimal rating;
    private Boolean isActive;

    public static InsuranceCompanyResponse fromEntity(InsuranceCompany company) {
        return InsuranceCompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .rating(company.getRating())
                .isActive(company.getIsActive())
                .build();
    }
}
```

---

## Step 2.2: Create InsuranceService

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/service/InsuranceService.java`

**Business Logic Requirements:**

### Insurance CRUD:
- `findAll(typeId, companyId, search, pageable)` — filters by typeId (optional), companyId (optional), name search (optional). Returns only active (isActive=true). Supports pagination/sorting.
- `findById(UUID id)` — returns single InsuranceResponse. Throws EntityNotFoundException if not found or isActive=false.
- `create(InsuranceRequest)` — validates typeId and companyId exist, checks duplicate name, builds entity, saves. Returns 201.
- `update(UUID id, InsuranceRequest)` — finds existing active entity, updates fields, saves. Returns 200.
- `softDelete(UUID id)` — sets isActive=false instead of hard delete.

### Insurance Company CRUD:
- `findAllCompanies(pageable)` — returns only active companies.
- `getAllCompanies()` — returns simple list (no pagination) for dropdowns.
- `findCompanyById(UUID id)` — returns single company.
- `createCompany(InsuranceCompanyRequest)` — validates no duplicate name, builds, saves.
- `updateCompany(UUID id, InsuranceCompanyRequest)` — updates name/rating/isActive.

### Insurance Types:
- `getAllTypes()` — returns List<InsuranceType> for dropdowns (no CRUD needed — seed data manages types).

### Validation Rules:
- Insurance name must be unique (case-insensitive check on create and update)
- typeId must reference an existing insurance_types.id
- companyId must reference an existing insurance_companies.id (active company)
- basePremium must be positive

**NOTE:** Event publishing (InsuranceEventPublisher) will be wired in a later step (Sub-plan 3). For now, do NOT inject an event publisher — the service only needs repositories.

```java
package com.insurancemanagementsystem.insurance.service;

import com.insurancemanagementsystem.insurance.dto.InsuranceCompanyRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceCompanyResponse;
import com.insurancemanagementsystem.insurance.dto.InsuranceRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceResponse;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import com.insurancemanagementsystem.insurance.entity.InsuranceCompany;
import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import com.insurancemanagementsystem.insurance.repository.InsuranceCompanyRepository;
import com.insurancemanagementsystem.insurance.repository.InsuranceRepository;
import com.insurancemanagementsystem.insurance.repository.InsuranceTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsuranceService {

    private final InsuranceRepository insuranceRepository;
    private final InsuranceTypeRepository insuranceTypeRepository;
    private final InsuranceCompanyRepository insuranceCompanyRepository;

    // ============================================================
    // Insurance CRUD
    // ============================================================

    @Transactional(readOnly = true)
    public Page<InsuranceResponse> findAll(Integer typeId, UUID companyId, String search, Pageable pageable) {
        boolean hasType = typeId != null;
        boolean hasCompany = companyId != null;
        boolean hasSearch = search != null && !search.isBlank();

        Page<Insurance> page;
        if (hasType && hasCompany) {
            page = insuranceRepository.findByTypeIdAndCompanyIdAndIsActiveTrue(typeId, companyId, pageable);
        } else if (hasType) {
            page = insuranceRepository.findByTypeIdAndIsActiveTrue(typeId, pageable);
        } else if (hasCompany) {
            page = insuranceRepository.findByCompanyIdAndIsActiveTrue(companyId, pageable);
        } else if (hasSearch) {
            page = insuranceRepository.searchByName(search, pageable);
        } else {
            page = insuranceRepository.findByIsActiveTrue(pageable);
        }
        return page.map(InsuranceResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public InsuranceResponse findById(UUID id) {
        Insurance insurance = insuranceRepository.findById(id)
                .filter(i -> i.getIsActive())
                .orElseThrow(() -> new EntityNotFoundException("Insurance not found with id: " + id));
        return InsuranceResponse.fromEntity(insurance);
    }

    @Transactional
    public InsuranceResponse create(InsuranceRequest request) {
        // Validate type exists
        insuranceTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Insurance type not found with id: " + request.getTypeId()));

        // Validate company exists and is active
        InsuranceCompany company = insuranceCompanyRepository.findById(request.getCompanyId())
                .filter(InsuranceCompany::getIsActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Insurance company not found or inactive: " + request.getCompanyId()));

        // Check duplicate name
        insuranceRepository.findByNameIgnoreCase(request.getName().trim())
                .ifPresent(_ -> {
                    throw new IllegalArgumentException(
                            "Insurance with name '" + request.getName() + "' already exists");
                });

        Insurance insurance = Insurance.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .typeId(request.getTypeId())
                .companyId(request.getCompanyId())
                .basePremium(request.getBasePremium())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        Insurance saved = insuranceRepository.save(insurance);
        log.info("Insurance created: id={}, name={}, typeId={}", saved.getId(), saved.getName(), saved.getTypeId());
        return InsuranceResponse.fromEntity(saved);
    }

    @Transactional
    public InsuranceResponse update(UUID id, InsuranceRequest request) {
        Insurance insurance = insuranceRepository.findById(id)
                .filter(i -> i.getIsActive())
                .orElseThrow(() -> new EntityNotFoundException("Insurance not found with id: " + id));

        // Validate type exists
        insuranceTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Insurance type not found with id: " + request.getTypeId()));

        // Validate company exists and is active
        insuranceCompanyRepository.findById(request.getCompanyId())
                .filter(InsuranceCompany::getIsActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Insurance company not found or inactive: " + request.getCompanyId()));

        // Check duplicate name (if changed)
        if (!insurance.getName().equalsIgnoreCase(request.getName().trim())) {
            insuranceRepository.findByNameIgnoreCase(request.getName().trim())
                    .ifPresent(_ -> {
                        throw new IllegalArgumentException(
                                "Insurance with name '" + request.getName() + "' already exists");
                    });
        }

        insurance.setName(request.getName().trim());
        insurance.setDescription(request.getDescription());
        insurance.setTypeId(request.getTypeId());
        insurance.setCompanyId(request.getCompanyId());
        insurance.setBasePremium(request.getBasePremium());
        insurance.setIsActive(request.getIsActive() != null ? request.getIsActive() : insurance.getIsActive());

        Insurance saved = insuranceRepository.save(insurance);
        log.info("Insurance updated: id={}, name={}", saved.getId(), saved.getName());
        return InsuranceResponse.fromEntity(saved);
    }

    @Transactional
    public InsuranceResponse softDelete(UUID id) {
        Insurance insurance = insuranceRepository.findById(id)
                .filter(i -> i.getIsActive())
                .orElseThrow(() -> new EntityNotFoundException("Insurance not found with id: " + id));

        insurance.setIsActive(false);
        Insurance saved = insuranceRepository.save(insurance);
        log.info("Insurance soft-deleted: id={}, name={}", saved.getId(), saved.getName());
        return InsuranceResponse.fromEntity(saved);
    }

    // ============================================================
    // Insurance Types
    // ============================================================

    @Transactional(readOnly = true)
    public List<InsuranceType> getAllTypes() {
        return insuranceTypeRepository.findAll();
    }

    // ============================================================
    // Insurance Companies CRUD
    // ============================================================

    @Transactional(readOnly = true)
    public Page<InsuranceCompanyResponse> findAllCompanies(Pageable pageable) {
        return insuranceCompanyRepository.findByIsActiveTrue(pageable)
                .map(InsuranceCompanyResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<InsuranceCompanyResponse> getAllCompanies() {
        return insuranceCompanyRepository.findByIsActiveTrue(Pageable.unpaged())
                .map(InsuranceCompanyResponse::fromEntity)
                .getContent();
    }

    @Transactional(readOnly = true)
    public InsuranceCompanyResponse findCompanyById(UUID id) {
        InsuranceCompany company = insuranceCompanyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Insurance company not found with id: " + id));
        return InsuranceCompanyResponse.fromEntity(company);
    }

    @Transactional
    public InsuranceCompanyResponse createCompany(InsuranceCompanyRequest request) {
        InsuranceCompany company = InsuranceCompany.builder()
                .name(request.getName().trim())
                .rating(request.getRating())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        InsuranceCompany saved = insuranceCompanyRepository.save(company);
        log.info("Insurance company created: id={}, name={}", saved.getId(), saved.getName());
        return InsuranceCompanyResponse.fromEntity(saved);
    }

    @Transactional
    public InsuranceCompanyResponse updateCompany(UUID id, InsuranceCompanyRequest request) {
        InsuranceCompany company = insuranceCompanyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Insurance company not found with id: " + id));

        company.setName(request.getName().trim());
        company.setRating(request.getRating());
        company.setIsActive(request.getIsActive() != null ? request.getIsActive() : company.getIsActive());

        InsuranceCompany saved = insuranceCompanyRepository.save(company);
        log.info("Insurance company updated: id={}, name={}", saved.getId(), saved.getName());
        return InsuranceCompanyResponse.fromEntity(saved);
    }
}
```

---

## Step 2.3: Create InsuranceController

**File to CREATE:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/controller/InsuranceController.java`

```java
package com.insurancemanagementsystem.insurance.controller;

import com.insurancemanagementsystem.common.web.dto.ApiResponse;
import com.insurancemanagementsystem.insurance.dto.InsuranceCompanyRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceCompanyResponse;
import com.insurancemanagementsystem.insurance.dto.InsuranceRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceResponse;
import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import com.insurancemanagementsystem.insurance.service.InsuranceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/insurances")
@RequiredArgsConstructor
public class InsuranceController {

    private final InsuranceService insuranceService;

    // ---------------------------------------------------------------
    // Insurance Products
    // ---------------------------------------------------------------

    @GetMapping
    public ResponseEntity<ApiResponse<Page<InsuranceResponse>>> getAll(
            @RequestParam(required = false) Integer typeId,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) String search,
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<InsuranceResponse> insurances = insuranceService.findAll(typeId, companyId, search, pageable);
        return ResponseEntity.ok(ApiResponse.success(insurances));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InsuranceResponse>> getById(@PathVariable UUID id) {
        InsuranceResponse insurance = insuranceService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(insurance));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InsuranceResponse>> create(@Valid @RequestBody InsuranceRequest request) {
        InsuranceResponse created = insuranceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Insurance created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InsuranceResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody InsuranceRequest request) {
        InsuranceResponse updated = insuranceService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Insurance updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<InsuranceResponse>> delete(@PathVariable UUID id) {
        InsuranceResponse deleted = insuranceService.softDelete(id);
        return ResponseEntity.ok(ApiResponse.success("Insurance deactivated successfully", deleted));
    }

    // ---------------------------------------------------------------
    // Insurance Types (read-only — seed data)
    // ---------------------------------------------------------------

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<InsuranceType>>> getTypes() {
        List<InsuranceType> types = insuranceService.getAllTypes();
        return ResponseEntity.ok(ApiResponse.success(types));
    }

    // ---------------------------------------------------------------
    // Insurance Companies
    // ---------------------------------------------------------------

    @GetMapping("/companies")
    public ResponseEntity<ApiResponse<Page<InsuranceCompanyResponse>>> getCompanies(
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<InsuranceCompanyResponse> companies = insuranceService.findAllCompanies(pageable);
        return ResponseEntity.ok(ApiResponse.success(companies));
    }

    @PostMapping("/companies")
    public ResponseEntity<ApiResponse<InsuranceCompanyResponse>> createCompany(
            @Valid @RequestBody InsuranceCompanyRequest request) {
        InsuranceCompanyResponse created = insuranceService.createCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Insurance company created successfully", created));
    }

    @PutMapping("/companies/{id}")
    public ResponseEntity<ApiResponse<InsuranceCompanyResponse>> updateCompany(
            @PathVariable UUID id,
            @Valid @RequestBody InsuranceCompanyRequest request) {
        InsuranceCompanyResponse updated = insuranceService.updateCompany(id, request);
        return ResponseEntity.ok(ApiResponse.success("Insurance company updated successfully", updated));
    }
}
```

---

## Step 2.4: Manual Smoke Test

After implementing, compile and start the service:

```bash
# Build
.\gradlew.bat :services:insurance-service:compileJava

# Start (requires insurance-db running on port 5436)
.\gradlew.bat :services:insurance-service:bootRun
```

Verify endpoints manually (sample curl/PowerShell):

```powershell
# 1. List insurance types
Invoke-RestMethod -Uri "http://localhost:8084/api/insurances/types" -Method Get

# 2. List insurance companies
Invoke-RestMethod -Uri "http://localhost:8084/api/insurances/companies" -Method Get

# 3. List all insurance products
Invoke-RestMethod -Uri "http://localhost:8084/api/insurances" -Method Get

# 4. Filter by type (e.g., typeId=1 = TRAFFIC)
Invoke-RestMethod -Uri "http://localhost:8084/api/insurances?typeId=1" -Method Get

# 5. Get single insurance product (replace with actual UUID from step 3)
Invoke-RestMethod -Uri "http://localhost:8084/api/insurances/<UUID>" -Method Get

# 6. Create a new insurance product
$body = @{
    name = "Test Insurance Product"
    description = "A test insurance product"
    typeId = 1
    companyId = "<COMPANY_UUID>"
    basePremium = 999.99
    isActive = $true
} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8084/api/insurances" -Method Post -Body $body -ContentType "application/json"

# 7. Update the insurance product
$body2 = @{
    name = "Updated Insurance Product"
    description = "Updated description"
    typeId = 2
    companyId = "<COMPANY_UUID>"
    basePremium = 1499.99
    isActive = $true
} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8084/api/insurances/<UUID>" -Method Put -Body $body2 -ContentType "application/json"

# 8. Soft-delete
Invoke-RestMethod -Uri "http://localhost:8084/api/insurances/<UUID>" -Method Delete

# 9. Verify deleted product returns 404
Invoke-RestMethod -Uri "http://localhost:8084/api/insurances/<UUID>" -Method Get

# 10. Create a new company
$companyBody = @{
    name = "Test Company"
    rating = 3.5
    isActive = $true
} | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8084/api/insurances/companies" -Method Post -Body $companyBody -ContentType "application/json"
```

**Expected responses:**
- GET all: 200 with wrapped ApiResponse containing paginated data
- GET by id: 200 with single insurance
- POST: 201 with created insurance
- PUT: 200 with updated insurance
- DELETE: 200 with deactivated insurance
- GET deleted: 404 with error message
- POST company: 201 with created company
- Invalid requests: 400 with validation errors
