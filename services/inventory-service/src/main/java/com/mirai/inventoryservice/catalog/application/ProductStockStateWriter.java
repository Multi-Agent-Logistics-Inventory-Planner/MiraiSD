package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Temporary write surface over {@code products.quantity}/{@code products.is_active} — exists
 * only because those columns are not yet split into {@code inventory} (quantity) and
 * per-site assortment (the master-catalog half of {@code is_active}). See
 * .specs/phase-5b-catalog-facade/spec.md, Product decisions, for each method's removal trigger.
 *
 * <p>Every method here preserves the caller's existing dirty-check exactly: a write that would
 * not change either field is skipped, matching {@code StockMovementService}'s and
 * {@code KujiBoxService}'s current behavior byte-for-byte (see AC-2b).
 *
 * <p>Joins the caller's existing transaction — {@code Propagation.MANDATORY}, not the default
 * {@code REQUIRED}: every current call site (e.g. {@code StockMovementService
 * .applyProductActiveStatusFromTotals}) already runs inside the same transaction as the stock
 * movement / outbox row it must commit atomically with (AC-5's atomicity requirement). Silently
 * opening a separate transaction here if none existed would hide a caller bug that breaks that
 * atomicity instead of failing the build/test immediately.
 */
@Service
public class ProductStockStateWriter {

    private final ProductRepository productRepository;

    public ProductStockStateWriter(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Paired {@code quantity} + {@code isActive} write. The caller derives {@code isActive}
     * itself (e.g. {@code StockMovementService}'s {@code shouldBeActive = total > 0}) — this
     * method does not derive it, only applies both fields together, preserving the exact
     * current split of responsibility. <b>Phase 6: deleted</b> together with the
     * {@code quantity} column once {@code inventory} owns quantity.
     *
     * @return {@code true} if a write actually happened, {@code false} if both fields already
     *     matched (no-op, matching the current dirty-check).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean applyStockState(UUID productId, int quantity, boolean isActive) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
        boolean changed = !Objects.equals(product.getQuantity(), quantity)
                || !Objects.equals(product.getIsActive(), isActive);
        if (!changed) {
            return false;
        }
        product.setQuantity(quantity);
        product.setIsActive(isActive);
        productRepository.save(product);
        return true;
    }

    /**
     * Batch form of {@link #applyStockState}, matching
     * {@code StockMovementService.applyProductActiveStatusFromTotals}'s existing batching
     * ({@code saveAll} once, dirty-checked per row). <b>Phase 6: deleted</b> alongside
     * {@link #applyStockState}.
     *
     * @return the ids of products actually written (unchanged rows are skipped and excluded).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> applyStockStateBatch(Map<UUID, StockState> stateByProductId) {
        if (stateByProductId == null || stateByProductId.isEmpty()) {
            return List.of();
        }
        List<UUID> changed = new ArrayList<>();
        List<Product> toSave = new ArrayList<>();
        for (Product product : productRepository.findAllById(stateByProductId.keySet())) {
            StockState state = stateByProductId.get(product.getId());
            boolean hasChange = !Objects.equals(product.getQuantity(), state.quantity())
                    || !Objects.equals(product.getIsActive(), state.isActive());
            if (hasChange) {
                product.setQuantity(state.quantity());
                product.setIsActive(state.isActive());
                toSave.add(product);
                changed.add(product.getId());
            }
        }
        if (!toSave.isEmpty()) {
            productRepository.saveAll(toSave);
        }
        return changed;
    }

    /**
     * {@code isActive}-only flip — kuji box open/close/reopen visibility toggles for a linked
     * child product. <b>Phase 6: replaced</b> by a per-site assortment mutation once
     * {@code is_active}'s stock-derived meaning is split from its master-catalog meaning (see
     * spec.md's hazard note).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void setActive(UUID productId, boolean active) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
        if (!Objects.equals(product.getIsActive(), active)) {
            product.setIsActive(active);
            productRepository.save(product);
        }
    }

    public record StockState(int quantity, boolean isActive) {
    }
}
