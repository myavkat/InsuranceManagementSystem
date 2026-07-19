package com.insurancemanagementsystem.insurance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceRequest {

	@NotBlank(message = "Insurance name is required")
	private String name;

	private String code; // optional — auto-generated from name if not set

	private String description;

	@NotNull(message = "Insurance type ID is required")
	private Integer typeId;

	@NotNull(message = "Base premium is required")
	@DecimalMin(value = "0.01", message = "Base premium must be positive")
	private BigDecimal basePremium;

	private Boolean isActive;

}
