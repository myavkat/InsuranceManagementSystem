# 01 — Remove Multi-Company Concept: common-message + insurance-service

## Status: READY FOR IMPLEMENTATION

## Objective

Delete the `InsuranceCompany` entity, table, repository, DTOs, and REST endpoints from insurance-service. Remove `companyId` from the `Insurance` entity and from all event POJOs in `common-message`. Update the SAGA consumer to look up insurance products by `typeId` only.

## Prerequisites

- Read `docs/outlines/01_SYSTEM_ARCHITECTURE.md` for tech stack
- Read `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` for service specs
- Read `docs/outlines/14_EVENT_SCHEMA_REGISTRY.md` for event schema docs (update after implementation)
- Read `AGENTS.md` for SAGA consumer rules, DB migration rules, commit conventions

## Build Order Within This Plan

```
common-message (event POJOs) → insurance-service code → infra/sql (DB init scripts) → tests
```

Always run `./gradlew :common-message:compileJava` first, then `./gradlew :insurance-service:compileJava` before running tests.

---

## PART A: common-message — Remove companyId from Event POJOs

These 5 files are in `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/`.

### A1. EstimationRequestedEvent.java

**File:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/EstimationRequestedEvent.java`

**Current code:**
```java
package com.insurancemanagementsystem.common.event.saga;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimationRequestedEvent extends BaseEvent {
    private UUID customerId;
    private UUID vehicleId;
    private UUID realEstateId;
    private Integer insuranceTypeId;
    private UUID companyId;

    @Override
    public String getEventType() {
        return EventConstants.ESTIMATION_REQUESTED;
    }
}
```

**Target state — remove the `companyId` field entirely:**
```java
package com.insurancemanagementsystem.common.event.saga;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimationRequestedEvent extends BaseEvent {
    private UUID customerId;
    private UUID vehicleId;
    private UUID realEstateId;
    private Integer insuranceTypeId;

    @Override
    public String getEventType() {
        return EventConstants.ESTIMATION_REQUESTED;
    }
}
```

### A2. PremiumCalculatedEvent.java

**File:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/PremiumCalculatedEvent.java`

**Current code:**
```java
package com.insurancemanagementsystem.common.event.saga;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremiumCalculatedEvent extends BaseEvent {
    private BigDecimal premium;
    private Map<String, BigDecimal> breakdown;
    private Integer insuranceTypeId;
    private UUID companyId;
    private UUID customerId;
    private UUID vehicleId;

    @Override
    public String getEventType() {
        return EventConstants.PREMIUM_CALCULATED;
    }
}
```

**Target state — remove `companyId` field, keep all others:**
```java
package com.insurancemanagementsystem.common.event.saga;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremiumCalculatedEvent extends BaseEvent {
    private BigDecimal premium;
    private Map<String, BigDecimal> breakdown;
    private Integer insuranceTypeId;
    private UUID customerId;
    private UUID vehicleId;

    @Override
    public String getEventType() {
        return EventConstants.PREMIUM_CALCULATED;
    }
}
```

### A3. InsuranceCreatedEvent.java

**File:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/InsuranceCreatedEvent.java`

**Current code:**
```java
package com.insurancemanagementsystem.common.event.domain;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceCreatedEvent extends BaseEvent {
    private UUID insuranceId;
    private Integer typeId;
    private UUID companyId;
    private String name;

    @Override
    public String getEventType() {
        return EventConstants.INSURANCE_CREATED;
    }
}
```

**Target state — remove `companyId`:**
```java
package com.insurancemanagementsystem.common.event.domain;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceCreatedEvent extends BaseEvent {
    private UUID insuranceId;
    private Integer typeId;
    private String name;

    @Override
    public String getEventType() {
        return EventConstants.INSURANCE_CREATED;
    }
}
```

### A4. InsuranceUpdatedEvent.java

**File:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/InsuranceUpdatedEvent.java`

**Current code (same structure as InsuranceCreatedEvent):**
```java
@EqualsAndHashCode(callSuper = true)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InsuranceUpdatedEvent extends BaseEvent {
    private UUID insuranceId;
    private Integer typeId;
    private UUID companyId;     // <-- REMOVE THIS LINE
    private String name;

    @Override
    public String getEventType() { return EventConstants.INSURANCE_UPDATED; }
}
```

**Target state:** Remove the `private UUID companyId;` line only. Fields become: `insuranceId`, `typeId`, `name`.

### A5. InsuranceDeletedEvent.java

**File:** `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/InsuranceDeletedEvent.java`

**Current code:**
```java
@EqualsAndHashCode(callSuper = true)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InsuranceDeletedEvent extends BaseEvent {
    private UUID insuranceId;
    private Integer typeId;
    private UUID companyId;     // <-- REMOVE THIS LINE

    @Override
    public String getEventType() { return EventConstants.INSURANCE_DELETED; }
}
```

