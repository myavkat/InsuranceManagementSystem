package com.insurancemanagementsystem.insurance.dto;

import com.insurancemanagementsystem.insurance.entity.RiskFactorHistory;
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
public class RiskFactorHistoryResponse {

	private UUID id;

	private UUID riskFactorId;

	private UUID insuranceId;

	private String factorName;

	private BigDecimal oldValue;

	private BigDecimal newValue;

	private Instant changedAt;

	public static RiskFactorHistoryResponse fromEntity(RiskFactorHistory entity) {
		return RiskFactorHistoryResponse.builder()
			.id(entity.getId())
			.riskFactorId(entity.getRiskFactorId())
			.insuranceId(entity.getInsuranceId())
			.factorName(entity.getFactorName())
			.oldValue(entity.getOldValue())
			.newValue(entity.getNewValue())
			.changedAt(entity.getChangedAt())
			.build();
	}

}
