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
	public ResponseEntity<ApiResponse<Page<EstimationResponse>>> getAll(@RequestParam(required = false) UUID customerId,
			@RequestParam(required = false) String status,
			@PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

		Page<EstimationResponse> estimations = estimationService.findAll(customerId, status, pageable);
		return ResponseEntity.ok(ApiResponse.success(estimations));
	}

	@PutMapping("/{id}/accept-offer")
	public ResponseEntity<ApiResponse<EstimationResponse>> acceptOffer(@PathVariable UUID id) {
		EstimationResponse updated = estimationService.acceptOffer(id);
		return ResponseEntity.ok(ApiResponse.success("Offer accepted — payment is now required", updated));
	}

	@PutMapping("/{id}/process-payment")
	public ResponseEntity<ApiResponse<EstimationResponse>> processPayment(@PathVariable UUID id) {
		EstimationResponse updated = estimationService.processPayment(id);
		return ResponseEntity.ok(ApiResponse.success("Payment processed — policy is now active", updated));
	}

}
