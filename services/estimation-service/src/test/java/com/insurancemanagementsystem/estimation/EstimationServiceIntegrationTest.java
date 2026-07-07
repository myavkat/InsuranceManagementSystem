package com.insurancemanagementsystem.estimation;

import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.config.OutboxProcessor;
import com.insurancemanagementsystem.common.config.OutboxRelay;
import com.insurancemanagementsystem.estimation.dto.EstimationRequest;
import com.insurancemanagementsystem.estimation.dto.EstimationResponse;
import com.insurancemanagementsystem.estimation.repository.EstimationRepository;
import com.insurancemanagementsystem.estimation.service.EstimationService;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration tests for the outbox-based deferred publish path.
 * <p>
 * These tests exercise the real transaction lifecycle using Testcontainers
 * for PostgreSQL and Kafka, verifying that:
 * <ul>
 *   <li>{@code EstimationService.create()} persists an {@link OutboxEvent}
 *       within the same transaction</li>
 *   <li>The {@link OutboxRelay} publishes outbox events to Kafka</li>
 *   <li>The REST API end-to-end flow produces events in Kafka</li>
 * </ul>
 * <p>
 * <strong>Rollback scenario (manual):</strong> To verify that a transaction
 * rollback prevents outbox event publication, wrap
 * {@code estimationService.create()} in a new {@code @Transactional} method
 * that throws a {@code RuntimeException} after the outbox event is saved,
 * then assert that no events reach Kafka and no outbox rows remain.
 * This is documented as a manual test scenario because {@code create()}
 * itself does not trigger a rollback on success.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class EstimationServiceIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(EstimationServiceIntegrationTest.class);

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_estimation_integration_db")
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
        // Long poll intervals prevent background tasks from interfering during tests
        registry.add("estimation.outbox.poll-interval-ms", () -> "600000");
        registry.add("estimation.saga.poll-interval-ms", () -> "600000");
    }

    @Autowired
    private EstimationService estimationService;

    @Autowired
    private EstimationRepository estimationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private RestTestClient restTestClient;

    private final ObjectMapper objectMapper = new JsonMapper();

    @BeforeEach
    void cleanUp() {
        estimationRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    // ---------------------------------------------------------------
    // Test 1: create() persists an OutboxEvent within the transaction
    // ---------------------------------------------------------------
    @Test
    void createEstimation_persistsOutboxEvent() {
        EstimationRequest request = createValidRequest();

        EstimationResponse response = estimationService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("STARTED");

        // Verify the outbox event was persisted in the database
        // within the same @Transactional boundary
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);

        OutboxEvent event = outboxEvents.getFirst();
        assertThat(event.getSagaId()).isEqualTo(response.getSagaId());
        assertThat(event.getTopic()).isEqualTo("estimation.saga");
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        // Payload should contain the serialized EventEnvelope with EstimationRequested
        assertThat(event.getPayload())
                .contains("EstimationRequested")
                .contains(response.getSagaId().toString());
    }

    // ---------------------------------------------------------------
    // Test 2: create() → OutboxRelay → event published to Kafka
    // ---------------------------------------------------------------
    @Test
    void createEstimation_outboxRelayPublishesToKafka() {
        KafkaConsumer<String, String> consumer = createTestConsumer();
        consumer.subscribe(List.of("estimation.saga"));
        // Warmup poll to join consumer group and set offset to latest
        consumer.poll(Duration.ofMillis(200));

        EstimationRequest request = createValidRequest();
        EstimationResponse response = estimationService.create(request);

        // Manually trigger the outbox processor to process the pending event.
        // In production this runs on a background thread via OutboxRelay; here
        // we call it directly for deterministic test behavior.
        outboxProcessor.processOutbox();

        // Poll Kafka for the published event
        List<ConsumerRecord<String, String>> records = pollForRecords(consumer, Duration.ofSeconds(5));
        assertThat(records).isNotEmpty();

        // Kafka messages are Base64-encoded by Spring Cloud Stream's internal serialization
        String eventJson = decodeKafkaMessage(records.getFirst().value());
        assertThat(eventJson)
                .contains("sagaId")
                .contains(response.getSagaId().toString())
                .contains("EstimationRequested");

        consumer.close();

        // Verify the outbox event transitioned to PUBLISHED
        List<OutboxEvent> eventsAfterProcessing = outboxEventRepository.findAll();
        assertThat(eventsAfterProcessing).hasSize(1);
        assertThat(eventsAfterProcessing.getFirst().getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
    }

    // ---------------------------------------------------------------
    // Test 3: create() with realEstateId (alternative input path)
    // ---------------------------------------------------------------
    @Test
    void createEstimation_withRealEstateId_publishesEvent() {
        KafkaConsumer<String, String> consumer = createTestConsumer();
        consumer.subscribe(List.of("estimation.saga"));
        // Warmup poll to join consumer group and set offset to latest
        consumer.poll(Duration.ofMillis(200));

        EstimationRequest request = new EstimationRequest();
        request.setCustomerId(UUID.randomUUID());
        request.setRealEstateId(UUID.randomUUID());
        request.setInsuranceTypeId(1);

        EstimationResponse response = estimationService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("STARTED");
        assertThat(response.getVehicleId()).isNull();
        assertThat(response.getRealEstateId()).isNotNull();

        outboxProcessor.processOutbox();

        List<ConsumerRecord<String, String>> records = pollForRecords(consumer, Duration.ofSeconds(5));
        assertThat(records).isNotEmpty();
        assertThat(decodeKafkaMessage(records.getFirst().value())).contains("EstimationRequested");

        consumer.close();
    }

    // ---------------------------------------------------------------
    // Test 4: REST API create → event reaches Kafka end-to-end
    // ---------------------------------------------------------------
    @Test
    void createEstimationViaRest_eventReachesKafka() {
        KafkaConsumer<String, String> consumer = createTestConsumer();
        consumer.subscribe(List.of("estimation.saga"));
        // Warmup poll to join consumer group and set offset to latest
        consumer.poll(Duration.ofMillis(200));

        EstimationRequest request = createValidRequest();

        restTestClient.post().uri("/api/estimations")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);

        outboxProcessor.processOutbox();

        List<ConsumerRecord<String, String>> records = pollForRecords(consumer, Duration.ofSeconds(5));
        assertThat(records).isNotEmpty();
        assertThat(decodeKafkaMessage(records.getFirst().value())).contains("EstimationRequested");

        consumer.close();
    }

    // ---------------------------------------------------------------
    // Test 5: Validation — vehicleId OR realEstateId is required
    // ---------------------------------------------------------------
    @Test
    void createEstimation_withoutVehicleOrRealEstate_throwsException() {
        EstimationRequest request = new EstimationRequest();
        request.setCustomerId(UUID.randomUUID());
        request.setInsuranceTypeId(1);
        // vehicleId and realEstateId intentionally null

        try {
            estimationService.create(request);
            assertThat(false).isTrue(); // should not reach here
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("Either vehicleId or realEstateId must be provided");
        }

        // No outbox event should have been saved
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).isEmpty();
    }

    // ---------------------------------------------------------------
    // Rollback scenario (documented — manual verification)
    // ---------------------------------------------------------------
    // To verify transaction rollback prevents outbox publication:
    //
    //   1. Create a new @Transactional(propagation = REQUIRES_NEW) method
    //      that calls estimationService.create() then throws RuntimeException.
    //   2. Call that method from a test.
    //   3. Assert outboxEventRepository.findAll() is empty.
    //   4. Assert no events reach Kafka (use test consumer, poll with timeout).
    //
    // This is documented but not implemented because the service's create()
    // method does not naturally trigger a rollback on success.
    @Test
    void rollbackScenario_documentation() {
        log.warn("Rollback scenario: wrap create() in a @Transactional method that throws "
                + "RuntimeException after save, then verify no events reach Kafka. "
                + "See javadoc on {} for details.", getClass().getSimpleName());
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    /**
     * Decodes a Kafka message value written by {@code JsonSerializer} with a
     * {@code String} payload. The payload (a JSON string) is serialized as a
     * JSON string literal ({@code "..."}) on the topic.
     * <p>
     * This method unwraps the JSON string literal to recover the original JSON.
     */
    private String decodeKafkaMessage(String jsonSerializedValue) {
        try {
            // Unwrap the JSON string literal → original JSON content
            return objectMapper.readValue(jsonSerializedValue, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode Kafka message: " + jsonSerializedValue, e);
        }
    }

    private KafkaConsumer<String, String> createTestConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-integration-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // Use "latest" to avoid reading events published by previous tests
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new KafkaConsumer<>(props);
    }

    private static List<ConsumerRecord<String, String>> pollForRecords(
            KafkaConsumer<String, String> consumer, Duration timeout) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline) && records.isEmpty()) {
            consumer.poll(Duration.ofMillis(500)).forEach(records::add);
        }
        return records;
    }

    private static EstimationRequest createValidRequest() {
        EstimationRequest request = new EstimationRequest();
        request.setCustomerId(UUID.randomUUID());
        request.setVehicleId(UUID.randomUUID());
        request.setInsuranceTypeId(1);
        return request;
    }
}
