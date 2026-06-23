package com.insurancemanagementsystem.customer.saga;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.insurancemanagementsystem.common.event.EventConstants;
import com.insurancemanagementsystem.common.event.EventEnvelope;
import com.insurancemanagementsystem.common.event.saga.CustomerInvalidatedEvent;
import com.insurancemanagementsystem.common.event.saga.CustomerValidatedEvent;
import com.insurancemanagementsystem.common.event.saga.EstimationFailedEvent;
import com.insurancemanagementsystem.common.event.saga.EstimationRequestedEvent;
import com.insurancemanagementsystem.common.messaging.MessagePublisher;
import com.insurancemanagementsystem.customer.entity.Customer;
import com.insurancemanagementsystem.customer.repository.CustomerRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EmbeddedKafka(
        topics = {"estimation.saga"},
        partitions = 1,
        controlledShutdown = true
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CustomerSagaConsumerTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_customer_db")
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
    private CustomerRepository customerRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private MessagePublisher messagePublisher;

    private final List<EventEnvelope> capturedEnvelopes = new ArrayList<>();

    private static final ObjectMapper MAPPER = new JsonMapper();

    private UUID sagaId;
    private UUID traceId;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
        capturedEnvelopes.clear();
        sagaId = UUID.randomUUID();
        traceId = UUID.randomUUID();

        // Capture published envelopes via doAnswer to avoid ArgumentCaptor + timeout() compatibility issues
        doAnswer(invocation -> {
            capturedEnvelopes.add(invocation.getArgument(1));
            return null;
        }).when(messagePublisher).publish(anyString(), any(EventEnvelope.class));
    }

    @Test
    void validCustomer_ShouldPublishCustomerValidated() throws Exception {
        Customer customer = customerRepository.save(Customer.builder()
                .firstName("John")
                .lastName("Doe")
                .nationalId("12345678901")
                .email("john.doe@example.com")
                .birthDate(LocalDate.of(1990, 1, 15))
                .build());

        EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder()
                .customerId(customer.getId())
                .build();
        EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
        String message = MAPPER.writeValueAsString(envelope);

        kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

        verify(messagePublisher, timeout(15000).times(1))
                .publish(anyString(), any(EventEnvelope.class));

        EventEnvelope published = capturedEnvelopes.get(0);
        assertThat(published.getEventType()).isEqualTo(EventConstants.CUSTOMER_VALIDATED);
        assertThat(published.getSagaId()).isEqualTo(sagaId);
        assertThat(published.getTraceId()).isEqualTo(traceId);

        CustomerValidatedEvent payload = MAPPER.convertValue(published.getPayload(), CustomerValidatedEvent.class);
        assertThat(payload.getCustomerId()).isEqualTo(customer.getId());
        assertThat(payload.getFirstName()).isEqualTo("John");
        assertThat(payload.getLastName()).isEqualTo("Doe");
    }

    @Test
    void invalidCustomerId_ShouldPublishCustomerInvalidated() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder()
                .customerId(nonExistentId)
                .build();
        EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
        String message = MAPPER.writeValueAsString(envelope);

        kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

        verify(messagePublisher, timeout(15000).times(1))
                .publish(anyString(), any(EventEnvelope.class));

        EventEnvelope published = capturedEnvelopes.get(0);
        assertThat(published.getEventType()).isEqualTo(EventConstants.CUSTOMER_INVALIDATED);
        assertThat(published.getSagaId()).isEqualTo(sagaId);

        CustomerInvalidatedEvent payload = MAPPER.convertValue(published.getPayload(), CustomerInvalidatedEvent.class);
        assertThat(payload.getCustomerId()).isEqualTo(nonExistentId);
        assertThat(payload.getReason()).containsIgnoringCase("not found");
    }

    @Test
    void softDeletedCustomer_ShouldPublishCustomerInvalidated() throws Exception {
        Customer customer = customerRepository.save(Customer.builder()
                .firstName("Jane")
                .lastName("Smith")
                .nationalId("98765432109")
                .build());
        customer.setDeletedAt(Instant.now());
        customerRepository.save(customer);

        EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder()
                .customerId(customer.getId())
                .build();
        EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
        String message = MAPPER.writeValueAsString(envelope);

        kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

        verify(messagePublisher, timeout(15000).times(1))
                .publish(anyString(), any(EventEnvelope.class));

        EventEnvelope published = capturedEnvelopes.get(0);
        assertThat(published.getEventType()).isEqualTo(EventConstants.CUSTOMER_INVALIDATED);

        CustomerInvalidatedEvent payload = MAPPER.convertValue(published.getPayload(), CustomerInvalidatedEvent.class);
        assertThat(payload.getCustomerId()).isEqualTo(customer.getId());
        assertThat(payload.getReason()).containsIgnoringCase("not found");
    }

    @Test
    void duplicateEstimationFailed_isIdempotent() throws Exception {
        // Use a fresh sagaId for this test
        UUID failSagaId = UUID.randomUUID();

        EstimationFailedEvent failEvent = EstimationFailedEvent.builder()
                .originalSagaId(failSagaId)
                .reason("Calculation timeout")
                .failedStep("InsuranceService")
                .build();
        EventEnvelope failEnvelope = failEvent.toEnvelope(failSagaId, traceId);
        String failMessage = MAPPER.writeValueAsString(failEnvelope);

        // Send first ESTIMATION_FAILED
        kafkaTemplate.send("estimation.saga", failMessage).get(10, TimeUnit.SECONDS);

        // Send duplicate ESTIMATION_FAILED
        kafkaTemplate.send("estimation.saga", failMessage).get(10, TimeUnit.SECONDS);

        // Wait for both messages to be consumed
        Thread.sleep(5000);

        // Now verify that the DuplicateEventStore prevented duplicate processing
        // by sending a valid ESTIMATION_REQUESTED with the SAME sagaId
        Customer customer = customerRepository.save(Customer.builder()
                .firstName("John")
                .lastName("Doe")
                .nationalId("12345678901")
                .birthDate(java.time.LocalDate.of(1990, 1, 15))
                .build());

        EstimationRequestedEvent requestEvent = EstimationRequestedEvent.builder()
                .customerId(customer.getId())
                .build();
        EventEnvelope requestEnvelope = requestEvent.toEnvelope(failSagaId, traceId);
        String requestMessage = MAPPER.writeValueAsString(requestEnvelope);

        kafkaTemplate.send("estimation.saga", requestMessage).get(10, TimeUnit.SECONDS);

        // The ESTIMATION_REQUESTED should still be processed normally (dedup scoped by eventType + sagaId)
        verify(messagePublisher, timeout(15000).times(1))
                .publish(anyString(), any(EventEnvelope.class));
    }

    @Test
    void duplicateEvent_ShouldBeIdempotent() throws Exception {
        Customer customer = customerRepository.save(Customer.builder()
                .firstName("John")
                .lastName("Doe")
                .nationalId("12345678901")
                .build());

        EstimationRequestedEvent sagaEvent = EstimationRequestedEvent.builder()
                .customerId(customer.getId())
                .build();
        EventEnvelope envelope = sagaEvent.toEnvelope(sagaId, traceId);
        String message = MAPPER.writeValueAsString(envelope);

        // Send first event and verify it was processed
        kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);
        verify(messagePublisher, timeout(15000).times(1))
                .publish(anyString(), any(EventEnvelope.class));
        assertThat(capturedEnvelopes).hasSize(1);

        // Send duplicate event with the same sagaId
        kafkaTemplate.send("estimation.saga", message).get(10, TimeUnit.SECONDS);

        // Wait for consumer to potentially process the duplicate
        Thread.sleep(5000);

        // Verify the total number of publishes is still 1 (idempotent)
        verify(messagePublisher, times(1))
                .publish(anyString(), any(EventEnvelope.class));
    }
}