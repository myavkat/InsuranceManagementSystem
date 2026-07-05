package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent;
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
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EstimationService {

    private final EstimationRepository estimationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

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
            Estimation.Status statusEnum = parseStatus(status);
            estimations = estimationRepository.findByCustomerIdAndStatus(customerId, statusEnum, pageable);
        } else if (customerId != null) {
            estimations = estimationRepository.findByCustomerId(customerId, pageable);
        } else if (status != null) {
            Estimation.Status statusEnum = parseStatus(status);
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

        saveOutboxEvent(sagaId, request);

        return EstimationResponse.fromEntity(estimation);
    }

    private void saveOutboxEvent(UUID sagaId, EstimationRequest request) {
        EstimationRequestedEvent event = EstimationRequestedEvent.builder()
                .customerId(request.getCustomerId())
                .vehicleId(request.getVehicleId())
                .realEstateId(request.getRealEstateId())
                .insuranceTypeId(request.getInsuranceTypeId())
                .companyId(request.getCompanyId())
                .build();

        EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());

        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox payload for sagaId=" + sagaId, e);
        }

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .sagaId(sagaId)
                .topic(EventConstants.ESTIMATION_SAGA)
                .payload(payloadJson)
                .status(OutboxEvent.Status.PENDING)
                .build();
        outboxEventRepository.save(outboxEvent);
        log.info("Saved outbox event for sagaId={} to topic={}", sagaId, EventConstants.ESTIMATION_SAGA);
    }

    private Estimation.Status parseStatus(String status) {
        try {
            return Estimation.Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid status: '" + status + "'. Valid values: STARTED, COMPLETED, REJECTED");
        }
    }
}
