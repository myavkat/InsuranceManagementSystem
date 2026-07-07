package com.insurancemanagementsystem.common.config;

import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Kafka trace propagation via Brave.
 * <p>
 * When Micrometer Tracing with Brave is on the classpath, this bean
 * enables automatic injection of trace headers (b3) into Kafka
 * producer records and extraction from consumer records.
 * <p>
 * Spring Cloud Stream's Kafka binder also auto-configures KafkaTracing
 * when Micrometer Tracing is detected — this configuration uses
 * {@code @ConditionalOnMissingBean} to avoid conflicts while ensuring
 * the bean is available for any direct KafkaTemplate usage.
 */
@AutoConfiguration
@ConditionalOnClass({Tracing.class, KafkaTracing.class})
public class KafkaTracingConfig {

    @Bean
    @ConditionalOnMissingBean(KafkaTracing.class)
    public KafkaTracing kafkaTracing(Tracing tracing) {
        return KafkaTracing.create(tracing);
    }
}
