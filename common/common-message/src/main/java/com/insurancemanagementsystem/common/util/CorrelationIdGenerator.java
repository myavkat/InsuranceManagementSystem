package com.insurancemanagementsystem.common.util;

import java.util.UUID;

/**
 * Generates correlation IDs for tracing and SAGA orchestration.
 * <p>
 * Uses UUID v4 (random) for all generated IDs. This is suitable for
 * distributed tracing where global uniqueness is required across services.
 */
public final class CorrelationIdGenerator {

    private CorrelationIdGenerator() {}

    /** Generate a new saga ID (UUID v4). */
    public static UUID generateSagaId() {
        return UUID.randomUUID();
    }

    /** Generate a new trace ID (UUID v4). */
    public static UUID generateTraceId() {
        return UUID.randomUUID();
    }

    /** Generate a new correlation ID (UUID v4) — alias for generateTraceId(). */
    public static UUID generateCorrelationId() {
        return UUID.randomUUID();
    }
}
