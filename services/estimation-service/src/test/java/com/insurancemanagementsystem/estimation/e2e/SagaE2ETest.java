package com.insurancemanagementsystem.estimation.e2e;

import com.insurancemanagementsystem.common.config.OutboxProcessor;
import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.*;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.repository.SagaEventRepository;
import com.insurancemanagementsystem.common.test.AbstractKafkaIntegrationTest;
import com.insurancemanagementsystem.estimation.client.InsuranceServiceClient;
import com.insurancemanagementsystem.estimation.dto.EstimationRequest;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import com.insurancemanagementsystem.estimation.service.SagaTimeoutService;
import jakarta.persistence.EntityManager;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * End-to-end integration tests for the SAGA event-driven workflow.
 * <p>
 * These tests spin up the estimation-service with real PostgreSQL and Kafka
 * (via Testcontainers) and simulate the full SAGA lifecycle by publishing
 * events that other services (customer, vehicle, insurance) would normally
 * produce. This is the <strong>Estimation-centric integration</strong>
 * approach (Approach B from the plan) — it validates the estimation service's
 * complete SAGA coordination logic without needing all microservices running.
 * <p>
 * Tests cover:
 * <ul>
 *   <li>Happy path: estimation → COMPLETED with premium</li>
 *   <li>Idempotency: duplicate events produce no side effects</li>
 *   <li>Timeout: stale estimations → REJECTED</li>
 *   <li>Failure paths: *Invalidated / CalculationFailed → REJECTED</li>
 *   <li>Poison message: malformed JSON doesn't block the consumer</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class SagaE2ETest extends AbstractKafkaIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SagaE2ETest.class);

    @DynamicPropertySource
    static void configureAdditionalProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Enable auto topic creation for tests (avoids dependency on kafka-init)
        registry.add("spring.cloud.stream.kafka.binder.configuration.auto.create.topics.enable", () -> true);
        registry.add("spring.kafka.producer.properties.auto.create.topics.enable", () -> true);
        // Disable background scheduled tasks to avoid interference during tests
        registry.add("estimation.outbox.poll-interval-ms", () -> "600000");
        registry.add("estimation.saga.poll-interval-ms", () -> "600000");
    }

    // ---------------------------------------------------------------
    // Injected beans
    // ---------------------------------------------------------------

    @Autowired
    private RestTestClient client;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EstimationRepository estimationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private SagaEventRepository sagaEventRepository;

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private SagaTimeoutService sagaTimeoutService;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private InsuranceServiceClient insuranceServiceClient;

    // ---------------------------------------------------------------
    // Cleanup before each test
    // ---------------------------------------------------------------

    @BeforeEach
    void cleanUp() {
        estimationRepository.deleteAll();
        outboxEventRepository.deleteAll();
        sagaEventRepository.deleteAll();

        // Default mock: return TRAFFIC insurance (typeId=1 → Vehicle)
        when(insuranceServiceClient.getInsurance(any(UUID.class)))
                .thenReturn(new InsuranceServiceClient.InsuranceInfo(UUID.randomUUID(), "Trafik Sigortası", 1, "Vehicle"));
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    /**
     * Creates an estimation via the REST API and returns the sagaId.
     */
    private UUID createEstimation(EstimationRequest request) throws Exception {
        byte[] responseBody = client.post().uri("/api/estimations")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();

        if (responseBody == null || responseBody.length == 0) {
            throw new RuntimeException("Empty response body from estimation creation");
        }
        JsonNode root = jsonMapper.readTree(responseBody);
        return UUID.fromString(root.get("data").get("sagaId").asText());
    }

    /**
     * Creates a valid estimation request with a vehicleId.
     */
    private EstimationRequest createValidVehicleRequest() {
        EstimationRequest request = new EstimationRequest();
        request.setCustomerId(UUID.randomUUID());
        request.setVehicleId(UUID.randomUUID());
        request.setInsuranceId(UUID.randomUUID());
        return request;
    }

    /**
     * Publishes a SAGA event to the estimation.saga topic via KafkaTemplate.
     * <p>
     * Uses KafkaTemplate<String, String> to send pre-serialized JSON events,
     * matching how other saga consumer tests publish. The StringSerializer
     * writes the JSON as UTF-8 bytes; the consumer's StringDeserializer reads
     * them back as a String for jsonMapper deserialization.
     */
    private void publishSagaEvent(UUID sagaId, UUID traceId, BaseEvent event) throws Exception {
        EventEnvelope envelope = event.toEnvelope(sagaId, traceId);
        String json = jsonMapper.writeValueAsString(envelope);
        kafkaTemplate.send(EventConstants.ESTIMATION_SAGA, json).get(10, TimeUnit.SECONDS);
    }

    // ===============================================================
    //  TEST 1: Happy Path — Full SAGA from STARTED to COMPLETED
    // ===============================================================

    @Test
    void happyPath_fullSagaFromStartToCompleted() throws Exception {
        // 1. Create estimation via REST API
        EstimationRequest request = createValidVehicleRequest();
        UUID sagaId = createEstimation(request);
        log.info("Created estimation with sagaId={}", sagaId);

        // 2. Process outbox to publish EstimationRequested to Kafka
        outboxProcessor.processOutbox();

        // 3. Simulate Customer Service: publish CustomerValidated
        CustomerValidatedEvent customerEvent = CustomerValidatedEvent.builder()
                .customerId(request.getCustomerId())
                .firstName("John")
                .lastName("Doe")
                .build();
        publishSagaEvent(sagaId, UUID.randomUUID(), customerEvent);

        // 4. Simulate Vehicle Service: publish VehicleValidated
        VehicleValidatedEvent vehicleEvent = VehicleValidatedEvent.builder()
                .vehicleId(request.getVehicleId())
                .plate("34ABC123")
                .build();
        publishSagaEvent(sagaId, UUID.randomUUID(), vehicleEvent);

        // 5. Simulate Insurance Service: publish PremiumCalculated
        BigDecimal premium = new BigDecimal("1500.00");
        PremiumCalculatedEvent premiumEvent = PremiumCalculatedEvent.builder()
                .premium(premium)
                .breakdown(Map.of("base", new BigDecimal("1500.00")))
                .build();
        publishSagaEvent(sagaId, UUID.randomUUID(), premiumEvent);

        // 6. Wait for consumer to process — verify COMPLETED
        await().atMost(30, SECONDS).untilAsserted(() -> {
            Estimation estimation = estimationRepository.findBySagaId(sagaId)
                    .orElseThrow(() -> new AssertionError("Estimation not found for sagaId=" + sagaId));
            assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.COMPLETED);
            assertThat(estimation.getPremium()).isEqualByComparingTo(premium);
        });

        // 7. Verify premium details were saved
        Estimation estimation = estimationRepository.findBySagaId(sagaId).orElseThrow();
        assertThat(estimation.getDetails()).isNotNull();
        assertThat(estimation.getDetails()).startsWith("{").endsWith("}");
        assertThat(estimation.getDetails()).contains("\"base\"");

        // 8. Verify saga_events table has dedup markers for all processed events
        assertThat(sagaEventRepository.findBySagaIdAndEventType(sagaId, EventConstants.CUSTOMER_VALIDATED))
                .as("CustomerValidated dedup marker")
                .isPresent();
        assertThat(sagaEventRepository.findBySagaIdAndEventType(sagaId, EventConstants.VEHICLE_VALIDATED))
                .as("VehicleValidated dedup marker")
                .isPresent();
        assertThat(sagaEventRepository.findBySagaIdAndEventType(sagaId, EventConstants.PREMIUM_CALCULATED))
                .as("PremiumCalculated dedup marker")
                .isPresent();
    }

    // ===============================================================
    //  TEST 2: Idempotency — Duplicate Events Have No Side Effects
    // ===============================================================

    @Test
    void duplicateEvents_areSafelyIgnored() throws Exception {
        // 1. Create estimation
        EstimationRequest request = createValidVehicleRequest();
        UUID sagaId = createEstimation(request);
        outboxProcessor.processOutbox();

        UUID traceId = UUID.randomUUID();

        // 2. Publish CustomerValidated (first time — should be processed)
        CustomerValidatedEvent customerEvent = CustomerValidatedEvent.builder()
                .customerId(request.getCustomerId())
                .firstName("John")
                .lastName("Doe")
                .build();
        publishSagaEvent(sagaId, traceId, customerEvent);

        // 3. Publish CustomerValidated AGAIN (duplicate — should be skipped)
        publishSagaEvent(sagaId, traceId, customerEvent);

        // 4. Publish the remaining events
        VehicleValidatedEvent vehicleEvent = VehicleValidatedEvent.builder()
                .vehicleId(request.getVehicleId())
                .plate("34ABC123")
                .build();
        publishSagaEvent(sagaId, traceId, vehicleEvent);

        PremiumCalculatedEvent premiumEvent = PremiumCalculatedEvent.builder()
                .premium(new BigDecimal("1500.00"))
                .build();
        publishSagaEvent(sagaId, traceId, premiumEvent);

        // 5. Wait for COMPLETED
        await().atMost(30, SECONDS).untilAsserted(() -> {
            Estimation estimation = estimationRepository.findBySagaId(sagaId)
                    .orElseThrow(() -> new AssertionError("Estimation not found"));
            assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.COMPLETED);
            assertThat(estimation.getPremium()).isEqualByComparingTo(new BigDecimal("1500.00"));
        });

        // 6. Verify saga_events table has ONLY ONE row per event type
        List<EventCount> counts = List.of(
                new EventCount(EventConstants.CUSTOMER_VALIDATED, 1),
                new EventCount(EventConstants.VEHICLE_VALIDATED, 1),
                new EventCount(EventConstants.PREMIUM_CALCULATED, 1)
        );
        for (EventCount ec : counts) {
            long actualCount = sagaEventRepository.findBySagaIdAndEventType(sagaId, ec.eventType)
                    .map(ignored -> 1L)
                    .orElse(0L);
            assertThat(actualCount)
                    .as("Duplicate check for %s", ec.eventType)
                    .isEqualTo(ec.expectedCount);
        }
    }

    @Test
    void duplicatePremiumCalculated_afterCompleted_isIgnored() throws Exception {
        // 1. Complete the happy path
        EstimationRequest request = createValidVehicleRequest();
        UUID sagaId = createEstimation(request);
        outboxProcessor.processOutbox();

        UUID traceId = UUID.randomUUID();
        CustomerValidatedEvent customerEvent = CustomerValidatedEvent.builder()
                .customerId(request.getCustomerId())
                .firstName("John")
                .lastName("Doe")
                .build();
        publishSagaEvent(sagaId, traceId, customerEvent);

        VehicleValidatedEvent vehicleEvent = VehicleValidatedEvent.builder()
                .vehicleId(request.getVehicleId())
                .plate("34ABC123")
                .build();
        publishSagaEvent(sagaId, traceId, vehicleEvent);

        PremiumCalculatedEvent firstPremium = PremiumCalculatedEvent.builder()
                .premium(new BigDecimal("1500.00"))
                .breakdown(Map.of("base", new BigDecimal("1500.00")))
                .build();
        publishSagaEvent(sagaId, traceId, firstPremium);

        // Wait for COMPLETED
        await().atMost(30, SECONDS).untilAsserted(() -> {
            Estimation estimation = estimationRepository.findBySagaId(sagaId)
                    .orElseThrow(() -> new AssertionError("Estimation not found"));
            assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.COMPLETED);
        });

        // 2. Publish another PremiumCalculated with DIFFERENT premium
        PremiumCalculatedEvent secondPremium = PremiumCalculatedEvent.builder()
                .premium(new BigDecimal("9999.99"))
                .breakdown(Map.of("base", new BigDecimal("9999.99")))
                .build();
        publishSagaEvent(sagaId, UUID.randomUUID(), secondPremium);

        // 3. Wait briefly to allow processing, then verify original values preserved
        Thread.sleep(2000);
        Estimation estimation = estimationRepository.findBySagaId(sagaId).orElseThrow();
        assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.COMPLETED);
        assertThat(estimation.getPremium()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    // ===============================================================
    //  TEST 3: Timeout — Stale Estimation REJECTED
    // ===============================================================

    @Test
    void timeout_estimationRejected() throws Exception {
        // 1. Create estimation (do NOT publish any response events)
        EstimationRequest request = createValidVehicleRequest();
        UUID sagaId = createEstimation(request);
        outboxProcessor.processOutbox();

        // 2. Manually set created_at in the past so the default 5-minute timeout
        //    naturally catches it. The entity field has updatable=false so we
        //    use a direct SQL update (wrapped in a TransactionTemplate to satisfy
        //    EntityManager's transaction requirement) to bypass JPA-level restrictions.
        transactionTemplate.executeWithoutResult(status -> {
            int updated = entityManager.createNativeQuery(
                    "UPDATE estimations SET created_at = CURRENT_TIMESTAMP - INTERVAL '10 minutes' WHERE saga_id = ?1")
                    .setParameter(1, sagaId)
                    .executeUpdate();
            assertThat(updated).as("Expected 1 row updated").isEqualTo(1);
        });

        // 3. Clear persistence context so the next load picks up the updated row
        entityManager.clear();

        // 4. Manually trigger timeout check (uses default 5-minute timeout)
        sagaTimeoutService.checkForTimedOutSagas();

        // 5. Verify estimation is REJECTED
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Estimation estimation = estimationRepository.findBySagaId(sagaId)
                    .orElseThrow(() -> new AssertionError("Estimation not found"));
            assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        });

        // 6. Verify EstimationFailed outbox event was published
        // (Look for a PENDING event for this saga — the original EstimationRequested
        //  was already PUBLISHED by the outbox processor)
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).isNotEmpty();
        OutboxEvent failedEvent = outboxEvents.stream()
                .filter(e -> e.getSagaId().equals(sagaId) && e.getStatus() == OutboxEvent.Status.PENDING)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No PENDING outbox event for sagaId=" + sagaId));
        assertThat(failedEvent.getPayload()).contains("EstimationFailed");
    }

    // ===============================================================
    //  TEST 4: Failure Paths → REJECTED
    // ===============================================================

    @Test
    void customerInvalidated_estimationRejected() throws Exception {
        EstimationRequest request = createValidVehicleRequest();
        UUID sagaId = createEstimation(request);
        outboxProcessor.processOutbox();

        // Publish CustomerInvalidated
        CustomerInvalidatedEvent event = CustomerInvalidatedEvent.builder()
                .customerId(request.getCustomerId())
                .reason("Customer not found")
                .build();
        publishSagaEvent(sagaId, UUID.randomUUID(), event);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            Estimation estimation = estimationRepository.findBySagaId(sagaId)
                    .orElseThrow(() -> new AssertionError("Estimation not found"));
            assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        });

        // Verify EstimationFailed outbox event
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).anyMatch(e ->
                e.getSagaId().equals(sagaId) && e.getPayload().contains("EstimationFailed"));
    }

    @Test
    void vehicleInvalidated_estimationRejected() throws Exception {
        EstimationRequest request = createValidVehicleRequest();
        UUID sagaId = createEstimation(request);
        outboxProcessor.processOutbox();

        // Publish VehicleInvalidated
        VehicleInvalidatedEvent event = VehicleInvalidatedEvent.builder()
                .vehicleId(request.getVehicleId())
                .reason("Vehicle not found")
                .build();
        publishSagaEvent(sagaId, UUID.randomUUID(), event);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            Estimation estimation = estimationRepository.findBySagaId(sagaId)
                    .orElseThrow(() -> new AssertionError("Estimation not found"));
            assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        });

        // Verify EstimationFailed outbox event
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).anyMatch(e ->
                e.getSagaId().equals(sagaId) && e.getPayload().contains("EstimationFailed"));
    }

    @Test
    void calculationFailed_estimationRejected() throws Exception {
        EstimationRequest request = createValidVehicleRequest();
        UUID sagaId = createEstimation(request);
        outboxProcessor.processOutbox();

        // Publish CalculationFailed
        CalculationFailedEvent event = CalculationFailedEvent.builder()
                .reason("Division by zero")
                .build();
        publishSagaEvent(sagaId, UUID.randomUUID(), event);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            Estimation estimation = estimationRepository.findBySagaId(sagaId)
                    .orElseThrow(() -> new AssertionError("Estimation not found"));
            assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.REJECTED);
        });

        // Verify EstimationFailed outbox event
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).anyMatch(e ->
                e.getSagaId().equals(sagaId) && e.getPayload().contains("EstimationFailed"));
    }

    // ===============================================================
    //  TEST 5: Poison Message — Malformed JSON
    // ===============================================================

    @Test
    void malformedJson_doesNotBlockConsumer() throws Exception {
        // 1. Publish a malformed JSON string to estimation.saga
        kafkaTemplate.send(EventConstants.ESTIMATION_SAGA, "not valid json at all {{{").get(10, TimeUnit.SECONDS);

        // 2. Give the consumer time to process (and fail to deserialize)
        Thread.sleep(2000);

        // 3. Verify the consumer is still functional by publishing a valid event
        EstimationRequest request = createValidVehicleRequest();
        UUID sagaId = createEstimation(request);
        outboxProcessor.processOutbox();

        // Publish events to complete the saga
        UUID traceId = UUID.randomUUID();
        publishSagaEvent(sagaId, traceId, CustomerValidatedEvent.builder()
                .customerId(request.getCustomerId())
                .firstName("John")
                .lastName("Doe")
                .build());
        publishSagaEvent(sagaId, traceId, VehicleValidatedEvent.builder()
                .vehicleId(request.getVehicleId())
                .plate("34ABC123")
                .build());
        publishSagaEvent(sagaId, traceId, PremiumCalculatedEvent.builder()
                .premium(new BigDecimal("1500.00"))
                .build());

        // 4. Verify the healthy path still works
        await().atMost(30, SECONDS).untilAsserted(() -> {
            Estimation estimation = estimationRepository.findBySagaId(sagaId)
                    .orElseThrow(() -> new AssertionError("Estimation not found"));
            assertThat(estimation.getStatus()).isEqualTo(Estimation.Status.COMPLETED);
        });
    }

    // ===============================================================
    //  Inner types
    // ===============================================================

    /**
     * Simple pair for tracking expected event-type counts in idempotency tests.
     */
    private record EventCount(String eventType, long expectedCount) {}
}
