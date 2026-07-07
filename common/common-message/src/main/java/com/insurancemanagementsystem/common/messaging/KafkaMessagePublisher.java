package com.insurancemanagementsystem.common.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka-based implementation of {@link MessagePublisher}.
 * <p>
 * Uses {@link KafkaTemplate}{@code <String, String>} to send pre-serialized JSON
 * strings directly to the configured Kafka topic. Unlike {@code StreamBridge},
 * this avoids the binder's content-type handling that converts String payloads to
 * {@code byte[]}, which caused deserialization issues with JsonSerializer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaMessagePublisher implements MessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void publish(String topic, Object message) {
        log.debug("Publishing message to {}: {}", topic, message);
        kafkaTemplate.send(topic, message.toString());
    }
}
