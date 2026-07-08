package com.insurancemanagementsystem.realestate.service;

import com.insurancemanagementsystem.realestate.client.CustomerServiceClient;
import com.insurancemanagementsystem.realestate.client.ReferenceDataServiceClient;
import com.insurancemanagementsystem.realestate.config.RealEstateEventPublisher;
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
    private final RealEstateEventPublisher realEstateEventPublisher;
    private final RealEstateConstructionTypeRepository constructionTypeRepository;
    private final RealEstateLuxuryClassRepository luxuryClassRepository;
    private final RealEstateUsageTypeRepository usageTypeRepository;
    private final CustomerServiceClient customerServiceClient;
    private final ReferenceDataServiceClient referenceDataServiceClient;

    // ---------- RealEstate CRUD ----------

    @Transactional(readOnly = true)
    public Page<RealEstateResponse> findAll(Pageable pageable) {
        Page<RealEstate> page = realEstateRepository.findAll(pageable);

        // Collect unique non-null city IDs and customer IDs from the page
        java.util.Set<Integer> cityIds = page.getContent().stream()
                .map(RealEstate::getCityId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Set<UUID> customerIds = page.getContent().stream()
                .map(RealEstate::getCustomerId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());

        // Resolve city names (the client already caches the full list)
        java.util.Map<Integer, String> cityNameMap = new java.util.HashMap<>();
        for (Integer cityId : cityIds) {
            String name = referenceDataServiceClient.getCityName(cityId);
            if (name != null) {
                cityNameMap.put(cityId, name);
            }
        }

        // Resolve customer names (one REST call per unique customer)
        java.util.Map<UUID, String> customerNameMap = new java.util.HashMap<>();
        for (UUID customerId : customerIds) {
            String name = customerServiceClient.getCustomerName(customerId);
            if (name != null) {
                customerNameMap.put(customerId, name);
            }
        }

        // Map entities to DTOs using pre-resolved names
        return page.map(realEstate -> {
            String cityName = cityNameMap.get(realEstate.getCityId());
            String customerName = customerNameMap.get(realEstate.getCustomerId());
            return toResponse(realEstate, cityName, customerName);
        });
    }

    @Transactional(readOnly = true)
    public RealEstateResponse findById(UUID id) {
        RealEstate realEstate = realEstateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RealEstate not found with id: " + id));
        String cityName = referenceDataServiceClient.getCityName(realEstate.getCityId());
        String customerName = customerServiceClient.getCustomerName(realEstate.getCustomerId());
        return toResponse(realEstate, cityName, customerName);
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
        realEstateEventPublisher.publishRealEstateCreated(saved);
        log.info("RealEstate created with id: {}", saved.getId());
        String cityName = referenceDataServiceClient.getCityName(saved.getCityId());
        String customerName = customerServiceClient.getCustomerName(saved.getCustomerId());
        return toResponse(saved, cityName, customerName);
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
        realEstateEventPublisher.publishRealEstateUpdated(saved);
        log.info("RealEstate updated with id: {}", saved.getId());
        String cityName = referenceDataServiceClient.getCityName(saved.getCityId());
        String customerName = customerServiceClient.getCustomerName(saved.getCustomerId());
        return toResponse(saved, cityName, customerName);
    }

    @Transactional
    public void delete(UUID id) {
        RealEstate realEstate = realEstateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RealEstate not found with id: " + id));
        realEstateEventPublisher.publishRealEstateDeleted(realEstate);
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

    private RealEstateResponse toResponse(RealEstate realEstate, String cityName, String customerName) {
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

        return RealEstateResponse.fromEntity(realEstate,
                constructionTypeName, luxuryClassName, usageTypeName,
                cityName, customerName);
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
