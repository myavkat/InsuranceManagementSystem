package com.insurancemanagementsystem.customer.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.repository.SagaEventRepository;
import com.insurancemanagementsystem.common.event.saga.CustomerInvalidatedEvent;
import com.insurancemanagementsystem.common.event.saga.CustomerValidatedEvent;
import com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent;
import com.insurancemanagementsystem.customer.entity.Customer;
import com.insurancemanagementsystem.customer.repository.CustomerRepository;
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
public class CustomerSagaConsumer {

    private final CustomerRepository customerRepository;
    private final SagaEventRepository sagaEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;

    @Bean
    public Consumer<String> processCustomerSaga(JsonMapper jsonMapper) {
        return message -> {
            // Deserialize — JacksonException (including StreamReadException) is a
            // RuntimeException in Jackson 3, but deserialization failures are
            // poison-pill messages that cannot be fixed by retry.
            EventEnvelope envelope;
            try {
                envelope = jsonMapper.readValue(message, EventEnvelope.class);
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

    /**
     * Validates the customer referenced in the estimation request and saves the result
     * as an outbox event within the same transaction as the dedup marker.
     * The outbox relay will later publish the event to Kafka atomically.
     */
    private void handleEstimationRequested(EventEnvelope envelope, UUID sagaId, UUID traceId) {
        String eventType = envelope.getEventType();

        transactionTemplate.executeWithoutResult(status -> {
            if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
                return;
            }

            // Convert the envelope payload to the typed event
            EstimationRequestedEvent requestEvent = jsonMapper.convertValue(
                    envelope.getPayload(), EstimationRequestedEvent.class);
            UUID customerId = requestEvent.getCustomerId();

            // Validate customer: must exist and not be soft-deleted
            Optional<Customer> customerOpt = customerRepository.findById(customerId)
                    .filter(c -> c.getDeletedAt() == null);

            EventEnvelope outcomeEnvelope;
            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();
                CustomerValidatedEvent validatedEvent = CustomerValidatedEvent.builder()
                        .customerId(customerId)
                        .firstName(customer.getFirstName())
                        .lastName(customer.getLastName())
                        .build();
                outcomeEnvelope = validatedEvent.toEnvelope(sagaId, traceId);
                log.info("Customer {} validated for saga: {}", customerId, sagaId);
            } else {
                String reason = "Customer not found or inactive";
                CustomerInvalidatedEvent invalidatedEvent = CustomerInvalidatedEvent.builder()
                        .customerId(customerId)
                        .reason(reason)
                        .build();
                outcomeEnvelope = invalidatedEvent.toEnvelope(sagaId, traceId);
                log.warn("Customer {} invalidated for saga: {} — {}", customerId, sagaId, reason);
            }

            // Save outbox event within the same transaction — the relay will publish it afterward
            outboxEventRepository.save(buildOutboxEvent(sagaId, outcomeEnvelope, EventConstants.ESTIMATION_SAGA));
            log.debug("Saved outbox event for sagaId={}, eventType={}", sagaId, outcomeEnvelope.getEventType());
        });
    }

    /**
     * Log-only handler. No Kafka publish is needed, so only the dedup guard applies.
     */
    private void handleEstimationFailed(EventEnvelope envelope) {
        UUID sagaId = envelope.getSagaId();
        String eventType = envelope.getEventType();
        if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
            return;
        }

        log.warn("Estimation failed for saga: {} — no compensation needed (read-only validation)", sagaId);
    }

    /**
     * Build a PENDING outbox event from the given saga context and event envelope.
     * The payload is serialised to JSON so the relay can publish it without re-entering
     * the consumer's classloader.
     */
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