**Target state:** Remove the `private UUID companyId;` line. Fields become: `insuranceId`, `typeId`.

---

## PART B: insurance-service — Delete InsuranceCompany Files

Delete these 4 files entirely (they will no longer compile once the entity is gone):

| # | File (absolute path) | Action |
|---|------|--------|
| 1 | `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/InsuranceCompany.java` | DELETE |
| 2 | `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/InsuranceCompanyRepository.java` | DELETE |
| 3 | `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceCompanyRequest.java` | DELETE |
| 4 | `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceCompanyResponse.java` | DELETE |

---

## PART C: insurance-service — Modify Existing Files

### C1. Entity: Insurance.java

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/entity/Insurance.java`

Remove the `companyId` column and the `insuranceCompany` relationship. The entity loses lines 34–35 (companyId field) and lines 67–69 (insuranceCompany ManyToOne).

**Current code (full):**
```java
package com.insurancemanagementsystem.insurance.entity;

import jakarta.persistence.*;
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
@Entity
@Table(name = "insurances")
public class Insurance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "type_id", nullable = false)
    private Integer typeId;

    @Column(name = "company_id", nullable = false)       // <-- DELETE
    private UUID companyId;                               // <-- DELETE

    @Column(name = "base_premium", precision = 12, scale = 2)
    private BigDecimal basePremium;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Convenience relationship mappings (read-only, no cascading)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", insertable = false, updatable = false)
    private InsuranceType insuranceType;

    @ManyToOne(fetch = FetchType.LAZY)                    // <-- DELETE
    @JoinColumn(name = "company_id", insertable = false, updatable = false)  // <-- DELETE
    private InsuranceCompany insuranceCompany;            // <-- DELETE
}
```

**Target state (after removals):**
```java
package com.insurancemanagementsystem.insurance.entity;

import jakarta.persistence.*;
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
@Entity
@Table(name = "insurances")
public class Insurance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "type_id", nullable = false)
    private Integer typeId;

    @Column(name = "base_premium", precision = 12, scale = 2)
    private BigDecimal basePremium;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", insertable = false, updatable = false)
    private InsuranceType insuranceType;
}
```

### C2. DTO: InsuranceRequest.java

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceRequest.java`

Remove the `companyId` field and its validation annotation.

**Current code:**
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

    @NotNull(message = "Company ID is required")       // <-- DELETE
    private UUID companyId;                              // <-- DELETE

    @NotNull(message = "Base premium is required")
    @DecimalMin(value = "0.01", message = "Base premium must be positive")
    private BigDecimal basePremium;

    private Boolean isActive;
}
```

**Target state:**
```java
package com.insurancemanagementsystem.insurance.dto;

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
public class InsuranceRequest {

    @NotBlank(message = "Insurance name is required")
    private String name;

    private String description;

    @NotNull(message = "Insurance type ID is required")
    private Integer typeId;

    @NotNull(message = "Base premium is required")
    @DecimalMin(value = "0.01", message = "Base premium must be positive")
    private BigDecimal basePremium;

    private Boolean isActive;
}
```

Note: the `UUID` import also becomes unused — remove it.

### C3. DTO: InsuranceResponse.java

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/dto/InsuranceResponse.java`

Remove `companyId`, `companyName`, `companyRating` fields. Remove the lazy-load mapping for company from `fromEntity()`. Remove the `InsuranceCompany` import.

**Current code:**
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
    private UUID companyId;                // <-- DELETE
    private String companyName;            // <-- DELETE
    private BigDecimal companyRating;      // <-- DELETE
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
                .companyId(insurance.getCompanyId())                                                    // <-- DELETE
                .companyName(insurance.getInsuranceCompany() != null ? insurance.getInsuranceCompany().getName() : null)  // <-- DELETE
                .companyRating(insurance.getInsuranceCompany() != null ? insurance.getInsuranceCompany().getRating() : null) // <-- DELETE
                .basePremium(insurance.getBasePremium())
                .isActive(insurance.getIsActive())
                .createdAt(insurance.getCreatedAt())
                .updatedAt(insurance.getUpdatedAt())
                .build();
    }
}
```

**Target state:**
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
                .basePremium(insurance.getBasePremium())
                .isActive(insurance.getIsActive())
                .createdAt(insurance.getCreatedAt())
                .updatedAt(insurance.getUpdatedAt())
                .build();
    }
}
```

### C4. Repository: InsuranceRepository.java

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/repository/InsuranceRepository.java`

Remove the two company-related query methods.

**Current code:**
```java
@Repository
public interface InsuranceRepository extends JpaRepository<Insurance, UUID> {

    Page<Insurance> findByIsActiveTrue(Pageable pageable);
    Page<Insurance> findByTypeIdAndIsActiveTrue(Integer typeId, Pageable pageable);

