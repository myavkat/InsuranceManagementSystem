package com.insurancemanagementsystem.realestate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealEstateRequest {

    @NotBlank(message = "Address is required")
    private String address;

    @NotNull(message = "City ID is required")
    private Integer cityId;

    private String district;

    @NotNull(message = "Square meters is required")
    @Min(value = 1, message = "Square meters must be positive")
    private BigDecimal squareMeters;

    private Integer constructionYear;

    @NotNull(message = "Construction type ID is required")
    private Integer constructionTypeId;

    @NotNull(message = "Luxury class ID is required")
    private Integer luxuryClassId;

    @NotNull(message = "Usage type ID is required")
    private Integer usageTypeId;

    @NotNull(message = "Customer ID is required")
    private UUID customerId;
}
