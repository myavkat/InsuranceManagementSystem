package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EstimationEventPublisherTest {

    @Mock
    private MessagePublisher messagePublisher;

    @InjectMocks
    private EstimationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<Object> messageCaptor;

    @Test
    void publishEstimationFailed_publishesToEstimationSaga() {
        UUID sagaId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();

        eventPublisher.publishEstimationFailed(sagaId, traceId, "Test failure", "TestStep");

        verify(messagePublisher).publish(eq(EventConstants.ESTIMATION_SAGA), messageCaptor.capture());

        Object sent = messageCaptor.getValue();
        assertThat(sent).isNotNull();
    }

    @Test
    void publishEstimationFailed_withNullTraceId_generatesRandomTraceId() {
        UUID sagaId = UUID.randomUUID();

        eventPublisher.publishEstimationFailed(sagaId, null, "Reason", "Step");

        verify(messagePublisher).publish(eq(EventConstants.ESTIMATION_SAGA), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isNotNull();
    }
}
