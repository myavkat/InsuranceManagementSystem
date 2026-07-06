package com.insurancemanagementsystem.vehicle.service;

import com.insurancemanagementsystem.vehicle.config.VehicleEventPublisher;
import com.insurancemanagementsystem.vehicle.dto.VehicleRequest;
import com.insurancemanagementsystem.vehicle.dto.VehicleResponse;
import com.insurancemanagementsystem.vehicle.entity.*;
import com.insurancemanagementsystem.vehicle.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private CarBrandRepository carBrandRepository;

    @Mock
    private CarModelRepository carModelRepository;

    @Mock
    private CarEngineRepository carEngineRepository;

    @Mock
    private CarFuelTypeRepository carFuelTypeRepository;

    @Mock
    private CarTypeRepository carTypeRepository;

    @Mock
    private CarPackageRepository carPackageRepository;

    @Mock
    private VehicleEventPublisher vehicleEventPublisher;

    @InjectMocks
    private VehicleService vehicleService;

    @Captor
    private ArgumentCaptor<Vehicle> vehicleCaptor;

    private static final UUID TEST_ID = UUID.randomUUID();
    private static final String PLATE = "34 ABC 1234";
    private static final String CHASSIS_NUMBER = "1HGCM82633A004352";
    private static final Integer BRAND_ID = 1;
    private static final Integer MODEL_ID = 10;
    private static final Integer ENGINE_ID = 100;
    private static final Integer FUEL_TYPE_ID = 1000;
    private static final Integer TYPE_ID = 10000;
    private static final Integer PACKAGE_ID = 100000;
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private Vehicle vehicle;
    private VehicleRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new VehicleRequest();
        validRequest.setPlate(PLATE);
        validRequest.setChassisNumber(CHASSIS_NUMBER);
        validRequest.setLicenseFirstDate(LocalDate.of(2020, 1, 15));
        validRequest.setCarBrandId(BRAND_ID);
        validRequest.setCarModelId(MODEL_ID);
        validRequest.setCarEngineId(ENGINE_ID);
        validRequest.setCarFuelTypeId(FUEL_TYPE_ID);
        validRequest.setCarTypeId(TYPE_ID);
        validRequest.setCarPackageId(PACKAGE_ID);
        validRequest.setCustomerId(CUSTOMER_ID);

        vehicle = Vehicle.builder()
                .id(TEST_ID)
                .plate(PLATE)
                .chassisNumber(CHASSIS_NUMBER)
                .licenseFirstDate(LocalDate.of(2020, 1, 15))
                .carBrandId(BRAND_ID)
                .carModelId(MODEL_ID)
                .carEngineId(ENGINE_ID)
                .carFuelTypeId(FUEL_TYPE_ID)
                .carTypeId(TYPE_ID)
                .carPackageId(PACKAGE_ID)
                .customerId(CUSTOMER_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private void mockReferenceExists() {
        when(carBrandRepository.existsById(BRAND_ID)).thenReturn(true);
        when(carModelRepository.existsById(MODEL_ID)).thenReturn(true);
        when(carEngineRepository.existsById(ENGINE_ID)).thenReturn(true);
        when(carFuelTypeRepository.existsById(FUEL_TYPE_ID)).thenReturn(true);
        when(carTypeRepository.existsById(TYPE_ID)).thenReturn(true);
        when(carPackageRepository.existsById(PACKAGE_ID)).thenReturn(true);
    }

    private void mockReferenceFindAll() {
        when(carBrandRepository.findById(BRAND_ID)).thenReturn(Optional.of(
                CarBrand.builder().id(BRAND_ID).name("TestBrand").build()));
        when(carModelRepository.findById(MODEL_ID)).thenReturn(Optional.of(
                CarModel.builder().id(MODEL_ID).name("TestModel").brandId(BRAND_ID).build()));
        when(carEngineRepository.findById(ENGINE_ID)).thenReturn(Optional.of(
                CarEngine.builder().id(ENGINE_ID).name("TestEngine").volume(new BigDecimal("2.0")).power(150).build()));
        when(carFuelTypeRepository.findById(FUEL_TYPE_ID)).thenReturn(Optional.of(
                CarFuelType.builder().id(FUEL_TYPE_ID).name("TestFuelType").build()));
        when(carTypeRepository.findById(TYPE_ID)).thenReturn(Optional.of(
                CarType.builder().id(TYPE_ID).name("TestType").build()));
        when(carPackageRepository.findById(PACKAGE_ID)).thenReturn(Optional.of(
                CarPackage.builder().id(PACKAGE_ID).name("TestPackage").build()));
    }

    // ---------------------------------------------------------------
    // findAll
    // ---------------------------------------------------------------
    @Test
    void findAll_ReturnsPaginatedVehicles() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Vehicle> vehiclePage = new PageImpl<>(List.of(vehicle));

        when(vehicleRepository.findAll(pageable)).thenReturn(vehiclePage);
        mockReferenceFindAll();

        Page<VehicleResponse> result = vehicleService.findAll(pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getPlate()).isEqualTo(PLATE);
        assertThat(result.getContent().getFirst().getCarBrandName()).isEqualTo("TestBrand");
        assertThat(result.getContent().getFirst().getCarModelName()).isEqualTo("TestModel");

        verify(vehicleRepository).findAll(pageable);
    }

    // ---------------------------------------------------------------
    // findById — found
    // ---------------------------------------------------------------
    @Test
    void findById_ExistingId_ReturnsVehicle() {
        when(vehicleRepository.findById(TEST_ID)).thenReturn(Optional.of(vehicle));
        mockReferenceFindAll();

        VehicleResponse response = vehicleService.findById(TEST_ID);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getPlate()).isEqualTo(PLATE);
        assertThat(response.getCarBrandName()).isEqualTo("TestBrand");

        verify(vehicleRepository).findById(TEST_ID);
    }

    // ---------------------------------------------------------------
    // findById — not found
    // ---------------------------------------------------------------
    @Test
    void findById_NonExistingId_ThrowsEntityNotFoundException() {
        when(vehicleRepository.findById(TEST_ID)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> vehicleService.findById(TEST_ID));
        assertThat(exception.getMessage()).contains("Vehicle not found");

        verify(vehicleRepository).findById(TEST_ID);
    }

    // ---------------------------------------------------------------
    // create — valid data
    // ---------------------------------------------------------------
    @Test
    void create_ValidRequest_ReturnsCreatedVehicle() {
        when(vehicleRepository.findByPlate(PLATE.trim())).thenReturn(Optional.empty());
        mockReferenceExists();
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);
        mockReferenceFindAll();

        VehicleResponse response = vehicleService.create(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getPlate()).isEqualTo(PLATE);
        assertThat(response.getChassisNumber()).isEqualTo(CHASSIS_NUMBER);
        assertThat(response.getCarBrandId()).isEqualTo(BRAND_ID);
        assertThat(response.getCustomerId()).isEqualTo(CUSTOMER_ID);

        verify(vehicleRepository).findByPlate(PLATE.trim());
        verify(vehicleRepository).save(any(Vehicle.class));
        verify(vehicleEventPublisher).publishVehicleCreated(any(Vehicle.class));
    }

    // ---------------------------------------------------------------
    // create — duplicate plate
    // ---------------------------------------------------------------
    @Test
    void create_DuplicatePlate_ThrowsIllegalArgumentException() {
        Vehicle existing = Vehicle.builder()
                .id(UUID.randomUUID())
                .plate(PLATE)
                .build();

        when(vehicleRepository.findByPlate(PLATE.trim())).thenReturn(Optional.of(existing));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> vehicleService.create(validRequest));
        assertThat(exception.getMessage()).contains("already exists");

        verify(vehicleRepository).findByPlate(PLATE.trim());
        verify(vehicleRepository, never()).save(any(Vehicle.class));
        verify(vehicleEventPublisher, never()).publishVehicleCreated(any(Vehicle.class));
    }

    // ---------------------------------------------------------------
    // create — invalid reference ID
    // ---------------------------------------------------------------
    @Test
    void create_InvalidBrandId_ThrowsIllegalArgumentException() {
        when(vehicleRepository.findByPlate(PLATE.trim())).thenReturn(Optional.empty());
        when(carBrandRepository.existsById(BRAND_ID)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> vehicleService.create(validRequest));
        assertThat(exception.getMessage()).contains("Car brand not found");

        verify(vehicleRepository).findByPlate(PLATE.trim());
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    // ---------------------------------------------------------------
    // update — valid data (same plate)
    // ---------------------------------------------------------------
    @Test
    void update_ValidRequest_ReturnsUpdatedVehicle() {
        when(vehicleRepository.findById(TEST_ID)).thenReturn(Optional.of(vehicle));
        mockReferenceExists();
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockReferenceFindAll();

        VehicleResponse response = vehicleService.update(TEST_ID, validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getPlate()).isEqualTo(PLATE);

        verify(vehicleRepository).findById(TEST_ID);
        verify(vehicleRepository).save(any(Vehicle.class));
        verify(vehicleEventPublisher).publishVehicleUpdated(any(Vehicle.class));
    }

    // ---------------------------------------------------------------
    // update — not found
    // ---------------------------------------------------------------
    @Test
    void update_NonExistingId_ThrowsEntityNotFoundException() {
        when(vehicleRepository.findById(TEST_ID)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> vehicleService.update(TEST_ID, validRequest));
        assertThat(exception.getMessage()).contains("Vehicle not found");

        verify(vehicleRepository).findById(TEST_ID);
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    // ---------------------------------------------------------------
    // delete — found
    // ---------------------------------------------------------------
    @Test
    void delete_ExistingId_DeletesVehicle() {
        when(vehicleRepository.findById(TEST_ID)).thenReturn(Optional.of(vehicle));

        vehicleService.delete(TEST_ID);

        verify(vehicleRepository).findById(TEST_ID);
        verify(vehicleRepository).delete(vehicle);
        verify(vehicleEventPublisher).publishVehicleDeleted(vehicle);
    }

    // ---------------------------------------------------------------
    // delete — not found
    // ---------------------------------------------------------------
    @Test
    void delete_NonExistingId_ThrowsEntityNotFoundException() {
        when(vehicleRepository.findById(TEST_ID)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> vehicleService.delete(TEST_ID));
        assertThat(exception.getMessage()).contains("Vehicle not found");

        verify(vehicleRepository).findById(TEST_ID);
        verify(vehicleRepository, never()).delete(any(Vehicle.class));
    }

    // ---------------------------------------------------------------
    // getBrands
    // ---------------------------------------------------------------
    @Test
    void getBrands_ReturnsBrandList() {
        List<CarBrand> brands = List.of(
                CarBrand.builder().id(1).name("BrandA").build(),
                CarBrand.builder().id(2).name("BrandB").build());

        when(carBrandRepository.findAll()).thenReturn(brands);

        List<CarBrand> result = vehicleService.getBrands();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("BrandA");
        verify(carBrandRepository).findAll();
    }

    // ---------------------------------------------------------------
    // getModelsByBrand
    // ---------------------------------------------------------------
    @Test
    void getModelsByBrand_ReturnsModelList() {
        List<CarModel> models = List.of(
                CarModel.builder().id(10).name("ModelX").brandId(1).build(),
                CarModel.builder().id(11).name("ModelY").brandId(1).build());

        when(carModelRepository.findByBrandId(1)).thenReturn(models);

        List<CarModel> result = vehicleService.getModelsByBrand(1);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("ModelX");
        verify(carModelRepository).findByBrandId(1);
    }

    // ---------------------------------------------------------------
    // getEngines
    // ---------------------------------------------------------------
    @Test
    void getEngines_ReturnsEngineList() {
        List<CarEngine> engines = List.of(
                CarEngine.builder().id(100).name("Engine1").build(),
                CarEngine.builder().id(101).name("Engine2").build());

        when(carEngineRepository.findAll()).thenReturn(engines);

        List<CarEngine> result = vehicleService.getEngines();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Engine1");
        verify(carEngineRepository).findAll();
    }

    // ---------------------------------------------------------------
    // getFuelTypes
    // ---------------------------------------------------------------
    @Test
    void getFuelTypes_ReturnsFuelTypeList() {
        List<CarFuelType> fuelTypes = List.of(
                CarFuelType.builder().id(1000).name("Gasoline").build(),
                CarFuelType.builder().id(1001).name("Diesel").build());

        when(carFuelTypeRepository.findAll()).thenReturn(fuelTypes);

        List<CarFuelType> result = vehicleService.getFuelTypes();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Gasoline");
        verify(carFuelTypeRepository).findAll();
    }

    // ---------------------------------------------------------------
    // getTypes
    // ---------------------------------------------------------------
    @Test
    void getTypes_ReturnsTypeList() {
        List<CarType> types = List.of(
                CarType.builder().id(10000).name("Sedan").build(),
                CarType.builder().id(10001).name("SUV").build());

        when(carTypeRepository.findAll()).thenReturn(types);

        List<CarType> result = vehicleService.getTypes();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Sedan");
        verify(carTypeRepository).findAll();
    }

    // ---------------------------------------------------------------
    // getPackages
    // ---------------------------------------------------------------
    @Test
    void getPackages_ReturnsPackageList() {
        List<CarPackage> packages = List.of(
                CarPackage.builder().id(100000).name("Base").build(),
                CarPackage.builder().id(100001).name("Premium").build());

        when(carPackageRepository.findAll()).thenReturn(packages);

        List<CarPackage> result = vehicleService.getPackages();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Base");
        verify(carPackageRepository).findAll();
    }
}
