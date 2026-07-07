package com.insurancemanagementsystem.common.config;

import com.insurancemanagementsystem.common.repository.SagaEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled cleanup for the saga_events dedup table.
 * <p>
 * Dedup markers are transient — once a SAGA completes or times out,
 * the markers serve no further purpose. This scheduled task deletes
 * markers older than the configured retention period to prevent
 * unbounded table growth (disk-exhaustion per AGENTS.md).
 * <p>
 * Default retention: 60 minutes (configurable via property).
 * Runs every 10 minutes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaEventCleanupService {

    private final SagaEventRepository sagaEventRepository;
    private final TransactionTemplate transactionTemplate;

    @Value("${saga-event.cleanup.retention-minutes:60}")
    private int retentionMinutes;

    /**
     * Delete dedup markers older than retentionMinutes.
     * Top-level try-catch prevents silent scheduler cancellation
     * on transient database errors (AGENTS.md requirement).
     */
    @Scheduled(fixedDelayString = "${saga-event.cleanup.interval-ms:600000}") // 10 minutes
    public void cleanupOldDedupMarkers() {
        try {
            Instant cutoff = Instant.now().minus(retentionMinutes, ChronoUnit.MINUTES);
            Integer deleted = transactionTemplate.execute(status ->
                sagaEventRepository.deleteByReceivedAtBefore(cutoff)
            );
            if (deleted != null && deleted > 0) {
                log.info("Cleaned up {} saga_events dedup markers older than {} minutes",
                        deleted, retentionMinutes);
            }
        } catch (Exception e) {
            log.error("SagaEventCleanupService.cleanupOldDedupMarkers() failed — "
                    + "scheduler will retry on next tick", e);
            // Do NOT re-throw — AGENTS.md: prevents silent scheduler cancellation
        }
    }
}
