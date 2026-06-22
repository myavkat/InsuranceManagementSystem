package com.insurancemanagementsystem.insurance.controller;

import com.insurancemanagementsystem.common.web.exception.GlobalExceptionHandler;
import com.insurancemanagementsystem.insurance.dto.InsuranceCompanyRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceCompanyResponse;
import com.insurancemanagementsystem.insurance.dto.InsuranceRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceResponse;
import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import com.insurancemanagementsystem.insurance.service.InsuranceService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = InsuranceController.class)
@Import(GlobalExceptionHandler.class)
class InsuranceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient restTestClient;

    @MockitoBean
    private InsuranceService insuranceService;

    private final UUID testId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindTo(mockMvc).build();
    }

    private InsuranceResponse createSampleResponse() {
        return InsuranceResponse.builder()
                .id(testId)
                .name("Kasko Sigortası")
                .description("Kapsamlı kasko teminatı")
                .typeId(1)
                .typeName("Kasko")
                .companyId(companyId)
                .companyName("Anadolu Sigorta")
                .companyRating(new BigDecimal("4.5"))
                .basePremium(new BigDecimal("2500.00"))
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private InsuranceCompanyResponse createSampleCompanyResponse() {
        return InsuranceCompanyResponse.builder()
                .id(companyId)
                .name("Anadolu Sigorta")
                .rating(new BigDecimal("4.5"))
                .isActive(true)
                .build();
    }

    private InsuranceType createSampleInsuranceType() {
        return new InsuranceType(1, "Kasko");
    }

    // ---------------------------------------------------------------
    // 1. GET /api/insurances — paginated list
    // ---------------------------------------------------------------
    @Test
    void getAll_ReturnsPaginatedResponse() {
        // Arrange
        Page<InsuranceResponse> page = new PageImpl<>(List.of(createSampleResponse()));
        given(insuranceService.findAll(any(), any(), any(), any(Pageable.class))).willReturn(page);

        // Act & Assert
        restTestClient.get().uri("/api/insurances")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").exists()
                .jsonPath("$.data.content").isArray()
                .jsonPath("$.data.content[0].name").isEqualTo("Kasko Sigortası")
                .jsonPath("$.data.content[0].typeName").isEqualTo("Kasko");

        verify(insuranceService).findAll(any(), any(), any(), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 2. GET /api/insurances?typeId=1 — filtered by type
    // ---------------------------------------------------------------
    @Test
    void getAll_WithTypeFilter_ReturnsFilteredResults() {
        // Arrange
        Page<InsuranceResponse> page = new PageImpl<>(List.of(createSampleResponse()));
        given(insuranceService.findAll(eq(1), any(), any(), any(Pageable.class))).willReturn(page);

        // Act & Assert
        restTestClient.get().uri("/api/insurances?typeId=1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").exists();

        verify(insuranceService).findAll(eq(1), any(), any(), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 3. GET /api/insurances?search=Kasko — filtered by search
    // ---------------------------------------------------------------
    @Test
    void getAll_WithSearch_ReturnsFilteredResults() {
        // Arrange
        Page<InsuranceResponse> page = new PageImpl<>(List.of(createSampleResponse()));
        given(insuranceService.findAll(any(), any(), eq("Kasko"), any(Pageable.class))).willReturn(page);

        // Act & Assert
        restTestClient.get().uri("/api/insurances?search=Kasko")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").exists();

        verify(insuranceService).findAll(any(), any(), eq("Kasko"), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 4. GET /api/insurances/{id} — found
    // ---------------------------------------------------------------
    @Test
    void getById_ReturnsInsurance() {
        // Arrange
        InsuranceResponse response = createSampleResponse();
        given(insuranceService.findById(testId)).willReturn(response);

        // Act & Assert
        restTestClient.get().uri("/api/insurances/{id}", testId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.name").isEqualTo("Kasko Sigortası")
                .jsonPath("$.data.typeName").isEqualTo("Kasko")
                .jsonPath("$.data.companyName").isEqualTo("Anadolu Sigorta")
                .jsonPath("$.data.basePremium").isEqualTo(2500.00);

        verify(insuranceService).findById(testId);
    }

    // ---------------------------------------------------------------
    // 5. GET /api/insurances/{id} — not found → 404
    // ---------------------------------------------------------------
    @Test
    void getById_NotFound_Returns404() {
        // Arrange
        given(insuranceService.findById(testId))
                .willThrow(new EntityNotFoundException("Insurance not found with id: " + testId));

        // Act & Assert
        restTestClient.get().uri("/api/insurances/{id}", testId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Insurance not found with id: " + testId);

        verify(insuranceService).findById(testId);
    }

    // ---------------------------------------------------------------
    // 6. POST /api/insurances — valid body → 201
    // ---------------------------------------------------------------
    @Test
    void create_WithValidBody_Returns201() {
        // Arrange
        InsuranceRequest request = new InsuranceRequest();
        request.setName("Kasko Sigortası");
        request.setDescription("Kapsamlı kasko teminatı");
        request.setTypeId(1);
        request.setCompanyId(companyId);
        request.setBasePremium(new BigDecimal("2500.00"));
        request.setIsActive(true);

        InsuranceResponse response = createSampleResponse();
        given(insuranceService.create(any(InsuranceRequest.class))).willReturn(response);

        // Act & Assert
        restTestClient.post().uri("/api/insurances")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Insurance created successfully")
                .jsonPath("$.data.name").isEqualTo("Kasko Sigortası");

        verify(insuranceService).create(any(InsuranceRequest.class));
    }

    // ---------------------------------------------------------------
    // 7. POST /api/insurances — invalid body → 400
    // ---------------------------------------------------------------
    @Test
    void create_WithInvalidBody_Returns400() {
        // Arrange (empty object — missing required fields)

        // Act & Assert
        restTestClient.post().uri("/api/insurances")
                .body(Collections.emptyMap())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isNotEmpty();

        verify(insuranceService, never()).create(any(InsuranceRequest.class));
    }

    // ---------------------------------------------------------------
    // 8. PUT /api/insurances/{id} — valid body → 200
    // ---------------------------------------------------------------
    @Test
    void update_Returns200() {
        // Arrange
        InsuranceRequest request = new InsuranceRequest();
        request.setName("Kasko Sigortası");
        request.setDescription("Kapsamlı kasko teminatı");
        request.setTypeId(1);
        request.setCompanyId(companyId);
        request.setBasePremium(new BigDecimal("2500.00"));
        request.setIsActive(true);

        InsuranceResponse response = createSampleResponse();
        given(insuranceService.update(any(UUID.class), any(InsuranceRequest.class))).willReturn(response);

        // Act & Assert
        restTestClient.put().uri("/api/insurances/{id}", testId)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Insurance updated successfully")
                .jsonPath("$.data.name").isEqualTo("Kasko Sigortası");

        verify(insuranceService).update(any(UUID.class), any(InsuranceRequest.class));
    }

    // ---------------------------------------------------------------
    // 9. DELETE /api/insurances/{id} → 200
    // ---------------------------------------------------------------
    @Test
    void delete_Returns200() {
        // Arrange
        InsuranceResponse response = createSampleResponse();
        given(insuranceService.softDelete(any(UUID.class))).willReturn(response);

        // Act & Assert
        restTestClient.delete().uri("/api/insurances/{id}", testId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Insurance deactivated successfully")
                .jsonPath("$.data.name").isEqualTo("Kasko Sigortası");

        verify(insuranceService).softDelete(any(UUID.class));
    }

    // ---------------------------------------------------------------
    // 10. GET /api/insurances/types — type list
    // ---------------------------------------------------------------
    @Test
    void getTypes_ReturnsTypeList() {
        // Arrange
        List<InsuranceType> types = List.of(createSampleInsuranceType());
        given(insuranceService.getAllTypes()).willReturn(types);

        // Act & Assert
        restTestClient.get().uri("/api/insurances/types")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data[0].id").isEqualTo(1)
                .jsonPath("$.data[0].name").isEqualTo("Kasko");

        verify(insuranceService).getAllTypes();
    }

    // ---------------------------------------------------------------
    // 11. GET /api/insurances/companies — paginated company list
    // ---------------------------------------------------------------
    @Test
    void getCompanies_ReturnsPaginatedResponse() {
        // Arrange
        Page<InsuranceCompanyResponse> page = new PageImpl<>(List.of(createSampleCompanyResponse()));
        given(insuranceService.findAllCompanies(any(Pageable.class))).willReturn(page);

        // Act & Assert
        restTestClient.get().uri("/api/insurances/companies")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").exists()
                .jsonPath("$.data.content").isArray()
                .jsonPath("$.data.content[0].name").isEqualTo("Anadolu Sigorta");

        verify(insuranceService).findAllCompanies(any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 12. POST /api/insurances/companies — valid body → 201
    // ---------------------------------------------------------------
    @Test
    void createCompany_WithValidBody_Returns201() {
        // Arrange
        InsuranceCompanyRequest request = new InsuranceCompanyRequest();
        request.setName("Anadolu Sigorta");
        request.setRating(new BigDecimal("4.5"));
        request.setIsActive(true);

        InsuranceCompanyResponse response = createSampleCompanyResponse();
        given(insuranceService.createCompany(any(InsuranceCompanyRequest.class))).willReturn(response);

        // Act & Assert
        restTestClient.post().uri("/api/insurances/companies")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Insurance company created successfully")
                .jsonPath("$.data.name").isEqualTo("Anadolu Sigorta");

        verify(insuranceService).createCompany(any(InsuranceCompanyRequest.class));
    }

    // ---------------------------------------------------------------
    // 13. PUT /api/insurances/companies/{id} — valid body → 200
    // ---------------------------------------------------------------
    @Test
    void updateCompany_Returns200() {
        // Arrange
        InsuranceCompanyRequest request = new InsuranceCompanyRequest();
        request.setName("Anadolu Sigorta");
        request.setRating(new BigDecimal("4.8"));
        request.setIsActive(true);

        InsuranceCompanyResponse response = InsuranceCompanyResponse.builder()
                .id(companyId)
                .name("Anadolu Sigorta")
                .rating(new BigDecimal("4.8"))
                .isActive(true)
                .build();

        given(insuranceService.updateCompany(any(UUID.class), any(InsuranceCompanyRequest.class))).willReturn(response);

        // Act & Assert
        restTestClient.put().uri("/api/insurances/companies/{id}", companyId)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Insurance company updated successfully")
                .jsonPath("$.data.name").isEqualTo("Anadolu Sigorta")
                .jsonPath("$.data.rating").isEqualTo(4.8);

        verify(insuranceService).updateCompany(any(UUID.class), any(InsuranceCompanyRequest.class));
    }
}
