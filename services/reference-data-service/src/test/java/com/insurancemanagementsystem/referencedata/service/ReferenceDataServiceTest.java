package com.insurancemanagementsystem.referencedata.service;

import com.insurancemanagementsystem.referencedata.dto.CityResponse;
import com.insurancemanagementsystem.referencedata.dto.ProfessionResponse;
import com.insurancemanagementsystem.referencedata.entity.City;
import com.insurancemanagementsystem.referencedata.entity.Profession;
import com.insurancemanagementsystem.referencedata.repository.CityRepository;
import com.insurancemanagementsystem.referencedata.repository.ProfessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {

	@Mock
	private CityRepository cityRepository;

	@Mock
	private ProfessionRepository professionRepository;

	@InjectMocks
	private ReferenceDataService service;

	private final City ankara = City.builder().id(6).name("Ankara").plateCode("06").build();

	private final City istanbul = City.builder().id(34).name("İstanbul").plateCode("34").build();

	private final Profession doctor = Profession.builder().id(1).name("Doktor").build();

	private final Profession engineer = Profession.builder().id(2).name("Mühendis").build();

	// ---------------------------------------------------------------
	// 1. getCities — returns mapped DTOs from repository
	// ---------------------------------------------------------------
	@Test
	void shouldReturnCitiesFromRepository() {
		// Given
		when(cityRepository.findAllByOrderByNameAsc()).thenReturn(List.of(ankara, istanbul));

		// When
		List<CityResponse> cities = service.getCities();

		// Then
		assertThat(cities).hasSize(2);
		assertThat(cities.get(0).getId()).isEqualTo(6);
		assertThat(cities.get(0).getName()).isEqualTo("Ankara");
		assertThat(cities.get(0).getPlateCode()).isEqualTo("06");
		assertThat(cities.get(1).getId()).isEqualTo(34);
		assertThat(cities.get(1).getName()).isEqualTo("İstanbul");
		assertThat(cities.get(1).getPlateCode()).isEqualTo("34");

		verify(cityRepository, times(1)).findAllByOrderByNameAsc();
	}

	// ---------------------------------------------------------------
	// 2. getCities — second call uses cache
	// ---------------------------------------------------------------
	@Test
	void shouldCacheCitiesOnSecondCall() {
		// Given
		when(cityRepository.findAllByOrderByNameAsc()).thenReturn(List.of(ankara, istanbul));

		// When: first call populates cache
		List<CityResponse> firstCall = service.getCities();
		// Second call should use cache
		List<CityResponse> secondCall = service.getCities();

		// Then
		assertThat(firstCall).isSameAs(secondCall); // same cached list reference
		verify(cityRepository, times(1)).findAllByOrderByNameAsc(); // only called once
	}

	// ---------------------------------------------------------------
	// 3. getProfessions — returns mapped DTOs from repository
	// ---------------------------------------------------------------
	@Test
	void shouldReturnProfessionsFromRepository() {
		// Given
		when(professionRepository.findAllByOrderByNameAsc()).thenReturn(List.of(doctor, engineer));

		// When
		List<ProfessionResponse> professions = service.getProfessions();

		// Then
		assertThat(professions).hasSize(2);
		assertThat(professions.get(0).getId()).isEqualTo(1);
		assertThat(professions.get(0).getName()).isEqualTo("Doktor");
		assertThat(professions.get(1).getId()).isEqualTo(2);
		assertThat(professions.get(1).getName()).isEqualTo("Mühendis");

		verify(professionRepository, times(1)).findAllByOrderByNameAsc();
	}

	// ---------------------------------------------------------------
	// 4. getProfessions — second call uses cache
	// ---------------------------------------------------------------
	@Test
	void shouldCacheProfessionsOnSecondCall() {
		// Given
		when(professionRepository.findAllByOrderByNameAsc()).thenReturn(List.of(doctor, engineer));

		// When
		List<ProfessionResponse> firstCall = service.getProfessions();
		List<ProfessionResponse> secondCall = service.getProfessions();

		// Then
		assertThat(firstCall).isSameAs(secondCall); // same cached list reference
		verify(professionRepository, times(1)).findAllByOrderByNameAsc(); // only called
																			// once
	}

	// ---------------------------------------------------------------
	// 5. invalidateCache — next call hits repository again
	// ---------------------------------------------------------------
	@Test
	void shouldInvalidateCache() {
		// Given
		when(cityRepository.findAllByOrderByNameAsc()).thenReturn(List.of(ankara, istanbul));

		// When: populate cache
		service.getCities();
		// Invalidate
		service.invalidateCache();
		// Next call should hit repository again
		service.getCities();

		// Then
		verify(cityRepository, times(2)).findAllByOrderByNameAsc();
	}

}
