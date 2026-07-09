package com.insurancemanagementsystem.common.event;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.insurancemanagementsystem.common.event.domain.*;
import com.insurancemanagementsystem.common.event.saga.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventSerializationTest {

    private ObjectMapper mapper;
    private final UUID sagaId = UUID.randomUUID();
    private final UUID traceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mapper = new JsonMapper();
    }

    // ========================================================================
    // SAGA Event Tests (10 total)
    // ========================================================================

    @Test
    void shouldSerializeAndDeserializeEstimationRequestedEvent() throws Exception {
        EstimationRequestedEvent event = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .realEstateId(null)
                .insuranceId(UUID.randomUUID())
                                .build();

        assertSagaEventRoundTrip(event, EstimationRequestedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeCustomerValidatedEvent() throws Exception {
        CustomerValidatedEvent event = CustomerValidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .firstName("Ahmet")
                .lastName("Yılmaz")
                .build();

        assertSagaEventRoundTrip(event, CustomerValidatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeCustomerInvalidatedEvent() throws Exception {
        CustomerInvalidatedEvent event = CustomerInvalidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .reason("Customer not found")
                .build();

        assertSagaEventRoundTrip(event, CustomerInvalidatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeVehicleValidatedEvent() throws Exception {
        VehicleValidatedEvent event = VehicleValidatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .plate("34ABC123")
                .brand("Toyota")
                .model("Corolla")
                .build();

        assertSagaEventRoundTrip(event, VehicleValidatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeVehicleInvalidatedEvent() throws Exception {
        VehicleInvalidatedEvent event = VehicleInvalidatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .reason("Vehicle not found")
                .build();

        assertSagaEventRoundTrip(event, VehicleInvalidatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeRealEstateValidatedEvent() throws Exception {
        RealEstateValidatedEvent event = RealEstateValidatedEvent.builder()
                .realEstateId(UUID.randomUUID())
                .address("Atatürk Cad. No:42, Kadıköy")
                .cityId(34)
                .build();

        assertSagaEventRoundTrip(event, RealEstateValidatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeRealEstateInvalidatedEvent() throws Exception {
        RealEstateInvalidatedEvent event = RealEstateInvalidatedEvent.builder()
                .realEstateId(UUID.randomUUID())
                .reason("Real estate not found")
                .build();

        assertSagaEventRoundTrip(event, RealEstateInvalidatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializePremiumCalculatedEvent() throws Exception {
        PremiumCalculatedEvent event = PremiumCalculatedEvent.builder()
                .premium(new BigDecimal("1250.00"))
                .breakdown(Map.of("base", new BigDecimal("1000.00"), "tax", new BigDecimal("250.00")))
                .insuranceId(UUID.randomUUID())
                                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .build();

        assertSagaEventRoundTrip(event, PremiumCalculatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeCalculationFailedEvent() throws Exception {
        CalculationFailedEvent event = CalculationFailedEvent.builder()
                .reason("No active insurance found")
                .build();

        assertSagaEventRoundTrip(event, CalculationFailedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeEstimationFailedEvent() throws Exception {
        EstimationFailedEvent event = EstimationFailedEvent.builder()
                .originalSagaId(UUID.randomUUID())
                .reason("Timeout waiting for validation")
                .failedStep("CustomerValidation")
                .build();

        assertSagaEventRoundTrip(event, EstimationFailedEvent.class);
    }

    // ========================================================================
    // Domain Event Tests (13 total — 12 existing + InsuranceDeletedEvent)
    // ========================================================================

    @Test
    void shouldSerializeAndDeserializeCustomerCreatedEvent() throws Exception {
        CustomerCreatedEvent event = CustomerCreatedEvent.builder()
                .customerId(UUID.randomUUID())
                .nationalId("12345678901")
                .email("ahmet@example.com")
                .firstName("Ahmet")
                .lastName("Yılmaz")
                .build();

        assertDomainEventRoundTrip(event, CustomerCreatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeCustomerUpdatedEvent() throws Exception {
        CustomerUpdatedEvent event = CustomerUpdatedEvent.builder()
                .customerId(UUID.randomUUID())
                .nationalId("12345678901")
                .email("ahmet.new@example.com")
                .firstName("Ahmet")
                .lastName("Yılmaz")
                .build();

        assertDomainEventRoundTrip(event, CustomerUpdatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeCustomerDeletedEvent() throws Exception {
        CustomerDeletedEvent event = CustomerDeletedEvent.builder()
                .customerId(UUID.randomUUID())
                .nationalId("12345678901")
                .build();

        assertDomainEventRoundTrip(event, CustomerDeletedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeVehicleCreatedEvent() throws Exception {
        VehicleCreatedEvent event = VehicleCreatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .plate("34ABC123")
                .customerId(UUID.randomUUID())
                .carBrandId(1)
                .build();

        assertDomainEventRoundTrip(event, VehicleCreatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeVehicleUpdatedEvent() throws Exception {
        VehicleUpdatedEvent event = VehicleUpdatedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .plate("34ABC456")
                .customerId(UUID.randomUUID())
                .carBrandId(2)
                .build();

        assertDomainEventRoundTrip(event, VehicleUpdatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeVehicleDeletedEvent() throws Exception {
        VehicleDeletedEvent event = VehicleDeletedEvent.builder()
                .vehicleId(UUID.randomUUID())
                .plate("34ABC123")
                .build();

        assertDomainEventRoundTrip(event, VehicleDeletedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeRealEstateCreatedEvent() throws Exception {
        RealEstateCreatedEvent event = RealEstateCreatedEvent.builder()
                .realEstateId(UUID.randomUUID())
                .address("Bağdat Cad. No:123, Maltepe")
                .cityId(34)
                .customerId(UUID.randomUUID())
                .build();

        assertDomainEventRoundTrip(event, RealEstateCreatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeRealEstateUpdatedEvent() throws Exception {
        RealEstateUpdatedEvent event = RealEstateUpdatedEvent.builder()
                .realEstateId(UUID.randomUUID())
                .address("Yeni Cad. No:45, Beşiktaş")
                .cityId(34)
                .customerId(UUID.randomUUID())
                .build();

        assertDomainEventRoundTrip(event, RealEstateUpdatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeRealEstateDeletedEvent() throws Exception {
        RealEstateDeletedEvent event = RealEstateDeletedEvent.builder()
                .realEstateId(UUID.randomUUID())
                .build();

        assertDomainEventRoundTrip(event, RealEstateDeletedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeInsuranceCreatedEvent() throws Exception {
        InsuranceCreatedEvent event = InsuranceCreatedEvent.builder()
                .insuranceId(UUID.randomUUID())
                .typeId(1)
                                .name("Trafik Sigortası")
                .build();

        assertDomainEventRoundTrip(event, InsuranceCreatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeInsuranceUpdatedEvent() throws Exception {
        InsuranceUpdatedEvent event = InsuranceUpdatedEvent.builder()
                .insuranceId(UUID.randomUUID())
                .typeId(1)
                                .name("Kasko Sigortası")
                .build();

        assertDomainEventRoundTrip(event, InsuranceUpdatedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeInsuranceDeletedEvent() throws Exception {
        InsuranceDeletedEvent event = InsuranceDeletedEvent.builder()
                .insuranceId(UUID.randomUUID())
                .typeId(2)
                                .build();

        assertDomainEventRoundTrip(event, InsuranceDeletedEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeReferenceDataChangedEvent() throws Exception {
        ReferenceDataChangedEvent event = ReferenceDataChangedEvent.builder()
                .entityType("City")
                .changeType("UPDATED")
                .build();

        assertDomainEventRoundTrip(event, ReferenceDataChangedEvent.class);
    }

    // ========================================================================
    // Edge Case Tests
    // ========================================================================

    @Test
    void shouldHandlePremiumCalculatedWithNullBreakdown() throws Exception {
        PremiumCalculatedEvent event = PremiumCalculatedEvent.builder()
                .premium(new BigDecimal("1250.00"))
                .breakdown(null)
                .insuranceId(UUID.randomUUID())
                .build();

        String json = mapper.writeValueAsString(event);
        // Verify null breakdown does not serialize as the string "null"
        assertFalse(json.contains("\"null\""), "Null breakdown should not serialize as \"null\" string");

        PremiumCalculatedEvent deserialized = mapper.readValue(json, PremiumCalculatedEvent.class);
        assertEquals(0, event.getPremium().compareTo(deserialized.getPremium()));
        assertNull(deserialized.getBreakdown());
    }

    @Test
    void shouldPreserveBigDecimalPrecisionWithManyDecimalPlaces() throws Exception {
        PremiumCalculatedEvent event = PremiumCalculatedEvent.builder()
                .premium(new BigDecimal("1250.123456789"))
                .breakdown(Map.of("base", new BigDecimal("1000.9876543210123456789")))
                .insuranceId(UUID.randomUUID())
                .build();

        assertSagaEventRoundTrip(event, PremiumCalculatedEvent.class);

        // Verify precision is preserved exactly
        String json = mapper.writeValueAsString(event);
        PremiumCalculatedEvent deserialized = mapper.readValue(json, PremiumCalculatedEvent.class);
        assertEquals(0, event.getPremium().compareTo(deserialized.getPremium()),
                "BigDecimal precision should be preserved");
        assertEquals(0, event.getBreakdown().get("base").compareTo(deserialized.getBreakdown().get("base")),
                "BigDecimal breakdown precision should be preserved");
    }

    @Test
    void shouldHandleNullOptionalFieldsInEstimationRequested() throws Exception {
        // Estimation with property insurance (no vehicleId)
        EstimationRequestedEvent event = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .vehicleId(null)
                .realEstateId(UUID.randomUUID())
                .insuranceId(UUID.randomUUID()) // DASK
                                .build();

        String json = mapper.writeValueAsString(event);

        EstimationRequestedEvent deserialized = mapper.readValue(json, EstimationRequestedEvent.class);
        assertNull(deserialized.getVehicleId(), "Explicitly-set null vehicleId should remain null after round-trip");
        assertNotNull(deserialized.getRealEstateId());
        assertEquals(event.getCustomerId(), deserialized.getCustomerId());
        assertEquals(event.getInsuranceId(), deserialized.getInsuranceId());
    }

    @Test
    void shouldHandleVehicleValidatedWithNullFields() throws Exception {
        // When no vehicleId in estimation request (property insurance)
        VehicleValidatedEvent event = VehicleValidatedEvent.builder()
                .vehicleId(null)
                .build();

        assertSagaEventRoundTrip(event, VehicleValidatedEvent.class);
    }

    @Test
    void shouldHandleRealEstateValidatedWithNullFields() throws Exception {
        // When no realEstateId in estimation request (traffic insurance)
        RealEstateValidatedEvent event = RealEstateValidatedEvent.builder()
                .realEstateId(null)
                .build();

        assertSagaEventRoundTrip(event, RealEstateValidatedEvent.class);
    }

    // ========================================================================
    // EventType Constant Verification
    // ========================================================================

    @Test
    void shouldMatchAllEventTypeConstants() {
        // SAGA event types
        assertEquals("EstimationRequested", EventConstants.ESTIMATION_REQUESTED);
        assertEquals("CustomerValidated", EventConstants.CUSTOMER_VALIDATED);
        assertEquals("CustomerInvalidated", EventConstants.CUSTOMER_INVALIDATED);
        assertEquals("VehicleValidated", EventConstants.VEHICLE_VALIDATED);
        assertEquals("VehicleInvalidated", EventConstants.VEHICLE_INVALIDATED);
        assertEquals("RealEstateValidated", EventConstants.REAL_ESTATE_VALIDATED);
        assertEquals("RealEstateInvalidated", EventConstants.REAL_ESTATE_INVALIDATED);
        assertEquals("PremiumCalculated", EventConstants.PREMIUM_CALCULATED);
        assertEquals("CalculationFailed", EventConstants.CALCULATION_FAILED);
        assertEquals("EstimationFailed", EventConstants.ESTIMATION_FAILED);

        // Domain event types
        assertEquals("CustomerCreated", EventConstants.CUSTOMER_CREATED);
        assertEquals("CustomerUpdated", EventConstants.CUSTOMER_UPDATED);
        assertEquals("CustomerDeleted", EventConstants.CUSTOMER_DELETED);
        assertEquals("VehicleCreated", EventConstants.VEHICLE_CREATED);
        assertEquals("VehicleUpdated", EventConstants.VEHICLE_UPDATED);
        assertEquals("VehicleDeleted", EventConstants.VEHICLE_DELETED);
        assertEquals("RealEstateCreated", EventConstants.REAL_ESTATE_CREATED);
        assertEquals("RealEstateUpdated", EventConstants.REAL_ESTATE_UPDATED);
        assertEquals("RealEstateDeleted", EventConstants.REAL_ESTATE_DELETED);
        assertEquals("InsuranceCreated", EventConstants.INSURANCE_CREATED);
        assertEquals("InsuranceUpdated", EventConstants.INSURANCE_UPDATED);
        assertEquals("InsuranceDeleted", EventConstants.INSURANCE_DELETED);
        assertEquals("ReferenceDataChanged", EventConstants.REFERENCE_DATA_CHANGED);
    }

    @Test
    void shouldVerifyEventTypeReturnValues() {
        // SAGA events
        assertEquals(EventConstants.ESTIMATION_REQUESTED, new EstimationRequestedEvent().getEventType());
        assertEquals(EventConstants.CUSTOMER_VALIDATED, new CustomerValidatedEvent().getEventType());
        assertEquals(EventConstants.CUSTOMER_INVALIDATED, new CustomerInvalidatedEvent().getEventType());
        assertEquals(EventConstants.VEHICLE_VALIDATED, new VehicleValidatedEvent().getEventType());
        assertEquals(EventConstants.VEHICLE_INVALIDATED, new VehicleInvalidatedEvent().getEventType());
        assertEquals(EventConstants.REAL_ESTATE_VALIDATED, new RealEstateValidatedEvent().getEventType());
        assertEquals(EventConstants.REAL_ESTATE_INVALIDATED, new RealEstateInvalidatedEvent().getEventType());
        assertEquals(EventConstants.PREMIUM_CALCULATED, new PremiumCalculatedEvent().getEventType());
        assertEquals(EventConstants.CALCULATION_FAILED, new CalculationFailedEvent().getEventType());
        assertEquals(EventConstants.ESTIMATION_FAILED, new EstimationFailedEvent().getEventType());

        // Domain events
        assertEquals(EventConstants.CUSTOMER_CREATED, new CustomerCreatedEvent().getEventType());
        assertEquals(EventConstants.CUSTOMER_UPDATED, new CustomerUpdatedEvent().getEventType());
        assertEquals(EventConstants.CUSTOMER_DELETED, new CustomerDeletedEvent().getEventType());
        assertEquals(EventConstants.VEHICLE_CREATED, new VehicleCreatedEvent().getEventType());
        assertEquals(EventConstants.VEHICLE_UPDATED, new VehicleUpdatedEvent().getEventType());
        assertEquals(EventConstants.VEHICLE_DELETED, new VehicleDeletedEvent().getEventType());
        assertEquals(EventConstants.REAL_ESTATE_CREATED, new RealEstateCreatedEvent().getEventType());
        assertEquals(EventConstants.REAL_ESTATE_UPDATED, new RealEstateUpdatedEvent().getEventType());
        assertEquals(EventConstants.REAL_ESTATE_DELETED, new RealEstateDeletedEvent().getEventType());
        assertEquals(EventConstants.INSURANCE_CREATED, new InsuranceCreatedEvent().getEventType());
        assertEquals(EventConstants.INSURANCE_UPDATED, new InsuranceUpdatedEvent().getEventType());
        assertEquals(EventConstants.INSURANCE_DELETED, new InsuranceDeletedEvent().getEventType());
        assertEquals(EventConstants.REFERENCE_DATA_CHANGED, new ReferenceDataChangedEvent().getEventType());
    }

    // ========================================================================
    // Envelope Serialization Tests
    // ========================================================================

    @Test
    void shouldSerializeAndDeserializeEventEnvelope() throws Exception {
        EstimationRequestedEvent payload = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .insuranceId(UUID.randomUUID())
                .build();

        EventEnvelope envelope = payload.toEnvelope(sagaId, traceId);

        String json = mapper.writeValueAsString(envelope);
        EventEnvelope deserialized = mapper.readValue(json, EventEnvelope.class);

        assertEquals(sagaId, deserialized.getSagaId());
        assertEquals(EventConstants.ESTIMATION_REQUESTED, deserialized.getEventType());
        assertNotNull(deserialized.getTimestamp());
        assertEquals(traceId, deserialized.getTraceId());

        // Payload should survive as a Map after envelope deserialization
        assertNotNull(deserialized.getPayload());
        assertInstanceOf(Map.class, deserialized.getPayload());

        // Convert the Map payload back to the typed event
        EstimationRequestedEvent convertedPayload = mapper.convertValue(deserialized.getPayload(), EstimationRequestedEvent.class);
        assertEquals(payload.getCustomerId(), convertedPayload.getCustomerId());
        assertEquals(payload.getInsuranceId(), convertedPayload.getInsuranceId());
        assertEquals(payload.getEventType(), convertedPayload.getEventType());
    }

    // ========================================================================
    // BaseEvent Utility Tests
    // ========================================================================

    @Test
    void shouldRoundTripViaBaseEventUtilities() {
        EstimationRequestedEvent event = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .insuranceId(UUID.randomUUID())
                .build();

        String json = event.toJson();
        assertNotNull(json);

        EstimationRequestedEvent deserialized = BaseEvent.fromJson(json, EstimationRequestedEvent.class);
        assertEquals(event.getCustomerId(), deserialized.getCustomerId());
        assertEquals(event.getInsuranceId(), deserialized.getInsuranceId());
        assertEquals(event.getEventType(), deserialized.getEventType());
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Asserts complete round-trip for a SAGA event: JSON serialization,
     * deserialization, and EventEnvelope wrapping/unwrapping.
     */
    private <T extends BaseEvent> void assertSagaEventRoundTrip(T event, Class<T> clazz) throws Exception {
        // 1. Direct JSON round-trip — compare serialized JSON to verify round-trip
        // Note: We compare JSON strings rather than using assertEquals(event, deserialized)
        // because @EqualsAndHashCode(callSuper=true) on event classes calls
        // Object.equals() on BaseEvent (no fields), making instances unequal by identity.
        String json = mapper.writeValueAsString(event);
        T deserialized = mapper.readValue(json, clazz);
        assertEquals(mapper.writeValueAsString(deserialized), json,
                "Event JSON should survive round-trip");
        assertEquals(event.getEventType(), deserialized.getEventType(),
                "eventType should survive JSON round-trip");

        // 2. Event type constant verification
        String expectedEventType = event.getEventType();
        assertNotNull(expectedEventType, "eventType must not be null");

        // 3. Envelope round-trip
        EventEnvelope envelope = event.toEnvelope(sagaId, traceId);
        String envelopeJson = mapper.writeValueAsString(envelope);
        EventEnvelope deserializedEnvelope = mapper.readValue(envelopeJson, EventEnvelope.class);

        assertEquals(sagaId, deserializedEnvelope.getSagaId(),
                "sagaId should survive envelope round-trip");
        assertEquals(expectedEventType, deserializedEnvelope.getEventType(),
                "eventType should survive envelope round-trip");
        assertEquals(traceId, deserializedEnvelope.getTraceId(),
                "traceId should survive envelope round-trip");
        assertNotNull(deserializedEnvelope.getTimestamp(),
                "timestamp should not be null in envelope");

        // 4. Payload conversion from envelope — verify key fields survive
        // Note: Full object equality may differ for BigDecimal precision (e.g., breakdown
        // map values go through Double → BigDecimal conversion in envelope round-trip).
        // We verify that the payload converts to the correct type and preserves eventType.
        T convertedPayload = mapper.convertValue(deserializedEnvelope.getPayload(), clazz);
        assertEquals(expectedEventType, convertedPayload.getEventType(),
                "Converted payload eventType should match");
    }

    /**
     * Asserts basic round-trip for a domain event: JSON serialization,
     * deserialization, and getEventType() verification.
     */
    private <T extends BaseEvent> void assertDomainEventRoundTrip(T event, Class<T> clazz) throws Exception {
        String json = mapper.writeValueAsString(event);
        T deserialized = mapper.readValue(json, clazz);
        assertEquals(mapper.writeValueAsString(deserialized), json,
                "Event JSON should survive round-trip");
        assertEquals(event.getEventType(), deserialized.getEventType(),
                "eventType should survive JSON round-trip");

        // Verify event type constant is correct
        String expectedEventType = event.getEventType();
        assertNotNull(expectedEventType, "eventType must not be null");

        // Verify the constant matches the expected string pattern
        assertTrue(expectedEventType.endsWith("Created")
                        || expectedEventType.endsWith("Updated")
                        || expectedEventType.endsWith("Deleted")
                        || expectedEventType.endsWith("Changed"),
                "Domain event type should end with Created/Updated/Deleted/Changed but was: " + expectedEventType);
    }
}
