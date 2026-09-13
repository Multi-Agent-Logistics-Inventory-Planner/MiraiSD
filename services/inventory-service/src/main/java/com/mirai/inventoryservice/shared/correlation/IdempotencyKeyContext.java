package com.mirai.inventoryservice.shared.correlation;

import org.slf4j.MDC;

/**
 * Access for the per-request idempotency key on v1 mutation routes (.specs/phase-6-inventory
 * Q-6c-3/T-6c-10). Nothing sets {@link #MDC_KEY} until T-6c-10's command layer exists — until
 * then {@link #current()} returns null everywhere, exactly like {@link CorrelationIdContext}
 * before {@code CorrelationIdFilter} existed. Deliberately not the same MDC key as an idempotency
 * *record* lookup: this only carries the client-supplied header value into the event envelope
 * (AC-4), it does not itself enforce the durable uniqueness constraint T-6c-10 owns.
 */
public final class IdempotencyKeyContext {

    public static final String HEADER_NAME = "Idempotency-Key";
    public static final String MDC_KEY = "idempotencyKey";

    private IdempotencyKeyContext() {
    }

    /** The current request's idempotency key, or null when none was supplied or set. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
