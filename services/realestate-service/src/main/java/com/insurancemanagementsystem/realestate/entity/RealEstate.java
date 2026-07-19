package com.insurancemanagementsystem.realestate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
@Table(name = "real_estates")
public class RealEstate {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "address", columnDefinition = "TEXT", nullable = false)
	private String address;

	@Column(name = "city_id", nullable = false)
	private Integer cityId;

	@Column(name = "district", length = 100)
	private String district;

	@Column(name = "square_meters", precision = 10, scale = 2)
	private BigDecimal squareMeters;

	@Column(name = "construction_year")
	private Integer constructionYear;

	@Column(name = "construction_type_id")
	private Integer constructionTypeId;

	@Column(name = "luxury_class_id")
	private Integer luxuryClassId;

	@Column(name = "usage_type_id")
	private Integer usageTypeId;

	@Column(name = "customer_id")
	private UUID customerId;

	@Column(name = "created_at", updatable = false)
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
