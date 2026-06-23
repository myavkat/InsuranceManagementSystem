package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.*;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.entity.SagaEvent;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import com.insurancemanagementsystem.estimation.repository.SagaEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstimationSagaConsumerTest {

    @Mock
    private EstimationRepository estimationRepository;

    @Mock
    private SagaEventRepository sagaEventRepository;

    @Captor
    private ArgumentCaptor<SagaEvent> sagaEventCaptor;

    @Mock
    private EstimationEventPublisher estimationEventPublisher;

    @InjectMocks
    private EstimationSagaConsumer consumer;

    private final JsonMapper jsonMapper = new JsonMapper();

    // ---------------------------------------------------------------
    // Helper: build JSON string from a BaseEvent + sagaId
    // ---------------------------------------------------------------
    private String buildEventJson(BaseEvent event, UUID sagaId) {
        EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());
        try {
            return jsonMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------------------------------------------------
    // 1. CustomerValidated → logs progress, marks as processed
    // ---------------------------------------------------------------
    @Test
    void customerValidated_event_marksAsProcessed() {
        UUID sagaId = UUID.randomUUID();
        CustomerValidatedEvent event = CustomerValidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .build();

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        verify(sagaEventRepository).save(sagaEventCaptor.capture());
        assertThat(sagaEventCaptor.getValue().getSagaId()).isEqualTo(sagaId);
        assertThat(sagaEventCaptor.getValue().getEventType()).isEqualTo(EventConstants.CUSTOMER_VALIDATED);
        verifyNoInteractions(estimationRepository);
        verifyNoInteractions(estimationEventPublisher);
    }

    // ---------------------------------------------------------------
    // 2. VehicleValidated → logs progress, marks as processed
    // ---------------------------------------------------------------
    @Test
    void vehicleValidated_event_marksAsProcessed() {
        UUID sagaId = UUID.randomUUID();
        VehicleValidatedEvent event = VehicleValidatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .plate("34ABC123")
                .build();

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        verify(sagaEventRepository).save(sagaEventCaptor.capture());
        assertThat(sagaEventCaptor.getValue().getSagaId()).isEqualTo(sagaId);
        assertThat(sagaEventCaptor.getValue().getEventType()).isEqualTo(EventConstants.VEHICLE_VALIDATED);
        verifyNoInteractions(estimationRepository);
        verifyNoInteractions(estimationEventPublisher);
    }

    // ---------------------------------------------------------------
    // 3. PremiumCalculated → transitions to COMPLETED with premium
    // ---------------------------------------------------------------
    @Test
    void premiumCalculated_event_transitionsToCompleted() {
        UUID sagaId = UUID.randomUUID();
        BigDecimal premium = new BigDecimal("1500.00");
        PremiumCalculatedEvent event = PremiumCalculatedEvent.builder()
                .premium(premium)
                .breakdown(Map.of("base", new BigDecimal("1500.00")))
                .build();

        Estimation estimation = Estimation.builder()
                .id(UUID.randomUUID())
                .sagaId(sagaId)
                .status(Estimation.Status.STARTED)
                .build();

        when(estimationRepository.findBySagaId(sagaId)).thenReturn(Optional.of(estimation));

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.COMPLETED);
        assertThat(estimation.getPremium()).isEqualByComparingTo(premium);
        assertThat(estimation.getDetails()).startsWith("{").endsWith("}");
        assertThat(estimation.getDetails()).contains("\"base\"");
        verify(estimationRepository).save(estimation);
        verify(sagaEventRepository).save(sagaEventCaptor.capture());
        SagaEvent saved = sagaEventCaptor.getValue();
        assertThat(saved.getSagaId()).isEqualTo(sagaId);
        assertThat(saved.getEventType()).isEqualTo(EventConstants.PREMIUM_CALCULATED);
    }

    // ---------------------------------------------------------------
    // 4. PremiumCalculated for non-existing sagaId → logs warning, no crash
    // ---------------------------------------------------------------
    @Test
    void premiumCalculated_forNonExistingSagaId_logsWarning() {
        UUID sagaId = UUID.randomUUID();
        PremiumCalculatedEvent event = PremiumCalculatedEvent.builder()
                .premium(new BigDecimal("1500.00"))
                .build();

        when(estimationRepository.findBySagaId(sagaId)).thenReturn(Optional.empty());

        // Should not throw
        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        verify(estimationRepository).findBySagaId(sagaId);
        verify(estimationRepository, never()).save(any());
        verify(sagaEventRepository).save(sagaEventCaptor.capture());
        assertThat(sagaEventCaptor.getValue().getSagaId()).isEqualTo(sagaId);
        assertThat(sagaEventCaptor.getValue().getEventType()).isEqualTo(EventConstants.PREMIUM_CALCULATED);
    }

    // ---------------------------------------------------------------
    // 5. CustomerInvalidated → transitions to REJECTED + publishes EstimationFailed
    // ---------------------------------------------------------------
    @Test
    void customerInvalidated_event_transitionsToRejected() {
        UUID sagaId = UUID.randomUUID();
        CustomerInvalidatedEvent event = CustomerInvalidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .reason("Customer not found")
                .build();

        Estimation estimation = Estimation.builder()
                .id(UUID.randomUUID())
                .sagaId(sagaId)
                .status(Estimation.Status.STARTED)
                .build();

        when(estimationRepository.findBySagaId(sagaId)).thenReturn(Optional.of(estimation));

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        assertThat(estimation.getDetails()).contains("reason");
        assertThat(estimation.getDetails()).contains("Customer validation failed");
        verify(estimationRepository).save(estimation);
        verify(estimationEventPublisher).publishEstimationFailed(
                eq(sagaId), any(UUID.class), eq("Customer validation failed"), eq(EventConstants.CUSTOMER_INVALIDATED));
        verify(sagaEventRepository).save(sagaEventCaptor.capture());
        assertThat(sagaEventCaptor.getValue().getSagaId()).isEqualTo(sagaId);
        assertThat(sagaEventCaptor.getValue().getEventType()).isEqualTo(EventConstants.CUSTOMER_INVALIDATED);
    }

    // ---------------------------------------------------------------
    // 6. VehicleInvalidated → transitions to REJECTED + publishes EstimationFailed
    // ---------------------------------------------------------------
    @Test
    void vehicleInvalidated_event_transitionsToRejected() {
        UUID sagaId = UUID.randomUUID();
        VehicleInvalidatedEvent event = VehicleInvalidatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .reason("Vehicle not found")
                .build();

        Estimation estimation = Estimation.builder()
                .id(UUID.randomUUID())
                .sagaId(sagaId)
                .status(Estimation.Status.STARTED)
                .build();

        when(estimationRepository.findBySagaId(sagaId)).thenReturn(Optional.of(estimation));

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        assertThat(estimation.getDetails()).contains("reason");
        assertThat(estimation.getDetails()).contains("Vehicle validation failed");
        verify(estimationRepository).save(estimation);
        verify(estimationEventPublisher).publishEstimationFailed(
                eq(sagaId), any(UUID.class), eq("Vehicle validation failed"), eq(EventConstants.VEHICLE_INVALIDATED));
        verify(sagaEventRepository).save(sagaEventCaptor.capture());
        assertThat(sagaEventCaptor.getValue().getSagaId()).isEqualTo(sagaId);
        assertThat(sagaEventCaptor.getValue().getEventType()).isEqualTo(EventConstants.VEHICLE_INVALIDATED);
    }

    // ---------------------------------------------------------------
    // 7. CalculationFailed → transitions to REJECTED + publishes EstimationFailed
    // ---------------------------------------------------------------
    @Test
    void calculationFailed_event_transitionsToRejected() {
        UUID sagaId = UUID.randomUUID();
        CalculationFailedEvent event = CalculationFailedEvent.builder()
                .reason("Division by zero")
                .build();

        Estimation estimation = Estimation.builder()
                .id(UUID.randomUUID())
                .sagaId(sagaId)
                .status(Estimation.Status.STARTED)
                .build();

        when(estimationRepository.findBySagaId(sagaId)).thenReturn(Optional.of(estimation));

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        assertThat(estimation.getDetails()).contains("reason");
        assertThat(estimation.getDetails()).contains("Premium calculation failed");
        verify(estimationRepository).save(estimation);
        verify(estimationEventPublisher).publishEstimationFailed(
                eq(sagaId), any(UUID.class), eq("Premium calculation failed"), eq(EventConstants.CALCULATION_FAILED));
        verify(sagaEventRepository).save(sagaEventCaptor.capture());
        assertThat(sagaEventCaptor.getValue().getSagaId()).isEqualTo(sagaId);
        assertThat(sagaEventCaptor.getValue().getEventType()).isEqualTo(EventConstants.CALCULATION_FAILED);
    }

    // ---------------------------------------------------------------
    // 8. Duplicate event — skipped (idempotency)
    // ---------------------------------------------------------------
    @Test
    void duplicateEvent_isSkipped() {
        UUID sagaId = UUID.randomUUID();
        lenient().when(sagaEventRepository.save(any(SagaEvent.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        CustomerValidatedEvent event = CustomerValidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .firstName("John")
                .build();

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        verify(sagaEventRepository).save(any(SagaEvent.class));
        verifyNoInteractions(estimationRepository);
        verifyNoInteractions(estimationEventPublisher);
    }

    // ---------------------------------------------------------------
    // 9. Duplicate PremiumCalculated — skipped
    // ---------------------------------------------------------------
    @Test
    void duplicatePremiumCalculated_isSkipped() {
        UUID sagaId = UUID.randomUUID();
        lenient().when(sagaEventRepository.save(any(SagaEvent.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        PremiumCalculatedEvent event = PremiumCalculatedEvent.builder()
                .premium(new BigDecimal("1500.00"))
                .build();

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        verify(sagaEventRepository).save(any(SagaEvent.class));
        verifyNoInteractions(estimationRepository);
        verifyNoInteractions(estimationEventPublisher);
    }

    // ---------------------------------------------------------------
    // 10. Duplicate failed event — skipped
    // ---------------------------------------------------------------
    @Test
    void duplicateFailedEvent_isSkipped() {
        UUID sagaId = UUID.randomUUID();
        lenient().when(sagaEventRepository.save(any(SagaEvent.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        CustomerInvalidatedEvent event = CustomerInvalidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .reason("Duplicate")
                .build();

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        verify(sagaEventRepository).save(any(SagaEvent.class));
        verifyNoInteractions(estimationRepository);
        verifyNoInteractions(estimationEventPublisher);
    }

    // ---------------------------------------------------------------
    // 11. Unknown event type → logged, no action
    // ---------------------------------------------------------------
    @Test
    void unknownEventType_loggedAsWarning() {
        UUID sagaId = UUID.randomUUID();
        EventEnvelope envelope = EventEnvelope.builder()
                .sagaId(sagaId)
                .eventType("UnknownEventType")
                .traceId(UUID.randomUUID())
                .payload(Map.of())
                .build();

        String json;
        try {
            json = jsonMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Should not throw
        consumer.processEstimationSaga(jsonMapper).accept(json);

        verifyNoInteractions(estimationRepository);
        verifyNoInteractions(estimationEventPublisher);
        verifyNoInteractions(sagaEventRepository);
    }

    // ---------------------------------------------------------------
    // 12. EstimationFailed event → logged, no compensation needed
    // ---------------------------------------------------------------
    @Test
    void estimationFailedEvent_loggedOnly() {
        UUID sagaId = UUID.randomUUID();
        EstimationFailedEvent event = EstimationFailedEvent.builder()
                .originalSagaId(sagaId)
                .reason("Something went wrong")
                .failedStep("CustomerService")
                .build();

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        verifyNoInteractions(estimationRepository);
        verifyNoInteractions(estimationEventPublisher);
        // Deduplication is not checked for EstimationFailed in the code
    }

    // ---------------------------------------------------------------
    // 13. Malformed JSON → caught by try/catch, no crash
    // ---------------------------------------------------------------
    @Test
    void malformedJson_doesNotCrash() {
        // Should not throw
        consumer.processEstimationSaga(jsonMapper).accept("not valid json");

        verifyNoInteractions(estimationRepository);
        verifyNoInteractions(estimationEventPublisher);
    }

    // ---------------------------------------------------------------
    // 14. PremiumCalculated when estimation not in STARTED → logs warning, no transition
    // ---------------------------------------------------------------
    @Test
    void premiumCalculated_forNonStartedEstimation_logsWarning() {
        UUID sagaId = UUID.randomUUID();
        PremiumCalculatedEvent event = PremiumCalculatedEvent.builder()
                .premium(new BigDecimal("1500.00"))
                .build();

        Estimation alreadyCompleted = Estimation.builder()
                .id(UUID.randomUUID())
                .sagaId(sagaId)
                .status(Estimation.Status.COMPLETED)
                .premium(new BigDecimal("2000.00"))
                .build();

        when(estimationRepository.findBySagaId(sagaId)).thenReturn(Optional.of(alreadyCompleted));

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        // Should NOT change status or premium
        assertThat(alreadyCompleted.getStatus()).isEqualTo(Estimation.Status.COMPLETED);
        assertThat(alreadyCompleted.getPremium()).isEqualByComparingTo(new BigDecimal("2000.00"));
        verify(estimationRepository, never()).save(any());
        verify(sagaEventRepository).save(sagaEventCaptor.capture());
        assertThat(sagaEventCaptor.getValue().getSagaId()).isEqualTo(sagaId);
        assertThat(sagaEventCaptor.getValue().getEventType()).isEqualTo(EventConstants.PREMIUM_CALCULATED);
    }

    // ---------------------------------------------------------------
    // 15. Failed event when estimation not in STARTED → logs warning, no transition
    // ---------------------------------------------------------------
    @Test
    void failedEvent_forNonStartedEstimation_logsWarning() {
        UUID sagaId = UUID.randomUUID();
        CustomerInvalidatedEvent event = CustomerInvalidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .reason("Invalid")
                .build();

        Estimation alreadyRejected = Estimation.builder()
                .id(UUID.randomUUID())
                .sagaId(sagaId)
                .status(Estimation.Status.REJECTED)
                .build();

        when(estimationRepository.findBySagaId(sagaId)).thenReturn(Optional.of(alreadyRejected));

        consumer.processEstimationSaga(jsonMapper).accept(buildEventJson(event, sagaId));

        // Should NOT change status
        assertThat(alreadyRejected.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        verify(estimationRepository, never()).save(any());
        verify(sagaEventRepository).save(sagaEventCaptor.capture());
        assertThat(sagaEventCaptor.getValue().getSagaId()).isEqualTo(sagaId);
        assertThat(sagaEventCaptor.getValue().getEventType()).isEqualTo(EventConstants.CUSTOMER_INVALIDATED);
        // Should NOT publish because no transition actually occurred
        verify(estimationEventPublisher, never()).publishEstimationFailed(any(), any(), any(), any());
    }
}
