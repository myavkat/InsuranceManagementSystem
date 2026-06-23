package com.insurancemanagementsystem.estimation.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.*;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class EstimationSagaConsumer {

    private final EstimationRepository estimationRepository;
    private final DeduplicationStore deduplicationStore;
    private final EstimationEventPublisher estimationEventPublisher;

    @Bean
    public Consumer<String> processEstimationSaga(JsonMapper jsonMapper) {
        return message -> {
            EventEnvelope envelope;
            try {
                envelope = jsonMapper.readValue(message, EventEnvelope.class);

                UUID sagaId = envelope.getSagaId();
                UUID traceId = envelope.getTraceId();
                String eventType = envelope.getEventType();

                MDC.put("sagaId", sagaId != null ? sagaId.toString() : "");
                MDC.put("traceId", traceId != null ? traceId.toString() : "");

                log.info("Received SAGA event: {} for sagaId: {}", eventType, sagaId);

                // Handle each event type — note: EstimationService already published
                // EstimationRequested, so we don't consume it here.
                switch (eventType) {
                    case EventConstants.CUSTOMER_VALIDATED ->
                        handleCustomerValidated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.VEHICLE_VALIDATED ->
                        handleVehicleValidated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.CUSTOMER_INVALIDATED ->
                        handleFailed(envelope, sagaId, traceId, "Customer validation failed");
                    case EventConstants.VEHICLE_INVALIDATED ->
                        handleFailed(envelope, sagaId, traceId, "Vehicle validation failed");
                    case EventConstants.CALCULATION_FAILED ->
                        handleFailed(envelope, sagaId, traceId, "Premium calculation failed");
                    case EventConstants.PREMIUM_CALCULATED ->
                        handlePremiumCalculated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.ESTIMATION_FAILED ->
                        handleEstimationFailed(envelope, sagaId);
                    default ->
                        log.warn("Unknown SAGA event type: {}", eventType);
                }
            } catch (Exception e) {
                log.error("Error processing SAGA message: {}", e.getMessage(), e);
            } finally {
                MDC.clear();
            }
        };
    }

    // ---------------------------------------------------------------
    // CustomerValidated — log progress (no state change needed in estimation)
    // ---------------------------------------------------------------
    private void handleCustomerValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        // Convert to typed event for logging
        CustomerValidatedEvent event = jsonMapper.convertValue(
                envelope.getPayload(), CustomerValidatedEvent.class);
        log.info("Customer validated for sagaId={}: customerId={}, {} {}",
                sagaId, event.getCustomerId(), event.getFirstName(), event.getLastName());
    }

    // ---------------------------------------------------------------
    // VehicleValidated — log progress (no state change needed in estimation)
    // ---------------------------------------------------------------
    private void handleVehicleValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        // Convert to typed event for logging
        VehicleValidatedEvent event = jsonMapper.convertValue(
                envelope.getPayload(), VehicleValidatedEvent.class);
        log.info("Vehicle validated for sagaId={}: vehicleId={}, plate={}", sagaId, event.getVehicleId(), event.getPlate());
    }

    // ---------------------------------------------------------------
    // PremiumCalculated — transition estimation to COMPLETED
    // ---------------------------------------------------------------
    private void handlePremiumCalculated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        // Convert payload
        PremiumCalculatedEvent event = jsonMapper.convertValue(
                envelope.getPayload(), PremiumCalculatedEvent.class);

        // Find estimation by sagaId
        estimationRepository.findBySagaId(sagaId).ifPresentOrElse(estimation -> {
            // Transition: STARTED → COMPLETED
            if (estimation.getStatus() != Estimation.Status.STARTED) {
                log.warn("Estimation {} is in status {} — cannot transition to COMPLETED",
                        estimation.getId(), estimation.getStatus());
                return;
            }

            estimation.setStatus(Estimation.Status.COMPLETED);
            estimation.setPremium(event.getPremium());
            if (event.getBreakdown() != null) {
                estimation.setDetails(jsonMapper.writeValueAsString(event.getBreakdown()));
            }
            estimationRepository.save(estimation);
            log.info("Estimation {} completed for sagaId={}: premium={}",
                    estimation.getId(), sagaId, event.getPremium());
        }, () -> log.warn("No estimation found for sagaId={}", sagaId));
    }

    // ---------------------------------------------------------------
    // Failed events — transition estimation to REJECTED, publish EstimationFailed
    // ---------------------------------------------------------------
    private void handleFailed(EventEnvelope envelope, UUID sagaId, UUID traceId, String reason) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        log.warn("SAGA failed for sagaId={}: eventType={}, reason={}", sagaId, eventType, reason);

        // Find and reject estimation
        estimationRepository.findBySagaId(sagaId).ifPresentOrElse(estimation -> {
            if (estimation.getStatus() != Estimation.Status.STARTED) {
                log.warn("Estimation {} is in status {} — cannot transition to REJECTED",
                        estimation.getId(), estimation.getStatus());
                return;
            }

            estimation.setStatus(Estimation.Status.REJECTED);
            estimation.setDetails(reason);
            estimationRepository.save(estimation);
            log.info("Estimation {} rejected for sagaId={}: {}", estimation.getId(), sagaId, reason);

            // Publish EstimationFailed for compensation in other services
            estimationEventPublisher.publishEstimationFailed(sagaId, traceId, reason, eventType);
        }, () -> log.warn("No estimation found for sagaId={}", sagaId));
    }

    // ---------------------------------------------------------------
    // EstimationFailed — log only (no reversible action for read-only services)
    // ---------------------------------------------------------------
    private void handleEstimationFailed(EventEnvelope envelope, UUID sagaId) {
        log.warn("Estimation failed for saga: {} — no compensation needed (estimation state updated)", sagaId);
    }
}
