package com.insurancemanagementsystem.referencedata.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.domain.ReferenceDataChangedEvent;
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataEventPublisher {

	private final MessagePublisher messagePublisher;

	/**
	 * Publish a ReferenceDataChangedEvent to reference-data.events topic. Called after
	 * any reference data mutation (admin endpoints, DB migrations, etc.).
	 */
	public void publishReferenceDataChanged(String entityType, String changeType) {
		ReferenceDataChangedEvent event = ReferenceDataChangedEvent.builder()
			.entityType(entityType)
			.changeType(changeType)
			.build();

		EventEnvelope envelope = event.toEnvelope(null, UUID.randomUUID());
		messagePublisher.publish(EventConstants.REFERENCE_DATA_EVENTS, envelope);
		log.info("Published ReferenceDataChanged event: entityType={}, changeType={}", entityType, changeType);
	}

}
