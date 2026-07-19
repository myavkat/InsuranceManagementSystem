package com.insurancemanagementsystem.realestate.controller;

import com.insurancemanagementsystem.common.web.dto.ApiResponse;
import com.insurancemanagementsystem.realestate.dto.RealEstateRequest;
import com.insurancemanagementsystem.realestate.dto.RealEstateResponse;
import com.insurancemanagementsystem.realestate.entity.RealEstateConstructionType;
import com.insurancemanagementsystem.realestate.entity.RealEstateLuxuryClass;
import com.insurancemanagementsystem.realestate.entity.RealEstateUsageType;
import com.insurancemanagementsystem.realestate.service.RealEstateService;
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
@RequestMapping("/api/real-estate")
@RequiredArgsConstructor
public class RealEstateController {

	private final RealEstateService realEstateService;

	@GetMapping
	public ResponseEntity<ApiResponse<Page<RealEstateResponse>>> getAll(
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
			@RequestParam(value = "search", required = false) String search,
			@RequestParam(value = "customerId", required = false) UUID customerId) {
		Page<RealEstateResponse> realEstates = realEstateService.findAll(pageable, search, customerId);
		return ResponseEntity.ok(ApiResponse.success(realEstates));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<RealEstateResponse>> getById(@PathVariable UUID id) {
		RealEstateResponse realEstate = realEstateService.findById(id);
		return ResponseEntity.ok(ApiResponse.success(realEstate));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<RealEstateResponse>> create(@Valid @RequestBody RealEstateRequest request) {
		RealEstateResponse created = realEstateService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success("RealEstate created successfully", created));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<RealEstateResponse>> update(@PathVariable UUID id,
			@Valid @RequestBody RealEstateRequest request) {
		RealEstateResponse updated = realEstateService.update(id, request);
		return ResponseEntity.ok(ApiResponse.success("RealEstate updated successfully", updated));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
		realEstateService.delete(id);
		return ResponseEntity.ok(ApiResponse.success("RealEstate deleted successfully", null));
	}

	@GetMapping("/construction-types")
	public ResponseEntity<ApiResponse<List<RealEstateConstructionType>>> getConstructionTypes() {
		List<RealEstateConstructionType> types = realEstateService.getConstructionTypes();
		return ResponseEntity.ok(ApiResponse.success(types));
	}

	@GetMapping("/luxury-classes")
	public ResponseEntity<ApiResponse<List<RealEstateLuxuryClass>>> getLuxuryClasses() {
		List<RealEstateLuxuryClass> classes = realEstateService.getLuxuryClasses();
		return ResponseEntity.ok(ApiResponse.success(classes));
	}

	@GetMapping("/usage-types")
	public ResponseEntity<ApiResponse<List<RealEstateUsageType>>> getUsageTypes() {
		List<RealEstateUsageType> types = realEstateService.getUsageTypes();
		return ResponseEntity.ok(ApiResponse.success(types));
	}

}
