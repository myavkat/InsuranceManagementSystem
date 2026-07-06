package com.insurancemanagementsystem.estimation.e2e;

import com.insurancemanagementsystem.common.config.OutboxProcessor;
import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.*;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.repository.SagaEventRepository;
import com.insurancemanagementsystem.estimation.dto.EstimationRequest;
import com.insurancemanagementsystem.estimation.entity.Estimation;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import com.insurancemanagementsystem.estimation.service.SagaTimeoutService;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
@Testcontainers
class SagaE2ETest {

    private static final Logger log = LoggerFactory.getLogger(SagaE2ETest.class);

    // ---------------------------------------------------------------
    // Testcontainers — shared across all tests in this class
    // ---------------------------------------------------------------

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_estimation_e2e_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.binder.brokers", kafka::getBootstrapServers);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "*");
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
    private StreamBridge streamBridge;

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

    // ---------------------------------------------------------------
    // Cleanup before each test
    // ---------------------------------------------------------------

    @BeforeEach
    void cleanUp() {
        estimationRepository.deleteAll();
        outboxEventRepository.deleteAll();
        sagaEventRepository.deleteAll();
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
        request.setInsuranceTypeId(1);
        request.setCompanyId(UUID.randomUUID());
        return request;
    }

    /**
     * Publishes a SAGA event to the estimation.saga topic via StreamBridge.
     * <p>
     * Uses StreamBridge instead of KafkaTemplate to ensure the message is
     * serialized with the same content-type handling (JSON string wrapping)
     * that the Spring Cloud Stream binder expects. Publishing via
     * KafkaTemplate with StringSerializer would produce raw JSON bytes that
     * the binder's deserializer might misinterpret.
     */
    private void publishSagaEvent(UUID sagaId, UUID traceId, BaseEvent event) throws Exception {
        EventEnvelope envelope = event.toEnvelope(sagaId, traceId);
        String json = jsonMapper.writeValueAsString(envelope);
        boolean sent = streamBridge.send(EventConstants.ESTIMATION_SAGA, json);
        if (!sent) {
            throw new RuntimeException("Failed to send event via StreamBridge for sagaId=" + sagaId);
        }
    }

    /**
     * Creates a Kafka consumer for reading events from a topic.
     */
    private KafkaConsumer<String, String> createTestConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-e2e-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new KafkaConsumer<>(props);
    }

    /**
     * Polls records from a Kafka consumer until the timeout expires.
     */
    private static List<ConsumerRecord<String, String>> pollForRecords(
            KafkaConsumer<String, String> consumer, Duration timeout) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline) && records.isEmpty()) {
            consumer.poll(Duration.ofMillis(500)).forEach(records::add);
        }
        return records;
    }

    /**
     * Decodes a Kafka message through the JSON-string-wrapping + Base64
     * encoding applied by Spring Cloud Stream's binder serialization.
     */
    private String decodeKafkaMessage(String jsonSerializedValue) {
        try {
            String base64Value = jsonMapper.readValue(jsonSerializedValue, String.class);
            return new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode Kafka message: " + jsonSerializedValue, e);
        }
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
        boolean sent = streamBridge.send(EventConstants.ESTIMATION_SAGA, "not valid json at all {{{");
        assertThat(sent).isTrue();

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
