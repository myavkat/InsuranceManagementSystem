package com.insurancemanagementsystem.customer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurancemanagementsystem.customer.dto.CustomerRequest;
import com.insurancemanagementsystem.customer.dto.CustomerResponse;
import com.insurancemanagementsystem.customer.exception.GlobalExceptionHandler;
import com.insurancemanagementsystem.customer.service.CustomerService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CustomerController.class)
@Import(GlobalExceptionHandler.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @MockitoBean
    private CustomerService customerService;

    private final UUID testId = UUID.randomUUID();

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
    void getAll_ReturnsPaginatedResponse() throws Exception {
        // Arrange
        Page<CustomerResponse> page = new PageImpl<>(List.of(createSampleResponse()));
        given(customerService.findAll(any(Pageable.class))).willReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].firstName").value("John"))
                .andExpect(jsonPath("$.data.content[0].lastName").value("Doe"));

        verify(customerService).findAll(any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 2. GET /api/customers?search=Doe — filtered results
    // ---------------------------------------------------------------
    @Test
    void getAll_WithSearch_ReturnsFilteredResults() throws Exception {
        // Arrange
        Page<CustomerResponse> page = new PageImpl<>(List.of(createSampleResponse()));
        given(customerService.search(eq("Doe"), any(), any(Pageable.class))).willReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/customers").param("search", "Doe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());

        verify(customerService).search(eq("Doe"), any(), any(Pageable.class));
        verify(customerService, never()).findAll(any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 3. GET /api/customers/{id} — found
    // ---------------------------------------------------------------
    @Test
    void getById_ReturnsCustomer() throws Exception {
        // Arrange
        CustomerResponse response = createSampleResponse();
        given(customerService.findById(testId)).willReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/customers/{id}", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.lastName").value("Doe"))
                .andExpect(jsonPath("$.data.nationalId").value("12345678901"))
                .andExpect(jsonPath("$.data.email").value("john.doe@example.com"));

        verify(customerService).findById(testId);
    }

    // ---------------------------------------------------------------
    // 4. GET /api/customers/{id} — not found → 404
    // ---------------------------------------------------------------
    @Test
    void getById_NotFound_Returns404() throws Exception {
        // Arrange
        given(customerService.findById(testId))
                .willThrow(new EntityNotFoundException("Customer not found with id: " + testId));

        // Act & Assert
        mockMvc.perform(get("/api/customers/{id}", testId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Customer not found with id: " + testId));

        verify(customerService).findById(testId);
    }

    // ---------------------------------------------------------------
    // 5. POST /api/customers — valid body → 201
    // ---------------------------------------------------------------
    @Test
    void create_WithValidBody_Returns201() throws Exception {
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
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Customer created successfully"))
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.lastName").value("Doe"));

        verify(customerService).create(any(CustomerRequest.class));
    }

    // ---------------------------------------------------------------
    // 6. POST /api/customers — invalid body → 400
    // ---------------------------------------------------------------
    @Test
    void create_WithInvalidBody_Returns400() throws Exception {
        // Arrange
        String invalidBody = "{}";

        // Act & Assert
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Validation failed")));

        verify(customerService, never()).create(any(CustomerRequest.class));
    }

    // ---------------------------------------------------------------
    // 7. PUT /api/customers/{id} — valid body → 200
    // ---------------------------------------------------------------
    @Test
    void update_Returns200() throws Exception {
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
        mockMvc.perform(put("/api/customers/{id}", testId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Customer updated successfully"))
                .andExpect(jsonPath("$.data.firstName").value("Jane"))
                .andExpect(jsonPath("$.data.nationalId").value("98765432109"));

        verify(customerService).update(any(UUID.class), any(CustomerRequest.class));
    }

    // ---------------------------------------------------------------
    // 8. DELETE /api/customers/{id} → 200
    // ---------------------------------------------------------------
    @Test
    void delete_Returns200() throws Exception {
        // Arrange
        CustomerResponse response = createSampleResponse();
        given(customerService.softDelete(any(UUID.class))).willReturn(response);

        // Act & Assert
        mockMvc.perform(delete("/api/customers/{id}", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Customer deleted successfully"))
                .andExpect(jsonPath("$.data.firstName").value("John"));

        verify(customerService).softDelete(any(UUID.class));
    }
}
