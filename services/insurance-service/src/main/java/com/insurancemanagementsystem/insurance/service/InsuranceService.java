package com.insurancemanagementsystem.insurance.service;

import com.insurancemanagementsystem.insurance.config.InsuranceEventPublisher;
import com.insurancemanagementsystem.insurance.dto.InsuranceRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceResponse;
import com.insurancemanagementsystem.insurance.dto.RiskFactorHistoryResponse;
import com.insurancemanagementsystem.insurance.dto.RiskFactorResponse;
import com.insurancemanagementsystem.insurance.dto.RiskFactorUpdateRequest;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import com.insurancemanagementsystem.insurance.entity.RiskFactor;
import com.insurancemanagementsystem.insurance.entity.RiskFactorHistory;
import com.insurancemanagementsystem.insurance.repository.InsuranceRepository;
import com.insurancemanagementsystem.insurance.repository.InsuranceTypeRepository;
import com.insurancemanagementsystem.insurance.repository.RiskFactorHistoryRepository;
import com.insurancemanagementsystem.insurance.repository.RiskFactorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsuranceService {

	private final InsuranceRepository insuranceRepository;

	private final InsuranceTypeRepository insuranceTypeRepository;

	private final InsuranceEventPublisher insuranceEventPublisher;

	private final RiskFactorRepository riskFactorRepository;

	private final RiskFactorHistoryRepository riskFactorHistoryRepository;

	// ============================================================
	// Insurance CRUD
	// ============================================================

	@Transactional(readOnly = true)
	public Page<InsuranceResponse> findAll(Integer typeId, String search, Pageable pageable) {
		boolean hasType = typeId != null;
		boolean hasSearch = search != null && !search.isBlank();

		Page<Insurance> page;
		if (hasType && hasSearch) {
			page = insuranceRepository.searchByName(search, pageable);
		}
		else if (hasType) {
			page = insuranceRepository.findByTypeId(typeId, pageable);
		}
		else if (hasSearch) {
			page = insuranceRepository.searchByName(search, pageable);
		}
		else {
			page = insuranceRepository.findAll(pageable);
		}
		return page.map(InsuranceResponse::fromEntity);
	}

	@Transactional(readOnly = true)
	public InsuranceResponse findById(UUID id) {
		Insurance insurance = insuranceRepository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Insurance not found with id: " + id));
		return InsuranceResponse.fromEntity(insurance);
	}

	@Transactional
	public InsuranceResponse create(InsuranceRequest request) {
		// Validate type exists
		insuranceTypeRepository.findById(request.getTypeId())
			.orElseThrow(
					() -> new IllegalArgumentException("Insurance type not found with id: " + request.getTypeId()));

		// Check duplicate name
		insuranceRepository.findByNameIgnoreCase(request.getName().trim()).ifPresent(_ -> {
			throw new IllegalArgumentException("Insurance with name '" + request.getName() + "' already exists");
		});

		// Generate code from name if not explicitly provided
		String code = request.getCode() != null && !request.getCode().isBlank() ? request.getCode().trim().toUpperCase()
				: request.getName().trim().toUpperCase().replaceAll("\\s+", "_");

		Insurance insurance = Insurance.builder()
			.name(request.getName().trim())
			.code(code)
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

	@Transactional
	public InsuranceResponse update(UUID id, InsuranceRequest request) {
		Insurance insurance = insuranceRepository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Insurance not found with id: " + id));

		// Validate type exists
		insuranceTypeRepository.findById(request.getTypeId())
			.orElseThrow(
					() -> new IllegalArgumentException("Insurance type not found with id: " + request.getTypeId()));

		// Check duplicate name (if changed)
		if (!insurance.getName().equalsIgnoreCase(request.getName().trim())) {
			insuranceRepository.findByNameIgnoreCase(request.getName().trim()).ifPresent(_ -> {
				throw new IllegalArgumentException("Insurance with name '" + request.getName() + "' already exists");
			});
		}

		insurance.setName(request.getName().trim());
		insurance.setDescription(request.getDescription());
		insurance.setTypeId(request.getTypeId());
		insurance.setBasePremium(request.getBasePremium());
		insurance.setIsActive(request.getIsActive() != null ? request.getIsActive() : insurance.getIsActive());

		Insurance saved = insuranceRepository.save(insurance);
		log.info("Insurance updated: id={}, name={}", saved.getId(), saved.getName());
		insuranceEventPublisher.publishInsuranceUpdated(saved);
		return InsuranceResponse.fromEntity(saved);
	}

	@Transactional
	public InsuranceResponse softDelete(UUID id) {
		Insurance insurance = insuranceRepository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Insurance not found with id: " + id));

		insurance.setIsActive(false);
		Insurance saved = insuranceRepository.save(insurance);
		insuranceEventPublisher.publishInsuranceDeleted(saved);
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
	// Risk Factors
	// ============================================================

	@Transactional(readOnly = true)
	public List<RiskFactorResponse> getRiskFactors(UUID insuranceId) {
		return riskFactorRepository.findByInsuranceId(insuranceId)
			.stream()
			.map(RiskFactorResponse::fromEntity)
			.toList();
	}

	@Transactional
	public List<RiskFactorResponse> updateRiskFactors(UUID insuranceId, List<RiskFactorUpdateRequest> updates) {
		List<RiskFactorResponse> results = new java.util.ArrayList<>();

		for (RiskFactorUpdateRequest update : updates) {
			RiskFactor factor = riskFactorRepository.findByInsuranceIdAndFactorName(insuranceId, update.getFactorName())
				.orElseThrow(() -> new EntityNotFoundException(
						"Risk factor '" + update.getFactorName() + "' not found for insurance " + insuranceId));

			BigDecimal oldValue = factor.getFactorValue();
			BigDecimal newValue = update.getFactorValue();

			// Skip if no change
			if (oldValue.compareTo(newValue) == 0) {
				results.add(RiskFactorResponse.fromEntity(factor));
				continue;
			}

			// Update the factor
			factor.setFactorValue(newValue);
			factor = riskFactorRepository.save(factor);

			// Record history
			RiskFactorHistory history = RiskFactorHistory.builder()
				.riskFactorId(factor.getId())
				.insuranceId(insuranceId)
				.factorName(factor.getFactorName())
				.oldValue(oldValue)
				.newValue(newValue)
				.changedAt(Instant.now())
				.build();
			riskFactorHistoryRepository.save(history);

			results.add(RiskFactorResponse.fromEntity(factor));
		}

		return results;
	}

	@Transactional(readOnly = true)
	public Page<RiskFactorHistoryResponse> getRiskFactorHistory(UUID insuranceId, Pageable pageable) {
		return riskFactorHistoryRepository.findByInsuranceIdOrderByChangedAtDesc(insuranceId, pageable)
			.map(RiskFactorHistoryResponse::fromEntity);
	}

}
