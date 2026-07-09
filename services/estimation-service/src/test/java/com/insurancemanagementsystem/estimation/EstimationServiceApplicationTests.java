package com.insurancemanagementsystem.estimation;

import com.insurancemanagementsystem.estimation.client.InsuranceServiceClient;
import com.insurancemanagementsystem.estimation.dto.EstimationRequest;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class EstimationServiceApplicationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_estimation_db")
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
    }

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private EstimationRepository estimationRepository;

    @MockitoBean
    private InsuranceServiceClient insuranceServiceClient;

    private final ObjectMapper objectMapper = new JsonMapper();

    @BeforeEach
    void cleanUp() {
        estimationRepository.deleteAll();

        when(insuranceServiceClient.getInsurance(any(UUID.class)))
                .thenReturn(new InsuranceServiceClient.InsuranceInfo(UUID.randomUUID(), "TRAFFIC", 1, "Vehicle"));
    }

    @Test
    void contextLoads() {
        // Verifies that the application context starts with all beans wired correctly
    }

    @Test
    void createEstimationViaRest_verifyInDb() throws Exception {
        EstimationRequest request = createValidRequest();

        ExchangeResult exchangeResult = restTestClient.post()
                .uri("/api/estimations")
                .body(request)
                .exchange()
                .returnResult();

        HttpStatusCode status = exchangeResult.getStatus();
        byte[] body = exchangeResult.getResponseBodyContent();
        assertThat(status.is2xxSuccessful()).isTrue();

        assertThat(body).isNotNull();

        // Extract ID from response body
        UUID estimationId = UUID.fromString(
                objectMapper.readTree(body).get("data").get("id").asText());

        // Verify entity persisted in DB
        Optional<Estimation> found = estimationRepository.findById(estimationId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(Estimation.Status.STARTED);
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getSagaId()).isNotNull();
    }

    @Test
    void getById_NotFound_Returns404() {
        restTestClient.get().uri("/api/estimations/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    void listEstimations_returnsEmptyList() {
        restTestClient.get().uri("/api/estimations")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.totalElements").isEqualTo(0);
    }

    @Test
    void createEstimation_thenListReturnsOne() {
        EstimationRequest request = createValidRequest();

        ExchangeResult postResult = restTestClient.post().uri("/api/estimations")
                .body(request)
                .exchange()
                .returnResult();

        byte[] postBody = postResult.getResponseBodyContent();
        assertThat(postResult.getStatus().is2xxSuccessful()).isTrue();

        restTestClient.get().uri("/api/estimations")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.totalElements").isEqualTo(1)
                .jsonPath("$.data.content[0].status").isEqualTo("STARTED")
                .jsonPath("$.data.content[0].customerId").isNotEmpty();
    }

    @Test
    void createEstimationWithMissingCustomerId_returns400() {
        EstimationRequest request = new EstimationRequest();
        // customerId is intentionally null — should fail @NotNull validation

        restTestClient.post().uri("/api/estimations")
                .body(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isNotEmpty();
    }

    private EstimationRequest createValidRequest() {
        EstimationRequest request = new EstimationRequest();
        request.setCustomerId(UUID.randomUUID());
        request.setVehicleId(UUID.randomUUID());
        request.setInsuranceId(UUID.randomUUID());
        return request;
    }
}
