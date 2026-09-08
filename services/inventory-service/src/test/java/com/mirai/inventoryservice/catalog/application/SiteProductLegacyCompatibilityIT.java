package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SiteProductRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Bidirectional MAIN/legacy product compatibility (.specs/phase-5c-site-products AC-5, T-4).
 * Real PostgreSQL via {@link BaseKafkaIntegrationTest}, which now seeds a MAIN site before every
 * test (T-4) - every product write goes through it.
 */
class SiteProductLegacyCompatibilityIT extends BaseKafkaIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private SiteProductService siteProductService;
    @Autowired private SiteAssortment siteAssortment;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SiteRepository siteRepository;

    @SpyBean private ProductRepository productRepository;
    @SpyBean private SiteProductRepository siteProductRepository;

    // A positive initialStock in createProduct() would otherwise route through the real
    // StockMovementService, which requires a pre-seeded NOT_ASSIGNED storage location this test
    // has no need for - only startsActive's derivation (already computed before that call)
    // matters here, matching ProductLifecycleTransactionBehaviorIT's precedent of mocking this
    // port out entirely.
    @MockBean private InitialStockPort initialStockPort;

    // Real forecast_predictions cleanup is ForecastPurgeAdapter's own concern (tested elsewhere);
    // here we only need to prove SiteProductService actually invokes the port on a true -> false
    // transition, and that a failure there rolls back the rest of the same transaction - matching
    // ProductLifecycleTransactionBehaviorIT's forecastPurgeFailureRollsBackUpdate precedent for
    // the legacy path.
    @MockBean private ForecastPurgePort forecastPurgePort;

    private UUID mainSiteId() {
        return siteRepository.findByCode("MAIN").orElseThrow().getId();
    }

    private UUID newCategory(String label) {
        return categoryRepository.save(Category.builder()
                        .name("Compat IT Category " + label + " " + System.nanoTime())
                        .slug("compat-it-category-" + label.toLowerCase() + "-" + System.nanoTime())
                        .build())
                .getId();
    }

    private UUID createProduct(String label, Integer initialStock) {
        return createProduct(label, initialStock, null, null);
    }

    private UUID createProduct(String label, Integer initialStock, BigDecimal unitCost, Boolean forecastingEnabled) {
        Product product = productService.createProduct(
                "COMPAT-" + label + "-" + System.nanoTime(), newCategory(label), null, null, null,
                "Compat IT Product " + label, "desc", null, null, null,
                unitCost, null, null, null, initialStock, null, null, null, forecastingEnabled);
        return product.getId();
    }

    private void updateUnitCostMsrpReorderPoint(UUID productId, BigDecimal unitCost, BigDecimal msrp, Integer reorderPoint) {
        productService.updateProduct(
                productId, null, null, null, null, null, null, null, reorderPoint, null, null,
                unitCost, msrp, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private Long mainVersion(UUID productId) {
        return siteProductRepository.findBySiteIdAndProductId(mainSiteId(), productId).orElseThrow().getVersion();
    }

    /** {@code null} for a field here means "omitted" (leave unchanged), matching the raw parameters below. */
    private SiteProductSettingsUpdate settingsUpdate(long version, Boolean forecastingEnabled, BigDecimal unitCost,
            BigDecimal msrp, Integer reorderPoint, Integer targetStockLevel, Integer leadTimeDays) {
        return new SiteProductSettingsUpdate(
                version,
                forecastingEnabled == null ? FieldUpdate.omitted() : FieldUpdate.of(forecastingEnabled),
                unitCost == null ? FieldUpdate.omitted() : FieldUpdate.of(unitCost),
                msrp == null ? FieldUpdate.omitted() : FieldUpdate.of(msrp),
                reorderPoint == null ? FieldUpdate.omitted() : FieldUpdate.of(reorderPoint),
                targetStockLevel == null ? FieldUpdate.omitted() : FieldUpdate.of(targetStockLevel),
                leadTimeDays == null ? FieldUpdate.omitted() : FieldUpdate.of(leadTimeDays));
    }

    // ---- Legacy create (AC-5) ----

    @Test
    void legacyCreateSeedsMainRowStockedWhenInitialStockPositive() {
        UUID productId = createProduct("Create-Stocked", 5);

        assertThat(siteAssortment.isStockedAt(mainSiteId(), productId)).isTrue();
        EffectiveProductSettings effective = siteAssortment.effectiveSettingsFor(mainSiteId(), productId);
        assertThat(effective.unitCost()).isNull();
        assertThat(effective.msrp()).isNull();
    }

    @Test
    void legacyCreateSeedsMainRowNotStockedWhenNoInitialStock() {
        UUID productId = createProduct("Create-NotStocked", null);

        // isStockedAt(false) alone also passes for an absent row (AC-4b's SECOND default) - assert
        // the MAIN row actually exists, with every nullable override left NULL, so this proves
        // AC-5's "creates a MAIN row" claim rather than merely "resolves not-stocked."
        SiteProduct mainRow = siteProductRepository.findBySiteIdAndProductId(mainSiteId(), productId).orElseThrow();
        assertThat(mainRow.getIsStocked()).isFalse();
        assertThat(mainRow.getUnitCost()).isNull();
        assertThat(mainRow.getMsrp()).isNull();
        assertThat(mainRow.getReorderPoint()).isNull();
        assertThat(mainRow.getTargetStockLevel()).isNull();
        assertThat(mainRow.getLeadTimeDays()).isNull();
    }

    // ---- Legacy activation/deactivation (AC-5) ----

    @Test
    void legacyActivateAndDeactivateSyncMainIsStockedAtomically() {
        UUID productId = createProduct("ActivateDeactivate", null);
        assertThat(siteAssortment.isStockedAt(mainSiteId(), productId)).isFalse();

        productService.activateProduct(productId);
        assertThat(productRepository.findById(productId).orElseThrow().getIsActive()).isTrue();
        assertThat(siteAssortment.isStockedAt(mainSiteId(), productId)).isTrue();

        productService.deactivateProduct(productId);
        assertThat(productRepository.findById(productId).orElseThrow().getIsActive()).isFalse();
        assertThat(siteAssortment.isStockedAt(mainSiteId(), productId)).isFalse();
    }

    // ---- Site-write (MAIN) -> legacy-read (AC-5) ----

    @Test
    void siteWriteOnMainDualWritesSettingsToProductsAtomically() {
        UUID productId = createProduct("SiteWriteDual", null);

        siteProductService.updateSettings(mainSiteId(), productId,
                settingsUpdate(mainVersion(productId), false, new BigDecimal("11.11"), new BigDecimal("22.22"), 15, 40, 7));

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getUnitCost()).isEqualByComparingTo("11.11");
        assertThat(product.getMsrp()).isEqualByComparingTo("22.22");
        assertThat(product.getReorderPoint()).isEqualTo(15);
        assertThat(product.getTargetStockLevel()).isEqualTo(40);
        assertThat(product.getLeadTimeDays()).isEqualTo(7);
        assertThat(product.getForecastingEnabled()).isFalse();
    }

    @Test
    void siteWriteSetStockedOnMainDualWritesIsActive() {
        UUID productId = createProduct("SiteWriteStocked", null);

        siteProductService.setStocked(mainSiteId(), productId, true);

        assertThat(productRepository.findById(productId).orElseThrow().getIsActive()).isTrue();
    }

    @Test
    void writeToNonMainSiteNeverTouchesGlobalProduct() {
        UUID productId = createProduct("NonMainSite", null, new BigDecimal("1.00"), null);
        Site second = siteRepository.save(Site.builder()
                .name("Compat IT SECOND").code("CI-" + Long.toString(System.nanoTime(), 36).toUpperCase())
                .build());

        SiteProduct secondRow = siteProductService.setStocked(second.getId(), productId, true);
        siteProductService.updateSettings(second.getId(), productId,
                settingsUpdate(secondRow.getVersion(), null, new BigDecimal("99.99"), null, null, null, null));

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getIsActive()).isFalse();
        assertThat(product.getUnitCost()).isEqualByComparingTo("1.00");
    }

    // ---- Clearing an override is the dual-write exception (AC-5/AC-6) ----

    @Test
    void clearingMainOverrideDoesNotDualWriteAndPreservesGlobalFallback() {
        UUID productId = createProduct("ClearOverride", null);

        // Set a MAIN override; dual-writes products.reorder_point too.
        siteProductService.updateSettings(mainSiteId(), productId,
                settingsUpdate(mainVersion(productId), null, null, null, 20, null, null));
        assertThat(productRepository.findById(productId).orElseThrow().getReorderPoint()).isEqualTo(20);

        // Simulate forecasting-service's nightly write straight to products.reorder_point.
        Product product = productRepository.findById(productId).orElseThrow();
        product.setReorderPoint(99);
        productRepository.save(product);

        // Clear the MAIN override: must update site_products only. An explicit FieldUpdate.of(null)
        // for reorderPoint, not omitted() - this is the AC-6 clear, distinct from "not provided".
        siteProductService.updateSettings(mainSiteId(), productId, new SiteProductSettingsUpdate(
                mainVersion(productId), FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted(),
                FieldUpdate.of(null), FieldUpdate.omitted(), FieldUpdate.omitted()));

        assertThat(productRepository.findById(productId).orElseThrow().getReorderPoint())
                .as("the clear must not revert products.reorder_point back to the old override value")
                .isEqualTo(99);
        assertThat(siteAssortment.effectiveSettingsFor(mainSiteId(), productId).reorderPoint())
                .as("MAIN must now resolve to the changed global value, not the stale override")
                .isEqualTo(99);

        Site second = siteRepository.save(Site.builder()
                .name("Compat IT ClearOverride SECOND")
                .code("CI-" + Long.toString(System.nanoTime(), 36).toUpperCase())
                .build());
        assertThat(siteAssortment.effectiveSettingsFor(second.getId(), productId).reorderPoint())
                .as("an unrelated inheriting site must also see the changed global value")
                .isEqualTo(99);
    }

    // ---- Legacy-write (PUT) -> site-read (AC-5) ----

    @Test
    void legacyUpdateSyncsOnlyFieldsMainAlreadyOverrides() {
        UUID productId = createProduct("LegacySync", null);

        // MAIN overrides unitCost only; msrp/reorderPoint stay NULL (inherited).
        siteProductService.updateSettings(mainSiteId(), productId,
                settingsUpdate(mainVersion(productId), null, new BigDecimal("5.00"), null, null, null, null));

        // Legacy PUT changes unitCost (overridden) and msrp (not overridden) together.
        updateUnitCostMsrpReorderPoint(productId, new BigDecimal("6.00"), new BigDecimal("30.00"), null);

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getUnitCost()).isEqualByComparingTo("6.00");
        assertThat(product.getMsrp()).isEqualByComparingTo("30.00");

        EffectiveProductSettings mainEffective = siteAssortment.effectiveSettingsFor(mainSiteId(), productId);
        assertThat(mainEffective.unitCost())
                .as("MAIN's existing override must follow the legacy edit")
                .isEqualByComparingTo("6.00");
        assertThat(mainEffective.msrp())
                .as("msrp was never overridden, so the legacy edit must reach MAIN only through global inheritance")
                .isEqualByComparingTo("30.00");
    }

    @Test
    void legacyUpdateNeverManufacturesAnOverrideForAFieldMainInherits() {
        UUID productId = createProduct("NoManufacture", null);

        updateUnitCostMsrpReorderPoint(productId, new BigDecimal("6.00"), null, null);

        // The site_products row itself must still show unit_cost as NULL (inherited), not a
        // freshly-manufactured override equal to the legacy value - this is the "row exists but
        // the field is untouched" claim, distinct from the effective-value assertion above.
        assertThat(siteProductRepository.findBySiteIdAndProductId(mainSiteId(), productId).orElseThrow().getUnitCost())
                .isNull();
    }

    // ---- AC-6b: shared-default policy - a MAIN settings change moves an inheriting site's
    // effective value, but not one that has overridden the field itself ----

    @Test
    void mainSettingsChangeMovesAnInheritingSitesEffectiveValue() {
        UUID productId = createProduct("SharedDefaultInherit", null);
        Site second = siteRepository.save(Site.builder()
                .name("Compat IT SharedDefault SECOND")
                .code("CI-" + Long.toString(System.nanoTime(), 36).toUpperCase())
                .build());
        // SECOND never carries this product (AC-4b's normal starting state) - it still inherits
        // products.* for the effective-value view even without a site_products row.
        // ProductService.createProduct defaults reorderPoint to 10 when not provided.
        assertThat(siteAssortment.effectiveSettingsFor(second.getId(), productId).reorderPoint()).isEqualTo(10);

        siteProductService.updateSettings(mainSiteId(), productId,
                settingsUpdate(mainVersion(productId), null, null, null, 25, null, null));

        assertThat(siteAssortment.effectiveSettingsFor(second.getId(), productId).reorderPoint())
                .as("SECOND has not overridden reorderPoint, so MAIN's dual-write to products.* moves its effective value")
                .isEqualTo(25);
    }

    @Test
    void mainSettingsChangeDoesNotMoveAnOverridingSitesEffectiveValue() {
        UUID productId = createProduct("SharedDefaultOverride", null);
        Site second = siteRepository.save(Site.builder()
                .name("Compat IT SharedDefault Override SECOND")
                .code("CI-" + Long.toString(System.nanoTime(), 36).toUpperCase())
                .build());
        SiteProduct secondRow = siteProductService.setStocked(second.getId(), productId, true);
        siteProductService.updateSettings(second.getId(), productId,
                settingsUpdate(secondRow.getVersion(), null, null, null, 99, null, null));

        siteProductService.updateSettings(mainSiteId(), productId,
                settingsUpdate(mainVersion(productId), null, null, null, 25, null, null));

        assertThat(siteAssortment.effectiveSettingsFor(second.getId(), productId).reorderPoint())
                .as("SECOND overrides reorderPoint itself, so MAIN's global change must not move it")
                .isEqualTo(99);
    }

    // ---- Forecasting-service writes never create manual overrides (AC-5) ----

    @Test
    void forecastingServiceRawWriteNeverCreatesSiteProductOverride() {
        UUID productId = createProduct("ForecastRaw", null);

        Product product = productRepository.findById(productId).orElseThrow();
        product.setReorderPoint(77);
        productRepository.save(product);

        assertThat(siteProductRepository.findBySiteIdAndProductId(mainSiteId(), productId).orElseThrow().getReorderPoint())
                .as("a raw products.reorder_point write must never populate site_products.reorder_point")
                .isNull();
        assertThat(siteAssortment.effectiveSettingsFor(mainSiteId(), productId).reorderPoint()).isEqualTo(77);
    }

    // ---- MAIN forecasting disable purges forecasts too (P2 review finding) ----

    @Test
    void siteWriteOnMainTrueToFalseForecastingTransitionPurgesForecasts() {
        // createProduct's forecastingEnabled arg null -> defaults to true (ProductService).
        UUID productId = createProduct("ForecastPurgeOn", null, null, null);

        siteProductService.updateSettings(mainSiteId(), productId,
                settingsUpdate(mainVersion(productId), false, null, null, null, null, null));

        verify(forecastPurgePort).purgeForecastsForProduct(eq(productId));
        assertThat(productRepository.findById(productId).orElseThrow().getForecastingEnabled()).isFalse();
    }

    @Test
    void siteWriteOnMainFalseToTrueForecastingTransitionDoesNotPurge() {
        UUID productId = createProduct("ForecastPurgeOff", null, null, false);

        siteProductService.updateSettings(mainSiteId(), productId,
                settingsUpdate(mainVersion(productId), true, null, null, null, null, null));

        verify(forecastPurgePort, never()).purgeForecastsForProduct(any());
    }

    @Test
    void siteWriteOnMainForecastingTransitionRollsBackProductAndSiteProductRowsWhenPurgeFails() {
        UUID productId = createProduct("ForecastPurgeRollback", null, null, null);
        doThrow(new RuntimeException("simulated forecast-purge failure"))
                .when(forecastPurgePort).purgeForecastsForProduct(eq(productId));

        long version = mainVersion(productId);
        assertThatThrownBy(() -> siteProductService.updateSettings(mainSiteId(), productId,
                settingsUpdate(version, false, new BigDecimal("12.34"), null, null, null, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated forecast-purge failure");

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getForecastingEnabled())
                .as("the products.forecasting_enabled save must roll back with the failed purge")
                .isTrue();
        assertThat(product.getUnitCost())
                .as("the rest of the same dual-write must roll back too, not just the forecasting field")
                .isNull();

        SiteProduct mainRow = siteProductRepository.findBySiteIdAndProductId(mainSiteId(), productId).orElseThrow();
        assertThat(mainRow.getForecastingEnabled())
                .as("the earlier site_products write in the same transaction must roll back too")
                .isTrue();
        assertThat(mainRow.getUnitCost()).isNull();
    }

    // ---- Atomic rollback (AC-5) ----

    @Test
    void siteWriteRollsBackAtomicallyWhenTheProductsDualWriteFails() {
        UUID productId = createProduct("RollbackSiteWrite", null);

        long version = mainVersion(productId);
        doThrow(new RuntimeException("simulated products dual-write failure"))
                .when(productRepository).save(any(Product.class));

        assertThatThrownBy(() -> siteProductService.updateSettings(mainSiteId(), productId,
                settingsUpdate(version, null, new BigDecimal("50.00"), null, null, null, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated products dual-write failure");

        assertThat(siteProductRepository.findBySiteIdAndProductId(mainSiteId(), productId).orElseThrow().getUnitCost())
                .as("the site_products write earlier in the same transaction must roll back too")
                .isNull();
    }

    @Test
    void legacyWriteRollsBackAtomicallyWhenTheMainSyncFails() {
        UUID productId = createProduct("RollbackLegacyWrite", null);
        siteProductService.updateSettings(mainSiteId(), productId,
                settingsUpdate(mainVersion(productId), null, new BigDecimal("5.00"), null, null, null, null));

        doThrow(new RuntimeException("simulated site_products sync failure"))
                .when(siteProductRepository).save(any());

        assertThatThrownBy(() -> updateUnitCostMsrpReorderPoint(productId, new BigDecimal("6.00"), null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated site_products sync failure");

        assertThat(productRepository.findById(productId).orElseThrow().getUnitCost())
                .as("the products.* save earlier in the same transaction must roll back too")
                .isEqualByComparingTo("5.00");
    }
}
