package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.estimation.config.OutboxEventSerializer;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaTimeoutServiceTest {

    @Mock
    private EstimationRepository estimationRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventSerializer outboxEventSerializer;

    @InjectMocks
    private SagaTimeoutService timeoutService;

    @Captor
    private ArgumentCaptor<Instant> cutoffCaptor;

    @BeforeEach
    void setUp() {
        // @Value fields are not injected by Mockito — set manually
        ReflectionTestUtils.setField(timeoutService, "timeoutMinutes", 5);
        lenient().when(outboxEventSerializer.buildEstimationFailedOutboxEvent(
                any(), any(), any(), any())).thenReturn(OutboxEvent.builder().build());
    }

    // ---------------------------------------------------------------
    // Helper: create a stale estimation
    // ---------------------------------------------------------------
    private Estimation createStaleEstimation(UUID sagaId) {
        return Estimation.builder()
                .id(UUID.randomUUID())
                .sagaId(sagaId)
                .status(Estimation.Status.STARTED)
                .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build();
    }

    // ---------------------------------------------------------------
    // 1. No stale estimations → no changes, no events published
    // ---------------------------------------------------------------
    @Test
    void noStaleEstimations_noChanges() {
        when(estimationRepository.findByStatusAndCreatedAtBefore(any(), any()))
                .thenReturn(List.of());

        timeoutService.checkForTimedOutSagas();

        verify(estimationRepository).findByStatusAndCreatedAtBefore(
                eq(Estimation.Status.STARTED), any(Instant.class));
        verify(estimationRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // 2. Found stale estimations → each is rejected + EstimationFailed published
    // ---------------------------------------------------------------
    @Test
    void staleEstimations_areRejected() {
        UUID sagaId = UUID.randomUUID();
        Estimation stale = createStaleEstimation(sagaId);

        when(estimationRepository.findByStatusAndCreatedAtBefore(any(), any()))
                .thenReturn(List.of(stale));

        timeoutService.checkForTimedOutSagas();

        assertThat(stale.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        assertThat(stale.getDetails()).isEqualTo("{\"reason\":\"SAGA timed out after 5 minutes\"}");

        verify(estimationRepository).save(stale);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    // ---------------------------------------------------------------
    // 3. Multiple stale estimations → all processed
    // ---------------------------------------------------------------
    @Test
    void multipleStaleEstimations_allProcessed() {
        UUID sagaId1 = UUID.randomUUID();
        UUID sagaId2 = UUID.randomUUID();
        Estimation stale1 = createStaleEstimation(sagaId1);
        Estimation stale2 = createStaleEstimation(sagaId2);

        when(estimationRepository.findByStatusAndCreatedAtBefore(any(), any()))
                .thenReturn(List.of(stale1, stale2));

        timeoutService.checkForTimedOutSagas();

        assertThat(stale1.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        assertThat(stale2.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        assertThat(stale1.getDetails()).isEqualTo("{\"reason\":\"SAGA timed out after 5 minutes\"}");
        assertThat(stale2.getDetails()).isEqualTo("{\"reason\":\"SAGA timed out after 5 minutes\"}");
        verify(estimationRepository, times(2)).save(any());
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));
    }

    // ---------------------------------------------------------------
    // 4. Correct cutoff time calculation (based on timeoutMinutes config)
    // ---------------------------------------------------------------
    @Test
    void usesCorrectCutoffTime() {
        when(estimationRepository.findByStatusAndCreatedAtBefore(any(), any()))
                .thenReturn(List.of());

        timeoutService.checkForTimedOutSagas();

        verify(estimationRepository).findByStatusAndCreatedAtBefore(
                eq(Estimation.Status.STARTED), cutoffCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        Instant expectedCutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        assertThat(cutoff).isCloseTo(expectedCutoff, within(1, ChronoUnit.SECONDS));
    }
}
