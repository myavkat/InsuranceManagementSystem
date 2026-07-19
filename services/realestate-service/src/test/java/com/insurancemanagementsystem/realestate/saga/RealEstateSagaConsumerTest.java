package com.insurancemanagementsystem.realestate.saga;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.insurancemanagementsystem.common.entity.OutboxEvent;
import com.insurancemanagementsystem.common.repository.OutboxEventRepository;
import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent;
import com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent;
import com.insurancemanagementsystem.common.event.saga.RealEstateInvalidatedEvent;
import com.insurancemanagementsystem.common.event.saga.RealEstateValidatedEvent;
import com.insurancemanagementsystem.realestate.entity.RealEstate;
import com.insurancemanagementsystem.realestate.repository.RealEstateRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EmbeddedKafka(topics = { "estimation.saga" }, partitions = 1, controlledShutdown = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RealEstateSagaConsumerTest {

	@Container
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
		.withDatabaseName("test_realestate_db")
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
	private RealEstateRepository realEstateRepository;

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
		realEstateRepository.deleteAll();
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

		// Ensure relay queries return empty lists so the OutboxRelay scheduled task is a
		// no-op
		when(outboxEventRepository.findTop10ByStatusOrderByCreatedAtAsc(any())).thenReturn(List.of());
		when(outboxEventRepository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of());
	}

	// ---------------------------------------------------------------
	// 1. Valid real estate → RealEstateValidated
	// ---------------------------------------------------------------
	@Test
	void validRealEstate_ShouldSaveOutboxEventForRealEstateValidated() throws Exception {
		// Arrange
		RealEstate realEstate = realEstateRepository
			.save(RealEstate.builder().address("123 Main St").cityId(34).build());

		EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder()
			.realEstateId(realEstate.getId())
			.build();
		EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
		String message = MAPPER.writeValueAsString(envelope);

		// Act — send Kafka message
		kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

		// Assert
		verify(outboxEventRepository, timeout(15000).times(1)).save(any(OutboxEvent.class));

		OutboxEvent saved = capturedOutboxEvents.get(0);
		EventEnvelope published = MAPPER.readValue(saved.getPayload(), EventEnvelope.class);
		assertThat(published.getEventType()).isEqualTo(EventConstants.REAL_ESTATE_VALIDATED);
		assertThat(published.getSagaId()).isEqualTo(sagaId);
		assertThat(published.getTraceId()).isEqualTo(traceId);

		RealEstateValidatedEvent payload = MAPPER.convertValue(published.getPayload(), RealEstateValidatedEvent.class);
		assertThat(payload.getRealEstateId()).isEqualTo(realEstate.getId());
		assertThat(payload.getAddress()).isEqualTo("123 Main St");
		assertThat(payload.getCityId()).isEqualTo(34);
	}

	// ---------------------------------------------------------------
	// 2. Non-existent realEstateId → RealEstateInvalidated
	// ---------------------------------------------------------------
	@Test
	void nonExistentRealEstateId_ShouldSaveOutboxEventForRealEstateInvalidated() throws Exception {
		// Arrange
		UUID nonExistentId = UUID.randomUUID();
		EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder().realEstateId(nonExistentId).build();
		EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
		String message = MAPPER.writeValueAsString(envelope);

		// Act — send Kafka message
		kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

		// Assert
		verify(outboxEventRepository, timeout(15000).times(1)).save(any(OutboxEvent.class));

		OutboxEvent saved = capturedOutboxEvents.get(0);
		EventEnvelope published = MAPPER.readValue(saved.getPayload(), EventEnvelope.class);
		assertThat(published.getEventType()).isEqualTo(EventConstants.REAL_ESTATE_INVALIDATED);
		assertThat(published.getSagaId()).isEqualTo(sagaId);

		RealEstateInvalidatedEvent payload = MAPPER.convertValue(published.getPayload(),
				RealEstateInvalidatedEvent.class);
		assertThat(payload.getRealEstateId()).isEqualTo(nonExistentId);
		assertThat(payload.getReason()).containsIgnoringCase("not found");
	}

	// ---------------------------------------------------------------
	// 3. Null realEstateId → RealEstateValidated with null realEstateId
	// ---------------------------------------------------------------
	@Test
	void nullRealEstateId_ShouldSaveOutboxEventForRealEstateValidatedWithNullId() throws Exception {
		// Arrange
		EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder().realEstateId(null).build();
		EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
		String message = MAPPER.writeValueAsString(envelope);

		// Act — send Kafka message
		kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

		// Assert
		verify(outboxEventRepository, timeout(15000).times(1)).save(any(OutboxEvent.class));

		OutboxEvent saved = capturedOutboxEvents.get(0);
		EventEnvelope published = MAPPER.readValue(saved.getPayload(), EventEnvelope.class);
		assertThat(published.getEventType()).isEqualTo(EventConstants.REAL_ESTATE_VALIDATED);
		assertThat(published.getSagaId()).isEqualTo(sagaId);

		RealEstateValidatedEvent payload = MAPPER.convertValue(published.getPayload(), RealEstateValidatedEvent.class);
		assertThat(payload.getRealEstateId()).isNull();
	}

	// ---------------------------------------------------------------
	// 4. Duplicate event → idempotent (save called once)
	// ---------------------------------------------------------------
	@Test
	void duplicateEvent_ShouldBeIdempotent() throws Exception {
		// Arrange
		RealEstate realEstate = realEstateRepository
			.save(RealEstate.builder().address("123 Main St").cityId(34).build());

		EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder()
			.realEstateId(realEstate.getId())
			.build();
		EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
		String message = MAPPER.writeValueAsString(envelope);

		// Act — send first event and verify it was processed
		kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);
		verify(outboxEventRepository, timeout(15000).times(1)).save(any(OutboxEvent.class));
		assertThat(capturedOutboxEvents).hasSize(1);

		// Send duplicate event with the same sagaId
		kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

		// Wait for consumer to potentially process the duplicate
		Thread.sleep(5000);

		// Verify the total number of saves is still 1 (idempotent)
		verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
	}

	// ---------------------------------------------------------------
	// 5. EstimationFailed → no outbox event saved
	// ---------------------------------------------------------------
	@Test
	void estimationFailed_ShouldNotSaveOutboxEvent() throws Exception {
		// Arrange
		EstimationFailedEvent failEvent = EstimationFailedEvent.builder()
			.originalSagaId(sagaId)
			.reason("Calculation timeout")
			.failedStep("InsuranceService")
			.build();
		EventEnvelope envelope = failEvent.toEnvelope(sagaId, traceId);
		String message = MAPPER.writeValueAsString(envelope);

		// Act — send ESTIMATION_FAILED
		kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

		// Wait for consumer to process
		Thread.sleep(5000);

		// Assert — no outbox event should be saved
		assertThat(capturedOutboxEvents).isEmpty();
	}

}
