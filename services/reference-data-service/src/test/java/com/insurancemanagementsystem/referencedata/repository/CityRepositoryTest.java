package com.insurancemanagementsystem.referencedata.repository;

import com.insurancemanagementsystem.referencedata.entity.City;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CityRepositoryTest {

	@Container
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16").withDatabaseName("testdb")
		.withUsername("test")
		.withPassword("test");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
	}

	@Autowired
	private CityRepository cityRepository;

	@Test
	void shouldReturnCitiesSortedByName() {
		// Given: insert test cities out of order
		City istanbul = City.builder().id(34).name("İstanbul").plateCode("34").build();
		City ankara = City.builder().id(6).name("Ankara").plateCode("06").build();
		cityRepository.saveAll(List.of(istanbul, ankara));

		// When
		List<City> cities = cityRepository.findAllByOrderByNameAsc();

		// Then
		assertThat(cities).hasSizeGreaterThanOrEqualTo(2);
		assertThat(cities.get(0).getName()).isEqualTo("Ankara");
		assertThat(cities.get(1).getName()).isEqualTo("İstanbul");
	}

	@Test
	void shouldFindCityById() {
		// Given
		City istanbul = City.builder().id(34).name("İstanbul").plateCode("34").build();
		cityRepository.save(istanbul);

		// When
		Optional<City> found = cityRepository.findById(34);

		// Then
		assertThat(found).isPresent();
		assertThat(found.get().getName()).isEqualTo("İstanbul");
		assertThat(found.get().getPlateCode()).isEqualTo("34");
	}

	@Test
	void shouldReturnEmptyForUnknownId() {
		// When
		Optional<City> found = cityRepository.findById(999);

		// Then
		assertThat(found).isEmpty();
	}

}
