# Plan: Sprint 4 — Vehicle & RealEstate — Step 3: Vehicle Service API Layer

## Objective
Create DTOs (VehicleRequest, VehicleResponse), VehicleService with full CRUD + reference data lookups, and VehicleController with all 11 REST endpoints.

## Context Files to Read First
1. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/controller/CustomerController.java`** — Controller pattern (ResponseEntity<ApiResponse<T>>, @PageableDefault, @Valid)
2. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/service/CustomerService.java`** — Service pattern (@Transactional, EntityNotFoundException, IllegalArgumentException)
3. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/dto/CustomerRequest.java`** — Request DTO pattern with validation annotations
4. **`services/customer-service/src/main/java/com/insurancemanagementsystem/customer/dto/CustomerResponse.java`** — Response DTO pattern with fromEntity() factory
5. **`services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationResponse.java`** — Alternative Response DTO with @Builder + fromEntity()
6. **`services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/Vehicle.java`** — Vehicle entity (created in Step 2)
7. **`services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/CarBrand.java`** — CarBrand entity
8. **`services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/entity/CarModel.java`** — CarModel entity
9. **`services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/repository/VehicleRepository.java`** — Vehicle repository
10. **`infra/sql/vehicle_db/init.sql`** — DB schema for column name reference

## Files to Create

### 1. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/dto/VehicleRequest.java`

```java
package com.insurancemanagementsystem.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRequest {

    @NotBlank(message = "Plate is required")
    @Pattern(regexp = "^[0-9]{2}\\s?[A-Z]{1,3}\\s?[0-9]{2,4}$",
             message = "Plate must be in Turkish format (e.g., 34 ABC 1234)")
    private String plate;

    @Size(min = 17, max = 17, message = "Chassis number must be exactly 17 characters")
    @Pattern(regexp = "^[A-Za-z0-9]{17}$", message = "Chassis number must be 17 alphanumeric characters")
    private String chassisNumber;

    @PastOrPresent(message = "License first date cannot be in the future")
    private LocalDate licenseFirstDate;

    @NotNull(message = "Car brand ID is required")
    private Integer carBrandId;

    @NotNull(message = "Car model ID is required")
    private Integer carModelId;

    @NotNull(message = "Car engine ID is required")
    private Integer carEngineId;

    @NotNull(message = "Car fuel type ID is required")
    private Integer carFuelTypeId;

    @NotNull(message = "Car type ID is required")
    private Integer carTypeId;

    @NotNull(message = "Car package ID is required")
    private Integer carPackageId;

    @NotNull(message = "Customer ID is required")
    private UUID customerId;
}
```

### 2. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/dto/VehicleResponse.java`

```java
package com.insurancemanagementsystem.vehicle.dto;

import com.insurancemanagementsystem.vehicle.entity.Vehicle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponse {
    private UUID id;
    private String plate;
    private String chassisNumber;
    private LocalDate licenseFirstDate;
    private Integer carBrandId;
    private String carBrandName;
    private Integer carModelId;
    private String carModelName;
    private Integer carEngineId;
    private String carEngineName;
    private BigDecimal carEngineVolume;
    private Integer carEnginePower;
    private Integer carFuelTypeId;
    private String carFuelTypeName;
    private Integer carTypeId;
    private String carTypeName;
    private Integer carPackageId;
    private String carPackageName;
    private UUID customerId;
    private Instant createdAt;
    private Instant updatedAt;

    public static VehicleResponse fromEntity(Vehicle vehicle,
                                              String brandName,
                                              String modelName,
                                              String engineName,
                                              BigDecimal engineVolume,
                                              Integer enginePower,
                                              String fuelTypeName,
                                              String typeName,
                                              String packageName) {
        return VehicleResponse.builder()
                .id(vehicle.getId())
                .plate(vehicle.getPlate())
                .chassisNumber(vehicle.getChassisNumber())
                .licenseFirstDate(vehicle.getLicenseFirstDate())
                .carBrandId(vehicle.getCarBrandId())
                .carBrandName(brandName)
                .carModelId(vehicle.getCarModelId())
                .carModelName(modelName)
                .carEngineId(vehicle.getCarEngineId())
                .carEngineName(engineName)
                .carEngineVolume(engineVolume)
                .carEnginePower(enginePower)
                .carFuelTypeId(vehicle.getCarFuelTypeId())
                .carFuelTypeName(fuelTypeName)
                .carTypeId(vehicle.getCarTypeId())
                .carTypeName(typeName)
                .carPackageId(vehicle.getCarPackageId())
                .carPackageName(packageName)
                .customerId(vehicle.getCustomerId())
                .createdAt(vehicle.getCreatedAt())
                .updatedAt(vehicle.getUpdatedAt())
                .build();
    }
}
```

