package com.insurancemanagementsystem.customer.controller;

import com.insurancemanagementsystem.customer.dto.CustomerRequest;
import com.insurancemanagementsystem.customer.dto.CustomerResponse;
import com.insurancemanagementsystem.common.web.exception.GlobalExceptionHandler;
import com.insurancemanagementsystem.customer.service.CustomerService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = CustomerController.class)
@Import(GlobalExceptionHandler.class)
class CustomerControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private RestTestClient restTestClient;

	@MockitoBean
	private CustomerService customerService;

	private final UUID testId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		restTestClient = RestTestClient.bindTo(mockMvc).build();
	}

	private CustomerResponse createSampleResponse() {
		return CustomerResponse.builder()
			.id(testId)
			.firstName("John")
			.lastName("Doe")
			.nationalId("12345678901")
			.email("john.doe@example.com")
			.phone("+905551234567")
			.birthDate(LocalDate.of(1990, 1, 15))
			.address("123 Main St")
			.cityId(34)
			.professionId(1)
			.createdAt(Instant.now())
			.updatedAt(Instant.now())
			.build();
	}

	// ---------------------------------------------------------------
	// 1. GET /api/customers — paginated list
	// ---------------------------------------------------------------
	@Test
	void getAll_ReturnsPaginatedResponse() {
		// Arrange
		Page<CustomerResponse> page = new PageImpl<>(List.of(createSampleResponse()));
		given(customerService.findAll(any(Pageable.class))).willReturn(page);

		// Act & Assert
		restTestClient.get()
			.uri("/api/customers")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(true)
			.jsonPath("$.data")
			.exists()
			.jsonPath("$.data.content")
			.isArray()
			.jsonPath("$.data.content[0].firstName")
			.isEqualTo("John")
			.jsonPath("$.data.content[0].lastName")
			.isEqualTo("Doe");

		verify(customerService).findAll(any(Pageable.class));
	}

	// ---------------------------------------------------------------
	// 2. GET /api/customers?search=Doe — filtered results
	// ---------------------------------------------------------------
	@Test
	void getAll_WithSearch_ReturnsFilteredResults() {
		// Arrange
		Page<CustomerResponse> page = new PageImpl<>(List.of(createSampleResponse()));
		given(customerService.search(eq("Doe"), any(), any(Pageable.class))).willReturn(page);

		// Act & Assert
		restTestClient.get()
			.uri("/api/customers?search=Doe")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(true)
			.jsonPath("$.data")
			.exists();

		verify(customerService).search(eq("Doe"), any(), any(Pageable.class));
		verify(customerService, never()).findAll(any(Pageable.class));
	}

	// ---------------------------------------------------------------
	// 3. GET /api/customers/{id} — found
	// ---------------------------------------------------------------
	@Test
	void getById_ReturnsCustomer() {
		// Arrange
		CustomerResponse response = createSampleResponse();
		given(customerService.findById(testId)).willReturn(response);

		// Act & Assert
		restTestClient.get()
			.uri("/api/customers/{id}", testId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(true)
			.jsonPath("$.data.firstName")
			.isEqualTo("John")
			.jsonPath("$.data.lastName")
			.isEqualTo("Doe")
			.jsonPath("$.data.nationalId")
			.isEqualTo("12345678901")
			.jsonPath("$.data.email")
			.isEqualTo("john.doe@example.com");

		verify(customerService).findById(testId);
	}

	// ---------------------------------------------------------------
	// 4. GET /api/customers/{id} — not found → 404
	// ---------------------------------------------------------------
	@Test
	void getById_NotFound_Returns404() {
		// Arrange
		given(customerService.findById(testId))
			.willThrow(new EntityNotFoundException("Customer not found with id: " + testId));

		// Act & Assert
		restTestClient.get()
			.uri("/api/customers/{id}", testId)
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(false)
			.jsonPath("$.message")
			.isEqualTo("Customer not found with id: " + testId);

		verify(customerService).findById(testId);
	}

	// ---------------------------------------------------------------
	// 5. POST /api/customers — valid body → 201
	// ---------------------------------------------------------------
	@Test
	void create_WithValidBody_Returns201() {
		// Arrange
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

		CustomerResponse response = createSampleResponse();
		given(customerService.create(any(CustomerRequest.class))).willReturn(response);

		// Act & Assert
		restTestClient.post()
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
			.jsonPath("$.data.lastName")
			.isEqualTo("Doe");

		verify(customerService).create(any(CustomerRequest.class));
	}

	// ---------------------------------------------------------------
	// 6. POST /api/customers — invalid body → 400
	// ---------------------------------------------------------------
	@Test
	void create_WithInvalidBody_Returns400() {
		// Arrange (empty object — missing required fields)

		// Act & Assert
		restTestClient.post()
			.uri("/api/customers")
			.body(Collections.emptyMap())
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(false)
			.jsonPath("$.message")
			.isNotEmpty();

		verify(customerService, never()).create(any(CustomerRequest.class));
	}

	// ---------------------------------------------------------------
	// 7. PUT /api/customers/{id} — valid body → 200
	// ---------------------------------------------------------------
	@Test
	void update_Returns200() {
		// Arrange
		CustomerRequest request = new CustomerRequest();
		request.setFirstName("Jane");
		request.setLastName("Smith");
		request.setNationalId("98765432109");
		request.setEmail("jane.smith@example.com");
		request.setPhone("+905551111111");
		request.setBirthDate(LocalDate.of(1985, 5, 20));
		request.setAddress("456 Oak Ave");
		request.setCityId(6);
		request.setProfessionId(2);

		CustomerResponse response = CustomerResponse.builder()
			.id(testId)
			.firstName("Jane")
			.lastName("Smith")
			.nationalId("98765432109")
			.email("jane.smith@example.com")
			.phone("+905551111111")
			.birthDate(LocalDate.of(1985, 5, 20))
			.address("456 Oak Ave")
			.cityId(6)
			.professionId(2)
			.createdAt(Instant.now())
			.updatedAt(Instant.now())
			.build();

		given(customerService.update(any(UUID.class), any(CustomerRequest.class))).willReturn(response);

		// Act & Assert
		restTestClient.put()
			.uri("/api/customers/{id}", testId)
			.body(request)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(true)
			.jsonPath("$.message")
			.isEqualTo("Customer updated successfully")
			.jsonPath("$.data.firstName")
			.isEqualTo("Jane")
			.jsonPath("$.data.nationalId")
			.isEqualTo("98765432109");

		verify(customerService).update(any(UUID.class), any(CustomerRequest.class));
	}

	// ---------------------------------------------------------------
	// 8. DELETE /api/customers/{id} → 200
	// ---------------------------------------------------------------
	@Test
	void delete_Returns200() {
		// Arrange
		CustomerResponse response = createSampleResponse();
		given(customerService.softDelete(any(UUID.class))).willReturn(response);

		// Act & Assert
		restTestClient.delete()
			.uri("/api/customers/{id}", testId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.success")
			.isEqualTo(true)
			.jsonPath("$.message")
			.isEqualTo("Customer deleted successfully")
			.jsonPath("$.data.firstName")
			.isEqualTo("John");

		verify(customerService).softDelete(any(UUID.class));
	}

}
