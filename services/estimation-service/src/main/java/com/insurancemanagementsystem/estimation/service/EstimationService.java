package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.estimation.dto.EstimationRequest;
import com.insurancemanagementsystem.estimation.dto.EstimationResponse;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EstimationService {

    private final EstimationRepository estimationRepository;

    @Transactional(readOnly = true)
    public EstimationResponse findById(UUID id) {
        Estimation estimation = estimationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Estimation not found with id: " + id));
        return EstimationResponse.fromEntity(estimation);
    }

    @Transactional(readOnly = true)
    public Page<EstimationResponse> findAll(UUID customerId, String status, Pageable pageable) {
        Page<Estimation> estimations;

        if (customerId != null && status != null) {
            Estimation.Status statusEnum = Estimation.Status.valueOf(status.toUpperCase());
            estimations = estimationRepository.findByCustomerIdAndStatus(customerId, statusEnum, pageable);
        } else if (customerId != null) {
            estimations = estimationRepository.findByCustomerId(customerId, pageable);
        } else if (status != null) {
            Estimation.Status statusEnum = Estimation.Status.valueOf(status.toUpperCase());
            estimations = estimationRepository.findByStatus(statusEnum, pageable);
        } else {
            estimations = estimationRepository.findAll(pageable);
        }

        return estimations.map(EstimationResponse::fromEntity);
    }

    @Transactional
    public EstimationResponse create(EstimationRequest request) {
        if (request.getVehicleId() == null && request.getRealEstateId() == null) {
            throw new IllegalArgumentException("Either vehicleId or realEstateId must be provided");
        }

        UUID sagaId = UUID.randomUUID();

        Estimation estimation = Estimation.builder()
                .sagaId(sagaId)
                .customerId(request.getCustomerId())
                .vehicleId(request.getVehicleId())
                .realEstateId(request.getRealEstateId())
                .insuranceTypeId(request.getInsuranceTypeId())
                .companyId(request.getCompanyId())
                .status(Estimation.Status.STARTED)
                .build();

        estimation = estimationRepository.save(estimation);
        log.info("Created estimation id={} with sagaId={}", estimation.getId(), sagaId);

        // TODO: In Step 4/5 — publish EstimationRequested event to estimation.saga topic
        // EstimationRequestedEvent event = EstimationRequestedEvent.builder()
        //         .customerId(request.getCustomerId())
        //         .vehicleId(request.getVehicleId())
        //         .realEstateId(request.getRealEstateId())
        //         .insuranceTypeId(request.getInsuranceTypeId())
        //         .companyId(request.getCompanyId())
        //         .build();
        // EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());
        // messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);

        return EstimationResponse.fromEntity(estimation);
    }
}