### 3. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java`

```java
package com.insurancemanagementsystem.vehicle.service;

import com.insurancemanagementsystem.vehicle.dto.VehicleRequest;
import com.insurancemanagementsystem.vehicle.dto.VehicleResponse;
import com.insurancemanagementsystem.vehicle.entity.*;
import com.insurancemanagementsystem.vehicle.repository.*;
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
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final CarBrandRepository carBrandRepository;
    private final CarModelRepository carModelRepository;
    private final CarEngineRepository carEngineRepository;
    private final CarFuelTypeRepository carFuelTypeRepository;
    private final CarTypeRepository carTypeRepository;
    private final CarPackageRepository carPackageRepository;

    // ---------- Vehicle CRUD ----------

    @Transactional(readOnly = true)
    public Page<VehicleResponse> findAll(Pageable pageable) {
        return vehicleRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VehicleResponse findById(UUID id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found with id: " + id));
        return toResponse(vehicle);
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        // Validate plate uniqueness
        vehicleRepository.findByPlate(request.getPlate().trim())
                .ifPresent(v -> {
                    throw new IllegalArgumentException("Vehicle with plate " + request.getPlate() + " already exists");
                });

        // Validate reference IDs exist
        validateReferenceIds(request);

        Vehicle vehicle = Vehicle.builder()
                .plate(request.getPlate().trim())
                .chassisNumber(request.getChassisNumber())
                .licenseFirstDate(request.getLicenseFirstDate())
                .carBrandId(request.getCarBrandId())
                .carModelId(request.getCarModelId())
                .carEngineId(request.getCarEngineId())
                .carFuelTypeId(request.getCarFuelTypeId())
                .carTypeId(request.getCarTypeId())
                .carPackageId(request.getCarPackageId())
                .customerId(request.getCustomerId())
                .build();

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle created with id: {} and plate: {}", saved.getId(), saved.getPlate());
        return toResponse(saved);
    }

    @Transactional
    public VehicleResponse update(UUID id, VehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found with id: " + id));

        // Validate plate uniqueness (skip if same plate)
        if (!vehicle.getPlate().equals(request.getPlate().trim())) {
            vehicleRepository.findByPlate(request.getPlate().trim())
                    .ifPresent(v -> {
                        throw new IllegalArgumentException("Vehicle with plate " + request.getPlate() + " already exists");
                    });
        }

        validateReferenceIds(request);

        vehicle.setPlate(request.getPlate().trim());
        vehicle.setChassisNumber(request.getChassisNumber());
        vehicle.setLicenseFirstDate(request.getLicenseFirstDate());
        vehicle.setCarBrandId(request.getCarBrandId());
        vehicle.setCarModelId(request.getCarModelId());
        vehicle.setCarEngineId(request.getCarEngineId());
        vehicle.setCarFuelTypeId(request.getCarFuelTypeId());
        vehicle.setCarTypeId(request.getCarTypeId());
        vehicle.setCarPackageId(request.getCarPackageId());
        vehicle.setCustomerId(request.getCustomerId());

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle updated with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found with id: " + id));
        vehicleRepository.delete(vehicle);
        log.info("Vehicle deleted with id: {}", id);
    }

    // ---------- Reference data endpoints ----------

    @Transactional(readOnly = true)
    public List<CarBrand> getBrands() {
        return carBrandRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CarModel> getModelsByBrand(Integer brandId) {
        return carModelRepository.findByBrandId(brandId);
    }

    @Transactional(readOnly = true)
    public List<CarEngine> getEngines() {
        return carEngineRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CarFuelType> getFuelTypes() {
        return carFuelTypeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CarType> getTypes() {
        return carTypeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CarPackage> getPackages() {
        return carPackageRepository.findAll();
    }

    // ---------- Helper methods ----------

    private VehicleResponse toResponse(Vehicle vehicle) {
        CarBrand brand = carBrandRepository.findById(vehicle.getCarBrandId()).orElse(null);
        CarModel model = carModelRepository.findById(vehicle.getCarModelId()).orElse(null);
        CarEngine engine = carEngineRepository.findById(vehicle.getCarEngineId()).orElse(null);
        CarFuelType fuelType = carFuelTypeRepository.findById(vehicle.getCarFuelTypeId()).orElse(null);
        CarType type = carTypeRepository.findById(vehicle.getCarTypeId()).orElse(null);
        CarPackage pkg = carPackageRepository.findById(vehicle.getCarPackageId()).orElse(null);

        return VehicleResponse.fromEntity(vehicle,
                brand != null ? brand.getName() : null,
                model != null ? model.getName() : null,
                engine != null ? engine.getName() : null,
                engine != null ? engine.getVolume() : null,
                engine != null ? engine.getPower() : null,
                fuelType != null ? fuelType.getName() : null,
                type != null ? type.getName() : null,
                pkg != null ? pkg.getName() : null);
    }

    private void validateReferenceIds(VehicleRequest request) {
        if (!carBrandRepository.existsById(request.getCarBrandId())) {
            throw new IllegalArgumentException("Car brand not found with id: " + request.getCarBrandId());
        }
        if (!carModelRepository.existsById(request.getCarModelId())) {
            throw new IllegalArgumentException("Car model not found with id: " + request.getCarModelId());
        }
        if (!carEngineRepository.existsById(request.getCarEngineId())) {
            throw new IllegalArgumentException("Car engine not found with id: " + request.getCarEngineId());
        }
        if (!carFuelTypeRepository.existsById(request.getCarFuelTypeId())) {
            throw new IllegalArgumentException("Car fuel type not found with id: " + request.getCarFuelTypeId());
        }
        if (!carTypeRepository.existsById(request.getCarTypeId())) {
            throw new IllegalArgumentException("Car type not found with id: " + request.getCarTypeId());
        }
        if (!carPackageRepository.existsById(request.getCarPackageId())) {
            throw new IllegalArgumentException("Car package not found with id: " + request.getCarPackageId());
        }
    }
}
```

