package com.insurancemanagementsystem.common.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxProcessor outboxProcessor;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "outbox-relay");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${outbox.poll-interval-ms:1000}")
    private int pollIntervalMs;

    @Value("${outbox.max-retries:3}")
    private int maxRetries;

    @Value("${outbox.failed-ttl-minutes:60}")
    private int failedTtlMinutes;

    @PostConstruct
    public void init() {
        outboxProcessor.setMaxRetries(maxRetries);
        outboxProcessor.setFailedTtlMinutes(failedTtlMinutes);

        scheduler.scheduleWithFixedDelay(outboxProcessor::processOutbox, 5, pollIntervalMs, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(outboxProcessor::cleanupEvents, 10, 30, TimeUnit.MINUTES);
        log.info("OutboxRelay initialized: pollInterval={}ms, maxRetries={}", pollIntervalMs, maxRetries);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
