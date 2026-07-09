package com.insurancemanagementsystem.estimation.dto;

import com.insurancemanagementsystem.estimation.entity.Estimation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimationResponse {
    private UUID id;
    private UUID sagaId;
    private UUID customerId;
    private String customerName;
    private String customerNationalId;
    private UUID vehicleId;
    private String vehiclePlate;
    private String vehicleChassisNumber;
    private UUID realEstateId;
    private String realEstateAddress;
    private Integer insuranceTypeId;
    private String insuranceTypeName;
    private String status;
    private BigDecimal premium;
    private String details;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Creates a response from the entity without enriched display fields.
     * Used for list queries (findAll) and create where cross-service calls are avoided.
     */
    public static EstimationResponse fromEntity(Estimation estimation) {
        return EstimationResponse.builder()
                .id(estimation.getId())
                .sagaId(estimation.getSagaId())
                .customerId(estimation.getCustomerId())
                .vehicleId(estimation.getVehicleId())
                .realEstateId(estimation.getRealEstateId())
                .insuranceTypeId(estimation.getInsuranceTypeId())
                .status(estimation.getStatus().name())
                .premium(estimation.getPremium())
                .details(estimation.getDetails())
                .createdAt(estimation.getCreatedAt())
                .updatedAt(estimation.getUpdatedAt())
                .build();
    }

    /**
     * Creates a fully enriched response with resolved display names.
     * Used for the detail endpoint (findById).
     */
    public static EstimationResponse fromEntity(Estimation estimation,
                                                  String customerName,
                                                  String customerNationalId,
                                                  String vehiclePlate,
                                                  String vehicleChassisNumber,
                                                  String insuranceTypeName) {
        return EstimationResponse.builder()
                .id(estimation.getId())
                .sagaId(estimation.getSagaId())
                .customerId(estimation.getCustomerId())
                .customerName(customerName)
                .customerNationalId(customerNationalId)
                .vehicleId(estimation.getVehicleId())
                .vehiclePlate(vehiclePlate)
                .vehicleChassisNumber(vehicleChassisNumber)
                .realEstateId(estimation.getRealEstateId())
                .insuranceTypeId(estimation.getInsuranceTypeId())
                .insuranceTypeName(insuranceTypeName)
                .status(estimation.getStatus().name())
                .premium(estimation.getPremium())
                .details(estimation.getDetails())
                .createdAt(estimation.getCreatedAt())
                .updatedAt(estimation.getUpdatedAt())
                .build();
    }
}
