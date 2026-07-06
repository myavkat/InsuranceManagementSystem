package com.insurancemanagementsystem.referencedata;

import com.insurancemanagementsystem.referencedata.entity.City;
import com.insurancemanagementsystem.referencedata.entity.Profession;
import com.insurancemanagementsystem.referencedata.repository.CityRepository;
import com.insurancemanagementsystem.referencedata.repository.ProfessionRepository;
import com.insurancemanagementsystem.referencedata.service.ReferenceDataService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class ReferenceDataServiceApplicationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("reference_data_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.binder.brokers", kafka::getBootstrapServers);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private ProfessionRepository professionRepository;

    @Autowired
    private ReferenceDataService referenceDataService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanUp() {
        cityRepository.deleteAll();
        professionRepository.deleteAll();
        referenceDataService.invalidateCache();
    }

    @Test
    void contextLoads() {
        // Verifies application context starts with all beans wired correctly
    }

    @Test
    void shouldReturnCitiesFromDatabase() {
        // Given: seed city data
        cityRepository.save(City.builder().id(6).name("Ankara").plateCode("06").build());
        cityRepository.save(City.builder().id(34).name("İstanbul").plateCode("34").build());

        // Verify @PrePersist set timestamps in DB
        assertThat(cityRepository.findById(6)).isPresent();
        assertThat(cityRepository.findById(6).get().getCreatedAt()).isNotNull();
        assertThat(cityRepository.findById(6).get().getUpdatedAt()).isNotNull();

        // When/Then
        restTestClient.get().uri("/api/reference-data/cities")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].name").isEqualTo("Ankara")
                .jsonPath("$.data[1].name").isEqualTo("İstanbul");
    }

    @Test
    void shouldReturnProfessionsFromDatabase() {
        // Given: seed profession data
        professionRepository.save(Profession.builder().id(1).name("Doktor").build());
        professionRepository.save(Profession.builder().id(2).name("Mühendis").build());

        // Verify @PrePersist set timestamps in DB
        assertThat(professionRepository.findById(1)).isPresent();
        assertThat(professionRepository.findById(1).get().getCreatedAt()).isNotNull();
        assertThat(professionRepository.findById(1).get().getUpdatedAt()).isNotNull();

        // When/Then
        restTestClient.get().uri("/api/reference-data/professions")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].name").isEqualTo("Doktor")
                .jsonPath("$.data[1].name").isEqualTo("Mühendis");
    }

    @Test
    void shouldReturnEmptyArrayWhenNoData() {
        // When/Then — empty database
        restTestClient.get().uri("/api/reference-data/cities")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(0);
    }

    @Test
    void shouldHaveCorrectResponseEnvelope() {
        // Given
        cityRepository.save(City.builder().id(6).name("Ankara").plateCode("06").build());

        // When/Then — verify all envelope fields exist
        restTestClient.get().uri("/api/reference-data/cities")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Operation successful")
                .jsonPath("$.data").isArray()
                .jsonPath("$.timestamp").isNotEmpty();
    }
}
