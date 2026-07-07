package com.insurancemanagementsystem.common.config;

import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.List;

/**
 * Shared Kafka error handler configuration for {@code @KafkaListener} containers.
 * <p>
 * <b>Scope:</b> This bean applies ONLY to {@code @KafkaListener}-annotated methods
 * (currently just {@link com.insurancemanagementsystem.common.messaging.DlqMonitor}).
 * Spring Cloud Stream functional {@code Consumer<String>} bindings (SAGA consumers)
 * use binder-level retry and DLQ configuration in {@code application.yml} instead.
 * <p>
 * Retry sequence: 1s → 2s → 4s → 8s (5 total attempts, max 20s elapsed),
 * then routes to {@code dlq.saga} partition 0.
 * Deserialization (poison-pill) failures are immediately routed to DLQ without retry.
 */
@Configuration
@Slf4j
public class KafkaErrorHandlerConfig {

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // dlq.saga has 1 partition — always route to partition 0
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (ConsumerRecord<?, ?> record, Exception exception) ->
                new TopicPartition("dlq.saga", 0)
        );

        // Retry sequence: 1s → 2s → 4s → 8s (5 total attempts, ~15s cumulative)
        // maxElapsedTime=20s adds buffer before DLQ routing
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000L);   // 1 second initial delay
        backOff.setMultiplier(2.0);          // double each retry
        backOff.setMaxInterval(8000L);       // cap at 8 seconds
        backOff.setMaxElapsedTime(20000L);   // cap retries at ~20s total

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Deserialization (poison-pill) failures won't be fixed by retry — route to DLQ immediately
        errorHandler.addNotRetryableExceptions(DeserializationException.class);

        log.info("Kafka error handler configured: retry 1s→2s→4s→8s (max 20s), DLQ=dlq.saga[0]");
        return errorHandler;
    }

    /**
     * Dedicated listener container factory for dlq-monitor-group.
     * Uses a no-retry error handler to prevent infinite DLQ loops —
     * if the monitor itself fails, the message is simply logged and committed
     * (not re-published to dlq.saga).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dlqMonitorContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // No retries, no DLQ publishing — just log and move on
        factory.setCommonErrorHandler(new CommonErrorHandler() {
            @Override
            public void handleRemaining(Exception thrownException,
                                        List<ConsumerRecord<?, ?>> records,
                                        org.apache.kafka.clients.consumer.Consumer<?, ?> consumer,
                                        MessageListenerContainer container) {
                log.error("DlqMonitor failed to process {} record(s) — committing offsets to avoid loop: {}",
                        records.size(), thrownException.getMessage(), thrownException);
                // Default behavior after this returns: commit offsets (acknowledge)
            }
        });
        return factory;
    }
}
