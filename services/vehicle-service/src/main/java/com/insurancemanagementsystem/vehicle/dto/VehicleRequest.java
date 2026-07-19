package com.insurancemanagementsystem.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRequest {

	@NotBlank(message = "Plate is required")
	@Pattern(regexp = "^[0-9]{2}\\s?[A-Z]{1,3}\\s?[0-9]{2,4}$",
			message = "Plate must be in Turkish format (e.g., 34 ABC 1234)")
	private String plate;

	@Size(min = 17, max = 17, message = "Chassis number must be exactly 17 characters")
	@Pattern(regexp = "^[A-Za-z0-9]{17}$", message = "Chassis number must be 17 alphanumeric characters")
	private String chassisNumber;

	@PastOrPresent(message = "License first date cannot be in the future")
	private LocalDate licenseFirstDate;

	@NotNull(message = "Car brand ID is required")
	private Integer carBrandId;

	@NotNull(message = "Car model ID is required")
	private Integer carModelId;

	@NotNull(message = "Car engine ID is required")
	private Integer carEngineId;

	@NotNull(message = "Car fuel type ID is required")
	private Integer carFuelTypeId;

	@NotNull(message = "Car type ID is required")
	private Integer carTypeId;

	@NotNull(message = "Car package ID is required")
	private Integer carPackageId;

	@NotNull(message = "Customer ID is required")
	private UUID customerId;

}
