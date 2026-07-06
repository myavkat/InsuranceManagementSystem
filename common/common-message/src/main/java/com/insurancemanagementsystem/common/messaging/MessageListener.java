package com.insurancemanagementsystem.common.messaging;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.repository.SagaEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Typed message listener base class for SAGA event consumers.
 * <p>
 * Handles deserialization, MDC context setup, dedup checking, and
 * exception handling so concrete consumers only implement business logic.
 *
 * @param <T> the specific event type this listener handles
 */
@Slf4j
public abstract class MessageListener<T extends BaseEvent> {

    protected final JsonMapper jsonMapper;
    protected final SagaEventRepository sagaEventRepository;
    protected final TransactionTemplate transactionTemplate;
    protected final Class<T> eventClass;

    protected MessageListener(JsonMapper jsonMapper,
                              SagaEventRepository sagaEventRepository,
                              TransactionTemplate transactionTemplate,
                              Class<T> eventClass) {
        this.jsonMapper = jsonMapper;
        this.sagaEventRepository = sagaEventRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventClass = eventClass;
    }

    /**
     * Returns a Spring Cloud Stream functional Consumer bean.
     * Subclasses call this in their {@code @Bean} method.
     */
    public Consumer<String> asConsumer() {
        return message -> {
            EventEnvelope envelope;
            try {
                envelope = jsonMapper.readValue(message, EventEnvelope.class);
            } catch (Exception e) {
                log.error("Failed to deserialize message — skipping (poison pill): {}", e.getMessage());
                return;
            }

            try {
                UUID sagaId = envelope.getSagaId();
                UUID traceId = envelope.getTraceId();
                String eventType = envelope.getEventType();

                MDC.put("sagaId", sagaId != null ? sagaId.toString() : "");
                MDC.put("traceId", traceId != null ? traceId.toString() : "");

                if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                    log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
                    return;
                }

                T event = jsonMapper.convertValue(envelope.getPayload(), eventClass);
                handleEvent(event, envelope);
            } catch (Exception e) {
                log.error("Error processing message: {}", e.getMessage(), e);
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException("Failed to process message", e);
            } finally {
                MDC.clear();
            }
        };
    }

    /**
     * Implement business logic for the event. Called after deserialization
     * and dedup check. The transaction is managed by the caller — use
     * {@code transactionTemplate.executeWithoutResult()} for multi-write operations.
     */
    protected abstract void handleEvent(T event, EventEnvelope envelope);
}
