package com.insurancemanagementsystem.vehicle.saga;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent;
import com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent;
import com.insurancemanagementsystem.common.event.saga.VehicleInvalidatedEvent;
import com.insurancemanagementsystem.common.event.saga.VehicleValidatedEvent;
import com.insurancemanagementsystem.vehicle.entity.Vehicle;
import com.insurancemanagementsystem.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EmbeddedKafka(
        topics = {"estimation.saga"},
        partitions = 1,
        controlledShutdown = true
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VehicleSagaConsumerTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_vehicle_saga_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private OutboxEventRepository outboxEventRepository;

    private final List<OutboxEvent> capturedOutboxEvents = new ArrayList<>();

    private static final ObjectMapper MAPPER = new JsonMapper();

    private UUID sagaId;
    private UUID traceId;

    @BeforeEach
    void setUp() {
        vehicleRepository.deleteAll();
        capturedOutboxEvents.clear();
        sagaId = UUID.randomUUID();
        traceId = UUID.randomUUID();

        // Capture saved outbox events and assign an ID (as the real DB would)
        doAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            if (event.getId() == null) {
                event.setId(UUID.randomUUID());
            }
            capturedOutboxEvents.add(event);
            return event;
        }).when(outboxEventRepository).save(any(OutboxEvent.class));

        // Ensure relay queries return empty lists so the OutboxRelay scheduled task is a no-op
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(any(), any()))
                .thenReturn(List.of());
    }

    // ---------------------------------------------------------------
    // 1. Valid vehicle → VehicleValidated
    // ---------------------------------------------------------------
    @Test
    void validVehicle_ShouldSaveOutboxEventForVehicleValidated() throws Exception {
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                .plate("34 ABC 1234")
                .chassisNumber("1HGCM82633A004352")
                .licenseFirstDate(LocalDate.of(2020, 1, 15))
                .carBrandId(1)
                .carModelId(1)
                .carEngineId(1)
                .carFuelTypeId(1)
                .carTypeId(1)
                .carPackageId(1)
                .customerId(UUID.randomUUID())
                .build());

        EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder()
                .vehicleId(vehicle.getId())
                .build();
        EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
        String message = MAPPER.writeValueAsString(envelope);

        kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));

        OutboxEvent saved = capturedOutboxEvents.get(0);
        EventEnvelope published = MAPPER.readValue(saved.getPayload(), EventEnvelope.class);
        assertThat(published.getEventType()).isEqualTo(EventConstants.VEHICLE_VALIDATED);
        assertThat(published.getSagaId()).isEqualTo(sagaId);
        assertThat(published.getTraceId()).isEqualTo(traceId);

        VehicleValidatedEvent payload = MAPPER.convertValue(published.getPayload(), VehicleValidatedEvent.class);
        assertThat(payload.getVehicleId()).isEqualTo(vehicle.getId());
        assertThat(payload.getPlate()).isEqualTo("34 ABC 1234");
    }

    // ---------------------------------------------------------------
    // 2. Non-existent vehicleId → VehicleInvalidated
    // ---------------------------------------------------------------
    @Test
    void nonExistentVehicleId_ShouldSaveOutboxEventForVehicleInvalidated() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder()
                .vehicleId(nonExistentId)
                .build();
        EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
        String message = MAPPER.writeValueAsString(envelope);

        kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));

        OutboxEvent saved = capturedOutboxEvents.get(0);
        EventEnvelope published = MAPPER.readValue(saved.getPayload(), EventEnvelope.class);
        assertThat(published.getEventType()).isEqualTo(EventConstants.VEHICLE_INVALIDATED);
        assertThat(published.getSagaId()).isEqualTo(sagaId);

        VehicleInvalidatedEvent payload = MAPPER.convertValue(published.getPayload(), VehicleInvalidatedEvent.class);
        assertThat(payload.getVehicleId()).isEqualTo(nonExistentId);
        assertThat(payload.getReason()).containsIgnoringCase("not found");
    }

    // ---------------------------------------------------------------
    // 3. Null vehicleId → VehicleValidated (skips validation)
    // ---------------------------------------------------------------
    @Test
    void nullVehicleId_ShouldSaveOutboxEventForVehicleValidatedWithNull() throws Exception {
        EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder()
                .vehicleId(null)
                .build();
        EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
        String message = MAPPER.writeValueAsString(envelope);

        kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));

        OutboxEvent saved = capturedOutboxEvents.get(0);
        EventEnvelope published = MAPPER.readValue(saved.getPayload(), EventEnvelope.class);
        assertThat(published.getEventType()).isEqualTo(EventConstants.VEHICLE_VALIDATED);

        VehicleValidatedEvent payload = MAPPER.convertValue(published.getPayload(), VehicleValidatedEvent.class);
        assertThat(payload.getVehicleId()).isNull();
    }

    // ---------------------------------------------------------------
    // 4. Duplicate event → idempotent (only one save)
    // ---------------------------------------------------------------
    @Test
    void duplicateEvent_ShouldBeIdempotent() throws Exception {
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                .plate("34 XYZ 5678")
                .chassisNumber("1HGCM82633A004353")
                .carBrandId(1)
                .carModelId(1)
                .carEngineId(1)
                .carFuelTypeId(1)
                .carTypeId(1)
                .carPackageId(1)
                .customerId(UUID.randomUUID())
                .build());

        EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder()
                .vehicleId(vehicle.getId())
                .build();
        EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
        String message = MAPPER.writeValueAsString(envelope);

        // Send first event and verify it was processed
        kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);
        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));
        assertThat(capturedOutboxEvents).hasSize(1);

        // Send duplicate event with the same sagaId
        kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

        // Wait for consumer to potentially process the duplicate
        Thread.sleep(5000);

        // Verify the total number of saves is still 1 (idempotent)
        verify(outboxEventRepository, times(1))
                .save(any(OutboxEvent.class));
    }

    // ---------------------------------------------------------------
    // 5. EstimationFailed → no outbox save (log only)
    // ---------------------------------------------------------------
    @Test
    void estimationFailed_ShouldNotSaveOutboxEvent() throws Exception {
        EstimationFailedEvent failEvent = EstimationFailedEvent.builder()
                .originalSagaId(sagaId)
                .reason("Calculation timeout")
                .failedStep("InsuranceService")
                .build();
        EventEnvelope envelope = failEvent.toEnvelope(sagaId, traceId);
        String message = MAPPER.writeValueAsString(envelope);

        kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

        // Wait for consumer to process the message
        Thread.sleep(5000);

        // Verify that outboxEventRepository.save was never called
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}
