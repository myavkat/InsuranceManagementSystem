package com.insurancemanagementsystem.insurance.service;

import com.insurancemanagementsystem.insurance.config.InsuranceEventPublisher;
import com.insurancemanagementsystem.insurance.dto.InsuranceCompanyRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceCompanyResponse;
import com.insurancemanagementsystem.insurance.dto.InsuranceRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceResponse;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import com.insurancemanagementsystem.insurance.entity.InsuranceCompany;
import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import com.insurancemanagementsystem.insurance.repository.InsuranceCompanyRepository;
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
    private final InsuranceCompanyRepository insuranceCompanyRepository;
    private final InsuranceEventPublisher insuranceEventPublisher;

    // ============================================================
    // Insurance CRUD
    // ============================================================

    @Transactional(readOnly = true)
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

        // Validate company exists and is active
        InsuranceCompany company = insuranceCompanyRepository.findById(request.getCompanyId())
                .filter(InsuranceCompany::getIsActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Insurance company not found or inactive: " + request.getCompanyId()));

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
                .companyId(request.getCompanyId())
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

        // Validate company exists and is active
        insuranceCompanyRepository.findById(request.getCompanyId())
                .filter(InsuranceCompany::getIsActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Insurance company not found or inactive: " + request.getCompanyId()));

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
        insurance.setCompanyId(request.getCompanyId());
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

    // ============================================================
    // Insurance Companies CRUD
    // ============================================================

    @Transactional(readOnly = true)
    public Page<InsuranceCompanyResponse> findAllCompanies(Pageable pageable) {
        return insuranceCompanyRepository.findByIsActiveTrue(pageable)
                .map(InsuranceCompanyResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<InsuranceCompanyResponse> getAllCompanies() {
        return insuranceCompanyRepository.findByIsActiveTrue(Pageable.unpaged())
                .map(InsuranceCompanyResponse::fromEntity)
                .getContent();
    }

    @Transactional(readOnly = true)
    public InsuranceCompanyResponse findCompanyById(UUID id) {
        InsuranceCompany company = insuranceCompanyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Insurance company not found with id: " + id));
        return InsuranceCompanyResponse.fromEntity(company);
    }

    @Transactional
    public InsuranceCompanyResponse createCompany(InsuranceCompanyRequest request) {
        InsuranceCompany company = InsuranceCompany.builder()
                .name(request.getName().trim())
                .rating(request.getRating())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        InsuranceCompany saved = insuranceCompanyRepository.save(company);
        log.info("Insurance company created: id={}, name={}", saved.getId(), saved.getName());
        return InsuranceCompanyResponse.fromEntity(saved);
    }

    @Transactional
    public InsuranceCompanyResponse updateCompany(UUID id, InsuranceCompanyRequest request) {
        InsuranceCompany company = insuranceCompanyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Insurance company not found with id: " + id));

        company.setName(request.getName().trim());
        company.setRating(request.getRating());
        company.setIsActive(request.getIsActive() != null ? request.getIsActive() : company.getIsActive());

        InsuranceCompany saved = insuranceCompanyRepository.save(company);
        log.info("Insurance company updated: id={}, name={}", saved.getId(), saved.getName());
        return InsuranceCompanyResponse.fromEntity(saved);
    }
}
