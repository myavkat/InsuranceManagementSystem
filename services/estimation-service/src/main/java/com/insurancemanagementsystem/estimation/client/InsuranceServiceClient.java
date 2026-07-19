package com.insurancemanagementsystem.estimation.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class InsuranceServiceClient {

	private final RestClient restClient;

	public InsuranceServiceClient(@Value("${estimation.insurance-service-url}") String baseUrl) {
		this.restClient = RestClient.create(baseUrl);
	}

	/**
	 * Fetches insurance details from the insurance service. Returns null if the
	 * insuranceId is null or the insurance is not found.
	 */
	public InsuranceInfo getInsurance(UUID insuranceId) {
		if (insuranceId == null) {
			return null;
		}
		try {
			var response = restClient.get().uri("/api/insurances/{id}", insuranceId).retrieve().body(Map.class);

			if (response == null) {
				return null;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> data = (Map<String, Object>) response.get("data");
			if (data == null) {
				return null;
			}

			UUID id = insuranceId; // Use the input — the response data.id is a String,
									// avoid parsing
			String name = (String) data.get("name");
			Integer typeId = data.get("typeId") != null ? ((Number) data.get("typeId")).intValue() : null;
			String typeName = (String) data.get("typeName");

			return new InsuranceInfo(id, name, typeId, typeName);
		}
		catch (Exception e) {
			log.warn("Failed to fetch insurance info for insuranceId={}: {}", insuranceId, e.getMessage());
			return null;
		}
	}

	/**
	 * Lightweight DTO for insurance information needed by the estimation service.
	 */
	public record InsuranceInfo(UUID id, String name, Integer typeId, String typeName) {
	}

}
