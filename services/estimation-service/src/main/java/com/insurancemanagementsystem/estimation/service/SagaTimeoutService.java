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
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaTimeoutService {

    private final EstimationRepository estimationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventSerializer outboxEventSerializer;
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
        try {
            Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
            List<Estimation> startedEstimations = estimationRepository
                    .findByStatusAndCreatedAtBefore(Estimation.Status.STARTED, cutoff);
            List<Estimation> waitingApprovalEstimations = estimationRepository
                    .findByStatusAndCreatedAtBefore(Estimation.Status.WAITING_APPROVAL, cutoff);

            List<Estimation> staleEstimations = new ArrayList<>();
            staleEstimations.addAll(startedEstimations);
            staleEstimations.addAll(waitingApprovalEstimations);

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

                    String reason = "SAGA timed out after " + timeoutMinutes + " minutes";

                    // Use stored traceId (fall back to sagaId for pre-migration records)
                    UUID traceId = estimation.getTraceId() != null ? estimation.getTraceId() : sagaId;

                    // Serialize outbox event FIRST — if serialization fails, exception propagates
                    // and @Transactional rolls back the transaction, keeping estimation as STARTED
                    OutboxEvent outboxEvent = outboxEventSerializer.buildEstimationFailedOutboxEvent(
                            sagaId, traceId, reason, "SagaTimeoutService", EventConstants.ESTIMATION_SAGA);

                    // Transition to REJECTED
                    estimation.setStatus(Estimation.Status.REJECTED);
                    try {
                        estimation.setDetails(jsonMapper.writeValueAsString(Map.of("reason", reason)));
                    } catch (Exception e) {
                        log.warn("Failed to serialize timeout details for sagaId={} — using safe fallback", sagaId, e);
                        try {
                            // Nested try with a fresh JsonMapper to avoid any poisoned state
                            estimation.setDetails(tools.jackson.databind.json.JsonMapper.builder()
                                    .build().writeValueAsString(Map.of("reason", reason)));
                        } catch (Exception ex) {
                            // Absolute last resort — log and set a minimal valid JSON literal
                            log.error("Safe fallback serialization also failed for sagaId={}: {}", sagaId, ex.getMessage(), ex);
                            estimation.setDetails("{\"reason\":\"serialization failed\"}");
                        }
                    }
                    estimationRepository.save(estimation);
                    outboxEventRepository.save(outboxEvent);

                    log.info("Rejected timed-out estimation sagaId={} and saved outbox event", sagaId);
                } catch (Exception e) {
                    log.error("Failed to timeout estimation sagaId={}: {}", estimation.getSagaId(), e.getMessage(), e);
                    // Continue with next estimation — don't let one failure block others
                }
            }
        } catch (Exception e) {
            log.error("SagaTimeoutService.checkForTimedOutSagas() failed — scheduler will retry on next tick", e);
            // Do NOT re-throw — prevents silent scheduler cancellation
        }
    }
}
