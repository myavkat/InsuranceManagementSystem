package com.insurancemanagementsystem.skeleton;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurancemanagementsystem.skeleton.entity.SampleEntity;
import com.insurancemanagementsystem.skeleton.repository.SampleRepository;
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureRestTestClient
@Testcontainers
class SkeletonApplicationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private SampleRepository sampleRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    void contextLoads() {
    }

    @Test
    void createEntityViaRest_verifyInDb() throws Exception {
        // Create entity via REST
        byte[] responseBody = restTestClient.post()
                .uri("/api/samples")
                .body(Map.of("name", "Test Sample"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBodyContent();

        assertThat(responseBody).isNotNull();

        JsonNode json = objectMapper.readTree(responseBody);
        assertThat(json.get("success").asBoolean()).isTrue();

        // Extract the created entity ID from response
        JsonNode data = json.get("data");
        assertThat(data).isNotNull();
        String idStr = data.get("id").asText();
        assertThat(idStr).isNotNull();
        UUID id = UUID.fromString(idStr);

        // Verify entity exists in database
        assertThat(sampleRepository.existsById(id)).isTrue();
        Optional<SampleEntity> found = sampleRepository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Sample");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();

        // Fetch via REST and verify
        restTestClient.get()
                .uri("/api/samples/{id}", id)
                .exchange()
                .expectStatus().isOk();
    }
}
