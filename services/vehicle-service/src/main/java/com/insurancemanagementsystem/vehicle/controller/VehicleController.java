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
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "customerId", required = false) UUID customerId) {
        Page<VehicleResponse> vehicles = vehicleService.findAll(pageable, search, customerId);
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
