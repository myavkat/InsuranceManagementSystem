package com.insurancemanagementsystem.referencedata.config;

import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReferenceDataEventPublisherTest {

	@Mock
	private MessagePublisher messagePublisher;

	@InjectMocks
	private ReferenceDataEventPublisher eventPublisher;

	@Test
	void shouldPublishReferenceDataChangedEvent() {
		// When
		eventPublisher.publishReferenceDataChanged("City", "UPDATE");

		// Then
		verify(messagePublisher).publish(any(), any());
	}

}
