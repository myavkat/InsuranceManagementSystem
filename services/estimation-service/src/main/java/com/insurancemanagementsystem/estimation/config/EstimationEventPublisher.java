package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EstimationEventPublisher {

    private final MessagePublisher messagePublisher;

    /**
     * Publish EstimationFailed to estimation.saga (compensation event).
     * Called when saga times out or when validation/calculation fails.
     */
    public void publishEstimationFailed(UUID sagaId, UUID traceId, String reason, String failedStep) {
        EstimationFailedEvent event = EstimationFailedEvent.builder()
                .originalSagaId(sagaId)
                .reason(reason)
                .failedStep(failedStep)
                .build();

        EventEnvelope envelope = event.toEnvelope(sagaId, traceId != null ? traceId : UUID.randomUUID());
        messagePublisher.publish(EventConstants.ESTIMATION_SAGA, envelope);
        log.warn("Published EstimationFailed for sagaId={}: reason={}, failedStep={}", sagaId, reason, failedStep);
    }
}
