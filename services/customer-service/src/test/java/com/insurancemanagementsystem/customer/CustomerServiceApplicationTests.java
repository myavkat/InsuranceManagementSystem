package com.insurancemanagementsystem.customer;

import tools.jackson.databind.ObjectMapper;
import com.insurancemanagementsystem.customer.dto.CustomerRequest;
import com.insurancemanagementsystem.customer.entity.Customer;
import com.insurancemanagementsystem.customer.repository.CustomerRepository;
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

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
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
	private RestTestClient restTestClient;

	@Autowired
	private CustomerRepository customerRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

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

		var result = restTestClient.post()
			.uri("/api/customers")
			.body(request)
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(true)
			.jsonPath("$.message")
			.isEqualTo("Customer created successfully")
			.jsonPath("$.data.firstName")
			.isEqualTo("John")
			.returnResult();

		// Extract ID for DB verification
		UUID customerId = UUID
			.fromString(objectMapper.readTree(result.getResponseBodyContent()).get("data").get("id").asText());

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

		restTestClient.post().uri("/api/customers").body(c1).exchange().expectStatus().isCreated();
		restTestClient.post().uri("/api/customers").body(c2).exchange().expectStatus().isCreated();

		// Search matching 1 customer
		restTestClient.get()
			.uri("/api/customers?search=Doe")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.data.totalElements")
			.isEqualTo(1);

		// Search matching both (both names contain "e")
		restTestClient.get()
			.uri("/api/customers?search=e")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.data.totalElements")
			.isEqualTo(2);

		// No search param — returns all
		restTestClient.get()
			.uri("/api/customers")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.data.totalElements")
			.isEqualTo(2);
	}

	@Test
	void softDeleteCustomer() throws Exception {
		byte[] createBody = restTestClient.post()
			.uri("/api/customers")
			.body(createValidRequest())
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody()
			.returnResult()
			.getResponseBodyContent();
		UUID customerId = UUID.fromString(objectMapper.readTree(createBody).get("data").get("id").asText());

		restTestClient.delete()
			.uri("/api/customers/{id}", customerId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(true);

		// Verify GET returns 404
		restTestClient.get().uri("/api/customers/{id}", customerId).exchange().expectStatus().isNotFound();

		// Verify deletedAt in DB
		Optional<Customer> found = customerRepository.findById(customerId);
		assertThat(found).isPresent();
		assertThat(found.get().getDeletedAt()).isNotNull();
	}

	@Test
	void updateCustomer() throws Exception {
		byte[] createBody = restTestClient.post()
			.uri("/api/customers")
			.body(createValidRequest())
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody()
			.returnResult()
			.getResponseBodyContent();
		UUID customerId = UUID.fromString(objectMapper.readTree(createBody).get("data").get("id").asText());

		CustomerRequest updateReq = createValidRequest();
		updateReq.setFirstName("Jane");
		updateReq.setNationalId("99999999999");

		restTestClient.put()
			.uri("/api/customers/{id}", customerId)
			.body(updateReq)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(true)
			.jsonPath("$.data.firstName")
			.isEqualTo("Jane")
			.jsonPath("$.data.nationalId")
			.isEqualTo("99999999999");

		Optional<Customer> found = customerRepository.findById(customerId);
		assertThat(found).isPresent();
		assertThat(found.get().getFirstName()).isEqualTo("Jane");
		assertThat(found.get().getNationalId()).isEqualTo("99999999999");
		assertThat(found.get().getDeletedAt()).isNull();
	}

	@Test
	void createCustomerWithDuplicateNationalId_Returns400() {
		restTestClient.post().uri("/api/customers").body(createValidRequest()).exchange().expectStatus().isCreated();

		CustomerRequest dup = createValidRequest();
		dup.setEmail("other@example.com");
		restTestClient.post()
			.uri("/api/customers")
			.body(dup)
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(false);
	}

	@Test
	void getCustomerById_NotFound_Returns404() {
		restTestClient.get()
			.uri("/api/customers/{id}", UUID.randomUUID())
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(false);
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