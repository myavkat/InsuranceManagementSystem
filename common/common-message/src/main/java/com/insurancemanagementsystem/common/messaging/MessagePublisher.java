package com.insurancemanagementsystem.common.messaging;

/**
 * Publishes messages to Kafka topics. Implementations handle the serialization
 * and delivery details based on the configured transport.
 */
public interface MessagePublisher {

    /**
     * Publish a message to the given Kafka topic.
     *
     * @param topic   the target Kafka topic
     * @param message the message payload (typically a pre-serialized JSON string)
     */
    void publish(String topic, Object message);
}
