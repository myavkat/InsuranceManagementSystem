package com.insurancemanagementsystem.vehicle.config;

import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.repository.SagaEventRepository;
import com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent;
import com.insurancemanagementsystem.common.event.saga.VehicleInvalidatedEvent;
import com.insurancemanagementsystem.common.event.saga.VehicleValidatedEvent;
import com.insurancemanagementsystem.vehicle.entity.CarBrand;
import com.insurancemanagementsystem.vehicle.entity.CarModel;
import com.insurancemanagementsystem.vehicle.entity.Vehicle;
import com.insurancemanagementsystem.vehicle.repository.CarBrandRepository;
import com.insurancemanagementsystem.vehicle.repository.CarModelRepository;
import com.insurancemanagementsystem.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class VehicleSagaConsumer {

    private final VehicleRepository vehicleRepository;
    private final CarBrandRepository carBrandRepository;
    private final CarModelRepository carModelRepository;
    private final SagaEventRepository sagaEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;

    @Bean
    public Consumer<String> processVehicleSaga(JsonMapper jsonMapperArg) {
        return message -> {
            EventEnvelope envelope;
            try {
                envelope = jsonMapperArg.readValue(message, EventEnvelope.class);

                UUID sagaId = envelope.getSagaId();
                UUID traceId = envelope.getTraceId();
                String eventType = envelope.getEventType();

                MDC.put("sagaId", sagaId != null ? sagaId.toString() : "");
                MDC.put("traceId", traceId != null ? traceId.toString() : "");

                log.info("Received SAGA event: {} for sagaId: {}", eventType, sagaId);

                switch (eventType) {
                    case EventConstants.ESTIMATION_REQUESTED ->
                        handleEstimationRequested(envelope, sagaId, traceId);
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

    private void handleEstimationRequested(EventEnvelope envelope, UUID sagaId, UUID traceId) {
        String eventType = envelope.getEventType();

        transactionTemplate.executeWithoutResult(status -> {
            if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
                log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
                return;
            }

            EstimationRequestedEvent requestEvent = jsonMapper.convertValue(
                    envelope.getPayload(), EstimationRequestedEvent.class);
            UUID vehicleId = requestEvent.getVehicleId();

            EventEnvelope outcomeEnvelope;
            if (vehicleId != null) {
                Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId);
                if (vehicleOpt.isPresent()) {
                    Vehicle vehicle = vehicleOpt.get();
                    String brandName = carBrandRepository.findById(vehicle.getCarBrandId())
                            .map(CarBrand::getName).orElse(null);
                    String modelName = carModelRepository.findById(vehicle.getCarModelId())
                            .map(CarModel::getName).orElse(null);

                    VehicleValidatedEvent validatedEvent = VehicleValidatedEvent.builder()
                            .vehicleId(vehicleId)
                            .plate(vehicle.getPlate())
                            .brand(brandName)
                            .model(modelName)
                            .build();
                    outcomeEnvelope = validatedEvent.toEnvelope(sagaId, traceId);
                    log.info("Vehicle {} validated for saga: {}", vehicleId, sagaId);
                } else {
                    String reason = "Vehicle not found";
                    VehicleInvalidatedEvent invalidatedEvent = VehicleInvalidatedEvent.builder()
                            .vehicleId(vehicleId)
                            .reason(reason)
                            .build();
                    outcomeEnvelope = invalidatedEvent.toEnvelope(sagaId, traceId);
                    log.warn("Vehicle {} invalidated for saga: {} — {}", vehicleId, sagaId, reason);
                }
            } else {
                // No vehicleId in the estimation request — this estimation doesn't need vehicle validation.
                // Still publish a validated event to unblock the saga
                VehicleValidatedEvent validatedEvent = VehicleValidatedEvent.builder()
                        .vehicleId(null)
                        .build();
                outcomeEnvelope = validatedEvent.toEnvelope(sagaId, traceId);
                log.info("No vehicleId in estimation request — publishing empty VehicleValidated for saga: {}", sagaId);
            }

            outboxEventRepository.save(buildOutboxEvent(sagaId, outcomeEnvelope, EventConstants.ESTIMATION_SAGA));
            log.debug("Saved outbox event for sagaId={}, eventType={}", sagaId, outcomeEnvelope.getEventType());
        });
    }

    private void handleEstimationFailed(EventEnvelope envelope) {
        UUID sagaId = envelope.getSagaId();
        String eventType = envelope.getEventType();
        if (sagaEventRepository.tryInsertDedup(sagaId, eventType)) {
            return;
        }
        log.warn("Estimation failed for saga: {} — no compensation needed (read-only validation)", sagaId);
    }

    private OutboxEvent buildOutboxEvent(UUID sagaId, EventEnvelope envelope, String topic) {
        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox payload for sagaId=" + sagaId, e);
        }
        return OutboxEvent.builder()
                .sagaId(sagaId)
                .topic(topic)
                .payload(payloadJson)
                .status(OutboxEvent.Status.PENDING)
                .build();
    }
}
