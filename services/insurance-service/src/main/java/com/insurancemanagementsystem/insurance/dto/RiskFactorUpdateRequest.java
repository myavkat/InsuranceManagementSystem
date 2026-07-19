package com.insurancemanagementsystem.insurance.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactorUpdateRequest {

	@NotBlank
	private String factorName;

	@NotNull
	@DecimalMin("0.00")
	@DecimalMax("1.00")
	private BigDecimal factorValue;

}
