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
