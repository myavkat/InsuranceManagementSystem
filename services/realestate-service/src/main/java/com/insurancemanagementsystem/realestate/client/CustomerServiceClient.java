package com.insurancemanagementsystem.realestate.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class CustomerServiceClient {

	private final RestClient restClient;

	public CustomerServiceClient(@Value("${realestate.customer-service-url}") String baseUrl) {
		this.restClient = RestClient.create(baseUrl);
	}

	/**
	 * Fetches the full display name (firstName + lastName) for a customer. Returns null
	 * if the customerId is null or the customer is not found.
	 */
	public String getCustomerName(UUID customerId) {
		if (customerId == null) {
			return null;
		}
		try {
			var response = restClient.get().uri("/api/customers/{id}", customerId).retrieve().body(Map.class);

			if (response == null) {
				return null;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> data = (Map<String, Object>) response.get("data");
			if (data == null) {
				return null;
			}

			String firstName = (String) data.get("firstName");
			String lastName = (String) data.get("lastName");

			if (firstName != null && lastName != null) {
				return firstName + " " + lastName;
			}
			else if (firstName != null) {
				return firstName;
			}
			else if (lastName != null) {
				return lastName;
			}
			return null;
		}
		catch (Exception e) {
			log.warn("Failed to fetch customer name for customerId={}: {}", customerId, e.getMessage());
			return null;
		}
	}

}
