package com.insurancemanagementsystem.referencedata.controller;

import com.insurancemanagementsystem.referencedata.dto.CityResponse;
import com.insurancemanagementsystem.referencedata.dto.ProfessionResponse;
import com.insurancemanagementsystem.referencedata.service.ReferenceDataService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.mockito.BDDMockito.given;

@WebMvcTest(ReferenceDataController.class)
class ReferenceDataControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private RestTestClient client;

	@MockitoBean
	private ReferenceDataService service;

	private final CityResponse ankara = CityResponse.builder().id(6).name("Ankara").plateCode("06").build();

	private final CityResponse istanbul = CityResponse.builder().id(34).name("İstanbul").plateCode("34").build();

	private final ProfessionResponse doctor = ProfessionResponse.builder().id(1).name("Doktor").build();

	private final ProfessionResponse engineer = ProfessionResponse.builder().id(2).name("Mühendis").build();

	@BeforeEach
	void setUp() {
		this.client = RestTestClient.bindTo(mockMvc).build();
	}

	@Test
	void shouldReturnCitiesList() {
		// Given
		given(service.getCities()).willReturn(List.of(ankara, istanbul));

		// When/Then
		client.get()
			.uri("/api/reference-data/cities")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(true)
			.jsonPath("$.data[0].id")
			.isEqualTo(6)
			.jsonPath("$.data[0].name")
			.isEqualTo("Ankara")
			.jsonPath("$.data[0].plateCode")
			.isEqualTo("06")
			.jsonPath("$.data[1].id")
			.isEqualTo(34)
			.jsonPath("$.data[1].name")
			.isEqualTo("İstanbul")
			.jsonPath("$.data[1].plateCode")
			.isEqualTo("34");
	}

	@Test
	void shouldReturnProfessionsList() {
		// Given
		given(service.getProfessions()).willReturn(List.of(doctor, engineer));

		// When/Then
		client.get()
			.uri("/api/reference-data/professions")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(true)
			.jsonPath("$.data[0].id")
			.isEqualTo(1)
			.jsonPath("$.data[0].name")
			.isEqualTo("Doktor")
			.jsonPath("$.data[1].id")
			.isEqualTo(2)
			.jsonPath("$.data[1].name")
			.isEqualTo("Mühendis");
	}

	@Test
	void shouldIncludeCacheControlHeader() {
		// Given
		given(service.getCities()).willReturn(List.of(ankara, istanbul));

		// When/Then
		client.get()
			.uri("/api/reference-data/cities")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueEquals("Cache-Control", "max-age=300");
	}

	@Test
	void shouldReturnSuccessTrue() {
		// Given
		given(service.getCities()).willReturn(List.of(ankara, istanbul));

		// When/Then
		client.get()
			.uri("/api/reference-data/cities")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(true);
	}

	@Test
	void shouldHandleServiceException() {
		// Given
		given(service.getCities()).willThrow(new RuntimeException("Internal error"));

		// When/Then
		client.get()
			.uri("/api/reference-data/cities")
			.exchange()
			.expectStatus()
			.is5xxServerError()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(false)
			.jsonPath("$.message")
			.isEqualTo("An unexpected error occurred");
	}

	@Test
	void shouldHandleIllegalArgumentException() {
		// Given
		given(service.getCities()).willThrow(new IllegalArgumentException("Invalid request"));

		// When/Then
		client.get()
			.uri("/api/reference-data/cities")
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(false)
			.jsonPath("$.message")
			.isEqualTo("Invalid request");
	}

}
