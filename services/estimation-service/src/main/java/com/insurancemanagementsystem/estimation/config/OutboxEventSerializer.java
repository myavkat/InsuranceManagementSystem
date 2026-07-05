package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent;
import com.insurancemanagementsystem.common.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventSerializer {

    private final JsonMapper jsonMapper;

    /**
     * Build and serialize an EstimationFailed outbox event.
     * Throws RuntimeException if serialization fails — caller must handle.
     */
    public OutboxEvent buildEstimationFailedOutboxEvent(
            UUID sagaId, String reason, String failedStep, String topic) {

        EstimationFailedEvent event = EstimationFailedEvent.builder()
                .originalSagaId(sagaId)
                .reason(reason)
                .failedStep(failedStep)
                .build();

        EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());

        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize EstimationFailed outbox payload for sagaId=" + sagaId, e);
        }

        return OutboxEvent.builder()
                .sagaId(sagaId)
                .topic(topic)
                .payload(payloadJson)
                .status(OutboxEvent.Status.PENDING)
                .build();
    }
}
