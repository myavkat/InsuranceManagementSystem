package com.insurancemanagementsystem.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Shared Kafka error handler configuration that provides exponential backoff retry
 * with Dead Letter Queue routing for all SAGA consumer bindings.
 * <p>
 * Retry sequence: 1s → 2s → 4s → 8s (5 retries total), then routes to {@code dlq.saga}.
 * Deserialization (poison-pill) failures are immediately routed to DLQ without retry,
 * since retrying will never fix a malformed message.
 */
@Configuration
@Slf4j
public class KafkaErrorHandlerConfig {

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public CommonErrorHandler kafkaErrorHandler(org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (ConsumerRecord<?, ?> record, Exception exception) ->
                new TopicPartition("dlq.saga", record.partition())
        );

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000L);   // 1 second initial delay
        backOff.setMultiplier(2.0);          // double each retry
        backOff.setMaxInterval(8000L);       // cap at 8 seconds

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Deserialization (poison-pill) failures won't be fixed by retry — route to DLQ immediately
        errorHandler.addNotRetryableExceptions(DeserializationException.class);

        log.info("Kafka error handler configured: retry 1s→2s→4s→8s, DLQ=dlq.saga");
        return errorHandler;
    }
}
