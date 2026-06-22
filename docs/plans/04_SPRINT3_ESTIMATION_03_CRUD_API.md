# Plan: Sprint 3 — Estimation Service — Step 3: CRUD API

## Objective
Create DTOs (request/response), `EstimationService` with business logic, and `EstimationController` with REST endpoints.

## Context Files to Read First
1. **`services/customer-service/src/main/java/.../dto/CustomerRequest.java`** — Request DTO pattern (Lombok, jakarta.validation)
2. **`services/customer-service/src/main/java/.../dto/CustomerResponse.java`** — Response DTO pattern
3. **`services/customer-service/src/main/java/.../service/CustomerService.java`** — Service pattern (@Service, @Transactional, EntityNotFoundException)
4. **`services/customer-service/src/main/java/.../controller/CustomerController.java`** — Controller pattern (@RestController, ApiResponse)
5. **`common/common-web/src/main/java/.../dto/ApiResponse.java`** — Shared API response (import from `com.insurancemanagementsystem.common.web.dto.ApiResponse`)
6. **`common/common-web/src/main/java/.../exception/GlobalExceptionHandler.java`** — Shared exception handler (auto-scanned via `scanBasePackages`)
7. **`docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md`** — Estimation endpoints spec (section 6)
8. **`docs/outlines/10_JAVA_CONVENTIONS.md`** — Datetime convention (Instant)

## Endpoints Required

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/estimations` | Create estimation request (starts SAGA) |
| `GET` | `/api/estimations/{id}` | Get estimation by ID with status and premium |
| `GET` | `/api/estimations` | List with filters: customerId, status, date range, paginated |

## Files to Create

### 1. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationRequest.java`

```java
package com.insurancemanagementsystem.estimation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstimationRequest {
    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    private UUID vehicleId;      // optional — null for non-vehicle insurances
    private UUID realEstateId;   // optional — null for non-real-estate insurances

    @NotNull(message = "Insurance type ID is required")
    private Integer insuranceTypeId;

    @NotNull(message = "Company ID is required")
    private UUID companyId;
}
```

Validation: at least one of `vehicleId` or `realEstateId` may be null, but not both required — the service layer can determine if the insurance type requires one.

### 2. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationResponse.java`

```java
package com.insurancemanagementsystem.estimation.dto;

import com.insurancemanagementsystem.estimation.entity.Estimation;
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
public class EstimationResponse {
    private UUID id;
    private UUID sagaId;
    private UUID customerId;
    private UUID vehicleId;
    private UUID realEstateId;
    private Integer insuranceTypeId;
    private UUID companyId;
    private String status;
    private BigDecimal premium;
    private String details;
    private Instant createdAt;
    private Instant updatedAt;

    public static EstimationResponse fromEntity(Estimation estimation) {
        return EstimationResponse.builder()
                .id(estimation.getId())
                .sagaId(estimation.getSagaId())
                .customerId(estimation.getCustomerId())
                .vehicleId(estimation.getVehicleId())
                .realEstateId(estimation.getRealEstateId())
                .insuranceTypeId(estimation.getInsuranceTypeId())
                .companyId(estimation.getCompanyId())
                .status(estimation.getStatus().name())
                .premium(estimation.getPremium())
                .details(estimation.getDetails())
                .createdAt(estimation.getCreatedAt())
                .updatedAt(estimation.getUpdatedAt())
                .build();
    }
}
```

### 3. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationListRequest.java`

For the list endpoint with optional filters and pagination (use Spring `Pageable` directly in controller — this DTO is not strictly needed but can be used as a params wrapper):

Actually — just use `@RequestParam` on the controller method directly + `Pageable`. No separate DTO needed.

### 4. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`

**IMPORTANT:** This service uses `MessagePublisher` and `EstimationEventPublisher` which are created in Plan 4. For now, create the base CRUD service. The create method should NOT publish events yet — that will be added when the messaging infrastructure is wired in (Plan 4 + 5). For this step:

- `findById(UUID id)` — returns EstimationResponse, throws EntityNotFoundException
- `findAll(UUID customerId, String status, Pageable pageable)` — filtered + paginated list
- `create(EstimationRequest request)` — validates, generates sagaId, creates estimation with STARTED status, saves, returns response (event publishing added in Step 4/5)

