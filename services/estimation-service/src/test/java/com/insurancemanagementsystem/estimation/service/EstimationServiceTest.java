package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.messaging.OutboxMessagePublisher;
import com.insurancemanagementsystem.estimation.client.CustomerServiceClient;
import com.insurancemanagementsystem.estimation.client.InsuranceServiceClient;
import com.insurancemanagementsystem.estimation.client.VehicleServiceClient;
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
import java.util.Map;
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
    private OutboxMessagePublisher outboxMessagePublisher;

    @Mock
    private CustomerServiceClient customerServiceClient;

    @Mock
    private VehicleServiceClient vehicleServiceClient;

    @Mock
    private InsuranceServiceClient insuranceServiceClient;

    @InjectMocks
    private EstimationService estimationService;

    @Captor
    private ArgumentCaptor<Estimation> estimationCaptor;

    private final UUID testId = UUID.randomUUID();

    private final UUID customerId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();
    private final UUID insuranceId = UUID.randomUUID();

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    private EstimationRequest createValidRequest() {
        EstimationRequest request = new EstimationRequest();
        request.setCustomerId(customerId);
        request.setVehicleId(vehicleId);
        request.setInsuranceId(insuranceId);
        return request;
    }

    private Estimation createSampleEntity() {
        return Estimation.builder()
                .id(testId)
                .sagaId(UUID.randomUUID())
                .customerId(customerId)
                .vehicleId(vehicleId)
                .insuranceId(insuranceId)
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
        when(insuranceServiceClient.getInsurance(insuranceId))
                .thenReturn(new InsuranceServiceClient.InsuranceInfo(insuranceId, "TRAFFIC", 1, "Vehicle"));

        // Act
        EstimationResponse response = estimationService.create(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testId);
        assertThat(response.getStatus()).isEqualTo("STARTED");
        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getVehicleId()).isEqualTo(vehicleId);
        assertThat(response.getInsuranceId()).isEqualTo(insuranceId);
        assertThat(response.getSagaId()).isNotNull();

        verify(estimationRepository).save(estimationCaptor.capture());
        Estimation saved = estimationCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(Estimation.Status.STARTED);
        assertThat(saved.getSagaId()).isNotNull();

        verify(outboxMessagePublisher).publish(any(), any(), any(), eq(EventConstants.ESTIMATION_SAGA));
    }

    // ---------------------------------------------------------------
    // 2. create — both vehicleId and realEstateId null
    // ---------------------------------------------------------------
    @Test
    void create_withBothVehicleAndRealEstateNull_throwsIllegalArgumentException() {
        // Arrange
        EstimationRequest request = new EstimationRequest();
        request.setCustomerId(customerId);
        request.setInsuranceId(insuranceId);
        // vehicleId and realEstateId are both null

        // Act & Assert
        when(insuranceServiceClient.getInsurance(insuranceId))
                .thenReturn(new InsuranceServiceClient.InsuranceInfo(insuranceId, "TRAFFIC", 1, "Vehicle"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> estimationService.create(request));
        assertThat(exception.getMessage()).contains("vehicleId is required for Vehicle-type insurance");

        verify(estimationRepository, never()).save(any(Estimation.class));
        verify(outboxMessagePublisher, never()).publish(any(), any(), any(), any());
    }

    // ---------------------------------------------------------------
    // 3. findById — existing id
    // ---------------------------------------------------------------
    @Test
    void findById_whenExists_returnsEstimationResponse() {
        // Arrange
        Estimation entity = createSampleEntity();
        when(estimationRepository.findById(testId)).thenReturn(Optional.of(entity));
        when(customerServiceClient.getCustomerName(customerId)).thenReturn("Ahmet Yılmaz");
        when(customerServiceClient.getCustomerNationalId(customerId)).thenReturn("12345678901");
        when(vehicleServiceClient.getVehicleInfo(vehicleId)).thenReturn(Map.of("plate", "34ABC123", "chassisNumber", "WDB1234567890"));
        when(insuranceServiceClient.getInsurance(insuranceId))
                .thenReturn(new InsuranceServiceClient.InsuranceInfo(insuranceId, "TRAFFIC", 1, "Vehicle"));

        // Act
        EstimationResponse response = estimationService.findById(testId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testId);
        assertThat(response.getStatus()).isEqualTo("STARTED");
        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getCustomerName()).isEqualTo("Ahmet Yılmaz");
        assertThat(response.getCustomerNationalId()).isEqualTo("12345678901");
        assertThat(response.getVehiclePlate()).isEqualTo("34ABC123");
        assertThat(response.getVehicleChassisNumber()).isEqualTo("WDB1234567890");
        assertThat(response.getInsuranceTypeName()).isEqualTo("Vehicle");

        verify(estimationRepository).findById(testId);
        verify(customerServiceClient).getCustomerName(customerId);
        verify(customerServiceClient).getCustomerNationalId(customerId);
        verify(vehicleServiceClient).getVehicleInfo(vehicleId);
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

    // ---------------------------------------------------------------
    // 8. findAll — with invalid status → throws IllegalArgumentException
    // ---------------------------------------------------------------
    @Test
    void findAll_withInvalidStatus_throwsIllegalArgumentException() {
        Pageable pageable = PageRequest.of(0, 20);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> estimationService.findAll(null, "INVALID", pageable));
        assertThat(exception.getMessage()).contains("Invalid status");
        assertThat(exception.getMessage()).contains("INVALID");

        verify(estimationRepository, never()).findAll(any(Pageable.class));
        verify(estimationRepository, never()).findByStatus(any(Estimation.Status.class), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 9. findAll — with customerId + invalid status
    // ---------------------------------------------------------------
    @Test
    void findAll_withCustomerIdAndInvalidStatus_throwsIllegalArgumentException() {
        Pageable pageable = PageRequest.of(0, 20);
        UUID customerId = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> estimationService.findAll(customerId, "INVALID", pageable));
        assertThat(exception.getMessage()).contains("Invalid status");

        verify(estimationRepository, never()).findByCustomerIdAndStatus(any(), any(), any(Pageable.class));
    }
}
