package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.estimation.config.OutboxEventSerializer;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaTimeoutService {

    private final EstimationRepository estimationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventSerializer outboxEventSerializer;

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
            UUID sagaId = estimation.getSagaId();
            log.warn("Timing out estimation id={}, sagaId={}, created at {}",
                    estimation.getId(), sagaId, estimation.getCreatedAt());

            String reason = "SAGA timed out after " + timeoutMinutes + " minutes";

            // Serialize outbox event FIRST — if serialization fails, exception propagates
            // and @Transactional rolls back the transaction, keeping estimation as STARTED
            OutboxEvent outboxEvent = outboxEventSerializer.buildEstimationFailedOutboxEvent(
                    sagaId, reason, "SagaTimeoutService", EventConstants.ESTIMATION_SAGA);

            // Transition to REJECTED
            estimation.setStatus(Estimation.Status.REJECTED);
            estimation.setDetails("{\"reason\":\"" + reason + "\"}");
            estimationRepository.save(estimation);
            outboxEventRepository.save(outboxEvent);

            log.info("Rejected timed-out estimation sagaId={} and saved outbox event", sagaId);
        }
    }
}
