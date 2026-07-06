package com.insurancemanagementsystem.vehicle.entity;

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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plate", length = 20, unique = true, nullable = false)
    private String plate;

    @Column(name = "chassis_number", length = 50)
    private String chassisNumber;

    @Column(name = "license_first_date")
    private LocalDate licenseFirstDate;

    @Column(name = "car_brand_id", nullable = false)
    private Integer carBrandId;

    @Column(name = "car_model_id", nullable = false)
    private Integer carModelId;

    @Column(name = "car_engine_id", nullable = false)
    private Integer carEngineId;

    @Column(name = "car_fuel_type_id", nullable = false)
    private Integer carFuelTypeId;

    @Column(name = "car_type_id", nullable = false)
    private Integer carTypeId;

    @Column(name = "car_package_id", nullable = false)
    private Integer carPackageId;

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
