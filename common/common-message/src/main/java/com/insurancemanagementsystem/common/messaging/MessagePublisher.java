package com.insurancemanagementsystem.common.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessagePublisher {

    private final StreamBridge streamBridge;

    public void publish(String topic, Object message) {
        log.debug("Publishing message to {}: {}", topic, message);
        boolean sent = streamBridge.send(topic, message);
        if (!sent) {
            throw new IllegalStateException(
                "Failed to send message to topic " + topic + " — StreamBridge returned false");
        }
    }

    /**
     * Publish a message after the current DB transaction commits.
     * If no transaction is active, publishes immediately.
     * This method uses an in-memory callback that is lost if the application crashes
     * between transaction commit and callback execution.
     *
     * @deprecated Use the outbox pattern ({@code OutboxEvent} table + {@code OutboxProcessor})
     *             instead. This method uses an in-memory callback that loses messages on crash.
     */
    @Deprecated(forRemoval = true)
    public void publishAfterCommit(String topic, Object message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.debug("Publishing message after transaction commit to {}: {}", topic, message);
                        boolean sent = streamBridge.send(topic, message);
                        if (!sent) {
                            log.error("Failed to send afterCommit message to topic {} — StreamBridge returned false", topic);
                        }
                    }
                });
            log.trace("Registered afterCommit publish to {}", topic);
        } else {
            // No active transaction — publish immediately
            publish(topic, message);
        }
    }
}