### 4. `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/controller/VehicleController.java`

```java
package com.insurancemanagementsystem.vehicle.controller;

import com.insurancemanagementsystem.common.web.dto.ApiResponse;
import com.insurancemanagementsystem.vehicle.dto.VehicleRequest;
import com.insurancemanagementsystem.vehicle.dto.VehicleResponse;
import com.insurancemanagementsystem.vehicle.entity.*;
import com.insurancemanagementsystem.vehicle.service.VehicleService;
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
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    // --- Vehicle CRUD ---

    @GetMapping
    public ResponseEntity<ApiResponse<Page<VehicleResponse>>> getAll(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<VehicleResponse> vehicles = vehicleService.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(vehicles));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleResponse>> getById(@PathVariable UUID id) {
        VehicleResponse vehicle = vehicleService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(vehicle));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VehicleResponse>> create(@Valid @RequestBody VehicleRequest request) {
        VehicleResponse created = vehicleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vehicle created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleRequest request) {
        VehicleResponse updated = vehicleService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Vehicle updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        vehicleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Vehicle deleted successfully", null));
    }

    // --- Reference data ---

    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<List<CarBrand>>> getBrands() {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getBrands()));
    }

    @GetMapping("/brands/{brandId}/models")
    public ResponseEntity<ApiResponse<List<CarModel>>> getModelsByBrand(@PathVariable Integer brandId) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getModelsByBrand(brandId)));
    }

    @GetMapping("/engines")
    public ResponseEntity<ApiResponse<List<CarEngine>>> getEngines() {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getEngines()));
    }

    @GetMapping("/fuel-types")
    public ResponseEntity<ApiResponse<List<CarFuelType>>> getFuelTypes() {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getFuelTypes()));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<CarType>>> getTypes() {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getTypes()));
    }

    @GetMapping("/packages")
    public ResponseEntity<ApiResponse<List<CarPackage>>> getPackages() {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getPackages()));
    }
}
```

## Key Conventions
- `ApiResponse<T>` from `com.insurancemanagementsystem.common.web.dto` — all responses must use `ApiResponse.success(...)`
- `@Valid` on `@RequestBody` for automatic Jakarta validation
- `@PageableDefault` for paginated endpoints (default sort by `createdAt` DESC)
- `ResponseEntity` wrapping `ApiResponse`
- `EntityNotFoundException` → 404 (handled by `GlobalExceptionHandler`)
- `IllegalArgumentException` → 400 (handled by `GlobalExceptionHandler`)
- `@Transactional` on write methods, `@Transactional(readOnly = true)` on reads
- Service does NOT inject or call EventPublisher — that's wired in Step 4

## Verification

```bash
.\gradlew.bat :services:vehicle-service:compileJava
```

Should compile successfully.

## Files Written
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/dto/VehicleRequest.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/dto/VehicleResponse.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/service/VehicleService.java` ✅
- `services/vehicle-service/src/main/java/com/insurancemanagementsystem/vehicle/controller/VehicleController.java` ✅
