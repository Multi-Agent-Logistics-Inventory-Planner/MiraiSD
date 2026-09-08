package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Supplies the actual managed/persistence-reference {@code Product} entity other modules need
 * when they write their own aggregate that holds a {@code @ManyToOne Product} association
 * ({@code MachineDisplay.product}, {@code KujiBoxTier.linkedProduct}, {@code KujiBox.product},
 * {@code ShipmentItem.item}, {@code StockMovement.item}, {@code LocationInventory.product}).
 * This is the one facade in {@code catalog.application} that deliberately returns the JPA entity
 * rather than an immutable record — {@link ProductRef} cannot be assigned into a JPA association.
 *
 * <p><b>{@code Propagation.MANDATORY}, not the default {@code REQUIRED}.</b> The caller is about
 * to attach the returned entity to an aggregate it is building in its own transaction — if this
 * method opened its own transaction instead of joining one already in progress (what
 * {@code REQUIRED} does when none exists), the returned reference would be scoped to a
 * transaction that closes the moment this method returns, leaving a detached entity/proxy the
 * caller cannot safely use. {@code MANDATORY} makes that impossible: it throws
 * {@code IllegalTransactionStateException} immediately if no transaction is already active,
 * rather than silently starting one.
 *
 * <p>Every caller is recorded in .specs/phase-5b-catalog-facade/log.md's T-0 table alongside its
 * assigned strategy (AC-5b) — this is not a general-purpose entity accessor.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class CatalogEntityAccess {

    private final ProductRepository productRepository;

    public CatalogEntityAccess(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * A lazy persistence reference (proxy, no {@code SELECT}) for {@code productId}. The
     * default for pure association writes: callers that have already established the product's
     * existence earlier in the same method (a prior facade read, or an id already known to be
     * valid) only need something to satisfy the FK, not a verified fetch.
     */
    public Product getReference(UUID productId) {
        return productRepository.getReferenceById(productId);
    }

    /**
     * An eagerly loaded, existence-verified {@code Product}. For a caller that has not already
     * established existence and needs this call itself to fail loudly
     * ({@link ProductNotFoundException}) if the id is invalid.
     */
    public Product requireManagedProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
    }
}