```java
package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent;
import com.insurancemanagementsystem.estimation.dto.EstimationRequest;
import com.insurancemanagementsystem.estimation.dto.EstimationResponse;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EstimationService {

    private final EstimationRepository estimationRepository;
    // Will be injected later in Step 4/5:
    // private final MessagePublisher messagePublisher;

    @Transactional(readOnly = true)
    public EstimationResponse findById(UUID id) {
        Estimation estimation = estimationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Estimation not found with id: " + id));
        return EstimationResponse.fromEntity(estimation);
    }

    @Transactional(readOnly = true)
    public Page<EstimationResponse> findAll(UUID customerId, String status, Pageable pageable) {
        // Filter logic:
        // - If both customerId and status provided: findByCustomerIdAndStatus
        // - If only customerId: findByCustomerId
        // - If only status: findByStatus
        // - If neither: findAll
        // Use Spring Data JPA Specifications or manual filtering
        // For simplicity, use specification/query-by-example or if/else branching
        
        Page<Estimation> estimations;
        
        if (customerId != null && status != null) {
            Estimation.Status statusEnum = Estimation.Status.valueOf(status.toUpperCase());
            estimations = estimationRepository.findByCustomerIdAndStatus(customerId, statusEnum, pageable);
        } else if (customerId != null) {
            estimations = estimationRepository.findByCustomerId(customerId, pageable);
        } else if (status != null) {
            Estimation.Status statusEnum = Estimation.Status.valueOf(status.toUpperCase());
            estimations = estimationRepository.findByStatus(statusEnum, pageable);
        } else {
            estimations = estimationRepository.findAll(pageable);
        }
        
        return estimations.map(EstimationResponse::fromEntity);
    }

    @Transactional
    public EstimationResponse create(EstimationRequest request) {
        // Validate: at least one of vehicleId or realEstateId should be provided
        if (request.getVehicleId() == null && request.getRealEstateId() == null) {
            throw new IllegalArgumentException("Either vehicleId or realEstateId must be provided");
        }

        // Generate sagaId (UUID) for SAGA correlation
        UUID sagaId = UUID.randomUUID();

        // Create estimation with STARTED status
        Estimation estimation = Estimation.builder()
                .sagaId(sagaId)
                .customerId(request.getCustomerId())
                .vehicleId(request.getVehicleId())
                .realEstateId(request.getRealEstateId())
                .insuranceTypeId(request.getInsuranceTypeId())
                .companyId(request.getCompanyId())
                .status(Estimation.Status.STARTED)
                .build();

        estimation = estimationRepository.save(estimation);
        log.info("Created estimation id={} with sagaId={}", estimation.getId(), sagaId);

        // TODO: In Step 4/5 — publish EstimationRequested event to estimation.saga topic
        // EstimationRequestedEvent event = EstimationRequestedEvent.builder()
        //         .customerId(request.getCustomerId())
        //         .vehicleId(request.getVehicleId())
        //         .realEstateId(request.getRealEstateId())
        //         .insuranceTypeId(request.getInsuranceTypeId())
        //         .companyId(request.getCompanyId())
        //         .build();
        // EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());
        // messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);

        return EstimationResponse.fromEntity(estimation);
    }
}
```

### 5. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/controller/EstimationController.java`

```java
package com.insurancemanagementsystem.estimation.controller;

import com.insurancemanagementsystem.common.web.dto.ApiResponse;
import com.insurancemanagementsystem.estimation.dto.EstimationRequest;
import com.insurancemanagementsystem.estimation.dto.EstimationResponse;
import com.insurancemanagementsystem.estimation.service.EstimationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/estimations")
@RequiredArgsConstructor
public class EstimationController {

    private final EstimationService estimationService;

    @PostMapping
    public ResponseEntity<ApiResponse<EstimationResponse>> create(@Valid @RequestBody EstimationRequest request) {
        EstimationResponse created = estimationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Estimation created successfully", created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EstimationResponse>> getById(@PathVariable UUID id) {
        EstimationResponse estimation = estimationService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(estimation));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<EstimationResponse>>> getAll(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<EstimationResponse> estimations = estimationService.findAll(customerId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(estimations));
    }
}
```

### 6. Update `EstimationRepository` — Add paginated query methods

The existing `EstimationRepository` needs paginated variants. Add these methods:

```java
Page<Estimation> findByCustomerId(UUID customerId, Pageable pageable);
Page<Estimation> findByStatus(Estimation.Status status, Pageable pageable);
Page<Estimation> findByCustomerIdAndStatus(UUID customerId, Estimation.Status status, Pageable pageable);
```

Open the existing `EstimationRepository.java` and add these 3 overloads.

## Verification
```bash
.\gradlew.bat :services:estimation-service:compileJava
```

## Summary
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationRequest.java` ✅
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationResponse.java` ✅
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java` ✅
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/controller/EstimationController.java` ✅
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/EstimationRepository.java` (updated with paginated methods) ✅
