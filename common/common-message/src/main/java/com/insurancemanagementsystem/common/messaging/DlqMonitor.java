package com.insurancemanagementsystem.common.messaging;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

/**
 * Monitors the dlq.saga topic and logs dead-lettered messages for admin review.
 * <p>
 * This is a diagnostic consumer — it does NOT reprocess or retry.
 * Admin intervention is required to investigate and potentially replay
 * DLQ messages after fixing the root cause.
 */
@Component
@Slf4j
public class DlqMonitor {

    /**
     * Consumes messages from dlq.saga and logs them with all available context.
     * Each message includes original topic, partition, offset, and exception details
     * in Kafka headers (populated by DeadLetterPublishingRecoverer).
     */
    @KafkaListener(
        topics = "dlq.saga",
        groupId = "dlq-monitor-group",
        containerFactory = "dlqMonitorContainerFactory",
        autoStartup = "true"
    )
    public void consume(ConsumerRecord<String, String> record) {
        log.error("""
                ========================================
                DLQ MESSAGE RECEIVED — ADMIN ACTION REQUIRED
                Topic: {}
                Partition: {}
                Offset: {}
                Key: {}
                Original Topic: {}
                Original Partition: {}
                Original Offset: {}
                Exception Message: {}
                Exception Stacktrace: {}
                Payload: {}
                ========================================\
                """,
            record.topic(),
            record.partition(),
            record.offset(),
            record.key(),
            header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
            header(record, KafkaHeaders.DLT_ORIGINAL_PARTITION),
            header(record, KafkaHeaders.DLT_ORIGINAL_OFFSET),
            header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
            header(record, KafkaHeaders.DLT_EXCEPTION_STACKTRACE),
            record.value()
        );
    }

    private String header(ConsumerRecord<String, String> record, String key) {
        var h = record.headers().lastHeader(key);
        return h != null ? new String(h.value()) : "N/A";
    }
}
