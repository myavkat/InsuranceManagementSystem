package com.insurancemanagementsystem.insurance.dto;

import com.insurancemanagementsystem.insurance.entity.Insurance;
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
public class InsuranceResponse {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private Integer typeId;
    private String typeName;
    private BigDecimal basePremium;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static InsuranceResponse fromEntity(Insurance insurance) {
        return InsuranceResponse.builder()
                .id(insurance.getId())
                .name(insurance.getName())
                .code(insurance.getCode())
                .description(insurance.getDescription())
                .typeId(insurance.getTypeId())
                .typeName(insurance.getInsuranceType() != null ? insurance.getInsuranceType().getName() : null)
                .basePremium(insurance.getBasePremium())
                .isActive(insurance.getIsActive())
                .createdAt(insurance.getCreatedAt())
                .updatedAt(insurance.getUpdatedAt())
                .build();
    }
}
