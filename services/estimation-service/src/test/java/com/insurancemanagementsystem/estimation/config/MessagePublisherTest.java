package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.messaging.KafkaMessagePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MessagePublisherTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;

	@InjectMocks
	private KafkaMessagePublisher messagePublisher;

	@Test
	void publish_sendsViaKafkaTemplate() {
		String topic = "test.topic";
		String payload = "test-payload";

		messagePublisher.publish(topic, payload);

		verify(kafkaTemplate).send(topic, payload);
	}

}
