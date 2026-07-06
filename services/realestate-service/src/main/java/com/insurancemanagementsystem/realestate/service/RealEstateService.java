package com.insurancemanagementsystem.realestate.service;

import com.insurancemanagementsystem.realestate.dto.RealEstateRequest;
import com.insurancemanagementsystem.realestate.dto.RealEstateResponse;
import com.insurancemanagementsystem.realestate.entity.RealEstate;
import com.insurancemanagementsystem.realestate.entity.RealEstateConstructionType;
import com.insurancemanagementsystem.realestate.entity.RealEstateLuxuryClass;
import com.insurancemanagementsystem.realestate.entity.RealEstateUsageType;
import com.insurancemanagementsystem.realestate.repository.RealEstateRepository;
import com.insurancemanagementsystem.realestate.repository.RealEstateConstructionTypeRepository;
import com.insurancemanagementsystem.realestate.repository.RealEstateLuxuryClassRepository;
import com.insurancemanagementsystem.realestate.repository.RealEstateUsageTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealEstateService {

    private final RealEstateRepository realEstateRepository;
    private final RealEstateConstructionTypeRepository constructionTypeRepository;
    private final RealEstateLuxuryClassRepository luxuryClassRepository;
    private final RealEstateUsageTypeRepository usageTypeRepository;

    // ---------- RealEstate CRUD ----------

    @Transactional(readOnly = true)
    public Page<RealEstateResponse> findAll(Pageable pageable) {
        return realEstateRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RealEstateResponse findById(UUID id) {
        RealEstate realEstate = realEstateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RealEstate not found with id: " + id));
        return toResponse(realEstate);
    }

    @Transactional
    public RealEstateResponse create(RealEstateRequest request) {
        validateConstructionYear(request);
        validateReferenceIds(request);

        RealEstate realEstate = RealEstate.builder()
                .address(request.getAddress().trim())
                .cityId(request.getCityId())
                .district(request.getDistrict())
                .squareMeters(request.getSquareMeters())
                .constructionYear(request.getConstructionYear())
                .constructionTypeId(request.getConstructionTypeId())
                .luxuryClassId(request.getLuxuryClassId())
                .usageTypeId(request.getUsageTypeId())
                .customerId(request.getCustomerId())
                .build();

        RealEstate saved = realEstateRepository.save(realEstate);
        log.info("RealEstate created with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public RealEstateResponse update(UUID id, RealEstateRequest request) {
        RealEstate realEstate = realEstateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RealEstate not found with id: " + id));

        validateConstructionYear(request);
        validateReferenceIds(request);

        realEstate.setAddress(request.getAddress().trim());
        realEstate.setCityId(request.getCityId());
        realEstate.setDistrict(request.getDistrict());
        realEstate.setSquareMeters(request.getSquareMeters());
        realEstate.setConstructionYear(request.getConstructionYear());
        realEstate.setConstructionTypeId(request.getConstructionTypeId());
        realEstate.setLuxuryClassId(request.getLuxuryClassId());
        realEstate.setUsageTypeId(request.getUsageTypeId());
        realEstate.setCustomerId(request.getCustomerId());

        RealEstate saved = realEstateRepository.save(realEstate);
        log.info("RealEstate updated with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        RealEstate realEstate = realEstateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RealEstate not found with id: " + id));
        realEstateRepository.delete(realEstate);
        log.info("RealEstate deleted with id: {}", id);
    }

    // ---------- Reference data methods ----------

    @Transactional(readOnly = true)
    public List<RealEstateConstructionType> getConstructionTypes() {
        return constructionTypeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<RealEstateLuxuryClass> getLuxuryClasses() {
        return luxuryClassRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<RealEstateUsageType> getUsageTypes() {
        return usageTypeRepository.findAll();
    }

    // ---------- Helper methods ----------

    private RealEstateResponse toResponse(RealEstate realEstate) {
        String constructionTypeName = constructionTypeRepository
                .findById(realEstate.getConstructionTypeId())
                .map(RealEstateConstructionType::getName)
                .orElse(null);
        String luxuryClassName = luxuryClassRepository
                .findById(realEstate.getLuxuryClassId())
                .map(RealEstateLuxuryClass::getName)
                .orElse(null);
        String usageTypeName = usageTypeRepository
                .findById(realEstate.getUsageTypeId())
                .map(RealEstateUsageType::getName)
                .orElse(null);

        return RealEstateResponse.fromEntity(realEstate, constructionTypeName, luxuryClassName, usageTypeName);
    }

    private void validateConstructionYear(RealEstateRequest request) {
        if (request.getConstructionYear() != null
                && request.getConstructionYear() > Year.now().getValue()) {
            throw new IllegalArgumentException(
                    "Construction year cannot be in the future: " + request.getConstructionYear());
        }
    }

    private void validateReferenceIds(RealEstateRequest request) {
        if (!constructionTypeRepository.existsById(request.getConstructionTypeId())) {
            throw new IllegalArgumentException(
                    "Construction type not found with id: " + request.getConstructionTypeId());
        }
        if (!luxuryClassRepository.existsById(request.getLuxuryClassId())) {
            throw new IllegalArgumentException(
                    "Luxury class not found with id: " + request.getLuxuryClassId());
        }
        if (!usageTypeRepository.existsById(request.getUsageTypeId())) {
            throw new IllegalArgumentException(
                    "Usage type not found with id: " + request.getUsageTypeId());
        }
    }
}
