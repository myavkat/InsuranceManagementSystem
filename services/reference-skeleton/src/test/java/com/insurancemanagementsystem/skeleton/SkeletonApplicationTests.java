package com.insurancemanagementsystem.skeleton;

import com.insurancemanagementsystem.skeleton.dto.ApiResponse;
import com.insurancemanagementsystem.skeleton.entity.SampleEntity;
import com.insurancemanagementsystem.skeleton.repository.SampleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SkeletonApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SampleRepository sampleRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void contextLoads() {
    }

    @SuppressWarnings("unchecked")
    @Test
    void createEntityViaRest_verifyInDb() {
        // Create entity via REST
        Map<String, String> request = Map.of("name", "Test Sample");
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/samples",
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
                "/api/samples/" + id,
                ApiResponse.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
    }
}
