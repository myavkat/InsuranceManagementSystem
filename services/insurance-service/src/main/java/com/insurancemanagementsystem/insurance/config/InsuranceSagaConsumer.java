package com.insurancemanagementsystem.insurance.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.*;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.insurance.repository.InsuranceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class InsuranceSagaConsumer {

    private final InsuranceRepository insuranceRepository;
    private final MessagePublisher messagePublisher;
    private final DeduplicationStore deduplicationStore;
    private final SagaAggregationStore aggregationStore;

    @Bean
    public Consumer<String> processInsuranceSaga(JsonMapper jsonMapper) {
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

                switch (eventType) {
                    case EventConstants.ESTIMATION_REQUESTED ->
                        handleEstimationRequested(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.CUSTOMER_VALIDATED ->
                        handleCustomerValidated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.VEHICLE_VALIDATED ->
                        handleVehicleValidated(envelope, sagaId, traceId, jsonMapper);
                    case EventConstants.CUSTOMER_INVALIDATED ->
                        handleInvalidated(envelope, sagaId, traceId, "Customer validation failed");
                    case EventConstants.VEHICLE_INVALIDATED ->
                        handleInvalidated(envelope, sagaId, traceId, "Vehicle validation failed");
                    case EventConstants.ESTIMATION_FAILED ->
                        handleEstimationFailed(envelope);
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
    // EstimationRequested — store insurance context
    // ---------------------------------------------------------------
    private void handleEstimationRequested(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        // Store in aggregation — will check if complete
        boolean ready = aggregationStore.storeAndCheckReady(sagaId.toString(), eventType, envelope);
        if (ready) {
            calculatePremium(sagaId, traceId, jsonMapper);
        }
    }

    // ---------------------------------------------------------------
    // CustomerValidated — store customer data
    // ---------------------------------------------------------------
    private void handleCustomerValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        boolean ready = aggregationStore.storeAndCheckReady(sagaId.toString(), eventType, envelope);
        if (ready) {
            calculatePremium(sagaId, traceId, jsonMapper);
        }
    }

    // ---------------------------------------------------------------
    // VehicleValidated — store vehicle data
    // ---------------------------------------------------------------
    private void handleVehicleValidated(EventEnvelope envelope, UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        String eventType = envelope.getEventType();

        if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
            log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
            return;
        }
        deduplicationStore.markProcessed(sagaId.toString(), eventType);

        boolean ready = aggregationStore.storeAndCheckReady(sagaId.toString(), eventType, envelope);
        if (ready) {
            calculatePremium(sagaId, traceId, jsonMapper);
        }
    }

    // ---------------------------------------------------------------
    // Invalidated — calculation not possible, publish failure
    // ---------------------------------------------------------------
    private void handleInvalidated(EventEnvelope envelope, UUID sagaId, UUID traceId, String reason) {
        log.warn("SAGA invalidated for sagaId={}: {}", sagaId, reason);
        aggregationStore.remove(sagaId.toString());

        CalculationFailedEvent failed = CalculationFailedEvent.builder()
                .reason(reason)
                .build();
        EventEnvelope outcome = failed.toEnvelope(sagaId, traceId);
        messagePublisher.publish(EventConstants.ESTIMATION_SAGA, outcome);
    }

    // ---------------------------------------------------------------
    // EstimationFailed — log only (no reversible action)
    // ---------------------------------------------------------------
    private void handleEstimationFailed(EventEnvelope envelope) {
        log.warn("Estimation failed for saga: {} — no compensation needed (calculation is stateless)",
                envelope.getSagaId());
    }

    // ---------------------------------------------------------------
    // Premium Calculation — core business logic
    // ---------------------------------------------------------------
    private void calculatePremium(UUID sagaId, UUID traceId, JsonMapper jsonMapper) {
        SagaAggregationStore.SagaState state = aggregationStore.retrieve(sagaId.toString());
        if (state == null) {
            log.warn("SAGA state not found for sagaId={} — already consumed?", sagaId);
            return;
        }

        // Extract estimation context
        EstimationRequestedEvent estimationEvent = jsonMapper.convertValue(
                state.getEstimationRequest().getPayload(), EstimationRequestedEvent.class);
        UUID customerId = estimationEvent.getCustomerId();
        UUID vehicleId = estimationEvent.getVehicleId();
        Integer insuranceTypeId = estimationEvent.getInsuranceTypeId();
        UUID companyId = estimationEvent.getCompanyId();

        // Extract customer data
        CustomerValidatedEvent customerEvent = jsonMapper.convertValue(
                state.getCustomerValidated().getPayload(), CustomerValidatedEvent.class);

        // Extract vehicle data
        VehicleValidatedEvent vehicleEvent = jsonMapper.convertValue(
                state.getVehicleValidated().getPayload(), VehicleValidatedEvent.class);

        // Look up insurance entity by typeId + companyId
        Optional<Insurance> insuranceOpt = insuranceRepository
                .findByTypeIdAndCompanyIdAndIsActiveTrue(insuranceTypeId, companyId, Pageable.unpaged())
                .stream().findFirst();

        if (insuranceOpt.isEmpty()) {
            publishCalculationFailed(sagaId, traceId,
                    "No active insurance found for typeId=" + insuranceTypeId + ", companyId=" + companyId);
            return;
        }

        Insurance insurance = insuranceOpt.get();
        BigDecimal basePremium = insurance.getBasePremium();
        if (basePremium == null) {
            publishCalculationFailed(sagaId, traceId, "Insurance has no base premium defined");
            return;
        }

        // Calculate premium: basePremium * risk factor
        // Risk factor = 1.0 (default) with simple adjustments
        BigDecimal riskFactor = BigDecimal.ONE;

        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        breakdown.put("basePremium", basePremium);

        // Simple risk factors (can be extended with more data later)
        BigDecimal measuredAdjustment = BigDecimal.ZERO;
        breakdown.put("riskFactor", riskFactor);
        breakdown.put("adjustment", measuredAdjustment);

        BigDecimal totalPremium = basePremium.multiply(riskFactor).add(measuredAdjustment);

        // Publish PremiumCalculated
        PremiumCalculatedEvent premiumEvent = PremiumCalculatedEvent.builder()
                .premium(totalPremium)
                .breakdown(breakdown)
                .insuranceTypeId(insuranceTypeId)
                .companyId(companyId)
                .customerId(customerId)
                .vehicleId(vehicleId)
                .build();

        EventEnvelope outcome = premiumEvent.toEnvelope(sagaId, traceId);
        messagePublisher.publish(EventConstants.ESTIMATION_SAGA, outcome);
        log.info("Premium calculated for sagaId={}: premium={}, typeId={}, companyId={}",
                sagaId, totalPremium, insuranceTypeId, companyId);
    }

    private void publishCalculationFailed(UUID sagaId, UUID traceId, String reason) {
        CalculationFailedEvent failed = CalculationFailedEvent.builder()
                .reason(reason)
                .build();
        EventEnvelope outcome = failed.toEnvelope(sagaId, traceId);
        messagePublisher.publish(EventConstants.ESTIMATION_SAGA, outcome);
        log.warn("Calculation failed for sagaId={}: {}", sagaId, reason);
    }
}
