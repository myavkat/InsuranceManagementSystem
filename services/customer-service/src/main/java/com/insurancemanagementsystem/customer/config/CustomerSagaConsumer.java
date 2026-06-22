package com.insurancemanagementsystem.customer.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
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
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class CustomerSagaConsumer {

    private final CustomerRepository customerRepository;
    private final MessagePublisher messagePublisher;
    private final DeduplicationStore deduplicationStore;

    @Bean
    public Consumer<String> processCustomerSaga(JsonMapper jsonMapper) {
        return message -> {
            EventEnvelope envelope;
            try {
                envelope = jsonMapper.readValue(message, EventEnvelope.class);

                UUID sagaId = envelope.getSagaId();
                UUID traceId = envelope.getTraceId();
                String eventType = envelope.getEventType();

                MDC.put("sagaId", sagaId != null ? sagaId.toString() : "");
                MDC.put("traceId", traceId != null ? traceId.toString() : "");

                log.info("Received SAGA event: {} for sagaId: {}", eventType, sagaId);

                switch (eventType) {
                    case EventConstants.ESTIMATION_REQUESTED ->
                        handleEstimationRequested(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.ESTIMATION_FAILED ->
                        handleEstimationFailed(envelope);
                    default ->
                        log.warn("Unknown SAGA event type: {}", eventType);
                }
            } catch (Exception e) {
                log.error("Error processing SAGA message: {}", e.getMessage(), e);
            } finally {
                MDC.clear();
            }
        };
    }

    private void handleEstimationRequested(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        // Convert the envelope payload to the typed event
        EstimationRequestedEvent requestEvent = jsonMapper.convertValue(
                envelope.getPayload(), EstimationRequestedEvent.class);
        UUID customerId = requestEvent.getCustomerId();

        // Idempotency check: skip if already processed
        String sagaIdStr = sagaId.toString();
        if (deduplicationStore.isDuplicate(sagaIdStr, eventType)) {
            log.info("Duplicate event detected: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaIdStr, eventType);

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

        // Publish validation outcome to the SAGA topic
        messagePublisher.publish(EventConstants.ESTIMATION_SAGA, outcomeEnvelope);
        log.debug("Published {} to {} for saga: {}",
                outcomeEnvelope.getEventType(), EventConstants.ESTIMATION_SAGA, sagaId);
    }

    private void handleEstimationFailed(EventEnvelope envelope) {
        // Log only — no reversible action for read-only validation per architecture outline
        log.warn("Estimation failed for saga: {} — no compensation needed (read-only validation)",
                envelope.getSagaId());
    }
}
