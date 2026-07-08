package com.insurancemanagementsystem.vehicle.dto;

import com.insurancemanagementsystem.vehicle.entity.Vehicle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponse {
    private UUID id;
    private String plate;
    private String chassisNumber;
    private LocalDate licenseFirstDate;
    private Integer carBrandId;
    private String carBrandName;
    private Integer carModelId;
    private String carModelName;
    private Integer carEngineId;
    private String carEngineName;
    private BigDecimal carEngineVolume;
    private Integer carEnginePower;
    private Integer carFuelTypeId;
    private String carFuelTypeName;
    private Integer carTypeId;
    private String carTypeName;
    private Integer carPackageId;
    private String carPackageName;
    private UUID customerId;
    private String customerName;
    private Instant createdAt;
    private Instant updatedAt;

    public static VehicleResponse fromEntity(Vehicle vehicle,
                                              String brandName,
                                              String modelName,
                                              String engineName,
                                              BigDecimal engineVolume,
                                              Integer enginePower,
                                              String fuelTypeName,
                                              String typeName,
                                              String packageName,
                                              String customerName) {
        return VehicleResponse.builder()
                .id(vehicle.getId())
                .plate(vehicle.getPlate())
                .chassisNumber(vehicle.getChassisNumber())
                .licenseFirstDate(vehicle.getLicenseFirstDate())
                .carBrandId(vehicle.getCarBrandId())
                .carBrandName(brandName)
                .carModelId(vehicle.getCarModelId())
                .carModelName(modelName)
                .carEngineId(vehicle.getCarEngineId())
                .carEngineName(engineName)
                .carEngineVolume(engineVolume)
                .carEnginePower(enginePower)
                .carFuelTypeId(vehicle.getCarFuelTypeId())
                .carFuelTypeName(fuelTypeName)
                .carTypeId(vehicle.getCarTypeId())
                .carTypeName(typeName)
                .carPackageId(vehicle.getCarPackageId())
                .carPackageName(packageName)
                .customerId(vehicle.getCustomerId())
                .customerName(customerName)
                .createdAt(vehicle.getCreatedAt())
                .updatedAt(vehicle.getUpdatedAt())
                .build();
    }
}
