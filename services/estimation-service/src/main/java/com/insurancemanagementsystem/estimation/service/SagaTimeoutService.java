package com.insurancemanagementsystem.estimation.service;

import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.entity.OutboxEvent;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import com.insurancemanagementsystem.estimation.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

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
    private final JsonMapper jsonMapper;

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
                estimation.setDetails("{\"reason\":\"SAGA timed out after " + timeoutMinutes + " minutes\"}");
                estimationRepository.save(estimation);

                // Insert outbox event instead of direct publish
                saveOutboxEvent(sagaId, "SAGA timed out after " + timeoutMinutes + " minutes", "SagaTimeoutService");
            } catch (Exception e) {
                log.error("Failed to process timeout for estimation id={}", estimation.getId(), e);
            }
        }
    }

    private void saveOutboxEvent(UUID sagaId, String reason, String failedStep) {
        EstimationFailedEvent event = EstimationFailedEvent.builder()
                .originalSagaId(sagaId)
                .reason(reason)
                .failedStep(failedStep)
                .build();
        EventEnvelope envelope = event.toEnvelope(sagaId, UUID.randomUUID());

        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("Failed to serialize outbox payload for sagaId={}", sagaId, e);
            return; // Acceptable — timeout will retry on next cycle
        }

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .sagaId(sagaId)
                .topic("estimation.saga")
                .payload(payloadJson)
                .status(OutboxEvent.Status.PENDING)
                .build();
        outboxEventRepository.save(outboxEvent);
        log.info("Saved outbox event for sagaId={} — timeout rejection", sagaId);
    }
}
