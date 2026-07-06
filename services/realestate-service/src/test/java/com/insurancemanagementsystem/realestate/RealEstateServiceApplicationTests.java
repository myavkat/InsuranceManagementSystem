package com.insurancemanagementsystem.realestate;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.insurancemanagementsystem.realestate.dto.RealEstateRequest;
import com.insurancemanagementsystem.realestate.entity.RealEstate;
import com.insurancemanagementsystem.realestate.entity.RealEstateConstructionType;
import com.insurancemanagementsystem.realestate.entity.RealEstateLuxuryClass;
import com.insurancemanagementsystem.realestate.entity.RealEstateUsageType;
import com.insurancemanagementsystem.realestate.repository.RealEstateRepository;
import com.insurancemanagementsystem.realestate.repository.RealEstateConstructionTypeRepository;
import com.insurancemanagementsystem.realestate.repository.RealEstateLuxuryClassRepository;
import com.insurancemanagementsystem.realestate.repository.RealEstateUsageTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class RealEstateServiceApplicationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_realestate_integration_db")
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
        registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "*");
        registry.add("realestate.outbox.poll-interval-ms", () -> "600000");
    }

    @Autowired
    private RealEstateRepository realEstateRepository;

    @Autowired
    private RealEstateConstructionTypeRepository constructionTypeRepository;

    @Autowired
    private RealEstateLuxuryClassRepository luxuryClassRepository;

    @Autowired
    private RealEstateUsageTypeRepository usageTypeRepository;

    @Autowired
    private RestTestClient restTestClient;

    private final ObjectMapper objectMapper = new JsonMapper();

    @BeforeEach
    void cleanUp() {
        realEstateRepository.deleteAll();
        constructionTypeRepository.deleteAll();
        luxuryClassRepository.deleteAll();
        usageTypeRepository.deleteAll();

        // Seed reference data
        constructionTypeRepository.save(new RealEstateConstructionType(1, "Concrete"));
        constructionTypeRepository.save(new RealEstateConstructionType(2, "Steel"));
        luxuryClassRepository.save(new RealEstateLuxuryClass(1, "A"));
        luxuryClassRepository.save(new RealEstateLuxuryClass(2, "B"));
        usageTypeRepository.save(new RealEstateUsageType(1, "Residential"));
        usageTypeRepository.save(new RealEstateUsageType(2, "Commercial"));
    }

    private RealEstateRequest createValidRequest() {
        RealEstateRequest request = new RealEstateRequest();
        request.setAddress("123 Main St");
        request.setCityId(34);
        request.setDistrict("Kadıköy");
        request.setSquareMeters(new BigDecimal("150.00"));
        request.setConstructionYear(2020);
        request.setConstructionTypeId(1);
        request.setLuxuryClassId(1);
        request.setUsageTypeId(1);
        request.setCustomerId(UUID.randomUUID());
        return request;
    }

    // ---------------------------------------------------------------
    // 1. Create via REST → 201, verify DB
    // ---------------------------------------------------------------
    @Test
    void createViaRest_Returns201AndPersists() {
        RealEstateRequest request = createValidRequest();

        restTestClient.post().uri("/api/real-estate")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("RealEstate created successfully")
                .jsonPath("$.data.address").isEqualTo("123 Main St")
                .jsonPath("$.data.cityId").isEqualTo(34);

        List<RealEstate> all = realEstateRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getAddress()).isEqualTo("123 Main St");
        assertThat(all.get(0).getCityId()).isEqualTo(34);
        assertThat(all.get(0).getSquareMeters()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    // ---------------------------------------------------------------
    // 2. Get by ID — POST then GET → 200
    // ---------------------------------------------------------------
    @Test
    void getByIdViaRest_Returns200() {
        // Create via repository for deterministic ID
        RealEstate realEstate = realEstateRepository.save(RealEstate.builder()
                .address("123 Main St")
                .cityId(34)
                .district("Kadıköy")
                .squareMeters(new BigDecimal("150.00"))
                .constructionYear(2020)
                .constructionTypeId(1)
                .luxuryClassId(1)
                .usageTypeId(1)
                .customerId(UUID.randomUUID())
                .build());

        restTestClient.get().uri("/api/real-estate/{id}", realEstate.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.id").isEqualTo(realEstate.getId().toString())
                .jsonPath("$.data.address").isEqualTo("123 Main St")
                .jsonPath("$.data.cityId").isEqualTo(34)
                .jsonPath("$.data.constructionTypeName").isEqualTo("Concrete")
                .jsonPath("$.data.luxuryClassName").isEqualTo("A")
                .jsonPath("$.data.usageTypeName").isEqualTo("Residential");
    }

    // ---------------------------------------------------------------
    // 3. List — POST two, GET → 200 with 2 items
    // ---------------------------------------------------------------
    @Test
    void listViaRest_ReturnsAll() {
        realEstateRepository.save(RealEstate.builder()
                .address("Address 1").cityId(34).constructionTypeId(1).luxuryClassId(1).usageTypeId(1)
                .customerId(UUID.randomUUID()).build());
        realEstateRepository.save(RealEstate.builder()
                .address("Address 2").cityId(35).constructionTypeId(2).luxuryClassId(2).usageTypeId(2)
                .customerId(UUID.randomUUID()).build());

        restTestClient.get().uri("/api/real-estate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.content.length()").isEqualTo(2)
                .jsonPath("$.data.content[0].address").exists()
                .jsonPath("$.data.content[1].address").exists();
    }

    // ---------------------------------------------------------------
    // 4. Update — POST then PUT → 200
    // ---------------------------------------------------------------
    @Test
    void updateViaRest_Returns200() {
        RealEstate realEstate = realEstateRepository.save(RealEstate.builder()
                .address("Old Address")
                .cityId(34)
                .constructionTypeId(1)
                .luxuryClassId(1)
                .usageTypeId(1)
                .customerId(UUID.randomUUID())
                .build());

        RealEstateRequest updateRequest = new RealEstateRequest();
        updateRequest.setAddress("Updated Address");
        updateRequest.setCityId(6);
        updateRequest.setDistrict("Beşiktaş");
        updateRequest.setSquareMeters(new BigDecimal("200.00"));
        updateRequest.setConstructionYear(2022);
        updateRequest.setConstructionTypeId(2);
        updateRequest.setLuxuryClassId(2);
        updateRequest.setUsageTypeId(2);
        updateRequest.setCustomerId(UUID.randomUUID());

        restTestClient.put().uri("/api/real-estate/{id}", realEstate.getId())
                .body(updateRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("RealEstate updated successfully")
                .jsonPath("$.data.address").isEqualTo("Updated Address")
                .jsonPath("$.data.cityId").isEqualTo(6);

        RealEstate updated = realEstateRepository.findById(realEstate.getId()).orElseThrow();
        assertThat(updated.getAddress()).isEqualTo("Updated Address");
        assertThat(updated.getCityId()).isEqualTo(6);
    }

    // ---------------------------------------------------------------
    // 5. Delete — POST then DELETE → 200, verify deleted
    // ---------------------------------------------------------------
    @Test
    void deleteViaRest_Returns200AndDeletes() {
        RealEstate realEstate = realEstateRepository.save(RealEstate.builder()
                .address("To Delete")
                .cityId(34)
                .constructionTypeId(1)
                .luxuryClassId(1)
                .usageTypeId(1)
                .customerId(UUID.randomUUID())
                .build());

        restTestClient.delete().uri("/api/real-estate/{id}", realEstate.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("RealEstate deleted successfully");

        assertThat(realEstateRepository.findById(realEstate.getId())).isNotPresent();
    }

    // ---------------------------------------------------------------
    // 6. Validation — missing address → 400
    // ---------------------------------------------------------------
    @Test
    void create_WithoutAddress_Returns400() {
        String requestJson = """
                {
                    "cityId": 34,
                    "squareMeters": 150.00,
                    "constructionTypeId": 1,
                    "luxuryClassId": 1,
                    "usageTypeId": 1,
                    "customerId": "%s"
                }
                """.formatted(UUID.randomUUID().toString());

        restTestClient.post().uri("/api/real-estate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").value(msg ->
                        assertThat((String) msg).containsIgnoringCase("address"));
    }

    // ---------------------------------------------------------------
    // 7. Validation — squareMeters = 0 → 400
    // ---------------------------------------------------------------
    @Test
    void create_WithInvalidSquareMeters_Returns400() {
        String requestJson = """
                {
                    "address": "Some Address",
                    "cityId": 34,
                    "squareMeters": 0,
                    "constructionTypeId": 1,
                    "luxuryClassId": 1,
                    "usageTypeId": 1,
                    "customerId": "%s"
                }
                """.formatted(UUID.randomUUID().toString());

        restTestClient.post().uri("/api/real-estate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").value(msg ->
                        assertThat((String) msg).containsIgnoringCase("squareMeters"));
    }

    // ---------------------------------------------------------------
    // 8. Reference endpoints → 200 with seed data
    // ---------------------------------------------------------------
    @Test
    void getConstructionTypes_ReturnsSeedData() {
        restTestClient.get().uri("/api/real-estate/construction-types")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].name").isEqualTo("Concrete")
                .jsonPath("$.data[1].name").isEqualTo("Steel");
    }

    @Test
    void getLuxuryClasses_ReturnsSeedData() {
        restTestClient.get().uri("/api/real-estate/luxury-classes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].name").isEqualTo("A")
                .jsonPath("$.data[1].name").isEqualTo("B");
    }

    @Test
    void getUsageTypes_ReturnsSeedData() {
        restTestClient.get().uri("/api/real-estate/usage-types")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].name").isEqualTo("Residential")
                .jsonPath("$.data[1].name").isEqualTo("Commercial");
    }
}
