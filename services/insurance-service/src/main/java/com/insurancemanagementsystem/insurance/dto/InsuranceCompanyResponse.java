package com.insurancemanagementsystem.insurance.dto;

import com.insurancemanagementsystem.insurance.entity.InsuranceCompany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceCompanyResponse {
    private UUID id;
    private String name;
    private BigDecimal rating;
    private Boolean isActive;

    public static InsuranceCompanyResponse fromEntity(InsuranceCompany company) {
        return InsuranceCompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .rating(company.getRating())
                .isActive(company.getIsActive())
                .build();
    }
}
