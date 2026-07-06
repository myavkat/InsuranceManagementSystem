package com.insurancemanagementsystem.realestate.dto;

import com.insurancemanagementsystem.realestate.entity.RealEstate;
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
public class RealEstateResponse {
    private UUID id;
    private String address;
    private Integer cityId;
    private String district;
    private BigDecimal squareMeters;
    private Integer constructionYear;
    private Integer constructionTypeId;
    private String constructionTypeName;
    private Integer luxuryClassId;
    private String luxuryClassName;
    private Integer usageTypeId;
    private String usageTypeName;
    private UUID customerId;
    private Instant createdAt;
    private Instant updatedAt;

    public static RealEstateResponse fromEntity(RealEstate realEstate,
                                                 String constructionTypeName,
                                                 String luxuryClassName,
                                                 String usageTypeName) {
        return RealEstateResponse.builder()
                .id(realEstate.getId())
                .address(realEstate.getAddress())
                .cityId(realEstate.getCityId())
                .district(realEstate.getDistrict())
                .squareMeters(realEstate.getSquareMeters())
                .constructionYear(realEstate.getConstructionYear())
                .constructionTypeId(realEstate.getConstructionTypeId())
                .constructionTypeName(constructionTypeName)
                .luxuryClassId(realEstate.getLuxuryClassId())
                .luxuryClassName(luxuryClassName)
                .usageTypeId(realEstate.getUsageTypeId())
                .usageTypeName(usageTypeName)
                .customerId(realEstate.getCustomerId())
                .createdAt(realEstate.getCreatedAt())
                .updatedAt(realEstate.getUpdatedAt())
                .build();
    }
}
