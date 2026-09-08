package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.catalog.domain.SiteProductNotFoundException;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SiteProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The sole writer of {@link SiteProduct} (.specs/phase-5c-site-products AC-4). Two distinct
 * operations, deliberately not merged:
 * <ul>
 *   <li>{@link #setStocked} is an upsert - it is how a product is first carried at a site, and
 *       how it is later de-assorted/re-assorted. It never touches override columns, so
 *       de-assorting (setting {@code isStocked = false}) retains whatever overrides the row
 *       already has (AC-4c).</li>
 *   <li>{@link #updateSettings} requires an existing row and throws
 *       {@link SiteProductNotFoundException} otherwise - a settings write can never manufacture a
 *       partial row for a product the site has never carried (AC-4b). A row retained with
 *       {@code isStocked = false} satisfies "row exists" and stays editable (AC-4c).</li>
 * </ul>
 * Dual-write to legacy {@code products.*} columns (AC-5) and the version/tri-state settings
 * contract (AC-6) are later tasks (T-4/T-4b) layered on top of these two operations.
 */
@Service
@Transactional
public class SiteProductService {

    private final SiteProductRepository siteProductRepository;
    private final ProductRepository productRepository;

    public SiteProductService(SiteProductRepository siteProductRepository, ProductRepository productRepository) {
        this.siteProductRepository = siteProductRepository;
        this.productRepository = productRepository;
    }

    public SiteProduct setStocked(UUID siteId, UUID productId, boolean isStocked) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        SiteProduct siteProduct = siteProductRepository.findBySiteIdAndProductId(siteId, product.getId())
                .orElseGet(() -> newSiteProductSeededFromGlobal(siteId, product));
        siteProduct.setIsStocked(isStocked);
        return siteProductRepository.save(siteProduct);
    }

    /**
     * A newly-carried product starts from a snapshot of its current global settings, not the
     * entity's bare defaults - {@code forecastingEnabled} in particular defaults to {@code true}
     * on a fresh {@link SiteProduct}, which would silently turn forecasting on for a product
     * whose global {@code forecastingEnabled} is {@code false} the moment a site first carries
     * it. This mirrors {@code EffectiveProductSettings.absent}'s fallback and V57's backfill
     * seeding, so the effective value a caller saw the instant before this upsert (via the
     * absent-row path) does not change just because the row now exists. Every nullable override
     * column is left NULL, exactly like backfill - this seeds the NOT NULL columns only.
     */
    private SiteProduct newSiteProductSeededFromGlobal(UUID siteId, Product product) {
        return SiteProduct.builder()
                .siteId(siteId)
                .productId(product.getId())
                .forecastingEnabled(Boolean.TRUE.equals(product.getForecastingEnabled()))
                .build();
    }

    public SiteProduct updateSettings(UUID siteId, UUID productId, SiteProductSettingsUpdate update) {
        SiteProduct siteProduct = siteProductRepository.findBySiteIdAndProductId(siteId, productId)
                .orElseThrow(() -> new SiteProductNotFoundException(
                        "No site_products row for site " + siteId + " and product " + productId));

        if (update.forecastingEnabled() != null) {
            siteProduct.setForecastingEnabled(update.forecastingEnabled());
        }
        siteProduct.setUnitCost(update.unitCost());
        siteProduct.setMsrp(update.msrp());
        siteProduct.setReorderPoint(update.reorderPoint());
        siteProduct.setTargetStockLevel(update.targetStockLevel());
        siteProduct.setLeadTimeDays(update.leadTimeDays());

        return siteProductRepository.save(siteProduct);
    }
}
