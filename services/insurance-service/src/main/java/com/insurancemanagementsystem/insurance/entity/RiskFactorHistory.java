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
@Table(name = "risk_factor_history")
public class RiskFactorHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "risk_factor_id", nullable = false)
    private UUID riskFactorId;

    @Column(name = "insurance_id", nullable = false)
    private UUID insuranceId;

    @Column(name = "factor_name", nullable = false, length = 50)
    private String factorName;

    @Column(name = "old_value", precision = 3, scale = 2)
    private BigDecimal oldValue;

    @Column(name = "new_value", nullable = false, precision = 3, scale = 2)
    private BigDecimal newValue;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }
}
