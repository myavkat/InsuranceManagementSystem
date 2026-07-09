package com.insurancemanagementsystem.insurance.saga;

import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.*;
import com.insurancemanagementsystem.insurance.entity.Insurance;
import com.insurancemanagementsystem.insurance.entity.InsuranceType;
import com.insurancemanagementsystem.insurance.entity.RiskFactor;
import com.insurancemanagementsystem.insurance.repository.InsuranceRepository;
import com.insurancemanagementsystem.insurance.repository.InsuranceTypeRepository;
import com.insurancemanagementsystem.insurance.repository.RiskFactorRepository;
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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
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
class InsuranceSagaConsumerTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_insurance_db")
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
    private InsuranceRepository insuranceRepository;

    @Autowired
    private InsuranceTypeRepository insuranceTypeRepository;

    @Autowired
    private RiskFactorRepository riskFactorRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private OutboxEventRepository outboxEventRepository;

    private final List<OutboxEvent> capturedOutboxEvents = new ArrayList<>();

    private static final JsonMapper MAPPER = new JsonMapper();

    private UUID sagaId;
    private UUID traceId;
    private UUID insuranceId;

    @BeforeEach
    void setUp() {
        insuranceRepository.deleteAll();
        insuranceTypeRepository.deleteAll();
        capturedOutboxEvents.clear();
        sagaId = UUID.randomUUID();
        traceId = UUID.randomUUID();

        // Seed test data
        insuranceTypeRepository.save(new InsuranceType(1, "Vehicle"));

        Insurance savedInsurance = insuranceRepository.save(Insurance.builder()
                .name("Traffic Insurance")
                .code("TRAFFIC_INSURANCE")
                .typeId(1)
                .basePremium(BigDecimal.valueOf(1000))
                .build());
        insuranceId = savedInsurance.getId();

        // Seed risk factors so premium calculation uses known composite risk
        riskFactorRepository.save(RiskFactor.builder()
                .insuranceId(savedInsurance.getId())
                .factorName("vehicleAge")
                .factorValue(new BigDecimal("1.00"))
                .build());
        riskFactorRepository.save(RiskFactor.builder()
                .insuranceId(savedInsurance.getId())
                .factorName("driverAge")
                .factorValue(new BigDecimal("1.00"))
                .build());

        // Capture saved outbox events
        doAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            if (event.getId() == null) {
                event.setId(UUID.randomUUID());
            }
            capturedOutboxEvents.add(event);
            return event;
        }).when(outboxEventRepository).save(any(OutboxEvent.class));

        // Ensure relay queries are no-ops
        when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(outboxEventRepository.findByStatusAndCreatedAtBefore(any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void allThreeEvents_valid_shouldSaveOutboxPremiumCalculated() throws Exception {
        // Send events out of order: CustomerValidated first, then VehicleValidated, then EstimationRequested
        CustomerValidatedEvent customerEvent = CustomerValidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(customerEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        VehicleValidatedEvent vehicleEvent = VehicleValidatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .plate("34ABC123")
                .brand("Toyota")
                .model("Corolla")
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(vehicleEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        EstimationRequestedEvent estimationEvent = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .insuranceId(insuranceId)
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(estimationEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));

        OutboxEvent saved = capturedOutboxEvents.get(0);
        EventEnvelope published = MAPPER.readValue(saved.getPayload(), EventEnvelope.class);
        assertThat(published.getEventType()).isEqualTo(EventConstants.PREMIUM_CALCULATED);
        assertThat(published.getSagaId()).isEqualTo(sagaId);
        assertThat(published.getTraceId()).isEqualTo(traceId);

        PremiumCalculatedEvent payload = MAPPER.convertValue(published.getPayload(), PremiumCalculatedEvent.class);
        assertThat(payload.getPremium()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(payload.getInsuranceId()).isEqualTo(insuranceId);
        assertThat(payload.getCustomerId()).isEqualTo(estimationEvent.getCustomerId());
        assertThat(payload.getVehicleId()).isEqualTo(estimationEvent.getVehicleId());
    }

    @Test
    void customerInvalidated_shouldSaveOutboxCalculationFailed() throws Exception {
        EstimationRequestedEvent estimationEvent = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .insuranceId(insuranceId)
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(estimationEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        CustomerInvalidatedEvent invalidatedEvent = CustomerInvalidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .reason("Customer not found")
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(invalidatedEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));

        OutboxEvent saved = capturedOutboxEvents.get(0);
        EventEnvelope published = MAPPER.readValue(saved.getPayload(), EventEnvelope.class);
        assertThat(published.getEventType()).isEqualTo(EventConstants.CALCULATION_FAILED);
        assertThat(published.getSagaId()).isEqualTo(sagaId);
        assertThat(published.getTraceId()).isEqualTo(traceId);

        CalculationFailedEvent payload = MAPPER.convertValue(published.getPayload(), CalculationFailedEvent.class);
        assertThat(payload.getReason()).containsIgnoringCase("Customer validation failed");
    }

    @Test
    void vehicleInvalidated_shouldSaveOutboxCalculationFailed() throws Exception {
        EstimationRequestedEvent estimationEvent = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .insuranceId(insuranceId)
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(estimationEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        VehicleInvalidatedEvent invalidatedEvent = VehicleInvalidatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .reason("Vehicle not found")
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(invalidatedEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));

        OutboxEvent saved = capturedOutboxEvents.get(0);
        EventEnvelope published = MAPPER.readValue(saved.getPayload(), EventEnvelope.class);
        assertThat(published.getEventType()).isEqualTo(EventConstants.CALCULATION_FAILED);
        assertThat(published.getSagaId()).isEqualTo(sagaId);
        assertThat(published.getTraceId()).isEqualTo(traceId);

        CalculationFailedEvent payload = MAPPER.convertValue(published.getPayload(), CalculationFailedEvent.class);
        assertThat(payload.getReason()).containsIgnoringCase("Vehicle validation failed");
    }

    @Test
    void duplicateInvalidatedEvent_isSkipped() throws Exception {
        // First send estimation request to establish context
        EstimationRequestedEvent estimationEvent = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .insuranceId(insuranceId)
                .build();
        kafkaTemplate.send("estimation.saga",
                        MAPPER.writeValueAsString(estimationEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        // Send CUSTOMER_INVALIDATED (should produce 1 outbox save)
        CustomerInvalidatedEvent invalidatedEvent = CustomerInvalidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .reason("Customer not found")
                .build();
        String invalidatedMessage = MAPPER.writeValueAsString(invalidatedEvent.toEnvelope(sagaId, traceId));
        kafkaTemplate.send("estimation.saga", invalidatedMessage).get(10, TimeUnit.SECONDS);

        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));
        assertThat(capturedOutboxEvents).hasSize(1);

        // Send duplicate CUSTOMER_INVALIDATED with the same sagaId
        kafkaTemplate.send("estimation.saga", invalidatedMessage).get(10, TimeUnit.SECONDS);

        // Wait for consumer to potentially process the duplicate
        Thread.sleep(5000);

        // Verify total outbox saves is still 1 (idempotent)
        verify(outboxEventRepository, times(1))
                .save(any(OutboxEvent.class));
        assertThat(capturedOutboxEvents).hasSize(1);
    }

    @Test
    void noMatchingInsurance_shouldSaveOutboxCalculationFailed() throws Exception {
        // Use a random UUID that has no corresponding Insurance in the DB
        EstimationRequestedEvent estimationEvent = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .insuranceId(UUID.randomUUID())
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(estimationEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        CustomerValidatedEvent customerEvent = CustomerValidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(customerEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        VehicleValidatedEvent vehicleEvent = VehicleValidatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .plate("34ABC123")
                .brand("Toyota")
                .model("Corolla")
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(vehicleEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));

        OutboxEvent saved = capturedOutboxEvents.get(0);
        EventEnvelope published = MAPPER.readValue(saved.getPayload(), EventEnvelope.class);
        assertThat(published.getEventType()).isEqualTo(EventConstants.CALCULATION_FAILED);
        assertThat(published.getSagaId()).isEqualTo(sagaId);
        assertThat(published.getTraceId()).isEqualTo(traceId);

        CalculationFailedEvent payload = MAPPER.convertValue(published.getPayload(), CalculationFailedEvent.class);
        assertThat(payload.getReason()).containsIgnoringCase("No active insurance");
    }

    @Test
    void duplicateEvents_shouldBeIdempotent() throws Exception {
        // First send all 3 events to trigger premium calculation
        CustomerValidatedEvent customerEvent = CustomerValidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .build();
        String customerMessage = MAPPER.writeValueAsString(customerEvent.toEnvelope(sagaId, traceId));

        VehicleValidatedEvent vehicleEvent = VehicleValidatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .plate("34ABC123")
                .brand("Toyota")
                .model("Corolla")
                .build();
        String vehicleMessage = MAPPER.writeValueAsString(vehicleEvent.toEnvelope(sagaId, traceId));

        EstimationRequestedEvent estimationEvent = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .insuranceId(insuranceId)
                .build();
        String estimationMessage = MAPPER.writeValueAsString(estimationEvent.toEnvelope(sagaId, traceId));

        kafkaTemplate.send("estimation.saga", customerMessage).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send("estimation.saga", vehicleMessage).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send("estimation.saga", estimationMessage).get(10, TimeUnit.SECONDS);

        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));
        assertThat(capturedOutboxEvents).hasSize(1);

        // Send duplicate CustomerValidated event with the same sagaId
        kafkaTemplate.send("estimation.saga", customerMessage).get(10, TimeUnit.SECONDS);

        // Wait for consumer to potentially process the duplicate
        Thread.sleep(5000);

        // Verify total outbox saves is still 1 (idempotent)
        verify(outboxEventRepository, times(1))
                .save(any(OutboxEvent.class));
        assertThat(capturedOutboxEvents).hasSize(1);
    }

    @Test
    void eventsOutOfOrder_shouldStillCalculate() throws Exception {
        // Send events out of order: VehicleValidated first, then CustomerValidated, then EstimationRequested
        VehicleValidatedEvent vehicleEvent = VehicleValidatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .plate("34ABC123")
                .brand("Toyota")
                .model("Corolla")
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(vehicleEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        CustomerValidatedEvent customerEvent = CustomerValidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(customerEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        EstimationRequestedEvent estimationEvent = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .insuranceId(insuranceId)
                .build();
        kafkaTemplate.send("estimation.saga",
                MAPPER.writeValueAsString(estimationEvent.toEnvelope(sagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));

        OutboxEvent saved = capturedOutboxEvents.get(0);
        EventEnvelope published = MAPPER.readValue(saved.getPayload(), EventEnvelope.class);
        assertThat(published.getEventType()).isEqualTo(EventConstants.PREMIUM_CALCULATED);
        assertThat(published.getSagaId()).isEqualTo(sagaId);
        assertThat(published.getTraceId()).isEqualTo(traceId);

        PremiumCalculatedEvent payload = MAPPER.convertValue(published.getPayload(), PremiumCalculatedEvent.class);
        assertThat(payload.getPremium()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(payload.getInsuranceId()).isEqualTo(insuranceId);
        assertThat(payload.getCustomerId()).isEqualTo(estimationEvent.getCustomerId());
        assertThat(payload.getVehicleId()).isEqualTo(estimationEvent.getVehicleId());
    }

    @Test
    void estimationFailedEvent_handlesDuplicates() throws Exception {
        // Use a fresh sagaId for this test
        UUID failSagaId = UUID.randomUUID();

        EstimationFailedEvent failEvent = EstimationFailedEvent.builder()
                .originalSagaId(failSagaId)
                .reason("Calculation timeout")
                .failedStep("PremiumCalculation")
                .build();
        EventEnvelope failEnvelope = failEvent.toEnvelope(failSagaId, traceId);
        String failMessage = MAPPER.writeValueAsString(failEnvelope);

        // Send first ESTIMATION_FAILED
        kafkaTemplate.send("estimation.saga", failMessage).get(10, TimeUnit.SECONDS);

        // Send duplicate ESTIMATION_FAILED with same sagaId
        kafkaTemplate.send("estimation.saga", failMessage).get(10, TimeUnit.SECONDS);

        // Wait for both messages to be consumed
        Thread.sleep(5000);

        // Now send a valid ESTIMATION_REQUESTED with the SAME sagaId to verify
        // the dedup doesn't block other event types for the same saga
        EstimationRequestedEvent estimationEvent = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .insuranceId(insuranceId)
                .build();
        kafkaTemplate.send("estimation.saga",
                        MAPPER.writeValueAsString(estimationEvent.toEnvelope(failSagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        CustomerValidatedEvent customerEvent = CustomerValidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .build();
        kafkaTemplate.send("estimation.saga",
                        MAPPER.writeValueAsString(customerEvent.toEnvelope(failSagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        VehicleValidatedEvent vehicleEvent = VehicleValidatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .plate("34ABC123")
                .brand("Toyota")
                .model("Corolla")
                .build();
        kafkaTemplate.send("estimation.saga",
                        MAPPER.writeValueAsString(vehicleEvent.toEnvelope(failSagaId, traceId)))
                .get(10, TimeUnit.SECONDS);

        // The 3 events should trigger premium calculation (valid saga, no duplicate)
        verify(outboxEventRepository, timeout(15000).times(1))
                .save(any(OutboxEvent.class));
    }
}
