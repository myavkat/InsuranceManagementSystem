package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent;
import com.insurancemanagementsystem.common.messaging.OutboxMessagePublisher;
import com.insurancemanagementsystem.common.util.CorrelationIdGenerator;
import com.insurancemanagementsystem.estimation.client.CustomerServiceClient;
import com.insurancemanagementsystem.estimation.client.InsuranceServiceClient;
import com.insurancemanagementsystem.estimation.client.VehicleServiceClient;
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

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EstimationService {

    private final EstimationRepository estimationRepository;
    private final OutboxMessagePublisher outboxMessagePublisher;
    private final CustomerServiceClient customerServiceClient;
    private final InsuranceServiceClient insuranceServiceClient;
    private final VehicleServiceClient vehicleServiceClient;

    private static final Map<Integer, String> INSURANCE_TYPE_NAMES = Map.of(
            1, "Vehicle",
            2, "Real Estate",
            3, "Health",
            4, "Life"
    );

    @Transactional(readOnly = true)
    public EstimationResponse findById(UUID id) {
        Estimation estimation = estimationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Estimation not found with id: " + id));

        String customerName = customerServiceClient.getCustomerName(estimation.getCustomerId());
        String customerNationalId = customerServiceClient.getCustomerNationalId(estimation.getCustomerId());

        String vehiclePlate = null;
        String vehicleChassisNumber = null;
        if (estimation.getVehicleId() != null) {
            Map<String, String> vehicleInfo = vehicleServiceClient.getVehicleInfo(estimation.getVehicleId());
            if (vehicleInfo != null) {
                vehiclePlate = vehicleInfo.get("plate");
                vehicleChassisNumber = vehicleInfo.get("chassisNumber");
            }
        }

        String insuranceName = null;
        String insuranceTypeName = null;
        if (estimation.getInsuranceId() != null) {
            try {
                InsuranceServiceClient.InsuranceInfo info = insuranceServiceClient.getInsurance(estimation.getInsuranceId());
                if (info != null) {
                    insuranceName = info.name();
                    insuranceTypeName = info.typeName() != null ? info.typeName() : INSURANCE_TYPE_NAMES.get(info.typeId());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch insurance info for insuranceId={}: {}", estimation.getInsuranceId(), e.getMessage());
                // Fall back to null — the response DTO handles null display names gracefully
            }
        }

        return EstimationResponse.fromEntity(estimation,
                customerName,
                customerNationalId,
                vehiclePlate,
                vehicleChassisNumber,
                insuranceName,
                insuranceTypeName);
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
        // Validate asset linkage based on insurance type
        UUID insuranceId = request.getInsuranceId();
        if (insuranceId == null) {
            throw new IllegalArgumentException("insuranceId is required");
        }

        // Fetch insurance to determine its type for validation
        InsuranceServiceClient.InsuranceInfo info;
        try {
            info = insuranceServiceClient.getInsurance(insuranceId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Insurance not found or unavailable: " + insuranceId, e);
        }
        Integer typeId = info != null ? info.typeId() : null;

        // Type 1 = Vehicle → vehicleId required
        if (typeId != null && typeId == 1 && request.getVehicleId() == null) {
            throw new IllegalArgumentException("vehicleId is required for Vehicle-type insurance");
        }

        // Type 2 = Real Estate → realEstateId required
        if (typeId != null && typeId == 2 && request.getRealEstateId() == null) {
            throw new IllegalArgumentException("realEstateId is required for Real Estate-type insurance");
        }

        // Type 3 = Health, Type 4 = Life → no asset required (nothing to validate)

        UUID sagaId = CorrelationIdGenerator.generateSagaId();
        UUID traceId = CorrelationIdGenerator.generateTraceId();

        Estimation estimation = Estimation.builder()
                .sagaId(sagaId)
                .customerId(request.getCustomerId())
                .vehicleId(request.getVehicleId())
                .realEstateId(request.getRealEstateId())
                .insuranceId(request.getInsuranceId())
                .traceId(traceId)
                .status(Estimation.Status.STARTED)
                .build();

        estimation = estimationRepository.save(estimation);
        log.info("Created estimation id={} with sagaId={}, traceId={}", estimation.getId(), sagaId, traceId);

        // Publish via shared outbox publisher (replaces inline saveOutboxEvent)
        EstimationRequestedEvent event = EstimationRequestedEvent.builder()
                .customerId(request.getCustomerId())
                .vehicleId(request.getVehicleId())
                .realEstateId(request.getRealEstateId())
                .insuranceId(request.getInsuranceId())
                .build();

        outboxMessagePublisher.publish(event, sagaId, traceId, EventConstants.ESTIMATION_SAGA);

        return EstimationResponse.fromEntity(estimation);
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
