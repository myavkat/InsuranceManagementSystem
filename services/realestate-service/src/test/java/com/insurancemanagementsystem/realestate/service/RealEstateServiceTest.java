package com.insurancemanagementsystem.realestate.service;

import com.insurancemanagementsystem.realestate.client.CustomerServiceClient;
import com.insurancemanagementsystem.realestate.client.ReferenceDataServiceClient;
import com.insurancemanagementsystem.realestate.config.RealEstateEventPublisher;
import com.insurancemanagementsystem.realestate.dto.RealEstateRequest;
import com.insurancemanagementsystem.realestate.dto.RealEstateResponse;
import com.insurancemanagementsystem.realestate.entity.RealEstate;
import com.insurancemanagementsystem.realestate.entity.RealEstateConstructionType;
import com.insurancemanagementsystem.realestate.entity.RealEstateLuxuryClass;
import com.insurancemanagementsystem.realestate.entity.RealEstateUsageType;
import com.insurancemanagementsystem.realestate.repository.RealEstateRepository;
import com.insurancemanagementsystem.realestate.repository.RealEstateConstructionTypeRepository;
import com.insurancemanagementsystem.realestate.repository.RealEstateLuxuryClassRepository;
import com.insurancemanagementsystem.realestate.repository.RealEstateUsageTypeRepository;
import jakarta.persistence.EntityNotFoundException;
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
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealEstateServiceTest {

    @Mock
    private RealEstateRepository realEstateRepository;

    @Mock
    private RealEstateEventPublisher realEstateEventPublisher;

    @Mock
    private RealEstateConstructionTypeRepository constructionTypeRepository;

    @Mock
    private RealEstateLuxuryClassRepository luxuryClassRepository;

    @Mock
    private RealEstateUsageTypeRepository usageTypeRepository;

    @Mock
    private CustomerServiceClient customerServiceClient;

    @Mock
    private ReferenceDataServiceClient referenceDataServiceClient;

    @InjectMocks
    private RealEstateService realEstateService;

    @Captor
    private ArgumentCaptor<RealEstate> realEstateCaptor;

    private static final UUID TEST_ID = UUID.randomUUID();
    private static final String ADDRESS = "123 Main St";
    private static final Integer CITY_ID = 34;
    private static final String DISTRICT = "Kadıköy";
    private static final BigDecimal SQUARE_METERS = new BigDecimal("120.50");
    private static final Integer CONSTRUCTION_YEAR = 2020;
    private static final Integer CONSTRUCTION_TYPE_ID = 1;
    private static final Integer LUXURY_CLASS_ID = 2;
    private static final Integer USAGE_TYPE_ID = 3;
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private RealEstateRequest createValidRequest() {
        RealEstateRequest request = new RealEstateRequest();
        request.setAddress(ADDRESS);
        request.setCityId(CITY_ID);
        request.setDistrict(DISTRICT);
        request.setSquareMeters(SQUARE_METERS);
        request.setConstructionYear(CONSTRUCTION_YEAR);
        request.setConstructionTypeId(CONSTRUCTION_TYPE_ID);
        request.setLuxuryClassId(LUXURY_CLASS_ID);
        request.setUsageTypeId(USAGE_TYPE_ID);
        request.setCustomerId(CUSTOMER_ID);
        return request;
    }

    private RealEstate createRealEstate(UUID id) {
        return RealEstate.builder()
                .id(id)
                .address(ADDRESS)
                .cityId(CITY_ID)
                .district(DISTRICT)
                .squareMeters(SQUARE_METERS)
                .constructionYear(CONSTRUCTION_YEAR)
                .constructionTypeId(CONSTRUCTION_TYPE_ID)
                .luxuryClassId(LUXURY_CLASS_ID)
                .usageTypeId(USAGE_TYPE_ID)
                .customerId(CUSTOMER_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private void mockReferenceDataExistence(boolean exists) {
        when(constructionTypeRepository.existsById(CONSTRUCTION_TYPE_ID)).thenReturn(exists);
        when(luxuryClassRepository.existsById(LUXURY_CLASS_ID)).thenReturn(exists);
        when(usageTypeRepository.existsById(USAGE_TYPE_ID)).thenReturn(exists);
    }

    private void mockReferenceDataNames() {
        when(constructionTypeRepository.findById(CONSTRUCTION_TYPE_ID))
                .thenReturn(Optional.of(new RealEstateConstructionType(CONSTRUCTION_TYPE_ID, "Concrete")));
        when(luxuryClassRepository.findById(LUXURY_CLASS_ID))
                .thenReturn(Optional.of(new RealEstateLuxuryClass(LUXURY_CLASS_ID, "A")));
        when(usageTypeRepository.findById(USAGE_TYPE_ID))
                .thenReturn(Optional.of(new RealEstateUsageType(USAGE_TYPE_ID, "Residential")));
        when(referenceDataServiceClient.getCityName(CITY_ID)).thenReturn("Istanbul");
        when(customerServiceClient.getCustomerName(CUSTOMER_ID)).thenReturn("John Doe");
    }

    // ---------------------------------------------------------------
    // 1. findAll — paginated results
    // ---------------------------------------------------------------
    @Test
    void findAll_ReturnsPaginatedResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        RealEstate realEstate = createRealEstate(TEST_ID);
        Page<RealEstate> page = new PageImpl<>(List.of(realEstate));

        when(realEstateRepository.findAll(pageable)).thenReturn(page);
        mockReferenceDataNames();

        // Act
        Page<RealEstateResponse> result = realEstateService.findAll(pageable);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(TEST_ID);
        assertThat(result.getContent().getFirst().getAddress()).isEqualTo(ADDRESS);
        assertThat(result.getContent().getFirst().getConstructionTypeName()).isEqualTo("Concrete");
        assertThat(result.getContent().getFirst().getLuxuryClassName()).isEqualTo("A");
        assertThat(result.getContent().getFirst().getUsageTypeName()).isEqualTo("Residential");
        assertThat(result.getContent().getFirst().getCityName()).isEqualTo("Istanbul");
        assertThat(result.getContent().getFirst().getCustomerName()).isEqualTo("John Doe");

        verify(realEstateRepository).findAll(pageable);
    }

    // ---------------------------------------------------------------
    // 2. findById — existing
    // ---------------------------------------------------------------
    @Test
    void findById_Existing_ReturnsResponse() {
        // Arrange
        RealEstate realEstate = createRealEstate(TEST_ID);
        when(realEstateRepository.findById(TEST_ID)).thenReturn(Optional.of(realEstate));
        mockReferenceDataNames();

        // Act
        RealEstateResponse response = realEstateService.findById(TEST_ID);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getAddress()).isEqualTo(ADDRESS);
        assertThat(response.getCityId()).isEqualTo(CITY_ID);
        assertThat(response.getSquareMeters()).isEqualByComparingTo(SQUARE_METERS);
        assertThat(response.getConstructionTypeName()).isEqualTo("Concrete");
        assertThat(response.getLuxuryClassName()).isEqualTo("A");
        assertThat(response.getUsageTypeName()).isEqualTo("Residential");
        assertThat(response.getCityName()).isEqualTo("Istanbul");
        assertThat(response.getCustomerName()).isEqualTo("John Doe");

        verify(realEstateRepository).findById(TEST_ID);
    }

    // ---------------------------------------------------------------
    // 3. findById — not found
    // ---------------------------------------------------------------
    @Test
    void findById_NotFound_ThrowsEntityNotFoundException() {
        // Arrange
        when(realEstateRepository.findById(TEST_ID)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> realEstateService.findById(TEST_ID));
        assertThat(exception.getMessage()).contains("RealEstate not found");

        verify(realEstateRepository).findById(TEST_ID);
    }

    // ---------------------------------------------------------------
    // 4. create — valid request
    // ---------------------------------------------------------------
    @Test
    void create_WithValidData_ReturnsSavedEntity() {
        // Arrange
        RealEstateRequest request = createValidRequest();
        RealEstate savedRealEstate = createRealEstate(TEST_ID);

        mockReferenceDataExistence(true);
        when(realEstateRepository.save(any(RealEstate.class))).thenReturn(savedRealEstate);
        mockReferenceDataNames();

        // Act
        RealEstateResponse response = realEstateService.create(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getAddress()).isEqualTo(ADDRESS);
        assertThat(response.getCityId()).isEqualTo(CITY_ID);
        assertThat(response.getDistrict()).isEqualTo(DISTRICT);
        assertThat(response.getSquareMeters()).isEqualByComparingTo(SQUARE_METERS);
        assertThat(response.getConstructionYear()).isEqualTo(CONSTRUCTION_YEAR);
        assertThat(response.getConstructionTypeId()).isEqualTo(CONSTRUCTION_TYPE_ID);
        assertThat(response.getLuxuryClassId()).isEqualTo(LUXURY_CLASS_ID);
        assertThat(response.getUsageTypeId()).isEqualTo(USAGE_TYPE_ID);
        assertThat(response.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(response.getCityName()).isEqualTo("Istanbul");
        assertThat(response.getCustomerName()).isEqualTo("John Doe");

        verify(constructionTypeRepository).existsById(CONSTRUCTION_TYPE_ID);
        verify(luxuryClassRepository).existsById(LUXURY_CLASS_ID);
        verify(usageTypeRepository).existsById(USAGE_TYPE_ID);
        verify(realEstateRepository).save(any(RealEstate.class));
        verify(realEstateEventPublisher).publishRealEstateCreated(savedRealEstate);
    }

    // ---------------------------------------------------------------
    // 5. create — future construction year
    // ---------------------------------------------------------------
    @Test
    void create_WithFutureConstructionYear_ThrowsIllegalArgumentException() {
        // Arrange
        RealEstateRequest request = createValidRequest();
        request.setConstructionYear(Year.now().getValue() + 1);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> realEstateService.create(request));
        assertThat(exception.getMessage()).contains("Construction year cannot be in the future");

        verify(realEstateRepository, never()).save(any(RealEstate.class));
        verify(realEstateEventPublisher, never()).publishRealEstateCreated(any(RealEstate.class));
    }

    // ---------------------------------------------------------------
    // 6. create — invalid reference IDs
    // ---------------------------------------------------------------
    @Test
    void create_WithInvalidReferenceId_ThrowsIllegalArgumentException() {
        // Arrange
        RealEstateRequest request = createValidRequest();
        // Set a different construction type ID so existsById returns false for the original
        request.setConstructionTypeId(999);

        when(constructionTypeRepository.existsById(999)).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> realEstateService.create(request));
        assertThat(exception.getMessage()).contains("Construction type not found");

        verify(constructionTypeRepository).existsById(999);
        verify(realEstateRepository, never()).save(any(RealEstate.class));
        verify(realEstateEventPublisher, never()).publishRealEstateCreated(any(RealEstate.class));
    }

    // ---------------------------------------------------------------
    // 7. create — null address throws NullPointerException
    // ---------------------------------------------------------------
    @Test
    void create_WithNullAddress_ThrowsNullPointerException() {
        // Arrange
        RealEstateRequest request = createValidRequest();
        request.setAddress(null);
        mockReferenceDataExistence(true);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> realEstateService.create(request));

        verify(realEstateRepository, never()).save(any(RealEstate.class));
        verify(realEstateEventPublisher, never()).publishRealEstateCreated(any(RealEstate.class));
    }

    // ---------------------------------------------------------------
    // 8. update — valid request
    // ---------------------------------------------------------------
    @Test
    void update_WithValidData_UpdatesAndReturnsEntity() {
        // Arrange
        RealEstate existingRealEstate = createRealEstate(TEST_ID);
        RealEstateRequest request = createValidRequest();

        when(realEstateRepository.findById(TEST_ID)).thenReturn(Optional.of(existingRealEstate));
        mockReferenceDataExistence(true);
        when(realEstateRepository.save(any(RealEstate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockReferenceDataNames();

        // Act
        RealEstateResponse response = realEstateService.update(TEST_ID, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getAddress()).isEqualTo(ADDRESS);
        assertThat(response.getCityId()).isEqualTo(CITY_ID);
        assertThat(response.getCityName()).isEqualTo("Istanbul");
        assertThat(response.getCustomerName()).isEqualTo("John Doe");

        verify(realEstateRepository).findById(TEST_ID);
        verify(realEstateRepository).save(any(RealEstate.class));
        verify(realEstateEventPublisher).publishRealEstateUpdated(any(RealEstate.class));
    }

    // ---------------------------------------------------------------
    // 9. update — non-existing ID
    // ---------------------------------------------------------------
    @Test
    void update_NonExistingId_ThrowsEntityNotFoundException() {
        // Arrange
        RealEstateRequest request = createValidRequest();

        when(realEstateRepository.findById(TEST_ID)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> realEstateService.update(TEST_ID, request));
        assertThat(exception.getMessage()).contains("RealEstate not found");

        verify(realEstateRepository).findById(TEST_ID);
        verify(realEstateRepository, never()).save(any(RealEstate.class));
        verify(realEstateEventPublisher, never()).publishRealEstateUpdated(any(RealEstate.class));
    }

    // ---------------------------------------------------------------
    // 10. delete — existing
    // ---------------------------------------------------------------
    @Test
    void delete_Existing_DeletesEntity() {
        // Arrange
        RealEstate realEstate = createRealEstate(TEST_ID);
        when(realEstateRepository.findById(TEST_ID)).thenReturn(Optional.of(realEstate));

        // Act
        realEstateService.delete(TEST_ID);

        // Assert
        verify(realEstateRepository).findById(TEST_ID);
        verify(realEstateEventPublisher).publishRealEstateDeleted(realEstate);
        verify(realEstateRepository).delete(realEstate);
    }

    // ---------------------------------------------------------------
    // 11. delete — non-existing ID
    // ---------------------------------------------------------------
    @Test
    void delete_NonExistingId_ThrowsEntityNotFoundException() {
        // Arrange
        when(realEstateRepository.findById(TEST_ID)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> realEstateService.delete(TEST_ID));
        assertThat(exception.getMessage()).contains("RealEstate not found");

        verify(realEstateRepository).findById(TEST_ID);
        verify(realEstateEventPublisher, never()).publishRealEstateDeleted(any(RealEstate.class));
        verify(realEstateRepository, never()).delete(any(RealEstate.class));
    }

    // ---------------------------------------------------------------
    // 12. getConstructionTypes — returns list
    // ---------------------------------------------------------------
    @Test
    void getConstructionTypes_ReturnsList() {
        // Arrange
        List<RealEstateConstructionType> types = List.of(
                new RealEstateConstructionType(1, "Concrete"),
                new RealEstateConstructionType(2, "Steel"));
        when(constructionTypeRepository.findAll()).thenReturn(types);

        // Act
        List<RealEstateConstructionType> result = realEstateService.getConstructionTypes();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Concrete");
        assertThat(result.get(1).getName()).isEqualTo("Steel");

        verify(constructionTypeRepository).findAll();
    }

    // ---------------------------------------------------------------
    // 13. getLuxuryClasses — returns list
    // ---------------------------------------------------------------
    @Test
    void getLuxuryClasses_ReturnsList() {
        // Arrange
        List<RealEstateLuxuryClass> classes = List.of(
                new RealEstateLuxuryClass(1, "A"),
                new RealEstateLuxuryClass(2, "B"));
        when(luxuryClassRepository.findAll()).thenReturn(classes);

        // Act
        List<RealEstateLuxuryClass> result = realEstateService.getLuxuryClasses();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("A");
        assertThat(result.get(1).getName()).isEqualTo("B");

        verify(luxuryClassRepository).findAll();
    }

    // ---------------------------------------------------------------
    // 14. getUsageTypes — returns list
    // ---------------------------------------------------------------
    @Test
    void getUsageTypes_ReturnsList() {
        // Arrange
        List<RealEstateUsageType> types = List.of(
                new RealEstateUsageType(1, "Residential"),
                new RealEstateUsageType(2, "Commercial"));
        when(usageTypeRepository.findAll()).thenReturn(types);

        // Act
        List<RealEstateUsageType> result = realEstateService.getUsageTypes();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Residential");
        assertThat(result.get(1).getName()).isEqualTo("Commercial");

        verify(usageTypeRepository).findAll();
    }
}
