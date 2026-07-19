package com.insurancemanagementsystem.estimation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstimationRequest {

	@NotNull(message = "Customer ID is required")
	private UUID customerId;

	private UUID vehicleId; // optional — null for non-vehicle insurances

	private UUID realEstateId; // optional — null for non-real-estate insurances

	@NotNull(message = "Insurance ID is required")
	private UUID insuranceId;

}
