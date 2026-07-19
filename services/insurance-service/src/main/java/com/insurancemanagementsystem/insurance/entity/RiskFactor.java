package com.insurancemanagementsystem.insurance.entity;

import jakarta.persistence.*;
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
@Entity
@Table(name = "risk_factors",
		uniqueConstraints = { @UniqueConstraint(columnNames = { "insurance_id", "factor_name" }) })
public class RiskFactor {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "insurance_id", nullable = false)
	private UUID insuranceId;

	@Column(name = "factor_name", nullable = false, length = 50)
	private String factorName;

	@Column(name = "factor_value", nullable = false, precision = 3, scale = 2)
	private BigDecimal factorValue;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	@PrePersist
	protected void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = Instant.now();
	}

}
