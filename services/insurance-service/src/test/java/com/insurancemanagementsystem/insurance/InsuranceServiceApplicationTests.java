package com.insurancemanagementsystem.insurance;

import tools.jackson.databind.ObjectMapper;
import com.insurancemanagementsystem.insurance.dto.InsuranceRequest;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import com.insurancemanagementsystem.insurance.repository.InsuranceRepository;
import com.insurancemanagementsystem.insurance.repository.InsuranceTypeRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class InsuranceServiceApplicationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_insurance_db")
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
    private InsuranceRepository insuranceRepository;

    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanUp() {
        insuranceRepository.deleteAll();
        insuranceTypeRepository.deleteAll();

        // Seed InsuranceType data
        insuranceTypeRepository.saveAll(List.of(
                new InsuranceType(1, "TRAFFIC"),
                new InsuranceType(2, "CASCO"),
                new InsuranceType(3, "DASK"),
                new InsuranceType(4, "HEALTH"),
                new InsuranceType(5, "LIFE")
        ));
    }

    @Test
    void contextLoads() {
        // Verifies that the application context starts with all beans wired correctly
    }

    @Test
    void createInsuranceViaRest_verifyInDb() throws Exception {
        InsuranceRequest request = createValidInsuranceRequest();

        var result = restTestClient.post()
                .uri("/api/insurances")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Insurance created successfully")
                .returnResult();

        // Extract ID from response body
        UUID insuranceId = UUID.fromString(
                objectMapper.readTree(result.getResponseBodyContent()).get("data").get("id").asText());

        // Verify entity persisted in DB
        Optional<Insurance> found = insuranceRepository.findById(insuranceId);
        assertThat(found).isPresent();
        assertThat(found.get().getIsActive()).isTrue();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void listInsurances() {
        // Seed an insurance in DB
        Insurance insurance = Insurance.builder()
                .name("TestInsurance")
                .typeId(1)
                .basePremium(new BigDecimal("1000"))
                .build();
        insuranceRepository.save(insurance);

        restTestClient.get().uri("/api/insurances")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.totalElements").isEqualTo(1)
                .jsonPath("$.data.content[0].name").isEqualTo("TestInsurance");
    }

    @Test
    void getById_NotFound_Returns404() {
        restTestClient.get().uri("/api/insurances/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    void softDelete_thenGetReturns404() throws Exception {
        byte[] createBody = restTestClient.post().uri("/api/insurances")
                .body(createValidInsuranceRequest())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBodyContent();
        UUID insuranceId = UUID.fromString(
                objectMapper.readTree(createBody).get("data").get("id").asText());

        restTestClient.delete().uri("/api/insurances/{id}", insuranceId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);

        // GET returns 404 after soft delete
        restTestClient.get().uri("/api/insurances/{id}", insuranceId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);

        // Verify isActive=false in DB
        Optional<Insurance> found = insuranceRepository.findById(insuranceId);
        assertThat(found).isPresent();
        assertThat(found.get().getIsActive()).isFalse();
    }

    @Test
    void updateInsurance() throws Exception {
        byte[] createBody = restTestClient.post().uri("/api/insurances")
                .body(createValidInsuranceRequest())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBodyContent();
        UUID insuranceId = UUID.fromString(
                objectMapper.readTree(createBody).get("data").get("id").asText());

        InsuranceRequest updateReq = new InsuranceRequest();
        updateReq.setName("UpdatedInsurance");
        updateReq.setTypeId(1);
        updateReq.setBasePremium(new BigDecimal("1500"));

        restTestClient.put().uri("/api/insurances/{id}", insuranceId)
                .body(updateReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Insurance updated successfully")
                .jsonPath("$.data.name").isEqualTo("UpdatedInsurance")
                .jsonPath("$.data.basePremium").isEqualTo(1500);

        // Verify via GET
        restTestClient.get().uri("/api/insurances/{id}", insuranceId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.name").isEqualTo("UpdatedInsurance")
                .jsonPath("$.data.basePremium").isEqualTo(1500);
    }

    @Test
    void createInsuranceWithBlankName_returns400() {
        InsuranceRequest request = new InsuranceRequest();
        request.setName("");
        request.setTypeId(1);
        request.setBasePremium(new BigDecimal("1000"));

        restTestClient.post().uri("/api/insurances")
                .body(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    void getTypes_returnsSeedData() {
        restTestClient.get().uri("/api/insurances/types")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(5)
                .jsonPath("$.data[0].name").isEqualTo("TRAFFIC")
                .jsonPath("$.data[1].name").isEqualTo("CASCO")
                .jsonPath("$.data[2].name").isEqualTo("DASK")
                .jsonPath("$.data[3].name").isEqualTo("HEALTH")
                .jsonPath("$.data[4].name").isEqualTo("LIFE");
    }

    private InsuranceRequest createValidInsuranceRequest() {
        InsuranceRequest request = new InsuranceRequest();
        request.setName("TestInsurance");
        request.setTypeId(1);
        request.setBasePremium(new BigDecimal("1000"));
        return request;
    }
}
