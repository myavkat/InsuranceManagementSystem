package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.estimation.entity.OutboxEvent;
import com.insurancemanagementsystem.estimation.repository.OutboxEventRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final MessagePublisher messagePublisher;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "outbox-relay");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${estimation.outbox.poll-interval-ms:1000}")
    private int pollIntervalMs;

    @Value("${estimation.outbox.batch-size:10}")
    private int batchSize;

    @Value("${estimation.outbox.max-retries:3}")
    private int maxRetries;

    @Value("${estimation.outbox.failed-ttl-minutes:60}")
    private int failedTtlMinutes;

    @PostConstruct
    public void init() {
        scheduler.scheduleWithFixedDelay(this::processOutbox, 5, pollIntervalMs, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::cleanupFailedEvents, 10, 30, TimeUnit.MINUTES);
        log.info("OutboxRelay initialized: pollInterval={}ms, batchSize={}, maxRetries={}",
                pollIntervalMs, batchSize, maxRetries);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    @Transactional
    public void processOutbox() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findTop10ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Processing {} outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                event.setStatus(OutboxEvent.Status.PUBLISHING);
                outboxEventRepository.save(event);

                messagePublisher.publish(event.getTopic(), event.getPayload());

                outboxEventRepository.delete(event);
                log.debug("Published outbox event id={} to topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={} to topic={}: {}",
                        event.getId(), event.getTopic(), e.getMessage());
                event.setStatus(OutboxEvent.Status.FAILED);
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getMessage());
                if (event.getRetryCount() >= maxRetries) {
                    log.warn("Outbox event id={} reached max retries ({}). Giving up.", event.getId(), maxRetries);
                } else {
                    event.setStatus(OutboxEvent.Status.PENDING);
                }
                outboxEventRepository.save(event);
            }
        }
    }

    @Transactional
    public void cleanupFailedEvents() {
        Instant cutoff = Instant.now().minus(failedTtlMinutes, ChronoUnit.MINUTES);
        List<OutboxEvent> staleFailed = outboxEventRepository
                .findByStatusAndCreatedAtBefore(OutboxEvent.Status.FAILED, cutoff);
        if (!staleFailed.isEmpty()) {
            outboxEventRepository.deleteAll(staleFailed);
            log.info("Cleaned up {} stale FAILED outbox events", staleFailed.size());
        }
    }
}
