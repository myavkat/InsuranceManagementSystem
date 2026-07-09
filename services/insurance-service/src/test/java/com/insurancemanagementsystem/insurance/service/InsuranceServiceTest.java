package com.insurancemanagementsystem.insurance.service;

import com.insurancemanagementsystem.insurance.config.InsuranceEventPublisher;
import com.insurancemanagementsystem.insurance.dto.InsuranceRequest;
import com.insurancemanagementsystem.insurance.dto.InsuranceResponse;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import com.insurancemanagementsystem.insurance.repository.InsuranceRepository;
import com.insurancemanagementsystem.insurance.repository.InsuranceTypeRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuranceServiceTest {

    @Mock
    private InsuranceRepository insuranceRepository;

    @Mock
    private InsuranceTypeRepository insuranceTypeRepository;

    @Mock
    private InsuranceEventPublisher insuranceEventPublisher;

    @InjectMocks
    private InsuranceService insuranceService;

    @Captor
    private ArgumentCaptor<Insurance> insuranceCaptor;

    private static final UUID TEST_ID = UUID.randomUUID();
    private static final Integer TEST_TYPE_ID = 1;
    private static final String TEST_NAME = "Health Insurance";
    private static final String TEST_DESCRIPTION = "Comprehensive health coverage";
    private static final BigDecimal TEST_BASE_PREMIUM = new BigDecimal("250.00");
    private static final String TEST_TYPE_NAME = "Health";

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    private InsuranceRequest createValidRequest() {
        InsuranceRequest request = new InsuranceRequest();
        request.setName(TEST_NAME);
        request.setDescription(TEST_DESCRIPTION);
        request.setTypeId(TEST_TYPE_ID);
        request.setBasePremium(TEST_BASE_PREMIUM);
        request.setIsActive(true);
        return request;
    }

    private Insurance createInsurance(UUID id, String name) {
        return Insurance.builder()
                .id(id)
                .name(name)
                .code(name.toUpperCase().replaceAll("\\s+", "_"))
                .description(TEST_DESCRIPTION)
                .typeId(TEST_TYPE_ID)
                .basePremium(TEST_BASE_PREMIUM)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private InsuranceType createInsuranceType() {
        return new InsuranceType(TEST_TYPE_ID, TEST_TYPE_NAME);
    }

    // ---------------------------------------------------------------
    // 1. create – valid request
    // ---------------------------------------------------------------
    @Test
    void create_withValidRequest_returnsInsuranceResponse() {
        // Arrange
        InsuranceRequest request = createValidRequest();
        InsuranceType insuranceType = createInsuranceType();
        Insurance savedInsurance = createInsurance(TEST_ID, TEST_NAME);

        when(insuranceTypeRepository.findById(TEST_TYPE_ID)).thenReturn(Optional.of(insuranceType));
        when(insuranceRepository.findByNameIgnoreCase(TEST_NAME.trim())).thenReturn(Optional.empty());
        when(insuranceRepository.save(any(Insurance.class))).thenReturn(savedInsurance);

        // Act
        InsuranceResponse response = insuranceService.create(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getName()).isEqualTo(TEST_NAME);
        assertThat(response.getDescription()).isEqualTo(TEST_DESCRIPTION);
        assertThat(response.getTypeId()).isEqualTo(TEST_TYPE_ID);
        assertThat(response.getBasePremium()).isEqualByComparingTo(TEST_BASE_PREMIUM);
        assertThat(response.getIsActive()).isTrue();

        verify(insuranceTypeRepository).findById(TEST_TYPE_ID);
        verify(insuranceRepository).findByNameIgnoreCase(TEST_NAME.trim());
        verify(insuranceRepository).save(any(Insurance.class));
        verify(insuranceEventPublisher).publishInsuranceCreated(any(Insurance.class));
    }

    // ---------------------------------------------------------------
    // 2. create – duplicate name
    // ---------------------------------------------------------------
    @Test
    void create_withDuplicateName_throwsIllegalArgumentException() {
        // Arrange
        InsuranceRequest request = createValidRequest();
        Insurance existingInsurance = createInsurance(UUID.randomUUID(), TEST_NAME);

        when(insuranceTypeRepository.findById(TEST_TYPE_ID)).thenReturn(Optional.of(createInsuranceType()));
        when(insuranceRepository.findByNameIgnoreCase(TEST_NAME.trim())).thenReturn(Optional.of(existingInsurance));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> insuranceService.create(request));
        assertThat(exception.getMessage()).contains("already exists");

        verify(insuranceTypeRepository).findById(TEST_TYPE_ID);
        verify(insuranceRepository).findByNameIgnoreCase(TEST_NAME.trim());
        verify(insuranceRepository, never()).save(any(Insurance.class));
        verify(insuranceEventPublisher, never()).publishInsuranceCreated(any(Insurance.class));
    }

    // ---------------------------------------------------------------
    // 3. create – invalid type ID
    // ---------------------------------------------------------------
    @Test
    void create_withInvalidTypeId_throwsIllegalArgumentException() {
        // Arrange
        InsuranceRequest request = createValidRequest();

        when(insuranceTypeRepository.findById(TEST_TYPE_ID)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> insuranceService.create(request));
        assertThat(exception.getMessage()).contains("not found");

        verify(insuranceTypeRepository).findById(TEST_TYPE_ID);
        verify(insuranceRepository, never()).findByNameIgnoreCase(anyString());
        verify(insuranceRepository, never()).save(any(Insurance.class));
    }

    // ---------------------------------------------------------------
    // 4. findById – exists
    // ---------------------------------------------------------------
    @Test
    void findById_whenExists_returnsInsuranceResponse() {
        // Arrange
        Insurance insurance = createInsurance(TEST_ID, TEST_NAME);

        when(insuranceRepository.findById(TEST_ID)).thenReturn(Optional.of(insurance));

        // Act
        InsuranceResponse response = insuranceService.findById(TEST_ID);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getName()).isEqualTo(TEST_NAME);
        assertThat(response.getDescription()).isEqualTo(TEST_DESCRIPTION);
        assertThat(response.getTypeId()).isEqualTo(TEST_TYPE_ID);
        assertThat(response.getBasePremium()).isEqualByComparingTo(TEST_BASE_PREMIUM);
        assertThat(response.getIsActive()).isTrue();

        verify(insuranceRepository).findById(TEST_ID);
    }

    // ---------------------------------------------------------------
    // 5. findById – not found
    // ---------------------------------------------------------------
    @Test
    void findById_whenNotExists_throwsEntityNotFoundException() {
        // Arrange
        when(insuranceRepository.findById(TEST_ID)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> insuranceService.findById(TEST_ID));
        assertThat(exception.getMessage()).contains("Insurance not found");

        verify(insuranceRepository).findById(TEST_ID);
    }

    // ---------------------------------------------------------------
    // 6. findById – inactive (soft-deleted)
    // ---------------------------------------------------------------
    @Test
    void findById_whenInactive_returnsInactiveInsurance() {
        // Arrange
        Insurance inactiveInsurance = Insurance.builder()
                .id(TEST_ID)
                .name(TEST_NAME)
                .code(TEST_NAME.toUpperCase().replaceAll("\\s+", "_"))
                .isActive(false)
                .build();

        when(insuranceRepository.findById(TEST_ID)).thenReturn(Optional.of(inactiveInsurance));

        // Act
        InsuranceResponse response = insuranceService.findById(TEST_ID);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getIsActive()).isFalse();

        verify(insuranceRepository).findById(TEST_ID);
    }

    // ---------------------------------------------------------------
    // 7. softDelete – sets isActive to false
    // ---------------------------------------------------------------
    @Test
    void softDelete_setsIsActiveFalse() {
        // Arrange
        Insurance activeInsurance = createInsurance(TEST_ID, TEST_NAME);

        when(insuranceRepository.findById(TEST_ID)).thenReturn(Optional.of(activeInsurance));
        when(insuranceRepository.save(any(Insurance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        InsuranceResponse response = insuranceService.softDelete(TEST_ID);

        // Assert
        verify(insuranceRepository).findById(TEST_ID);
        verify(insuranceRepository).save(insuranceCaptor.capture());

        Insurance savedInsurance = insuranceCaptor.getValue();
        assertThat(savedInsurance.getIsActive()).isFalse();

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getIsActive()).isFalse();

        verify(insuranceEventPublisher, never()).publishInsuranceCreated(any(Insurance.class));
        verify(insuranceEventPublisher, never()).publishInsuranceUpdated(any(Insurance.class));
    }

    // ---------------------------------------------------------------
    // 8. update – all fields updated
    // ---------------------------------------------------------------
    @Test
    void update_updatesAllFields() {
        // Arrange
        Insurance existingInsurance = createInsurance(TEST_ID, "Old Name");
        InsuranceRequest request = createValidRequest();

        when(insuranceRepository.findById(TEST_ID)).thenReturn(Optional.of(existingInsurance));
        when(insuranceTypeRepository.findById(TEST_TYPE_ID)).thenReturn(Optional.of(createInsuranceType()));
        when(insuranceRepository.findByNameIgnoreCase(TEST_NAME.trim())).thenReturn(Optional.empty());
        when(insuranceRepository.save(any(Insurance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        InsuranceResponse response = insuranceService.update(TEST_ID, request);

        // Assert
        verify(insuranceRepository).findById(TEST_ID);
        verify(insuranceTypeRepository).findById(TEST_TYPE_ID);
        verify(insuranceRepository).findByNameIgnoreCase(TEST_NAME.trim());
        verify(insuranceRepository).save(insuranceCaptor.capture());

        Insurance updatedInsurance = insuranceCaptor.getValue();
        assertThat(updatedInsurance.getName()).isEqualTo(TEST_NAME);
        assertThat(updatedInsurance.getDescription()).isEqualTo(TEST_DESCRIPTION);
        assertThat(updatedInsurance.getTypeId()).isEqualTo(TEST_TYPE_ID);
        assertThat(updatedInsurance.getBasePremium()).isEqualByComparingTo(TEST_BASE_PREMIUM);
        assertThat(updatedInsurance.getIsActive()).isTrue();

        verify(insuranceEventPublisher).publishInsuranceUpdated(updatedInsurance);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo(TEST_NAME);
    }

    // ---------------------------------------------------------------
    // 9. update – changed name with duplicate
    // ---------------------------------------------------------------
    @Test
    void update_withChangedNameAndDuplicate_throwsIllegalArgumentException() {
        // Arrange
        Insurance existingInsurance = createInsurance(TEST_ID, "Old Name");
        InsuranceRequest request = createValidRequest();
        Insurance duplicateInsurance = createInsurance(UUID.randomUUID(), TEST_NAME);

        when(insuranceRepository.findById(TEST_ID)).thenReturn(Optional.of(existingInsurance));
        when(insuranceTypeRepository.findById(TEST_TYPE_ID)).thenReturn(Optional.of(createInsuranceType()));
        when(insuranceRepository.findByNameIgnoreCase(TEST_NAME.trim())).thenReturn(Optional.of(duplicateInsurance));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> insuranceService.update(TEST_ID, request));
        assertThat(exception.getMessage()).contains("already exists");

        verify(insuranceRepository).findById(TEST_ID);
        verify(insuranceTypeRepository).findById(TEST_TYPE_ID);
        verify(insuranceRepository).findByNameIgnoreCase(TEST_NAME.trim());
        verify(insuranceRepository, never()).save(any(Insurance.class));
        verify(insuranceEventPublisher, never()).publishInsuranceUpdated(any(Insurance.class));
    }

    // ---------------------------------------------------------------
    // 10. findAll – with type filter
    // ---------------------------------------------------------------
    @Test
    void findAll_withTypeFilter_returnsFilteredPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Insurance insurance = createInsurance(TEST_ID, TEST_NAME);
        Page<Insurance> insurancePage = new PageImpl<>(List.of(insurance));

        when(insuranceRepository.findByTypeId(TEST_TYPE_ID, pageable)).thenReturn(insurancePage);

        // Act
        Page<InsuranceResponse> result = insuranceService.findAll(TEST_TYPE_ID, null, pageable);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getName()).isEqualTo(TEST_NAME);
        assertThat(result.getContent().getFirst().getTypeId()).isEqualTo(TEST_TYPE_ID);

        verify(insuranceRepository).findByTypeId(TEST_TYPE_ID, pageable);
        verify(insuranceRepository, never()).searchByName(anyString(), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 11. findAll – with search
    // ---------------------------------------------------------------
    @Test
    void findAll_withSearch_returnsFilteredPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Insurance insurance = createInsurance(TEST_ID, TEST_NAME);
        Page<Insurance> insurancePage = new PageImpl<>(List.of(insurance));

        when(insuranceRepository.searchByName(TEST_NAME, pageable)).thenReturn(insurancePage);

        // Act
        Page<InsuranceResponse> result = insuranceService.findAll(null, TEST_NAME, pageable);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getName()).isEqualTo(TEST_NAME);

        verify(insuranceRepository).searchByName(TEST_NAME, pageable);
        verify(insuranceRepository, never()).findByTypeId(anyInt(), any(Pageable.class));
        verify(insuranceRepository, never()).findAll(any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 12. findAll – without filter
    // ---------------------------------------------------------------
    @Test
    void findAll_withoutFilter_returnsAllActive() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Insurance insurance = createInsurance(TEST_ID, TEST_NAME);
        Page<Insurance> insurancePage = new PageImpl<>(List.of(insurance));

        when(insuranceRepository.findAll(pageable)).thenReturn(insurancePage);

        // Act
        Page<InsuranceResponse> result = insuranceService.findAll(null, null, pageable);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getName()).isEqualTo(TEST_NAME);

        verify(insuranceRepository).findAll(pageable);
        verify(insuranceRepository, never()).findByTypeId(anyInt(), any(Pageable.class));
        verify(insuranceRepository, never()).searchByName(anyString(), any(Pageable.class));
    }
}
