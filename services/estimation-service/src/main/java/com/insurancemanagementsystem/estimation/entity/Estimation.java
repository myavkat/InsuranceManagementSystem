package com.insurancemanagementsystem.estimation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "estimations")
public class Estimation {

    public enum Status {
        STARTED, COMPLETED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "saga_id", unique = true, nullable = false)
    private UUID sagaId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "real_estate_id")
    private UUID realEstateId;

    @Column(name = "insurance_type_id")
    private Integer insuranceTypeId;

    @Column(name = "company_id")
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private Status status;

    @Column(name = "premium", precision = 12, scale = 2)
    private BigDecimal premium;

    @Column(name = "details", columnDefinition = "JSONB")
    private String details;

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
