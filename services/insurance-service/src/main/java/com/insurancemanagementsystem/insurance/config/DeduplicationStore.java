package com.insurancemanagementsystem.insurance.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DeduplicationStore {

    private final ConcurrentHashMap<String, Instant> store = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(10);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "dedup-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
        log.info("DeduplicationStore initialized with TTL={}m, cleanup interval=5m", ttl.toMinutes());
    }

    /**
     * Check if a (sagaId, eventType) pair has already been processed.
     */
    public boolean isDuplicate(String sagaId, String eventType) {
        return store.containsKey(buildKey(sagaId, eventType));
    }

    /**
     * Mark a (sagaId, eventType) pair as processed.
     */
    public void markProcessed(String sagaId, String eventType) {
        store.put(buildKey(sagaId, eventType), Instant.now());
        log.trace("Marked as processed: sagaId={}, eventType={}", sagaId, eventType);
    }

    private static String buildKey(String sagaId, String eventType) {
        return sagaId + ":" + eventType;
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(ttl);
        int before = store.size();
        store.values().removeIf(ts -> ts.isBefore(cutoff));
        int after = store.size();
        if (before != after) {
            log.debug("DeduplicationStore cleanup: removed {} entries ({} remaining)", before - after, after);
        }
    }
}
