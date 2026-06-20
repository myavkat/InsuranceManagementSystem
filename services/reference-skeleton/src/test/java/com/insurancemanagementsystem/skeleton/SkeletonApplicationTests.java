package com.insurancemanagementsystem.skeleton;

import com.insurancemanagementsystem.skeleton.dto.ApiResponse;
import com.insurancemanagementsystem.skeleton.entity.SampleEntity;
import com.insurancemanagementsystem.skeleton.repository.SampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
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

    @LocalServerPort
    private int port;

    @Autowired
    private SampleRepository sampleRepository;

    private RestTemplate restTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
    }

    @Test
    void contextLoads() {
    }

    @SuppressWarnings("unchecked")
    @Test
    void createEntityViaRest_verifyInDb() {
        String baseUrl = "http://localhost:" + port;

        // Create entity via REST
        Map<String, String> request = Map.of("name", "Test Sample");
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                baseUrl + "/api/samples",
                request,
                ApiResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());

        // Extract the created entity ID from response (data is a LinkedHashMap)
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertNotNull(data);
        String idStr = (String) data.get("id");
        assertNotNull(idStr);
        UUID id = UUID.fromString(idStr);

        // Verify entity exists in database
        assertTrue(sampleRepository.existsById(id));
        SampleEntity saved = sampleRepository.findById(id).orElseThrow();
        assertEquals("Test Sample", saved.getName());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        // Fetch via REST and verify
        ResponseEntity<ApiResponse> getResponse = restTemplate.getForEntity(
                baseUrl + "/api/samples/" + id,
                ApiResponse.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
    }
}
