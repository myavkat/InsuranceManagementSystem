package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.estimation.config.EstimationEventPublisher;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaTimeoutService {

    private final EstimationRepository estimationRepository;
    private final EstimationEventPublisher estimationEventPublisher;

    @Value("${estimation.saga.timeout-minutes:5}")
    private int timeoutMinutes;

    /**
     * Scheduled task that runs every 30 seconds.
     * Finds all estimations in STARTED status older than timeoutMinutes,
     * transitions them to REJECTED, and publishes EstimationFailed.
     */
    @Scheduled(fixedDelayString = "${estimation.saga.poll-interval-ms:30000}")
    @Transactional
    public void checkForTimedOutSagas() {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
        List<Estimation> staleEstimations = estimationRepository
                .findByStatusAndCreatedAtBefore(Estimation.Status.STARTED, cutoff);

        if (staleEstimations.isEmpty()) {
            log.trace("No timed-out estimations found (timeout={}min)", timeoutMinutes);
            return;
        }

        log.warn("Found {} timed-out estimations (timeout={}min)", staleEstimations.size(), timeoutMinutes);

        for (Estimation estimation : staleEstimations) {
            try {
                UUID sagaId = estimation.getSagaId();
                log.warn("Timing out estimation id={}, sagaId={}, created at {}",
                        estimation.getId(), sagaId, estimation.getCreatedAt());

                // Transition to REJECTED
                estimation.setStatus(Estimation.Status.REJECTED);
                estimation.setDetails("SAGA timed out after " + timeoutMinutes + " minutes");
                estimationRepository.save(estimation);

                // Defer publish until after DB transaction commits (atomicity)
                UUID capturedSagaId = sagaId;
                int capturedTimeout = timeoutMinutes;
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            estimationEventPublisher.publishEstimationFailed(
                                    capturedSagaId,
                                    null,
                                    "SAGA timed out after " + capturedTimeout + " minutes",
                                    "SagaTimeoutService");
                        }
                    });
            } catch (Exception e) {
                log.error("Failed to process timeout for estimation id={}", estimation.getId(), e);
            }
        }
    }
}
