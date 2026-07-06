package com.insurancemanagementsystem.common.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Thread-local SAGA context for MDC management.
 * <p>
 * Sets sagaId and traceId in SLF4J MDC so that structured logging
 * captures these for every log statement within the scope.
 * Use in try-with-resources to auto-clear:
 * <pre>{@code
 * try (var ctx = SagaContext.enter(sagaId, traceId)) {
 *     // ... business logic — all logs carry sagaId + traceId
 * }
 * }</pre>
 */
public final class SagaContext implements AutoCloseable {

    private static final String SAGA_ID_KEY = "sagaId";
    private static final String TRACE_ID_KEY = "traceId";

    private SagaContext() {}

    public static SagaContext enter(UUID sagaId, UUID traceId) {
        SagaContext ctx = new SagaContext();
        MDC.put(SAGA_ID_KEY, sagaId != null ? sagaId.toString() : "");
        MDC.put(TRACE_ID_KEY, traceId != null ? traceId.toString() : "");
        return ctx;
    }

    @Override
    public void close() {
        MDC.remove(SAGA_ID_KEY);
        MDC.remove(TRACE_ID_KEY);
    }
}
