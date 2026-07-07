package com.insurancemanagementsystem.insurance.service;

import com.insurancemanagementsystem.insurance.config.InsuranceEventPublisher;
import com.insurancemanagementsystem.insurance.dto.InsuranceRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceResponse;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import com.insurancemanagementsystem.insurance.entity.InsuranceType;
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
    private final InsuranceEventPublisher insuranceEventPublisher;

    // ============================================================
    // Insurance CRUD
    // ============================================================

    @Transactional(readOnly = true)
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
                .filter(i -> i.getIsActive())
                .orElseThrow(() -> new EntityNotFoundException("Insurance not found with id: " + id));

        // Validate type exists
        insuranceTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Insurance type not found with id: " + request.getTypeId()));

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
                .filter(i -> i.getIsActive())
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
}
