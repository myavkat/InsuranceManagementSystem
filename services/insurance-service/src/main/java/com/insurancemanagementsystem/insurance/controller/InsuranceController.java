package com.insurancemanagementsystem.insurance.controller;

import com.insurancemanagementsystem.common.web.dto.ApiResponse;
import com.insurancemanagementsystem.insurance.dto.InsuranceRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceResponse;
import com.insurancemanagementsystem.insurance.dto.RiskFactorHistoryResponse;
import com.insurancemanagementsystem.insurance.dto.RiskFactorResponse;
import com.insurancemanagementsystem.insurance.dto.RiskFactorUpdateRequest;
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
            @RequestParam(required = false) String search,
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<InsuranceResponse> insurances = insuranceService.findAll(typeId, search, pageable);
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
        return ResponseEntity.ok(ApiResponse.success("Insurance deleted successfully", deleted));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<InsuranceResponse>> deactivate(@PathVariable UUID id) {
        InsuranceResponse updated = insuranceService.softDelete(id);
        return ResponseEntity.ok(ApiResponse.success("Insurance deactivated successfully", updated));
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
    // Risk Factors
    // ---------------------------------------------------------------

    @GetMapping("/{id}/risk-factors")
    public ResponseEntity<ApiResponse<List<RiskFactorResponse>>> getRiskFactors(@PathVariable UUID id) {
        List<RiskFactorResponse> factors = insuranceService.getRiskFactors(id);
        return ResponseEntity.ok(ApiResponse.success(factors));
    }

    @PutMapping("/{id}/risk-factors")
    public ResponseEntity<ApiResponse<List<RiskFactorResponse>>> updateRiskFactors(
            @PathVariable UUID id,
            @Valid @RequestBody List<RiskFactorUpdateRequest> updates) {
        List<RiskFactorResponse> updated = insuranceService.updateRiskFactors(id, updates);
        return ResponseEntity.ok(ApiResponse.success("Risk factors updated", updated));
    }

    @GetMapping("/{id}/risk-factors/history")
    public ResponseEntity<ApiResponse<Page<RiskFactorHistoryResponse>>> getRiskFactorHistory(
            @PathVariable UUID id,
            @PageableDefault(sort = "changedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RiskFactorHistoryResponse> history = insuranceService.getRiskFactorHistory(id, pageable);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
