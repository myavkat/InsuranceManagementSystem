package com.insurancemanagementsystem.common.messaging;

import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Publishes events via the transactional outbox pattern.
 * <p>
 * All methods save an {@link OutboxEvent} to the database. The
 * {@link com.insurancemanagementsystem.common.config.OutboxRelay} picks up PENDING events
 * and delivers them to Kafka.
 * <p>
 * This is the canonical way to publish SAGA events. For domain events, services may use
 * this or direct {@link MessagePublisher} depending on durability requirements.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxMessagePublisher {

	private final OutboxEventRepository outboxEventRepository;

	private final JsonMapper jsonMapper;

	/**
	 * Publish a SAGA event via the outbox.
	 * @param event the event payload (must extend BaseEvent)
	 * @param sagaId the saga correlation ID
	 * @param traceId the trace ID (propagated from incoming event)
	 * @param topic the Kafka topic
	 */
	public void publish(BaseEvent event, UUID sagaId, UUID traceId, String topic) {
		String payloadJson;
		try {
			payloadJson = jsonMapper.writeValueAsString(event.toEnvelope(sagaId, traceId));
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to serialize outbox payload for sagaId=" + sagaId, e);
		}

		OutboxEvent outboxEvent = OutboxEvent.builder()
			.sagaId(sagaId)
			.topic(topic)
			.payload(payloadJson)
			.status(OutboxEvent.Status.PENDING)
			.build();
		outboxEventRepository.save(outboxEvent);
		log.debug("Saved outbox event for sagaId={}, eventType={} to topic={}", sagaId, event.getEventType(), topic);
	}

}
