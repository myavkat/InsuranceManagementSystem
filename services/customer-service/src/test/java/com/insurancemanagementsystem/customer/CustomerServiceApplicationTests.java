package com.insurancemanagementsystem.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurancemanagementsystem.customer.dto.CustomerRequest;
import com.insurancemanagementsystem.customer.entity.Customer;
import com.insurancemanagementsystem.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class CustomerServiceApplicationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_customer_db")
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
    private TestRestTemplate restTemplate;

    @Autowired
    private CustomerRepository customerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void cleanUp() {
        customerRepository.deleteAll();
    }

    @Test
    void contextLoads() {
        // Verifies that the application context starts with all beans wired correctly
    }

    @Test
    void createCustomerViaRest_verifyInDb() throws Exception {
        CustomerRequest request = createValidRequest();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/customers", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.get("message").asText()).isEqualTo("Customer created successfully");

        JsonNode data = json.get("data");
        UUID customerId = UUID.fromString(data.get("id").asText());
        assertThat(customerId).isNotNull();
        assertThat(data.get("firstName").asText()).isEqualTo("John");
        assertThat(data.get("lastName").asText()).isEqualTo("Doe");

        // Verify entity persisted in DB
        Optional<Customer> found = customerRepository.findById(customerId);
        assertThat(found).isPresent();
        assertThat(found.get().getDeletedAt()).isNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void searchCustomers() throws Exception {
        CustomerRequest c1 = new CustomerRequest();
        c1.setFirstName("John");
        c1.setLastName("Doe");
        c1.setNationalId("12345678901");
        c1.setEmail("john.doe@example.com");
        c1.setBirthDate(LocalDate.of(1990, 1, 15));

        CustomerRequest c2 = new CustomerRequest();
        c2.setFirstName("Jane");
        c2.setLastName("Smith");
        c2.setNationalId("98765432109");
        c2.setEmail("jane.smith@example.com");
        c2.setBirthDate(LocalDate.of(1985, 5, 20));

        restTemplate.postForEntity("/api/customers", c1, String.class);
        restTemplate.postForEntity("/api/customers", c2, String.class);

        // Search matching 1 customer
        ResponseEntity<String> searchRes = restTemplate.getForEntity(
                "/api/customers?search=Doe", String.class);
        assertThat(searchRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode searchJson = objectMapper.readTree(searchRes.getBody());
        assertThat(searchJson.get("data").get("totalElements").asInt()).isEqualTo(1);

        // Search matching both (both names contain "e")
        ResponseEntity<String> allRes = restTemplate.getForEntity(
                "/api/customers?search=e", String.class);
        JsonNode allJson = objectMapper.readTree(allRes.getBody());
        assertThat(allJson.get("data").get("totalElements").asInt()).isEqualTo(2);

        // No search param — returns all
        ResponseEntity<String> plainRes = restTemplate.getForEntity(
                "/api/customers", String.class);
        JsonNode plainJson = objectMapper.readTree(plainRes.getBody());
        assertThat(plainJson.get("data").get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void softDeleteCustomer() throws Exception {
        ResponseEntity<String> createRes = restTemplate.postForEntity(
                "/api/customers", createValidRequest(), String.class);
        UUID customerId = UUID.fromString(
                objectMapper.readTree(createRes.getBody()).get("data").get("id").asText());

        ResponseEntity<String> deleteRes = restTemplate.exchange(
                "/api/customers/" + customerId, HttpMethod.DELETE, null, String.class);
        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode deleteJson = objectMapper.readTree(deleteRes.getBody());
        assertThat(deleteJson.get("success").asBoolean()).isTrue();

        // Verify GET returns 404
        ResponseEntity<String> getRes = restTemplate.getForEntity(
                "/api/customers/" + customerId, String.class);
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify deletedAt in DB
        Optional<Customer> found = customerRepository.findById(customerId);
        assertThat(found).isPresent();
        assertThat(found.get().getDeletedAt()).isNotNull();
    }

    @Test
    void updateCustomer() throws Exception {
        ResponseEntity<String> createRes = restTemplate.postForEntity(
                "/api/customers", createValidRequest(), String.class);
        UUID customerId = UUID.fromString(
                objectMapper.readTree(createRes.getBody()).get("data").get("id").asText());

        CustomerRequest updateReq = createValidRequest();
        updateReq.setFirstName("Jane");
        updateReq.setNationalId("99999999999");

        HttpEntity<CustomerRequest> reqEntity = new HttpEntity<>(updateReq);
        ResponseEntity<String> updateRes = restTemplate.exchange(
                "/api/customers/" + customerId, HttpMethod.PUT, reqEntity, String.class);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updateJson = objectMapper.readTree(updateRes.getBody());
        assertThat(updateJson.get("data").get("firstName").asText()).isEqualTo("Jane");
        assertThat(updateJson.get("data").get("nationalId").asText()).isEqualTo("99999999999");

        Optional<Customer> found = customerRepository.findById(customerId);
        assertThat(found).isPresent();
        assertThat(found.get().getFirstName()).isEqualTo("Jane");
        assertThat(found.get().getNationalId()).isEqualTo("99999999999");
        assertThat(found.get().getDeletedAt()).isNull();
    }

    @Test
    void createCustomerWithDuplicateNationalId_Returns400() throws Exception {
        restTemplate.postForEntity("/api/customers", createValidRequest(), String.class);

        CustomerRequest dup = createValidRequest();
        dup.setEmail("other@example.com");
        ResponseEntity<String> res = restTemplate.postForEntity(
                "/api/customers", dup, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode json = objectMapper.readTree(res.getBody());
        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("message").asText()).contains("already exists");
    }

    @Test
    void getCustomerById_NotFound_Returns404() throws Exception {
        ResponseEntity<String> res = restTemplate.getForEntity(
                "/api/customers/" + UUID.randomUUID(), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode json = objectMapper.readTree(res.getBody());
        assertThat(json.get("success").asBoolean()).isFalse();
    }

    private CustomerRequest createValidRequest() {
        CustomerRequest request = new CustomerRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setNationalId("12345678901");
        request.setEmail("john.doe@example.com");
        request.setPhone("+905551234567");
        request.setBirthDate(LocalDate.of(1990, 1, 15));
        request.setAddress("123 Main St");
        request.setCityId(34);
        request.setProfessionId(1);
        return request;
    }
}
