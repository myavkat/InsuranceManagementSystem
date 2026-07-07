package com.insurancemanagementsystem.realestate.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.repository.SagaEventRepository;
import com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent;
import com.insurancemanagementsystem.common.event.saga.RealEstateInvalidatedEvent;
import com.insurancemanagementsystem.common.event.saga.RealEstateValidatedEvent;
import com.insurancemanagementsystem.realestate.entity.RealEstate;
import com.insurancemanagementsystem.realestate.repository.RealEstateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RealEstateSagaConsumer {

    private final RealEstateRepository realEstateRepository;
    private final SagaEventRepository sagaEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;

    @Bean
    public Consumer<String> processRealEstateSaga(JsonMapper jsonMapperArg) {
        return message -> {
            // Deserialize — JacksonException (including StreamReadException) is a
            // RuntimeException in Jackson 3, but deserialization failures are
            // poison-pill messages that cannot be fixed by retry.
            EventEnvelope envelope;
            try {
                envelope = jsonMapperArg.readValue(message, EventEnvelope.class);
            } catch (Exception e) {
                log.error("Failed to deserialize SAGA message — routing to DLQ: {}", e.getMessage(), e);
                throw new RuntimeException("Deserialization failed — routing to DLQ", e);
            }

            try {
                UUID sagaId = envelope.getSagaId();
                UUID traceId = envelope.getTraceId();
                String eventType = envelope.getEventType();

                MDC.put("sagaId", sagaId != null ? sagaId.toString() : "");
                MDC.put("traceId", traceId != null ? traceId.toString() : "");

                log.info("Received SAGA event: {} for sagaId: {}", eventType, sagaId);

                switch (eventType) {
                    case EventConstants.ESTIMATION_REQUESTED ->
                        handleEstimationRequested(envelope, sagaId, traceId);
                    case EventConstants.ESTIMATION_FAILED ->
                        handleEstimationFailed(envelope);
                    default ->
                        log.warn("Unknown SAGA event type: {}", eventType);
                }
            } catch (Exception e) {
                log.error("Error processing SAGA message: {}", e.getMessage(), e);
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException("Failed to process SAGA message", e);
            } finally {
                MDC.clear();
            }
        };
    }

    private void handleEstimationRequested(EventEnvelope envelope, UUID sagaId, UUID traceId) {
        String eventType = envelope.getEventType();

        transactionTemplate.executeWithoutResult(status -> {
            if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
                return;
            }

            EstimationRequestedEvent requestEvent = jsonMapper.convertValue(
                    envelope.getPayload(), EstimationRequestedEvent.class);
            UUID realEstateId = requestEvent.getRealEstateId();

            EventEnvelope outcomeEnvelope;
            if (realEstateId != null) {
                Optional<RealEstate> realEstateOpt = realEstateRepository.findById(realEstateId);
                if (realEstateOpt.isPresent()) {
                    RealEstate realEstate = realEstateOpt.get();
                    RealEstateValidatedEvent validatedEvent = RealEstateValidatedEvent.builder()
                            .realEstateId(realEstateId)
                            .address(realEstate.getAddress())
                            .cityId(realEstate.getCityId())
                            .build();
                    outcomeEnvelope = validatedEvent.toEnvelope(sagaId, traceId);
                    log.info("RealEstate {} validated for saga: {}", realEstateId, sagaId);
                } else {
                    String reason = "Real estate not found";
                    RealEstateInvalidatedEvent invalidatedEvent = RealEstateInvalidatedEvent.builder()
                            .realEstateId(realEstateId)
                            .reason(reason)
                            .build();
                    outcomeEnvelope = invalidatedEvent.toEnvelope(sagaId, traceId);
                    log.warn("RealEstate {} invalidated for saga: {} — {}", realEstateId, sagaId, reason);
                }
            } else {
                // No realEstateId in the estimation request — this estimation doesn't need real estate validation.
                // Still publish a validated event to unblock the saga
                RealEstateValidatedEvent validatedEvent = RealEstateValidatedEvent.builder()
                        .realEstateId(null)
                        .build();
                outcomeEnvelope = validatedEvent.toEnvelope(sagaId, traceId);
                log.info("No realEstateId in estimation request — publishing empty RealEstateValidated for saga: {}", sagaId);
            }

            outboxEventRepository.save(buildOutboxEvent(sagaId, outcomeEnvelope, EventConstants.ESTIMATION_SAGA));
            log.debug("Saved outbox event for sagaId={}, eventType={}", sagaId, outcomeEnvelope.getEventType());
        });
    }

    private void handleEstimationFailed(EventEnvelope envelope) {
        UUID sagaId = envelope.getSagaId();
        String eventType = envelope.getEventType();

        transactionTemplate.executeWithoutResult(status -> {
            if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                return;
            }
            log.warn("Estimation failed for saga: {} — no compensation needed (read-only validation)", sagaId);
        });
    }

    private OutboxEvent buildOutboxEvent(UUID sagaId, EventEnvelope envelope, String topic) {
        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox payload for sagaId=" + sagaId, e);
        }
        return OutboxEvent.builder()
                .sagaId(sagaId)
                .topic(topic)
                .payload(payloadJson)
                .status(OutboxEvent.Status.PENDING)
                .build();
    }
}
