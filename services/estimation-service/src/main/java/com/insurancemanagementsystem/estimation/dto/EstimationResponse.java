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
    private UUID vehicleId;
    private UUID realEstateId;
    private Integer insuranceTypeId;
    private UUID companyId;
    private String status;
    private BigDecimal premium;
    private String details;
    private Instant createdAt;
    private Instant updatedAt;

    public static EstimationResponse fromEntity(Estimation estimation) {
        return EstimationResponse.builder()
                .id(estimation.getId())
                .sagaId(estimation.getSagaId())
                .customerId(estimation.getCustomerId())
                .vehicleId(estimation.getVehicleId())
                .realEstateId(estimation.getRealEstateId())
                .insuranceTypeId(estimation.getInsuranceTypeId())
                .companyId(estimation.getCompanyId())
                .status(estimation.getStatus().name())
                .premium(estimation.getPremium())
                .details(estimation.getDetails())
                .createdAt(estimation.getCreatedAt())
                .updatedAt(estimation.getUpdatedAt())
                .build();
    }
}
