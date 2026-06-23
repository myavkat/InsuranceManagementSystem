package com.insurancemanagementsystem.estimation.controller;

import com.insurancemanagementsystem.common.web.exception.GlobalExceptionHandler;
import com.insurancemanagementsystem.estimation.dto.EstimationRequest;
import com.insurancemanagementsystem.estimation.dto.EstimationResponse;
import com.insurancemanagementsystem.estimation.service.EstimationService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = EstimationController.class)
@Import(GlobalExceptionHandler.class)
class EstimationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient restTestClient;

    @MockitoBean
    private EstimationService estimationService;

    private final UUID testId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();
    private final Integer insuranceTypeId = 1;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindTo(mockMvc).build();
    }

    private EstimationResponse createSampleResponse() {
        return EstimationResponse.builder()
                .id(testId)
                .sagaId(UUID.randomUUID())
                .customerId(customerId)
                .vehicleId(vehicleId)
                .insuranceTypeId(insuranceTypeId)
                .companyId(companyId)
                .status("STARTED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ---------------------------------------------------------------
    // 1. POST /api/estimations — valid request → 201 CREATED
    // ---------------------------------------------------------------
    @Test
    void create_WithValidRequest_Returns201() {
        EstimationRequest request = new EstimationRequest();
        request.setCustomerId(customerId);
        request.setVehicleId(vehicleId);
        request.setInsuranceTypeId(insuranceTypeId);
        request.setCompanyId(companyId);

        EstimationResponse response = createSampleResponse();
        given(estimationService.create(any(EstimationRequest.class))).willReturn(response);

        restTestClient.post().uri("/api/estimations")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Estimation created successfully")
                .jsonPath("$.data.id").isEqualTo(testId.toString())
                .jsonPath("$.data.status").isEqualTo("STARTED");

        verify(estimationService).create(any(EstimationRequest.class));
    }

    // ---------------------------------------------------------------
    // 2. POST /api/estimations — missing customerId → 400 BAD_REQUEST
    // ---------------------------------------------------------------
    @Test
    void create_WithMissingCustomerId_Returns400() {
        restTestClient.post().uri("/api/estimations")
                .body(new EstimationRequest())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isNotEmpty();

        verify(estimationService, never()).create(any(EstimationRequest.class));
    }

    // ---------------------------------------------------------------
    // 3. POST /api/estimations — both vehicleId and realEstateId null → 400
    // ---------------------------------------------------------------
    @Test
    void create_WithBothVehicleAndRealEstateNull_Returns400() {
        EstimationRequest request = new EstimationRequest();
        request.setCustomerId(customerId);
        request.setInsuranceTypeId(insuranceTypeId);
        request.setCompanyId(companyId);

        given(estimationService.create(any(EstimationRequest.class)))
                .willThrow(new IllegalArgumentException("Either vehicleId or realEstateId must be provided"));

        restTestClient.post().uri("/api/estimations")
                .body(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Either vehicleId or realEstateId must be provided");

        verify(estimationService).create(any(EstimationRequest.class));
    }

    // ---------------------------------------------------------------
    // 4. GET /api/estimations/{id} — existing id → 200 OK
    // ---------------------------------------------------------------
    @Test
    void getById_WhenExists_Returns200() {
        EstimationResponse response = createSampleResponse();
        given(estimationService.findById(testId)).willReturn(response);

        restTestClient.get().uri("/api/estimations/{id}", testId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.id").isEqualTo(testId.toString())
                .jsonPath("$.data.status").isEqualTo("STARTED");

        verify(estimationService).findById(testId);
    }

    // ---------------------------------------------------------------
    // 5. GET /api/estimations/{id} — non-existing id → 404 NOT_FOUND
    // ---------------------------------------------------------------
    @Test
    void getById_WhenNotExists_Returns404() {
        given(estimationService.findById(testId))
                .willThrow(new EntityNotFoundException("Estimation not found with id: " + testId));

        restTestClient.get().uri("/api/estimations/{id}", testId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Estimation not found with id: " + testId);

        verify(estimationService).findById(testId);
    }

    // ---------------------------------------------------------------
    // 6. GET /api/estimations — no filters → 200 OK + paginated list
    // ---------------------------------------------------------------
    @Test
    void getAll_WithNoFilters_ReturnsPaginatedList() {
        Page<EstimationResponse> page = new PageImpl<>(List.of(createSampleResponse()));
        given(estimationService.findAll(isNull(), isNull(), any(Pageable.class))).willReturn(page);

        restTestClient.get().uri("/api/estimations")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.content").isArray()
                .jsonPath("$.data.content[0].id").isEqualTo(testId.toString())
                .jsonPath("$.data.content[0].status").isEqualTo("STARTED");

        verify(estimationService).findAll(isNull(), isNull(), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 7. GET /api/estimations?customerId=... → 200 OK + filtered list
    // ---------------------------------------------------------------
    @Test
    void getAll_WithCustomerIdFilter_ReturnsFilteredList() {
        Page<EstimationResponse> page = new PageImpl<>(List.of(createSampleResponse()));
        given(estimationService.findAll(eq(customerId), isNull(), any(Pageable.class))).willReturn(page);

        restTestClient.get().uri("/api/estimations?customerId={customerId}", customerId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.content").isArray()
                .jsonPath("$.data.content[0].id").isEqualTo(testId.toString());

        verify(estimationService).findAll(eq(customerId), isNull(), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 8. GET /api/estimations?status=STARTED → 200 OK + filtered list
    // ---------------------------------------------------------------
    @Test
    void getAll_WithStatusFilter_ReturnsFilteredList() {
        Page<EstimationResponse> page = new PageImpl<>(List.of(createSampleResponse()));
        given(estimationService.findAll(isNull(), eq("STARTED"), any(Pageable.class))).willReturn(page);

        restTestClient.get().uri("/api/estimations?status=STARTED")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.content").isArray()
                .jsonPath("$.data.content[0].id").isEqualTo(testId.toString());

        verify(estimationService).findAll(isNull(), eq("STARTED"), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 9. GET /api/estimations?status=INVALID → 400 BAD_REQUEST
    // ---------------------------------------------------------------
    @Test
    void getAll_WithInvalidStatus_Returns400() {
        given(estimationService.findAll(isNull(), eq("INVALID"), any(Pageable.class)))
                .willThrow(new IllegalArgumentException("Invalid status: 'INVALID'. Valid values: STARTED, COMPLETED, REJECTED"));

        restTestClient.get().uri("/api/estimations?status=INVALID")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Invalid status: 'INVALID'. Valid values: STARTED, COMPLETED, REJECTED");
    }
}
