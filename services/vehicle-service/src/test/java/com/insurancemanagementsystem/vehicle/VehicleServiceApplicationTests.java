package com.insurancemanagementsystem.vehicle;

import com.insurancemanagementsystem.vehicle.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class VehicleServiceApplicationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_vehicle_integration_db")
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
        registry.add("vehicle.outbox.poll-interval-ms", () -> "600000");
    }

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static boolean referenceDataSeeded = false;

    @BeforeEach
    void setUp() {
        vehicleRepository.deleteAll();
        if (!referenceDataSeeded) {
            seedReferenceData();
            referenceDataSeeded = true;
        }
    }

    private void seedReferenceData() {
        jdbcTemplate.update("INSERT INTO car_brands (id, name) VALUES (1, 'TestBrand')");
        jdbcTemplate.update("INSERT INTO car_models (id, name, brand_id) VALUES (1, 'TestModel', 1)");
        jdbcTemplate.update("INSERT INTO car_engines (id, name) VALUES (1, 'TestEngine')");
        jdbcTemplate.update("INSERT INTO car_fuel_types (id, name) VALUES (1, 'TestFuel')");
        jdbcTemplate.update("INSERT INTO car_types (id, name) VALUES (1, 'TestType')");
        jdbcTemplate.update("INSERT INTO car_packages (id, name) VALUES (1, 'TestPackage')");
    }

    // ---------------------------------------------------------------
    // Test 1: Create vehicle via REST
    // ---------------------------------------------------------------
    @Test
    void createVehicleViaRest_Returns201() {
        Map<String, Object> request = createValidVehicleRequest();

        restTestClient.post().uri("/api/vehicles")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Vehicle created successfully")
                .jsonPath("$.data.plate").isEqualTo("34 ABC 1234");

        // Verify vehicle exists in DB
        assertThat(vehicleRepository.count()).isEqualTo(1);
        var saved = vehicleRepository.findAll().iterator().next();
        assertThat(saved.getPlate()).isEqualTo("34 ABC 1234");
        assertThat(saved.getCarBrandId()).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // Test 2: Get vehicle by ID
    // ---------------------------------------------------------------
    @Test
    void getVehicleById_Returns200() {
        String createdId = createVehicleAndGetId();

        restTestClient.get().uri("/api/vehicles/{id}", createdId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.id").isEqualTo(createdId)
                .jsonPath("$.data.plate").isEqualTo("34 ABC 1234");
    }

    // ---------------------------------------------------------------
    // Test 3: List vehicles
    // ---------------------------------------------------------------
    @Test
    void listVehicles_ReturnsPage() {
        createVehicleWithPlate("34 ABC 1234");
        createVehicleWithPlate("35 DEF 5678");

        restTestClient.get().uri("/api/vehicles")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.content.length()").isNumber();
    }

    // ---------------------------------------------------------------
    // Test 4: Update vehicle
    // ---------------------------------------------------------------
    @Test
    void updateVehicle_Returns200() {
        String createdId = createVehicleAndGetId();

        Map<String, Object> updateRequest = createValidVehicleRequest();
        updateRequest.put("plate", "99 ZZ 9999");

        restTestClient.put().uri("/api/vehicles/{id}", createdId)
                .body(updateRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Vehicle updated successfully")
                .jsonPath("$.data.plate").isEqualTo("99 ZZ 9999");

        // Verify in DB
        var updated = vehicleRepository.findById(UUID.fromString(createdId)).orElseThrow();
        assertThat(updated.getPlate()).isEqualTo("99 ZZ 9999");
    }

    // ---------------------------------------------------------------
    // Test 5: Delete vehicle
    // ---------------------------------------------------------------
    @Test
    void deleteVehicle_Returns200() {
        String createdId = createVehicleAndGetId();

        restTestClient.delete().uri("/api/vehicles/{id}", createdId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Vehicle deleted successfully");

        // Verify gone from DB
        assertThat(vehicleRepository.findById(UUID.fromString(createdId))).isEmpty();
    }

    // ---------------------------------------------------------------
    // Test 6: Validation — missing plate → 400
    // ---------------------------------------------------------------
    @Test
    void createVehicleWithMissingPlate_Returns400() {
        Map<String, Object> invalidRequest = createValidVehicleRequest();
        invalidRequest.remove("plate");

        restTestClient.post().uri("/api/vehicles")
                .body(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isNotEmpty();
    }

    // ---------------------------------------------------------------
    // Test 7: Reference endpoints
    // ---------------------------------------------------------------
    @Test
    void getBrands_Returns200() {
        restTestClient.get().uri("/api/vehicles/brands")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray();
    }

    @Test
    void getEngines_Returns200() {
        restTestClient.get().uri("/api/vehicles/engines")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray();
    }

    @Test
    void getFuelTypes_Returns200() {
        restTestClient.get().uri("/api/vehicles/fuel-types")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray();
    }

    @Test
    void getTypes_Returns200() {
        restTestClient.get().uri("/api/vehicles/types")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray();
    }

    @Test
    void getPackages_Returns200() {
        restTestClient.get().uri("/api/vehicles/packages")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray();
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    private Map<String, Object> createValidVehicleRequest() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("plate", "34 ABC 1234");
        map.put("chassisNumber", "1HGCM82633A004352");
        map.put("licenseFirstDate", "2020-01-15");
        map.put("carBrandId", 1);
        map.put("carModelId", 1);
        map.put("carEngineId", 1);
        map.put("carFuelTypeId", 1);
        map.put("carTypeId", 1);
        map.put("carPackageId", 1);
        map.put("customerId", UUID.randomUUID().toString());
        return map;
    }

    private String createVehicleAndGetId() {
        Map<String, Object> request = createValidVehicleRequest();
        request.put("customerId", UUID.randomUUID().toString());

        restTestClient.post().uri("/api/vehicles")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.id").exists();

        var saved = vehicleRepository.findAll().iterator().next();
        return saved.getId().toString();
    }

    private void createVehicleWithPlate(String plate) {
        Map<String, Object> request = createValidVehicleRequest();
        request.put("plate", plate);
        request.put("customerId", UUID.randomUUID().toString());

        restTestClient.post().uri("/api/vehicles")
                .body(request)
                .exchange()
                .expectStatus().isCreated();
    }
}