    Page<Insurance> findByCompanyIdAndIsActiveTrue(UUID companyId, Pageable pageable);                          // <-- DELETE
    Page<Insurance> findByTypeIdAndCompanyIdAndIsActiveTrue(Integer typeId, UUID companyId, Pageable pageable); // <-- DELETE

    @Query("SELECT i FROM Insurance i WHERE i.isActive = true AND LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Insurance> searchByName(@Param("search") String search, Pageable pageable);

    Optional<Insurance> findByNameIgnoreCase(String name);
}
```

**Target state:**
```java
@Repository
public interface InsuranceRepository extends JpaRepository<Insurance, UUID> {

    Page<Insurance> findByIsActiveTrue(Pageable pageable);
    Page<Insurance> findByTypeIdAndIsActiveTrue(Integer typeId, Pageable pageable);

    @Query("SELECT i FROM Insurance i WHERE i.isActive = true AND LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Insurance> searchByName(@Param("search") String search, Pageable pageable);

    Optional<Insurance> findByNameIgnoreCase(String name);
}
```

Remove the unused `UUID` import after deleting company methods.

### C5. Controller: InsuranceController.java

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/controller/InsuranceController.java`

Three changes:
1. Remove `companyId` query param from `getAll()` method
2. Remove the entire "Insurance Companies" section (lines 83–108 in original): `getCompanies()`, `createCompany()`, `updateCompany()`
3. Remove unused imports: `InsuranceCompanyRequest`, `InsuranceCompanyResponse`

**Current `getAll()` signature:**
```java
@GetMapping
public ResponseEntity<ApiResponse<Page<InsuranceResponse>>> getAll(
        @RequestParam(required = false) Integer typeId,
        @RequestParam(required = false) UUID companyId,            // <-- DELETE
        @RequestParam(required = false) String search,
        @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
    Page<InsuranceResponse> insurances = insuranceService.findAll(typeId, companyId, search, pageable);
    return ResponseEntity.ok(ApiResponse.success(insurances));
}
```

**Target `getAll()` signature:**
```java
@GetMapping
public ResponseEntity<ApiResponse<Page<InsuranceResponse>>> getAll(
        @RequestParam(required = false) Integer typeId,
        @RequestParam(required = false) String search,
        @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
    Page<InsuranceResponse> insurances = insuranceService.findAll(typeId, search, pageable);
    return ResponseEntity.ok(ApiResponse.success(insurances));
}
```

**Delete these three endpoint methods entirely (83–107 in original file):**
```java
// DELETE ENTIRE SECTION:
// ---------------------------------------------------------------
// Insurance Companies
// ---------------------------------------------------------------

@GetMapping("/companies")
public ResponseEntity<ApiResponse<Page<InsuranceCompanyResponse>>> getCompanies(
        @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) { ... }

@PostMapping("/companies")
public ResponseEntity<ApiResponse<InsuranceCompanyResponse>> createCompany(
        @Valid @RequestBody InsuranceCompanyRequest request) { ... }

@PutMapping("/companies/{id}")
public ResponseEntity<ApiResponse<InsuranceCompanyResponse>> updateCompany(
        @PathVariable UUID id,
        @Valid @RequestBody InsuranceCompanyRequest request) { ... }
```

Also remove the imports for `InsuranceCompanyRequest` and `InsuranceCompanyResponse`.

### C6. Service: InsuranceService.java

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/service/InsuranceService.java`

Major changes:
1. Remove `InsuranceCompanyRepository` field and import
2. Remove `InsuranceCompany` entity import
3. Remove `InsuranceCompanyRequest`/`InsuranceCompanyResponse` DTO imports
4. Simplify `findAll()` — no company filter parameter
5. Remove company validation from `create()` method
6. Remove company validation from `update()` method
7. Remove `setCompanyId()` calls in create/update
8. Delete entire "Insurance Companies CRUD" section: `findAllCompanies()`, `getAllCompanies()`, `findCompanyById()`, `createCompany()`, `updateCompany()`

**Current `findAll()` signature:**
```java
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
```

**Target `findAll()`:**
```java
public Page<InsuranceResponse> findAll(Integer typeId, String search, Pageable pageable) {
    boolean hasType = typeId != null;
    boolean hasSearch = search != null && !search.isBlank();

    Page<Insurance> page;
    if (hasType) {
        page = insuranceRepository.findByTypeIdAndIsActiveTrue(typeId, pageable);
    } else if (hasSearch) {
        page = insuranceRepository.searchByName(search, pageable);
    } else {
        page = insuranceRepository.findByIsActiveTrue(pageable);
    }
    return page.map(InsuranceResponse::fromEntity);
}
```

**Current `create()` method (lines 68–101):**
```java
@Transactional
public InsuranceResponse create(InsuranceRequest request) {
    insuranceTypeRepository.findById(request.getTypeId())
            .orElseThrow(() -> new IllegalArgumentException(
                    "Insurance type not found with id: " + request.getTypeId()));

    // DELETE THIS BLOCK (lines 76-79):
    InsuranceCompany company = insuranceCompanyRepository.findById(request.getCompanyId())
            .filter(InsuranceCompany::getIsActive)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Insurance company not found or inactive: " + request.getCompanyId()));

    insuranceRepository.findByNameIgnoreCase(request.getName().trim())
            .ifPresent(_ -> {
                throw new IllegalArgumentException(
                        "Insurance with name '" + request.getName() + "' already exists");
            });

    Insurance insurance = Insurance.builder()
            .name(request.getName().trim())
            .description(request.getDescription())
            .typeId(request.getTypeId())
            .companyId(request.getCompanyId())   // <-- DELETE
            .basePremium(request.getBasePremium())
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .build();

    Insurance saved = insuranceRepository.save(insurance);
    log.info("Insurance created: id={}, name={}, typeId={}", saved.getId(), saved.getName(), saved.getTypeId());
    insuranceEventPublisher.publishInsuranceCreated(saved);
    return InsuranceResponse.fromEntity(saved);
}
```

**Target `create()` method:**
```java
@Transactional
public InsuranceResponse create(InsuranceRequest request) {
    insuranceTypeRepository.findById(request.getTypeId())
            .orElseThrow(() -> new IllegalArgumentException(
                    "Insurance type not found with id: " + request.getTypeId()));

    insuranceRepository.findByNameIgnoreCase(request.getName().trim())
            .ifPresent(_ -> {
                throw new IllegalArgumentException(
                        "Insurance with name '" + request.getName() + "' already exists");
            });

    Insurance insurance = Insurance.builder()
            .name(request.getName().trim())
            .description(request.getDescription())
            .typeId(request.getTypeId())
            .basePremium(request.getBasePremium())
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .build();

    Insurance saved = insuranceRepository.save(insurance);
    log.info("Insurance created: id={}, name={}, typeId={}", saved.getId(), saved.getName(), saved.getTypeId());
    insuranceEventPublisher.publishInsuranceCreated(saved);
    return InsuranceResponse.fromEntity(saved);
}
```

**Current `update()` method — delete the company validation block (lines 114–118):**
```java
// DELETE:
insuranceCompanyRepository.findById(request.getCompanyId())
        .filter(InsuranceCompany::getIsActive)
        .orElseThrow(() -> new IllegalArgumentException(
                "Insurance company not found or inactive: " + request.getCompanyId()));
```

And remove line 132:
```java
insurance.setCompanyId(request.getCompanyId());   // <-- DELETE
```

**Delete entire "Insurance Companies CRUD" section (lines 164–213):**
```java
// DELETE:
// ============================================================
// Insurance Companies CRUD
// ============================================================

@Transactional(readOnly = true)
public Page<InsuranceCompanyResponse> findAllCompanies(Pageable pageable) { ... }

@Transactional(readOnly = true)
public List<InsuranceCompanyResponse> getAllCompanies() { ... }

@Transactional(readOnly = true)
public InsuranceCompanyResponse findCompanyById(UUID id) { ... }

@Transactional
public InsuranceCompanyResponse createCompany(InsuranceCompanyRequest request) { ... }

@Transactional
public InsuranceCompanyResponse updateCompany(UUID id, InsuranceCompanyRequest request) { ... }
```

Also remove the `insuranceCompanyRepository` field declaration (line 32):
```java
private final InsuranceCompanyRepository insuranceCompanyRepository;  // <-- DELETE
```

### C7. Config: InsuranceEventPublisher.java

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceEventPublisher.java`

Remove `.companyId(...)` from all three event builders.

**Current `publishInsuranceCreated()`:**
```java
public void publishInsuranceCreated(Insurance insurance) {
    InsuranceCreatedEvent event = InsuranceCreatedEvent.builder()
            .insuranceId(insurance.getId())
            .typeId(insurance.getTypeId())
            .companyId(insurance.getCompanyId())   // <-- DELETE
            .name(insurance.getName())
            .build();
    ...
}
```

**Target:**
```java
public void publishInsuranceCreated(Insurance insurance) {
    InsuranceCreatedEvent event = InsuranceCreatedEvent.builder()
            .insuranceId(insurance.getId())
            .typeId(insurance.getTypeId())
            .name(insurance.getName())
            .build();
    ...
}
```

Same pattern for `publishInsuranceUpdated()` and `publishInsuranceDeleted()` — remove `.companyId(insurance.getCompanyId())` from both.

### C8. Config: InsuranceSagaConsumer.java

**File:** `services/insurance-service/src/main/java/com/insurancemanagementsystem/insurance/config/InsuranceSagaConsumer.java`

This is the most critical change. The `calculatePremium()` method currently:
1. Extracts `companyId` from `EstimationRequestedEvent` (line 201)
2. Looks up `Insurance` by `typeId + companyId` (line 213)
3. Sets `companyId` in `PremiumCalculatedEvent` (line 246)
4. Logs `companyId` (line 253)

All of these need updating.

**Current `calculatePremium()` method (lines 188–255):**
```java
private void calculatePremium(UUID sagaId, UUID traceId) {
    SagaAggregationStore.SagaState state = aggregationStore.retrieve(sagaId.toString());
    if (state == null) {
        log.warn("SAGA state not found for sagaId={} — already consumed?", sagaId);
        return;
    }

    EstimationRequestedEvent estimationEvent = jsonMapper.convertValue(
            state.getEstimationRequest().getPayload(), EstimationRequestedEvent.class);
    UUID customerId = estimationEvent.getCustomerId();
    UUID vehicleId = estimationEvent.getVehicleId();
    Integer insuranceTypeId = estimationEvent.getInsuranceTypeId();
    UUID companyId = estimationEvent.getCompanyId();                            // <-- DELETE

    CustomerValidatedEvent customerEvent = jsonMapper.convertValue(
            state.getCustomerValidated().getPayload(), CustomerValidatedEvent.class);
    VehicleValidatedEvent vehicleEvent = jsonMapper.convertValue(
            state.getVehicleValidated().getPayload(), VehicleValidatedEvent.class);

    Optional<Insurance> insuranceOpt = insuranceRepository
            .findByTypeIdAndCompanyIdAndIsActiveTrue(insuranceTypeId, companyId, Pageable.unpaged())  // <-- CHANGE
            .stream().findFirst();

    if (insuranceOpt.isEmpty()) {
        publishCalculationFailed(sagaId, traceId,
                "No active insurance found for typeId=" + insuranceTypeId + ", companyId=" + companyId);  // <-- CHANGE
        return;
    }

    Insurance insurance = insuranceOpt.get();
    BigDecimal basePremium = insurance.getBasePremium();
    if (basePremium == null) {
        publishCalculationFailed(sagaId, traceId, "Insurance has no base premium defined");
        return;
    }

    BigDecimal riskFactor = BigDecimal.ONE;
    Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
    breakdown.put("basePremium", basePremium);
    BigDecimal measuredAdjustment = BigDecimal.ZERO;
    breakdown.put("riskFactor", riskFactor);
    breakdown.put("adjustment", measuredAdjustment);
    BigDecimal totalPremium = basePremium.multiply(riskFactor).add(measuredAdjustment);

    PremiumCalculatedEvent premiumEvent = PremiumCalculatedEvent.builder()
            .premium(totalPremium)
            .breakdown(breakdown)
            .insuranceTypeId(insuranceTypeId)
            .companyId(companyId)                 // <-- DELETE
            .customerId(customerId)
            .vehicleId(vehicleId)
            .build();

    EventEnvelope outcome = premiumEvent.toEnvelope(sagaId, traceId);
    outboxEventRepository.save(buildOutboxEvent(sagaId, outcome, EventConstants.ESTIMATION_SAGA));
    log.info("Premium calculated for sagaId={}: premium={}, typeId={}, companyId={}",
            sagaId, totalPremium, insuranceTypeId, companyId);  // <-- CHANGE
}
```

**Target `calculatePremium()` method:**
```java
private void calculatePremium(UUID sagaId, UUID traceId) {
    SagaAggregationStore.SagaState state = aggregationStore.retrieve(sagaId.toString());
    if (state == null) {
        log.warn("SAGA state not found for sagaId={} — already consumed?", sagaId);
        return;
    }

    EstimationRequestedEvent estimationEvent = jsonMapper.convertValue(
            state.getEstimationRequest().getPayload(), EstimationRequestedEvent.class);
    UUID customerId = estimationEvent.getCustomerId();
    UUID vehicleId = estimationEvent.getVehicleId();
    Integer insuranceTypeId = estimationEvent.getInsuranceTypeId();

    CustomerValidatedEvent customerEvent = jsonMapper.convertValue(
            state.getCustomerValidated().getPayload(), CustomerValidatedEvent.class);
    VehicleValidatedEvent vehicleEvent = jsonMapper.convertValue(
            state.getVehicleValidated().getPayload(), VehicleValidatedEvent.class);

    // Look up insurance by typeId only — single provider system
    Optional<Insurance> insuranceOpt = insuranceRepository
            .findByTypeIdAndIsActiveTrue(insuranceTypeId, Pageable.unpaged())
            .stream().findFirst();

    if (insuranceOpt.isEmpty()) {
        publishCalculationFailed(sagaId, traceId,
                "No active insurance found for typeId=" + insuranceTypeId);
        return;
    }

    Insurance insurance = insuranceOpt.get();
    BigDecimal basePremium = insurance.getBasePremium();
    if (basePremium == null) {
        publishCalculationFailed(sagaId, traceId, "Insurance has no base premium defined");
        return;
    }

    BigDecimal riskFactor = BigDecimal.ONE;
    Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
    breakdown.put("basePremium", basePremium);
    BigDecimal measuredAdjustment = BigDecimal.ZERO;
    breakdown.put("riskFactor", riskFactor);
    breakdown.put("adjustment", measuredAdjustment);
    BigDecimal totalPremium = basePremium.multiply(riskFactor).add(measuredAdjustment);

    PremiumCalculatedEvent premiumEvent = PremiumCalculatedEvent.builder()
            .premium(totalPremium)
            .breakdown(breakdown)
            .insuranceTypeId(insuranceTypeId)
            .customerId(customerId)
            .vehicleId(vehicleId)
            .build();

    EventEnvelope outcome = premiumEvent.toEnvelope(sagaId, traceId);
    outboxEventRepository.save(buildOutboxEvent(sagaId, outcome, EventConstants.ESTIMATION_SAGA));
    log.info("Premium calculated for sagaId={}: premium={}, typeId={}",
            sagaId, totalPremium, insuranceTypeId);
}
```

---

## PART D: Database — Update SQL Init Script

**File:** `infra/sql/insurance_db/init.sql`

### D1. Drop the `insurance_companies` table

Delete lines 8–13:
```sql
-- DELETE:
CREATE TABLE IF NOT EXISTS insurance_companies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    rating DECIMAL(2,1),
    is_active BOOLEAN DEFAULT TRUE
);
```

### D2. Remove FK and column from `insurances` table

Change the `insurances` DDL — remove the `company_id` column and its FK reference, plus the index:

**Current (lines 15–28):**
```sql
CREATE TABLE IF NOT EXISTS insurances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type_id INT NOT NULL REFERENCES insurance_types(id),
    company_id UUID NOT NULL REFERENCES insurance_companies(id),   -- DELETE
    base_premium DECIMAL(12,2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_insurances_type ON insurances(type_id);
CREATE INDEX IF NOT EXISTS idx_insurances_company ON insurances(company_id);  -- DELETE
```

**Target:**
```sql
CREATE TABLE IF NOT EXISTS insurances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type_id INT NOT NULL REFERENCES insurance_types(id),
    base_premium DECIMAL(12,2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_insurances_type ON insurances(type_id);
```

### D3. Add ALTER TABLE migration for existing databases

Per AGENTS.md DB schema rules: "Schema DDL must include migration path." After the `CREATE TABLE IF NOT EXISTS` block, add:

```sql
-- Migration: remove company_id from insurances (multi-company concept removed)
ALTER TABLE insurances DROP COLUMN IF EXISTS company_id;
DROP INDEX IF EXISTS idx_insurances_company;
```

### D4. Delete seed data for companies (lines 36–40)

```sql
-- DELETE all of:
INSERT INTO insurance_companies (id, name, rating, is_active) VALUES 
(uuid_generate_v4(), 'Anadolu Sigorta', 4.5, TRUE), ...;
```

### D5. Rewrite seed data for insurances (lines 42–58)

The current seed data uses `SELECT ... FROM insurance_companies WHERE name = ...` to get company IDs. Since the `insurance_companies` table is gone, the seed data must use direct UUIDs or omit the company dimension entirely. Since we removed `company_id`, just insert directly:

**Replace lines 42–58 with:**
```sql
-- Seed insurance products (single-provider system)
INSERT INTO insurances (name, description, type_id, base_premium, is_active) VALUES
('Zorunlu Trafik Sigortası', 'Legal required traffic insurance', 1, 1250.00, TRUE),
('Kapsamlı Kasko', 'Full comprehensive insurance', 2, 3500.00, TRUE),
('Doğal Afet Sigortası (DASK)', 'Earthquake insurance', 3, 450.00, TRUE),
('Tamamlayıcı Sağlık Sigortası', 'Complementary health insurance', 4, 2800.00, TRUE),
('Hayat Sigortası', 'Life insurance', 5, 1500.00, TRUE);
```

Note: Previously there were duplicate product names differentiated by company (e.g., "Zorunlu Trafik Sigortası" from both Anadolu Sigorta and Ak Sigorta). Now there is one product per type since the system is a single provider. The `name` field still has a uniqueness constraint (checked in service layer via `findByNameIgnoreCase`), so we cannot have duplicate names. We keep one representative product per type.

### D6. Also drop the `insurance_companies` table itself via migration

```sql
-- Migration: drop insurance_companies table (multi-company concept removed)
DROP TABLE IF EXISTS insurance_companies;
```

Add this after the `CREATE TABLE IF NOT EXISTS` block for `insurance_companies` (or better, replace the `CREATE TABLE IF NOT EXISTS insurance_companies` block entirely with the `DROP TABLE IF EXISTS`).

**Full target `infra/sql/insurance_db/init.sql`:**
```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS insurance_types (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS insurances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type_id INT NOT NULL REFERENCES insurance_types(id),
    base_premium DECIMAL(12,2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_insurances_type ON insurances(type_id);

-- Seed insurance types
INSERT INTO insurance_types (id, name) VALUES 
(1, 'TRAFFIC'), (2, 'CASCO'), (3, 'DASK'), (4, 'HEALTH'), (5, 'LIFE')
ON CONFLICT (id) DO NOTHING;

-- Seed insurance products (single-provider system — one product per type)
INSERT INTO insurances (name, description, type_id, base_premium, is_active) VALUES
('Zorunlu Trafik Sigortası', 'Legal required traffic insurance', 1, 1250.00, TRUE),
('Kapsamlı Kasko', 'Full comprehensive insurance', 2, 3500.00, TRUE),
('Doğal Afet Sigortası (DASK)', 'Earthquake insurance', 3, 450.00, TRUE),
('Tamamlayıcı Sağlık Sigortası', 'Complementary health insurance', 4, 2800.00, TRUE),
('Hayat Sigortası', 'Life insurance', 5, 1500.00, TRUE);

-- Migration: drop insurance_companies table and remove company_id FK (multi-company concept removed)
ALTER TABLE insurances DROP COLUMN IF EXISTS company_id;
DROP INDEX IF EXISTS idx_insurances_company;
DROP TABLE IF EXISTS insurance_companies;

CREATE TABLE IF NOT EXISTS saga_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(saga_id, event_type)
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID,
    topic VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PUBLISHING','PUBLISHED','FAILED')),
    retry_count INT DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events(status, created_at);

CREATE TABLE IF NOT EXISTS saga_aggregations (
    saga_id UUID PRIMARY KEY,
    estimation_request_payload JSONB,
    customer_validated_payload JSONB,
    vehicle_validated_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## PART E: Test Files

### E1. InsuranceServiceTest.java

**File:** `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/service/InsuranceServiceTest.java`

Changes:
- Remove `InsuranceCompany` import, `InsuranceCompanyRepository` mock field, `TEST_COMPANY_ID`, `TEST_COMPANY_NAME`, `TEST_COMPANY_RATING` constants
- Remove `request.setCompanyId(...)` calls
- Remove `createActiveCompany()` helper method
- Remove `when(insuranceCompanyRepository.findById(...))` stubs
- Remove assertions on `response.getCompanyId()`, `response.getCompanyName()`, `response.getCompanyRating()`
- Remove `verify(insuranceCompanyRepository)` calls
- Remove company CRUD test methods

Remove the following lines/patterns:
```
Line 7:  import ...entity.InsuranceCompany;                                    → DELETE
Line 9:  import ...repository.InsuranceCompanyRepository;                      → DELETE
Line 46: private InsuranceCompanyRepository insuranceCompanyRepository;         → DELETE
Line 58: private static final UUID TEST_COMPANY_ID = UUID.randomUUID();        → DELETE
Line 64: private static final String TEST_COMPANY_NAME = "Insurance Corp";     → DELETE
Line 65: private static final BigDecimal TEST_COMPANY_RATING = new BigDecimal("4.5"); → DELETE
Line 76: request.setCompanyId(TEST_COMPANY_ID);                                → DELETE
Line 88: .companyId(TEST_COMPANY_ID)                                           → DELETE
Lines 100-107: createActiveCompany() method                                    → DELETE
Line 117: InsuranceCompany company = createActiveCompany();                    → DELETE
Line 121: when(insuranceCompanyRepository.findById(...))                       → DELETE
Line 134: assertThat(response.getCompanyId()).isEqualTo(TEST_COMPANY_ID);      → DELETE
Line 139: verify(insuranceCompanyRepository).findById(...)                     → DELETE
Line 155: when(insuranceCompanyRepository.findById(...))                       → DELETE
Line 164: verify(insuranceCompanyRepository).findById(...)                     → DELETE
Line 186: verify(insuranceCompanyRepository, never()).findById(...)            → DELETE
Line 210: assertThat(response.getCompanyId()).isEqualTo(TEST_COMPANY_ID);      → DELETE
Line 295: when(insuranceCompanyRepository.findById(...))                       → DELETE
Line 305: verify(insuranceCompanyRepository).findById(...)                     → DELETE
Line 313: assertThat(updatedInsurance.getCompanyId()).isEqualTo(TEST_COMPANY_ID); → DELETE
Line 335: when(insuranceCompanyRepository.findById(...))                       → DELETE
Line 345: verify(insuranceCompanyRepository).findById(...)                     → DELETE
```

Also remove any test methods that test company CRUD (findAllCompanies, createCompany, updateCompany, etc.).

### E2. InsuranceControllerTest.java

**File:** `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/controller/InsuranceControllerTest.java`

Changes:
- Remove `InsuranceCompanyRequest`, `InsuranceCompanyResponse` imports
- Remove `companyId` field, `.companyId(...)`, `.companyName(...)`, `.companyRating(...)` from test response builders
- Remove `createSampleCompanyResponse()` helper method
- Remove `jsonPath("$.data.companyName")` assertion
- Remove `request.setCompanyId(companyId)` calls
- Remove test methods: `getCompanies_ReturnsPage`, `createCompany_WithValidBody_Returns201`, `updateCompany_Returns200`

### E3. InsuranceServiceApplicationTests.java (Integration test)

**File:** `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/InsuranceServiceApplicationTests.java`

Changes:
- Remove `InsuranceCompanyRequest` and `InsuranceCompany` imports
- Remove `InsuranceCompanyRepository` field
- Remove `testCompanyId` field
- Remove the company seed block in setup
- Remove `.companyId(testCompanyId)` from request builders
- Remove `createValidCompanyRequest()` helper method
- Remove `createCompany_thenListCompanies()` test method

### E4. InsuranceSagaConsumerTest.java

**File:** `services/insurance-service/src/test/java/com/insurancemanagementsystem/insurance/saga/InsuranceSagaConsumerTest.java`

Changes:
- Remove `InsuranceCompany` import, `InsuranceCompanyRepository` mock field
- Remove `companyId` field
- Remove company seed block in setup (`insuranceCompanyRepository.deleteAll()`, `InsuranceCompany company = insuranceCompanyRepository.save(...)`)
- Remove `.companyId(companyId)` from all `EstimationRequestedEvent` builders
- Remove `assertThat(payload.getCompanyId()).isEqualTo(companyId)` assertions
- Remove `.companyId(companyId)` from all `PremiumCalculatedEvent` builders

The test should still work because it builds `EstimationRequestedEvent` without `companyId` — the event class no longer has that field after Part A changes.

### E5. EventSerializationTest.java (common-message)

**File:** `common/common-message/src/test/java/com/insurancemanagementsystem/common/event/EventSerializationTest.java`

This test file builds event objects with `.companyId(UUID.randomUUID())`. Remove all 6 occurrences:

```
Line 38:  .companyId(UUID.randomUUID())  — in shouldSerializeAndDeserializeEstimationRequestedEvent
Line 114: .companyId(UUID.randomUUID())  — in shouldSerializeAndDeserializePremiumCalculatedEvent
Line 254: .companyId(UUID.randomUUID())  — in shouldSerializeAndDeserializeInsuranceCreatedEvent (line 254)
Line 266: .companyId(UUID.randomUUID())  — in shouldSerializeAndDeserializeInsuranceUpdatedEvent (line 266)
Line 278: .companyId(UUID.randomUUID())  — in shouldSerializeAndDeserializeInsuranceDeletedEvent (line 278)
Line 342: .companyId(UUID.randomUUID())  — in shouldHandleNullOptionalFields (line 342)
```

Remove each `.companyId(UUID.randomUUID())` line. The builders will still compile because the event classes no longer have that field.

Verify: `./gradlew :common-message:test`

---

## OPEN QUESTION

**Q: After removing company, each insurance type can have only one product.** The current `findByNameIgnoreCase()` uniqueness check in `InsuranceService.create()` still enforces unique names. But what about type uniqueness? Currently you can have two "Zorunlu Trafik Sigortası" products for different companies. After this change, there can be only one per type. Should we add a uniqueness check on `typeId` in the service layer?

**Recommendation:** Add `insuranceRepository.findByTypeIdAndIsActiveTrue(...)` check in `create()` — if any active product already exists for that type, reject the create. This prevents accidentally creating duplicate type products in a single-provider system.

---

## Acceptance Criteria

- [ ] All 5 common-message event classes compile without `companyId`
- [ ] `EventSerializationTest.java` in common-message has no `.companyId(...)` builder calls — `./gradlew :common-message:test` passes
- [ ] 4 insurance-service files are deleted: `InsuranceCompany.java`, `InsuranceCompanyRequest.java`, `InsuranceCompanyResponse.java`, `InsuranceCompanyRepository.java`
- [ ] `Insurance.java` has no `companyId` field or `insuranceCompany` relationship
- [ ] `InsuranceRequest.java` and `InsuranceResponse.java` have no company-related fields
- [ ] `InsuranceRepository.java` has no company-related query methods
- [ ] `InsuranceController.java` has no `companyId` query param and no `/companies` endpoints
- [ ] `InsuranceService.java` has no company validation, no `insuranceCompanyRepository` field, no company CRUD methods
- [ ] `InsuranceSagaConsumer.java` looks up insurance by `typeId` only
- [ ] `InsuranceEventPublisher.java` publishes events without `companyId`
- [ ] `infra/sql/insurance_db/init.sql` has no `insurance_companies` table, no `company_id` column, no company seed data
- [ ] All test files compile and pass: `./gradlew :common-message:test :insurance-service:test`
- [ ] `grep -ri "companyid\|company_id\|insurancecompany\|insurance_company" services/insurance-service/src/` returns zero results
- [ ] `grep -ri "companyid\|company_id\|insurancecompany\|insurance_company" common/common-message/src/` returns zero results
