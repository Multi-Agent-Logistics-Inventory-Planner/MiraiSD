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
 * AC-5's MAIN dual-write lives here too: when the target site is MAIN, both operations also
 * update {@code products.*} in the same transaction, so forecasting-service and the legacy
 * {@code /api/products} readers keep seeing MAIN's current values. Clearing an override (an
 * explicit {@code null} field in {@link #updateSettings}) is the deliberate exception - it never
 * dual-writes, since that would clear the very global fallback the cleared field is about to
 * depend on. The reverse direction - legacy writes syncing back into MAIN's row - is
 * {@link #syncExistingMainOverrides}, called from {@code ProductService}. The full version/
 * tri-state settings contract (AC-6) is T-4b, layered on top of this baseline.
 */
@Service
@Transactional
public class SiteProductService {

    private final SiteProductRepository siteProductRepository;
    private final ProductRepository productRepository;
    private final MainSiteResolver mainSiteResolver;
    private final ForecastPurgePort forecastPurgePort;

    public SiteProductService(
            SiteProductRepository siteProductRepository,
            ProductRepository productRepository,
            MainSiteResolver mainSiteResolver,
            ForecastPurgePort forecastPurgePort) {
        this.siteProductRepository = siteProductRepository;
        this.productRepository = productRepository;
        this.mainSiteResolver = mainSiteResolver;
        this.forecastPurgePort = forecastPurgePort;
    }

    public SiteProduct setStocked(UUID siteId, UUID productId, boolean isStocked) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        SiteProduct siteProduct = siteProductRepository.findBySiteIdAndProductId(siteId, product.getId())
                .orElseGet(() -> newSiteProductSeededFromGlobal(siteId, product));
        siteProduct.setIsStocked(isStocked);
        SiteProduct saved = siteProductRepository.save(siteProduct);

        if (mainSiteResolver.isMain(siteId)) {
            product.setIsActive(isStocked);
            productRepository.save(product);
        }

        return saved;
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

        SiteProduct saved = siteProductRepository.save(siteProduct);

        if (mainSiteResolver.isMain(siteId)) {
            dualWriteToGlobalProduct(productId, update);
        }

        return saved;
    }

    /**
     * AC-5: a MAIN settings write updates {@code products.*} atomically, for every field this
     * call actually sets. Deliberately excludes a {@code null} field (clearing an override) -
     * clearing must restore inheritance from the current global value, not erase it at the same
     * moment MAIN starts depending on it (AC-5's dual-write exception).
     * <p>
     * Mirrors {@code ProductService.updateProduct}'s forecast-purge behavior: a
     * {@code forecastingEnabled} true -> false transition purges existing forecast predictions in
     * the same transaction, so a MAIN settings write doesn't leave stale predictions visible
     * through unfiltered forecast reads the way the legacy path already guards against.
     */
    private void dualWriteToGlobalProduct(UUID productId, SiteProductSettingsUpdate update) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        boolean changed = false;
        boolean turningForecastingOff = false;
        if (update.forecastingEnabled() != null) {
            boolean wasForecastingEnabled = !Boolean.FALSE.equals(product.getForecastingEnabled());
            turningForecastingOff = wasForecastingEnabled && Boolean.FALSE.equals(update.forecastingEnabled());
            product.setForecastingEnabled(update.forecastingEnabled());
            changed = true;
        }
        if (update.unitCost() != null) {
            product.setUnitCost(update.unitCost());
            changed = true;
        }
        if (update.msrp() != null) {
            product.setMsrp(update.msrp());
            changed = true;
        }
        if (update.reorderPoint() != null) {
            product.setReorderPoint(update.reorderPoint());
            changed = true;
        }
        if (update.targetStockLevel() != null) {
            product.setTargetStockLevel(update.targetStockLevel());
            changed = true;
        }
        if (update.leadTimeDays() != null) {
            product.setLeadTimeDays(update.leadTimeDays());
            changed = true;
        }

        if (changed) {
            productRepository.save(product);
        }
        if (turningForecastingOff) {
            forecastPurgePort.purgeForecastsForProduct(productId);
        }
    }

    /**
     * AC-5's reverse direction: a legacy {@code /api/products} write ({@code ProductService})
     * calls this after saving {@code products.*} so MAIN's overrides stay in sync - but only for
     * fields MAIN currently overrides. A field MAIN inherits (NULL on the row) is left untouched:
     * the new global value already reaches MAIN through {@code EffectiveProductSettings}'
     * fallback, and touching it here would manufacture an override the site never asked for.
     * A no-op (never creates a row) when MAIN has never carried the product - legacy writes never
     * manufacture assortment, only {@link #setStocked} does.
     */
    public void syncExistingMainOverrides(UUID mainSiteId, UUID productId, SiteProductSettingsUpdate changedFields) {
        siteProductRepository.findBySiteIdAndProductId(mainSiteId, productId).ifPresent(siteProduct -> {
            boolean changed = false;
            if (changedFields.forecastingEnabled() != null) {
                siteProduct.setForecastingEnabled(changedFields.forecastingEnabled());
                changed = true;
            }
            if (changedFields.unitCost() != null && siteProduct.getUnitCost() != null) {
                siteProduct.setUnitCost(changedFields.unitCost());
                changed = true;
            }
            if (changedFields.msrp() != null && siteProduct.getMsrp() != null) {
                siteProduct.setMsrp(changedFields.msrp());
                changed = true;
            }
            if (changedFields.reorderPoint() != null && siteProduct.getReorderPoint() != null) {
                siteProduct.setReorderPoint(changedFields.reorderPoint());
                changed = true;
            }
            if (changedFields.targetStockLevel() != null && siteProduct.getTargetStockLevel() != null) {
                siteProduct.setTargetStockLevel(changedFields.targetStockLevel());
                changed = true;
            }
            if (changedFields.leadTimeDays() != null && siteProduct.getLeadTimeDays() != null) {
                siteProduct.setLeadTimeDays(changedFields.leadTimeDays());
                changed = true;
            }
            if (changed) {
                siteProductRepository.save(siteProduct);
            }
        });
    }
}
