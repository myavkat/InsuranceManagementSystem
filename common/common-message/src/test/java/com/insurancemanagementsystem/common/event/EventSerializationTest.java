package com.insurancemanagementsystem.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurancemanagementsystem.common.event.saga.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }

    @Test
    void testEstimationRequestedEvent() throws Exception {
        EstimationRequestedEvent event = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .vehicleId(UUID.randomUUID())
                .insuranceTypeId(1)
                .companyId(UUID.randomUUID())
                .build();

        String json = mapper.writeValueAsString(event);
        EstimationRequestedEvent deserialized = mapper.readValue(json, EstimationRequestedEvent.class);

        assertEquals(event.getCustomerId(), deserialized.getCustomerId());
        assertEquals(event.getVehicleId(), deserialized.getVehicleId());
        assertEquals(event.getEventType(), deserialized.getEventType());
    }

    @Test
    void testCustomerValidatedEvent() throws Exception {
        CustomerValidatedEvent event = CustomerValidatedEvent.builder()
                .customerId(UUID.randomUUID())
                .firstName("Ahmet")
                .lastName("Yılmaz")
                .build();

        String json = mapper.writeValueAsString(event);
        CustomerValidatedEvent deserialized = mapper.readValue(json, CustomerValidatedEvent.class);

        assertEquals("Ahmet", deserialized.getFirstName());
        assertEquals(event.getEventType(), deserialized.getEventType());
    }

    @Test
    void testPremiumCalculatedEvent() throws Exception {
        PremiumCalculatedEvent event = PremiumCalculatedEvent.builder()
                .premium(new BigDecimal("1250.00"))
                .breakdown(Map.of("base", new BigDecimal("1000.00"), "tax", new BigDecimal("250.00")))
                .insuranceTypeId(1)
                .build();

        String json = mapper.writeValueAsString(event);
        PremiumCalculatedEvent deserialized = mapper.readValue(json, PremiumCalculatedEvent.class);

        assertEquals(0, event.getPremium().compareTo(deserialized.getPremium()));
        assertEquals(2, deserialized.getBreakdown().size());
    }

    @Test
    void testEventEnvelope() throws Exception {
        EstimationRequestedEvent payload = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .insuranceTypeId(2)
                .build();

        EventEnvelope envelope = payload.toEnvelope(UUID.randomUUID(), UUID.randomUUID());

        String json = mapper.writeValueAsString(envelope);
        assertTrue(json.contains("\"eventType\":\"EstimationRequested\""));
        assertTrue(json.contains("\"sagaId\""));
    }

    @Test
    void testBaseEventSerialization() {
        EstimationRequestedEvent event = EstimationRequestedEvent.builder()
                .customerId(UUID.randomUUID())
                .insuranceTypeId(1)
                .build();

        String json = event.toJson();
        assertNotNull(json);

        EstimationRequestedEvent deserialized = BaseEvent.fromJson(json, EstimationRequestedEvent.class);
        assertEquals(event.getCustomerId(), deserialized.getCustomerId());
    }

    @Test
    void testEventTypeConstants() {
        assertEquals("EstimationRequested", EventConstants.ESTIMATION_REQUESTED);
        assertEquals("CustomerValidated", EventConstants.CUSTOMER_VALIDATED);
        assertEquals("PremiumCalculated", EventConstants.PREMIUM_CALCULATED);
        assertEquals("EstimationFailed", EventConstants.ESTIMATION_FAILED);
    }
}
