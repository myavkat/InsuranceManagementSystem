package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.estimation.config.MessagePublisher;
import com.insurancemanagementsystem.estimation.dto.EstimationRequest;
import com.insurancemanagementsystem.estimation.dto.EstimationResponse;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstimationServiceTest {

    @Mock
    private EstimationRepository estimationRepository;

    @Mock
    private MessagePublisher messagePublisher;

    @InjectMocks
    private EstimationService estimationService;

    @Captor
    private ArgumentCaptor<Estimation> estimationCaptor;

    private final UUID testId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();
    private final Integer insuranceTypeId = 1;

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    private EstimationRequest createValidRequest() {
        EstimationRequest request = new EstimationRequest();
        request.setCustomerId(customerId);
        request.setVehicleId(vehicleId);
        request.setInsuranceTypeId(insuranceTypeId);
        request.setCompanyId(companyId);
        return request;
    }

    private Estimation createSampleEntity() {
        return Estimation.builder()
                .id(testId)
                .sagaId(UUID.randomUUID())
                .customerId(customerId)
                .vehicleId(vehicleId)
                .insuranceTypeId(insuranceTypeId)
                .companyId(companyId)
                .status(Estimation.Status.STARTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ---------------------------------------------------------------
    // 1. create — valid request
    // ---------------------------------------------------------------
    @Test
    void create_withValidRequest_createsEstimationWithStartedStatus() {
        // Arrange
        EstimationRequest request = createValidRequest();
        Estimation savedEntity = createSampleEntity();

        when(estimationRepository.save(any(Estimation.class))).thenReturn(savedEntity);

        // Act
        EstimationResponse response = estimationService.create(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testId);
        assertThat(response.getStatus()).isEqualTo("STARTED");
        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getVehicleId()).isEqualTo(vehicleId);
        assertThat(response.getCompanyId()).isEqualTo(companyId);
        assertThat(response.getInsuranceTypeId()).isEqualTo(insuranceTypeId);
        assertThat(response.getSagaId()).isNotNull();

        verify(estimationRepository).save(estimationCaptor.capture());
        Estimation saved = estimationCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(Estimation.Status.STARTED);
        assertThat(saved.getSagaId()).isNotNull();

        verify(messagePublisher).publish(anyString(), any());
    }

    // ---------------------------------------------------------------
    // 2. create — both vehicleId and realEstateId null
    // ---------------------------------------------------------------
    @Test
    void create_withBothVehicleAndRealEstateNull_throwsIllegalArgumentException() {
        // Arrange
        EstimationRequest request = new EstimationRequest();
        request.setCustomerId(customerId);
        request.setInsuranceTypeId(insuranceTypeId);
        request.setCompanyId(companyId);
        // vehicleId and realEstateId are both null

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> estimationService.create(request));
        assertThat(exception.getMessage()).contains("Either vehicleId or realEstateId must be provided");

        verify(estimationRepository, never()).save(any(Estimation.class));
        verify(messagePublisher, never()).publish(anyString(), any());
    }

    // ---------------------------------------------------------------
    // 3. findById — existing id
    // ---------------------------------------------------------------
    @Test
    void findById_whenExists_returnsEstimationResponse() {
        // Arrange
        Estimation entity = createSampleEntity();
        when(estimationRepository.findById(testId)).thenReturn(Optional.of(entity));

        // Act
        EstimationResponse response = estimationService.findById(testId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testId);
        assertThat(response.getStatus()).isEqualTo("STARTED");
        assertThat(response.getCustomerId()).isEqualTo(customerId);

        verify(estimationRepository).findById(testId);
    }

    // ---------------------------------------------------------------
    // 4. findById — non-existing id
    // ---------------------------------------------------------------
    @Test
    void findById_whenNotExists_throwsEntityNotFoundException() {
        // Arrange
        when(estimationRepository.findById(testId)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> estimationService.findById(testId));
        assertThat(exception.getMessage()).contains("Estimation not found");

        verify(estimationRepository).findById(testId);
    }

    // ---------------------------------------------------------------
    // 5. findAll — no filters
    // ---------------------------------------------------------------
    @Test
    void findAll_withNoFilters_returnsAllEstimations() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Estimation entity = createSampleEntity();
        Page<Estimation> entityPage = new PageImpl<>(List.of(entity));

        when(estimationRepository.findAll(pageable)).thenReturn(entityPage);

        // Act
        Page<EstimationResponse> result = estimationService.findAll(null, null, pageable);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(testId);

        verify(estimationRepository).findAll(pageable);
        verify(estimationRepository, never()).findByCustomerId(any(UUID.class), any(Pageable.class));
        verify(estimationRepository, never()).findByStatus(any(Estimation.Status.class), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 6. findAll — with customerId filter
    // ---------------------------------------------------------------
    @Test
    void findAll_withCustomerId_returnsFilteredResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Estimation entity = createSampleEntity();
        Page<Estimation> entityPage = new PageImpl<>(List.of(entity));

        when(estimationRepository.findByCustomerId(customerId, pageable)).thenReturn(entityPage);

        // Act
        Page<EstimationResponse> result = estimationService.findAll(customerId, null, pageable);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getCustomerId()).isEqualTo(customerId);

        verify(estimationRepository).findByCustomerId(customerId, pageable);
        verify(estimationRepository, never()).findAll(any(Pageable.class));
        verify(estimationRepository, never()).findByStatus(any(Estimation.Status.class), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 7. findAll — with status filter
    // ---------------------------------------------------------------
    @Test
    void findAll_withStatus_returnsFilteredResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Estimation entity = createSampleEntity();
        Page<Estimation> entityPage = new PageImpl<>(List.of(entity));

        when(estimationRepository.findByStatus(Estimation.Status.STARTED, pageable)).thenReturn(entityPage);

        // Act
        Page<EstimationResponse> result = estimationService.findAll(null, "STARTED", pageable);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getStatus()).isEqualTo("STARTED");

        verify(estimationRepository).findByStatus(Estimation.Status.STARTED, pageable);
        verify(estimationRepository, never()).findAll(any(Pageable.class));
        verify(estimationRepository, never()).findByCustomerId(any(UUID.class), any(Pageable.class));
    }
}
