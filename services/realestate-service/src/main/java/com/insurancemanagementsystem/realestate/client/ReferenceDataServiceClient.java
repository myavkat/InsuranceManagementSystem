package com.insurancemanagementsystem.realestate.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ReferenceDataServiceClient {

	private final RestClient restClient;

	private volatile Map<Integer, String> cachedCityNames = Map.of();

	private volatile Instant cacheExpiry = Instant.MIN;

	public ReferenceDataServiceClient(@Value("${realestate.reference-data-service-url}") String baseUrl) {
		this.restClient = RestClient.create(baseUrl);
	}

	/**
	 * Returns the city name for a given city ID, or null if not found. Results are cached
	 * for 5 minutes since the cities list rarely changes.
	 */
	public String getCityName(Integer cityId) {
		if (cityId == null) {
			return null;
		}
		Map<Integer, String> cityMap = getCityMap();
		return cityMap.get(cityId);
	}

	private Map<Integer, String> getCityMap() {
		// Double-checked locking for thread-safe lazy cache refresh
		if (Instant.now().isBefore(cacheExpiry)) {
			return cachedCityNames;
		}
		synchronized (this) {
			if (Instant.now().isBefore(cacheExpiry)) {
				return cachedCityNames;
			}
			try {
				var response = restClient.get().uri("/api/reference-data/cities").retrieve().body(Map.class);

				if (response == null) {
					return cachedCityNames;
				}

				@SuppressWarnings("unchecked")
				List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
				if (data == null) {
					return cachedCityNames;
				}

				Map<Integer, String> newMap = new HashMap<>();
				for (Map<String, Object> city : data) {
					Integer id = (Integer) city.get("id");
					String name = (String) city.get("name");
					if (id != null && name != null) {
						newMap.put(id, name);
					}
				}
				cachedCityNames = Collections.unmodifiableMap(newMap);
				cacheExpiry = Instant.now().plusSeconds(300); // 5 minutes
				log.debug("Refreshed city name cache: {} cities", newMap.size());
			}
			catch (Exception e) {
				log.warn("Failed to fetch cities list: {}. Using {} cached entries.", e.getMessage(),
						cachedCityNames.size());
				// Extend stale cache by 60 seconds to avoid hammering a failing service
				cacheExpiry = Instant.now().plusSeconds(60);
			}
			return cachedCityNames;
		}
	}

}
