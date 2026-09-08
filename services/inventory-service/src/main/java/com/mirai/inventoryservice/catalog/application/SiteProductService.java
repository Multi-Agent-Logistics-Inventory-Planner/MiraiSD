package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.catalog.domain.SiteProductNotFoundException;
import com.mirai.inventoryservice.catalog.domain.SiteProductVersionConflictException;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SiteProductRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
 * {@link #syncExistingMainOverrides}, called from {@code ProductService}.
 * <p>
 * {@link #updateSettings} also enforces AC-6's version contract: the caller's
 * {@code expectedVersion} must match the row's current version, and a missing version is
 * rejected the same way a stale one is (never last-write-wins).
 */
@Service
@Transactional
public class SiteProductService {

    private final SiteProductRepository siteProductRepository;
    private final ProductRepository productRepository;
    private final MainSiteResolver mainSiteResolver;
    private final ForecastPurgePort forecastPurgePort;
    private final SiteProductVersionReader siteProductVersionReader;

    public SiteProductService(
            SiteProductRepository siteProductRepository,
            ProductRepository productRepository,
            MainSiteResolver mainSiteResolver,
            ForecastPurgePort forecastPurgePort,
            SiteProductVersionReader siteProductVersionReader) {
        this.siteProductRepository = siteProductRepository;
        this.productRepository = productRepository;
        this.mainSiteResolver = mainSiteResolver;
        this.forecastPurgePort = forecastPurgePort;
        this.siteProductVersionReader = siteProductVersionReader;
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

        requireCurrentVersion(siteId, productId, siteProduct, update.expectedVersion());

        if (update.forecastingEnabled().isPresent() && update.forecastingEnabled().value() != null) {
            siteProduct.setForecastingEnabled(update.forecastingEnabled().value());
        }
        if (update.unitCost().isPresent()) {
            siteProduct.setUnitCost(update.unitCost().value());
        }
        if (update.msrp().isPresent()) {
            siteProduct.setMsrp(update.msrp().value());
        }
        if (update.reorderPoint().isPresent()) {
            siteProduct.setReorderPoint(update.reorderPoint().value());
        }
        if (update.targetStockLevel().isPresent()) {
            siteProduct.setTargetStockLevel(update.targetStockLevel().value());
        }
        if (update.leadTimeDays().isPresent()) {
            siteProduct.setLeadTimeDays(update.leadTimeDays().value());
        }

        SiteProduct saved;
        try {
            saved = siteProductRepository.saveAndFlush(siteProduct);
        } catch (ObjectOptimisticLockingFailureException e) {
            // The flush failed, so this transaction's persistence context must be treated as
            // unusable for further work - re-querying through it here risks a second failure,
            // surfacing as a 500 instead of the intended 409 (review finding). Read the winner's
            // current version through a fresh, independent transaction instead.
            Long currentVersion = siteProductVersionReader.currentVersion(siteId, productId);
            throw versionConflict(siteId, productId, update.expectedVersion(), currentVersion);
        }

        if (mainSiteResolver.isMain(siteId)) {
            dualWriteToGlobalProduct(productId, update);
        }

        return saved;
    }

    /**
     * AC-6: rejects a stale version and a missing one identically - a settings write must never
     * fall back to last-write-wins just because the caller didn't send a version. The row is
     * already loaded here (no flush has been attempted yet), so its in-memory version is safe to
     * use directly - no need to re-query.
     */
    private void requireCurrentVersion(UUID siteId, UUID productId, SiteProduct siteProduct, Long expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(siteProduct.getVersion())) {
            throw versionConflict(siteId, productId, expectedVersion, siteProduct.getVersion());
        }
    }

    private SiteProductVersionConflictException versionConflict(
            UUID siteId, UUID productId, Long expectedVersion, Long currentVersion) {
        return new SiteProductVersionConflictException(
                "Version conflict updating site product settings for site " + siteId + " and product " + productId
                        + ": expected version " + expectedVersion + " but current version is " + currentVersion);
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
        if (update.forecastingEnabled().isPresent() && update.forecastingEnabled().value() != null) {
            boolean wasForecastingEnabled = !Boolean.FALSE.equals(product.getForecastingEnabled());
            turningForecastingOff = wasForecastingEnabled && Boolean.FALSE.equals(update.forecastingEnabled().value());
            product.setForecastingEnabled(update.forecastingEnabled().value());
            changed = true;
        }
        if (update.unitCost().isPresent() && update.unitCost().value() != null) {
            product.setUnitCost(update.unitCost().value());
            changed = true;
        }
        if (update.msrp().isPresent() && update.msrp().value() != null) {
            product.setMsrp(update.msrp().value());
            changed = true;
        }
        if (update.reorderPoint().isPresent() && update.reorderPoint().value() != null) {
            product.setReorderPoint(update.reorderPoint().value());
            changed = true;
        }
        if (update.targetStockLevel().isPresent() && update.targetStockLevel().value() != null) {
            product.setTargetStockLevel(update.targetStockLevel().value());
            changed = true;
        }
        if (update.leadTimeDays().isPresent() && update.leadTimeDays().value() != null) {
            product.setLeadTimeDays(update.leadTimeDays().value());
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
    public void syncExistingMainOverrides(UUID mainSiteId, UUID productId, ProductFieldChanges changedFields) {
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
