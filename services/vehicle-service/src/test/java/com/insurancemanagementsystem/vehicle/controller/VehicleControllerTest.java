package com.insurancemanagementsystem.vehicle.controller;

import com.insurancemanagementsystem.common.web.exception.GlobalExceptionHandler;
import com.insurancemanagementsystem.vehicle.dto.VehicleRequest;
import com.insurancemanagementsystem.vehicle.dto.VehicleResponse;
import com.insurancemanagementsystem.vehicle.entity.*;
import com.insurancemanagementsystem.vehicle.service.VehicleService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = VehicleController.class)
@Import(GlobalExceptionHandler.class)
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient client;

    @MockitoBean
    private VehicleService vehicleService;

    private final UUID testId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    private VehicleResponse createSampleResponse() {
        return VehicleResponse.builder()
                .id(testId)
                .plate("34 ABC 1234")
                .chassisNumber("1HGCM82633A004352")
                .licenseFirstDate(LocalDate.of(2020, 1, 15))
                .carBrandId(1)
                .carBrandName("TestBrand")
                .carModelId(10)
                .carModelName("TestModel")
                .carEngineId(100)
                .carEngineName("TestEngine")
                .carEngineVolume(new BigDecimal("2.0"))
                .carEnginePower(150)
                .carFuelTypeId(1000)
                .carFuelTypeName("Gasoline")
                .carTypeId(10000)
                .carTypeName("Sedan")
                .carPackageId(100000)
                .carPackageName("Base")
                .customerId(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ---------------------------------------------------------------
    // 1. GET /api/vehicles — paginated list
    // ---------------------------------------------------------------
    @Test
    void getAll_ReturnsPaginatedResponse() {
        Page<VehicleResponse> page = new PageImpl<>(List.of(createSampleResponse()));
        given(vehicleService.findAll(any(Pageable.class), any(), any())).willReturn(page);

        client.get().uri("/api/vehicles")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").exists()
                .jsonPath("$.data.content").isArray()
                .jsonPath("$.data.content[0].plate").isEqualTo("34 ABC 1234")
                .jsonPath("$.data.content[0].carBrandName").isEqualTo("TestBrand");

        verify(vehicleService).findAll(any(Pageable.class), any(), any());
    }

    // ---------------------------------------------------------------
    // 2. GET /api/vehicles/{id} — found
    // ---------------------------------------------------------------
    @Test
    void getById_ReturnsVehicle() {
        VehicleResponse response = createSampleResponse();
        given(vehicleService.findById(testId)).willReturn(response);

        client.get().uri("/api/vehicles/{id}", testId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.plate").isEqualTo("34 ABC 1234")
                .jsonPath("$.data.id").isEqualTo(testId.toString())
                .jsonPath("$.data.carBrandName").isEqualTo("TestBrand");

        verify(vehicleService).findById(testId);
    }

    // ---------------------------------------------------------------
    // 3. GET /api/vehicles/{id} — not found → 404
    // ---------------------------------------------------------------
    @Test
    void getById_NotFound_Returns404() {
        given(vehicleService.findById(testId))
                .willThrow(new EntityNotFoundException("Vehicle not found with id: " + testId));

        client.get().uri("/api/vehicles/{id}", testId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Vehicle not found with id: " + testId);

        verify(vehicleService).findById(testId);
    }

    // ---------------------------------------------------------------
    // 4. POST /api/vehicles — valid body → 201
    // ---------------------------------------------------------------
    @Test
    void create_WithValidBody_Returns201() {
        VehicleRequest request = new VehicleRequest();
        request.setPlate("34 ABC 1234");
        request.setChassisNumber("1HGCM82633A004352");
        request.setLicenseFirstDate(LocalDate.of(2020, 1, 15));
        request.setCarBrandId(1);
        request.setCarModelId(10);
        request.setCarEngineId(100);
        request.setCarFuelTypeId(1000);
        request.setCarTypeId(10000);
        request.setCarPackageId(100000);
        request.setCustomerId(UUID.randomUUID());

        VehicleResponse response = createSampleResponse();
        given(vehicleService.create(any(VehicleRequest.class))).willReturn(response);

        client.post().uri("/api/vehicles")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Vehicle created successfully")
                .jsonPath("$.data.plate").isEqualTo("34 ABC 1234");

        verify(vehicleService).create(any(VehicleRequest.class));
    }

    // ---------------------------------------------------------------
    // 5. POST /api/vehicles — invalid body (missing plate) → 400
    // ---------------------------------------------------------------
    @Test
    void create_WithMissingPlate_Returns400() {
        client.post().uri("/api/vehicles")
                .body(java.util.Collections.emptyMap())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isNotEmpty();

        verify(vehicleService, never()).create(any(VehicleRequest.class));
    }

    // ---------------------------------------------------------------
    // 6. PUT /api/vehicles/{id} — valid body → 200
    // ---------------------------------------------------------------
    @Test
    void update_Returns200() {
        VehicleRequest request = new VehicleRequest();
        request.setPlate("34 ABC 5678");
        request.setChassisNumber("1HGCM82633A004353");
        request.setLicenseFirstDate(LocalDate.of(2021, 3, 10));
        request.setCarBrandId(2);
        request.setCarModelId(20);
        request.setCarEngineId(200);
        request.setCarFuelTypeId(2000);
        request.setCarTypeId(20000);
        request.setCarPackageId(200000);
        request.setCustomerId(UUID.randomUUID());

        VehicleResponse response = VehicleResponse.builder()
                .id(testId)
                .plate("34 ABC 5678")
                .carBrandId(2)
                .carModelId(20)
                .build();

        given(vehicleService.update(any(UUID.class), any(VehicleRequest.class))).willReturn(response);

        client.put().uri("/api/vehicles/{id}", testId)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Vehicle updated successfully")
                .jsonPath("$.data.plate").isEqualTo("34 ABC 5678");

        verify(vehicleService).update(any(UUID.class), any(VehicleRequest.class));
    }

    // ---------------------------------------------------------------
    // 7. DELETE /api/vehicles/{id} → 200
    // ---------------------------------------------------------------
    @Test
    void delete_Returns200() {
        client.delete().uri("/api/vehicles/{id}", testId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Vehicle deleted successfully");

        verify(vehicleService).delete(testId);
    }

    // ---------------------------------------------------------------
    // 8. GET /api/vehicles/brands
    // ---------------------------------------------------------------
    @Test
    void getBrands_ReturnsBrandList() {
        List<CarBrand> brands = List.of(
                CarBrand.builder().id(1).name("BrandA").build());
        given(vehicleService.getBrands()).willReturn(brands);

        client.get().uri("/api/vehicles/brands")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data[0].name").isEqualTo("BrandA");

        verify(vehicleService).getBrands();
    }

    // ---------------------------------------------------------------
    // 9. GET /api/vehicles/brands/{brandId}/models
    // ---------------------------------------------------------------
    @Test
    void getModelsByBrand_ReturnsModelList() {
        List<CarModel> models = List.of(
                CarModel.builder().id(10).name("ModelX").brandId(1).build());
        given(vehicleService.getModelsByBrand(1)).willReturn(models);

        client.get().uri("/api/vehicles/brands/{brandId}/models", 1)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data[0].name").isEqualTo("ModelX");

        verify(vehicleService).getModelsByBrand(1);
    }

    // ---------------------------------------------------------------
    // 10. GET /api/vehicles/engines
    // ---------------------------------------------------------------
    @Test
    void getEngines_ReturnsEngineList() {
        List<CarEngine> engines = List.of(
                CarEngine.builder().id(100).name("Engine1").build());
        given(vehicleService.getEngines()).willReturn(engines);

        client.get().uri("/api/vehicles/engines")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data[0].name").isEqualTo("Engine1");

        verify(vehicleService).getEngines();
    }

    // ---------------------------------------------------------------
    // 11. GET /api/vehicles/fuel-types
    // ---------------------------------------------------------------
    @Test
    void getFuelTypes_ReturnsFuelTypeList() {
        List<CarFuelType> fuelTypes = List.of(
                CarFuelType.builder().id(1000).name("Gasoline").build());
        given(vehicleService.getFuelTypes()).willReturn(fuelTypes);

        client.get().uri("/api/vehicles/fuel-types")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data[0].name").isEqualTo("Gasoline");

        verify(vehicleService).getFuelTypes();
    }

    // ---------------------------------------------------------------
    // 12. GET /api/vehicles/types
    // ---------------------------------------------------------------
    @Test
    void getTypes_ReturnsTypeList() {
        List<CarType> types = List.of(
                CarType.builder().id(10000).name("Sedan").build());
        given(vehicleService.getTypes()).willReturn(types);

        client.get().uri("/api/vehicles/types")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data[0].name").isEqualTo("Sedan");

        verify(vehicleService).getTypes();
    }

    // ---------------------------------------------------------------
    // 13. GET /api/vehicles/packages
    // ---------------------------------------------------------------
    @Test
    void getPackages_ReturnsPackageList() {
        List<CarPackage> packages = List.of(
                CarPackage.builder().id(100000).name("Base").build());
        given(vehicleService.getPackages()).willReturn(packages);

        client.get().uri("/api/vehicles/packages")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data[0].name").isEqualTo("Base");

        verify(vehicleService).getPackages();
    }
}
