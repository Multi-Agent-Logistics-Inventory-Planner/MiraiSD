package com.mirai.inventoryservice.shared.idempotency;

/**
 * Thrown when a caller replays an {@code Idempotency-Key} already recorded for their (site, user)
 * with a request whose fingerprint does not match the original (.specs/phase-6-inventory Q-6c-3).
 * Maps to {@code 409 Conflict} ({@code GlobalExceptionHandler}), the same status
 * {@code SiteProductVersionConflictException} uses for a comparable "the same identity was reused
 * for a different thing" case.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
