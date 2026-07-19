package com.insurancemanagementsystem.estimation.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class VehicleServiceClient {

	private final RestClient restClient;

	public VehicleServiceClient(@Value("${estimation.vehicle-service-url}") String baseUrl) {
		this.restClient = RestClient.create(baseUrl);
	}

	/**
	 * Fetches vehicle info (plate, chassisNumber) for a vehicle. Returns a map with keys
	 * "plate" and "chassisNumber", or null if the vehicleId is null or not found.
	 */
	public Map<String, String> getVehicleInfo(UUID vehicleId) {
		if (vehicleId == null) {
			return null;
		}
		try {
			var response = restClient.get().uri("/api/vehicles/{id}", vehicleId).retrieve().body(Map.class);

			if (response == null) {
				return null;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> data = (Map<String, Object>) response.get("data");
			if (data == null) {
				return null;
			}

			String plate = (String) data.get("plate");
			String chassisNumber = (String) data.get("chassisNumber");

			if (plate == null && chassisNumber == null) {
				return null;
			}

			return Map.of("plate", plate != null ? plate : "", "chassisNumber",
					chassisNumber != null ? chassisNumber : "");
		}
		catch (Exception e) {
			log.warn("Failed to fetch vehicle info for vehicleId={}: {}", vehicleId, e.getMessage());
			return null;
		}
	}

}
