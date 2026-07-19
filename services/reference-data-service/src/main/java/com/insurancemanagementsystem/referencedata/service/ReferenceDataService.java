package com.insurancemanagementsystem.referencedata.service;

import com.insurancemanagementsystem.referencedata.dto.CityResponse;
import com.insurancemanagementsystem.referencedata.dto.ProfessionResponse;
import com.insurancemanagementsystem.referencedata.entity.City;
import com.insurancemanagementsystem.referencedata.entity.Profession;
import com.insurancemanagementsystem.referencedata.repository.CityRepository;
import com.insurancemanagementsystem.referencedata.repository.ProfessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataService {

	private final CityRepository cityRepository;

	private final ProfessionRepository professionRepository;

	// In-memory cache with TTL
	private volatile List<CityResponse> cachedCities;

	private volatile List<ProfessionResponse> cachedProfessions;

	private volatile Instant citiesCacheExpiry = Instant.MIN;

	private volatile Instant professionsCacheExpiry = Instant.MIN;

	private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

	@Transactional(readOnly = true)
	public List<CityResponse> getCities() {
		if (cachedCities == null || Instant.now().isAfter(citiesCacheExpiry)) {
			synchronized (this) {
				if (cachedCities == null || Instant.now().isAfter(citiesCacheExpiry)) {
					cachedCities = cityRepository.findAllByOrderByNameAsc().stream().map(this::toCityResponse).toList();
					citiesCacheExpiry = Instant.now().plusSeconds(CACHE_TTL_SECONDS);
					log.debug("Cities cache refreshed: {} entries", cachedCities.size());
				}
			}
		}
		return cachedCities;
	}

	@Transactional(readOnly = true)
	public List<ProfessionResponse> getProfessions() {
		if (cachedProfessions == null || Instant.now().isAfter(professionsCacheExpiry)) {
			synchronized (this) {
				if (cachedProfessions == null || Instant.now().isAfter(professionsCacheExpiry)) {
					cachedProfessions = professionRepository.findAllByOrderByNameAsc()
						.stream()
						.map(this::toProfessionResponse)
						.toList();
					professionsCacheExpiry = Instant.now().plusSeconds(CACHE_TTL_SECONDS);
					log.debug("Professions cache refreshed: {} entries", cachedProfessions.size());
				}
			}
		}
		return cachedProfessions;
	}

	/**
	 * Invalidate caches. Called after data changes (admin endpoints or future mutation
	 * operations).
	 */
	public void invalidateCache() {
		synchronized (this) {
			cachedCities = null;
			cachedProfessions = null;
			citiesCacheExpiry = Instant.MIN;
			professionsCacheExpiry = Instant.MIN;
			log.info("Reference data caches invalidated");
		}
	}

	private CityResponse toCityResponse(City city) {
		return CityResponse.builder().id(city.getId()).name(city.getName()).plateCode(city.getPlateCode()).build();
	}

	private ProfessionResponse toProfessionResponse(Profession profession) {
		return ProfessionResponse.builder().id(profession.getId()).name(profession.getName()).build();
	}

}
