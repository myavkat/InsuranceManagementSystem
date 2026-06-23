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
        streamBridge.send(topic, message);
    }

    /**
     * Publish a message after the current DB transaction commits.
     * If no transaction is active, publishes immediately.
     * This prevents the "dual-write" problem where the DB is updated
     * but the message is lost (e.g., if the message broker is unavailable).
     */
    public void publishAfterCommit(String topic, Object message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.debug("Publishing message after transaction commit to {}: {}", topic, message);
                        streamBridge.send(topic, message);
                    }
                });
            log.trace("Registered afterCommit publish to {}", topic);
        } else {
            // No active transaction — publish immediately
            publish(topic, message);
        }
    }
}
