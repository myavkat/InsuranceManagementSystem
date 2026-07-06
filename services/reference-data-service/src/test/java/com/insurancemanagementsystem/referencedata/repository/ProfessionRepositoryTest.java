package com.insurancemanagementsystem.referencedata.repository;

import com.insurancemanagementsystem.referencedata.entity.Profession;
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
class ProfessionRepositoryTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb")
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
    private ProfessionRepository professionRepository;

    @Test
    void shouldReturnProfessionsSortedByName() {
        // Given
        Profession doctor = Profession.builder().id(1).name("Doktor").build();
        Profession engineer = Profession.builder().id(2).name("Mühendis").build();
        professionRepository.saveAll(List.of(doctor, engineer));

        // When
        List<Profession> professions = professionRepository.findAllByOrderByNameAsc();

        // Then
        assertThat(professions).hasSizeGreaterThanOrEqualTo(2);
        assertThat(professions.get(0).getName()).isEqualTo("Doktor");
        assertThat(professions.get(1).getName()).isEqualTo("Mühendis");
    }

    @Test
    void shouldFindProfessionById() {
        // Given
        Profession doctor = Profession.builder().id(1).name("Doktor").build();
        professionRepository.save(doctor);

        // When
        Optional<Profession> found = professionRepository.findById(1);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Doktor");
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        // When
        Optional<Profession> found = professionRepository.findById(999);

        // Then
        assertThat(found).isEmpty();
    }
}
