package com.insurancemanagementsystem.realestate.controller;

import com.insurancemanagementsystem.common.web.exception.GlobalExceptionHandler;
import com.insurancemanagementsystem.realestate.dto.RealEstateRequest;
import com.insurancemanagementsystem.realestate.dto.RealEstateResponse;
import com.insurancemanagementsystem.realestate.entity.RealEstateConstructionType;
import com.insurancemanagementsystem.realestate.entity.RealEstateLuxuryClass;
import com.insurancemanagementsystem.realestate.entity.RealEstateUsageType;
import com.insurancemanagementsystem.realestate.service.RealEstateService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = RealEstateController.class)
@Import(GlobalExceptionHandler.class)
class RealEstateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient restTestClient;

    @MockitoBean
    private RealEstateService realEstateService;

    private final UUID testId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindTo(mockMvc).build();
    }

    private RealEstateResponse createSampleResponse() {
        return RealEstateResponse.builder()
                .id(testId)
                .address("123 Main St")
                .cityId(34)
                .district("Kadıköy")
                .squareMeters(new BigDecimal("120.50"))
                .constructionYear(2020)
                .constructionTypeId(1)
                .constructionTypeName("Concrete")
                .luxuryClassId(2)
                .luxuryClassName("A")
                .usageTypeId(3)
                .usageTypeName("Residential")
                .customerId(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ---------------------------------------------------------------
    // 1. GET /api/real-estate — paginated list
    // ---------------------------------------------------------------
    @Test
    void getAll_ReturnsPaginatedResponse() {
        // Arrange
        Page<RealEstateResponse> page = new PageImpl<>(List.of(createSampleResponse()));
        given(realEstateService.findAll(any(Pageable.class), any(), any())).willReturn(page);

        // Act & Assert
        restTestClient.get().uri("/api/real-estate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").exists()
                .jsonPath("$.data.content").isArray()
                .jsonPath("$.data.content[0].address").isEqualTo("123 Main St")
                .jsonPath("$.data.content[0].squareMeters").isEqualTo(120.50);

        verify(realEstateService).findAll(any(Pageable.class), any(), any());
    }

    // ---------------------------------------------------------------
    // 2. GET /api/real-estate/{id} — found
    // ---------------------------------------------------------------
    @Test
    void getById_ReturnsRealEstate() {
        // Arrange
        RealEstateResponse response = createSampleResponse();
        given(realEstateService.findById(testId)).willReturn(response);

        // Act & Assert
        restTestClient.get().uri("/api/real-estate/{id}", testId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.address").isEqualTo("123 Main St")
                .jsonPath("$.data.cityId").isEqualTo(34)
                .jsonPath("$.data.constructionTypeName").isEqualTo("Concrete");

        verify(realEstateService).findById(testId);
    }

    // ---------------------------------------------------------------
    // 3. GET /api/real-estate/{id} — not found → 404
    // ---------------------------------------------------------------
    @Test
    void getById_NotFound_Returns404() {
        // Arrange
        given(realEstateService.findById(testId))
                .willThrow(new EntityNotFoundException("RealEstate not found with id: " + testId));

        // Act & Assert
        restTestClient.get().uri("/api/real-estate/{id}", testId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("RealEstate not found with id: " + testId);

        verify(realEstateService).findById(testId);
    }

    // ---------------------------------------------------------------
    // 4. POST /api/real-estate — valid body → 201
    // ---------------------------------------------------------------
    @Test
    void create_WithValidBody_Returns201() {
        // Arrange
        RealEstateRequest request = new RealEstateRequest();
        request.setAddress("123 Main St");
        request.setCityId(34);
        request.setDistrict("Kadıköy");
        request.setSquareMeters(new BigDecimal("120.50"));
        request.setConstructionYear(2020);
        request.setConstructionTypeId(1);
        request.setLuxuryClassId(2);
        request.setUsageTypeId(3);
        request.setCustomerId(UUID.randomUUID());

        RealEstateResponse response = createSampleResponse();
        given(realEstateService.create(any(RealEstateRequest.class))).willReturn(response);

        // Act & Assert
        restTestClient.post().uri("/api/real-estate")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("RealEstate created successfully")
                .jsonPath("$.data.address").isEqualTo("123 Main St")
                .jsonPath("$.data.squareMeters").isEqualTo(120.50);

        verify(realEstateService).create(any(RealEstateRequest.class));
    }

    // ---------------------------------------------------------------
    // 5. POST /api/real-estate — missing address → 400
    // ---------------------------------------------------------------
    @Test
    void create_WithoutAddress_Returns400() {
        // Arrange — JSON with all fields except address (blank)
        String requestJson = """
                {
                    "cityId": 34,
                    "squareMeters": 120.50,
                    "constructionTypeId": 1,
                    "luxuryClassId": 2,
                    "usageTypeId": 3,
                    "customerId": "%s"
                }
                """.formatted(UUID.randomUUID().toString());

        // Act & Assert
        restTestClient.post().uri("/api/real-estate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").value(msg ->
                        org.assertj.core.api.Assertions.assertThat((String) msg).contains("address", "Address"));
    }

    // ---------------------------------------------------------------
    // 6. POST /api/real-estate — squareMeters = 0 → 400
    // ---------------------------------------------------------------
    @Test
    void create_WithInvalidSquareMeters_Returns400() {
        // Arrange — JSON with squareMeters=0
        String requestJson = """
                {
                    "address": "123 Main St",
                    "cityId": 34,
                    "squareMeters": 0,
                    "constructionTypeId": 1,
                    "luxuryClassId": 2,
                    "usageTypeId": 3,
                    "customerId": "%s"
                }
                """.formatted(UUID.randomUUID().toString());

        // Act & Assert
        restTestClient.post().uri("/api/real-estate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").value(msg ->
                        org.assertj.core.api.Assertions.assertThat((String) msg).contains("squareMeters"));
    }

    // ---------------------------------------------------------------
    // 7. PUT /api/real-estate/{id} — valid body → 200
    // ---------------------------------------------------------------
    @Test
    void update_Returns200() {
        // Arrange
        RealEstateRequest request = new RealEstateRequest();
        request.setAddress("456 Oak Ave");
        request.setCityId(6);
        request.setDistrict("Beşiktaş");
        request.setSquareMeters(new BigDecimal("200.00"));
        request.setConstructionYear(2022);
        request.setConstructionTypeId(2);
        request.setLuxuryClassId(1);
        request.setUsageTypeId(2);
        request.setCustomerId(UUID.randomUUID());

        RealEstateResponse response = RealEstateResponse.builder()
                .id(testId)
                .address("456 Oak Ave")
                .cityId(6)
                .district("Beşiktaş")
                .squareMeters(new BigDecimal("200.00"))
                .constructionYear(2022)
                .constructionTypeId(2)
                .constructionTypeName("Steel")
                .luxuryClassId(1)
                .luxuryClassName("B")
                .usageTypeId(2)
                .usageTypeName("Commercial")
                .customerId(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        given(realEstateService.update(any(UUID.class), any(RealEstateRequest.class))).willReturn(response);

        // Act & Assert
        restTestClient.put().uri("/api/real-estate/{id}", testId)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("RealEstate updated successfully")
                .jsonPath("$.data.address").isEqualTo("456 Oak Ave")
                .jsonPath("$.data.squareMeters").isEqualTo(200.00);

        verify(realEstateService).update(any(UUID.class), any(RealEstateRequest.class));
    }

    // ---------------------------------------------------------------
    // 8. DELETE /api/real-estate/{id} → 200
    // ---------------------------------------------------------------
    @Test
    void delete_Returns200() {
        // Act & Assert
        restTestClient.delete().uri("/api/real-estate/{id}", testId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("RealEstate deleted successfully");

        verify(realEstateService).delete(testId);
    }

    // ---------------------------------------------------------------
    // 9. DELETE /api/real-estate/{id} — not found → 404
    // ---------------------------------------------------------------
    @Test
    void delete_NotFound_Returns404() {
        // Arrange
        doThrow(new EntityNotFoundException("RealEstate not found with id: " + testId))
                .when(realEstateService).delete(testId);

        // Act & Assert
        restTestClient.delete().uri("/api/real-estate/{id}", testId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("RealEstate not found with id: " + testId);

        verify(realEstateService).delete(testId);
    }

    // ---------------------------------------------------------------
    // 10. GET /api/real-estate/construction-types → 200 with list
    // ---------------------------------------------------------------
    @Test
    void getConstructionTypes_Returns200() {
        // Arrange
        List<RealEstateConstructionType> types = List.of(
                new RealEstateConstructionType(1, "Concrete"),
                new RealEstateConstructionType(2, "Steel"));
        given(realEstateService.getConstructionTypes()).willReturn(types);

        // Act & Assert
        restTestClient.get().uri("/api/real-estate/construction-types")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].name").isEqualTo("Concrete")
                .jsonPath("$.data[1].name").isEqualTo("Steel");

        verify(realEstateService).getConstructionTypes();
    }

    // ---------------------------------------------------------------
    // 11. GET /api/real-estate/luxury-classes → 200 with list
    // ---------------------------------------------------------------
    @Test
    void getLuxuryClasses_Returns200() {
        // Arrange
        List<RealEstateLuxuryClass> classes = List.of(
                new RealEstateLuxuryClass(1, "A"),
                new RealEstateLuxuryClass(2, "B"));
        given(realEstateService.getLuxuryClasses()).willReturn(classes);

        // Act & Assert
        restTestClient.get().uri("/api/real-estate/luxury-classes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].name").isEqualTo("A")
                .jsonPath("$.data[1].name").isEqualTo("B");

        verify(realEstateService).getLuxuryClasses();
    }

    // ---------------------------------------------------------------
    // 12. GET /api/real-estate/usage-types → 200 with list
    // ---------------------------------------------------------------
    @Test
    void getUsageTypes_Returns200() {
        // Arrange
        List<RealEstateUsageType> types = List.of(
                new RealEstateUsageType(1, "Residential"),
                new RealEstateUsageType(2, "Commercial"));
        given(realEstateService.getUsageTypes()).willReturn(types);

        // Act & Assert
        restTestClient.get().uri("/api/real-estate/usage-types")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].name").isEqualTo("Residential")
                .jsonPath("$.data[1].name").isEqualTo("Commercial");

        verify(realEstateService).getUsageTypes();
    }
}
