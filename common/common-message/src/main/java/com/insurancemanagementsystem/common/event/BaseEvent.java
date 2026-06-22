package com.insurancemanagementsystem.common.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

public abstract class BaseEvent {

    private static final ObjectMapper MAPPER = new JsonMapper();

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
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    public static <T extends BaseEvent> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }
}