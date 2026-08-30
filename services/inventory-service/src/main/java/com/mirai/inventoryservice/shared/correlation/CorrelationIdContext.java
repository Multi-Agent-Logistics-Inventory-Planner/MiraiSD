package com.mirai.inventoryservice.shared.correlation;

import org.slf4j.MDC;

/**
 * Constants and access for the per-request correlation ID set by {@link CorrelationIdFilter}.
 */
public final class CorrelationIdContext {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdContext() {
    }

    /** The current request's correlation ID, or null outside a request (e.g. a scheduled job). */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
