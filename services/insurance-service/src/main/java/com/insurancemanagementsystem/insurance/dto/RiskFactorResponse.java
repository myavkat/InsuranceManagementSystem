package com.insurancemanagementsystem.insurance.dto;

import com.insurancemanagementsystem.insurance.entity.RiskFactor;
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
public class RiskFactorResponse {

	private UUID id;

	private UUID insuranceId;

	private String factorName;

	private BigDecimal factorValue;

	private Instant createdAt;

	private Instant updatedAt;

	public static RiskFactorResponse fromEntity(RiskFactor entity) {
		return RiskFactorResponse.builder()
			.id(entity.getId())
			.insuranceId(entity.getInsuranceId())
			.factorName(entity.getFactorName())
			.factorValue(entity.getFactorValue())
			.createdAt(entity.getCreatedAt())
			.updatedAt(entity.getUpdatedAt())
			.build();
	}

}
