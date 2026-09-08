package com.mirai.inventoryservice.catalog.domain;

/**
 * AC-6 (.specs/phase-5c-site-products): a settings write against a {@code site_products} row
 * whose {@code version} does not match the caller's expectation - either a stale version or a
 * missing one on a row that exists - is rejected rather than applied last-write-wins. Maps to
 * {@code 409 Conflict} ({@code GlobalExceptionHandler}), naming the row's current version in the
 * message.
 */
public class SiteProductVersionConflictException extends RuntimeException {
    public SiteProductVersionConflictException(String message) {
        super(message);
    }
}
