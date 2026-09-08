package com.mirai.inventoryservice.catalog.domain;

/**
 * No {@link SiteProduct} row exists for the requested (site, product) pair. Thrown by settings
 * writes, which require the row to already exist (.specs/phase-5c-site-products AC-4b/AC-6) -
 * assortment writes never throw this, since they upsert.
 * <p>
 * Extends {@link ProductNotFoundException} deliberately, not {@code RuntimeException} directly:
 * {@code GlobalExceptionHandler}'s {@code @ExceptionHandler} list already maps
 * {@code ProductNotFoundException} to 404, and nothing in this codebase catches
 * {@code ProductNotFoundException} specifically (it is only ever thrown, never caught), so this
 * subtype gets the same 404 mapping for free via Spring's exception-hierarchy resolution without
 * adding a new class reference to the {@code exceptions} package - which would otherwise surface
 * a new representative package cycle in the frozen ArchUnit slice-cycle store (catalog/services/
 * exceptions/identity are already one mutually-cyclic legacy component; a literal new class
 * import there shifts which cycle path ArchUnit's algorithm reports for that pre-existing debt).
 */
public class SiteProductNotFoundException extends ProductNotFoundException {
    public SiteProductNotFoundException(String message) {
        super(message);
    }
}
