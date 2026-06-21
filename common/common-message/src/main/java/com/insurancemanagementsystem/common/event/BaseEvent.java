package com.insurancemanagementsystem.common.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.UUID;

public abstract class BaseEvent {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @JsonIgnore
    public abstract String getEventType();

    public EventEnvelope toEnvelope(UUID sagaId, UUID traceId) {
        return EventEnvelope.builder()
                .sagaId(sagaId)
                .eventType(getEventType())
                .timestamp(Instant.now())
                .traceId(traceId)
                .payload(this)
                .build();
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    public static <T extends BaseEvent> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }
}
