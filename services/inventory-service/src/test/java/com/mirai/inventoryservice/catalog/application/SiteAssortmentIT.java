package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Category;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.catalog.domain.SiteProductNotFoundException;
import com.mirai.inventoryservice.catalog.domain.SiteProductVersionConflictException;
import com.mirai.inventoryservice.catalog.infrastructure.CategoryRepository;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.integration.BaseKafkaIntegrationTest;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.sites.infrastructure.SiteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full-stack round trips for {@link SiteAssortment}/{@link SiteProductService} against real
 * PostgreSQL (.specs/phase-5c-site-products AC-4b/AC-4c). SECOND starts with zero site_products
 * rows in production (backfill only seeds MAIN, T-2), so a freshly-created SECOND site here - with
 * no row ever inserted for it - is exactly that normal state, not a contrived edge case.
 */
class SiteAssortmentIT extends BaseKafkaIntegrationTest {

    @Autowired private SiteAssortment siteAssortment;
    @Autowired private SiteProductService siteProductService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SiteRepository siteRepository;

    private Product newProduct(String label) {
        Category category = categoryRepository.save(Category.builder()
                .name("SiteAssortment IT Category " + label + " " + System.nanoTime())
                .slug("site-assortment-it-category-" + label.toLowerCase() + "-" + System.nanoTime())
                .build());
        return productRepository.save(Product.builder()
                .sku("SITE-ASSORTMENT-IT-" + label + "-" + System.nanoTime())
                .name("SiteAssortment IT Product " + label)
                .category(category)
                .unitCost(new BigDecimal("1.00"))
                .msrp(new BigDecimal("2.00"))
                .build());
    }

    private Site newSite(String label) {
        return siteRepository.save(Site.builder()
                .name("SiteAssortment IT Site " + label)
                .code("SA-" + Long.toString(System.nanoTime(), 36).toUpperCase())
                .build());
    }

    /** AC-4b: SECOND's normal state (no row ever created) resolves as not-stocked with global fallback. */
    @Test
    void aSiteWithNoAssortmentRowResolvesEveryProductAsNotStockedWithGlobalFallback() {
        Product product = newProduct("Absent");
        Site second = newSite("Absent-SECOND");

        EffectiveProductSettings effective = siteAssortment.effectiveSettingsFor(second.getId(), product.getId());

        assertThat(effective.isStocked()).isFalse();
        assertThat(effective.unitCost()).isEqualByComparingTo("1.00");
        assertThat(effective.msrp()).isEqualByComparingTo("2.00");
        assertThat(siteAssortment.stockedProductIds(second.getId())).isEmpty();
    }

    /** AC-4b: a settings write against a product this site has never carried is rejected, never a partial row. */
    @Test
    void settingsWriteAgainstAProductTheSiteHasNeverCarriedIsRejected() {
        Product product = newProduct("NeverCarried");
        Site second = newSite("NeverCarried-SECOND");
        SiteProductSettingsUpdate update = new SiteProductSettingsUpdate(
                0L, FieldUpdate.omitted(), FieldUpdate.of(new BigDecimal("5.00")), FieldUpdate.omitted(),
                FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted());

        assertThatThrownBy(() -> siteProductService.updateSettings(second.getId(), product.getId(), update))
                .isInstanceOf(SiteProductNotFoundException.class);
        assertThat(siteAssortment.isStockedAt(second.getId(), product.getId())).isFalse();
    }

    /** AC-4b: an assortment upsert is how a product is first carried at a site. */
    @Test
    void assortmentUpsertCreatesTheRowAndCarriesTheProduct() {
        Product product = newProduct("Upsert");
        Site site = newSite("Upsert");

        siteProductService.setStocked(site.getId(), product.getId(), true);

        assertThat(siteAssortment.isStockedAt(site.getId(), product.getId())).isTrue();
        assertThat(siteAssortment.stockedProductIds(site.getId())).containsExactly(product.getId());
    }

    /**
     * AC-4c: the full round trip - set an override, de-assort (row retained), confirm the read
     * still returns the saved override (not the global fallback), confirm it stays editable, then
     * re-assort and confirm the configuration survived intact.
     */
    @Test
    void deAssortingRetainsOverridesAndReAssortingRestoresConfigurationIntact() {
        Product product = newProduct("RoundTrip");
        Site site = newSite("RoundTrip");

        SiteProduct carried = siteProductService.setStocked(site.getId(), product.getId(), true);
        siteProductService.updateSettings(site.getId(), product.getId(),
                new SiteProductSettingsUpdate(
                        carried.getVersion(), FieldUpdate.omitted(), FieldUpdate.of(new BigDecimal("7.77")),
                        FieldUpdate.omitted(), FieldUpdate.of(30), FieldUpdate.omitted(), FieldUpdate.omitted()));

        SiteProduct deAssorted = siteProductService.setStocked(site.getId(), product.getId(), false);

        EffectiveProductSettings whileDeAssorted = siteAssortment.effectiveSettingsFor(site.getId(), product.getId());
        assertThat(whileDeAssorted.isStocked()).isFalse();
        assertThat(whileDeAssorted.unitCost()).isEqualByComparingTo("7.77");
        assertThat(whileDeAssorted.reorderPoint()).isEqualTo(30);

        // Still editable while de-assorted.
        siteProductService.updateSettings(site.getId(), product.getId(),
                new SiteProductSettingsUpdate(
                        deAssorted.getVersion(), FieldUpdate.omitted(), FieldUpdate.of(new BigDecimal("8.88")),
                        FieldUpdate.omitted(), FieldUpdate.of(30), FieldUpdate.omitted(), FieldUpdate.omitted()));

        siteProductService.setStocked(site.getId(), product.getId(), true);

        EffectiveProductSettings reAssorted = siteAssortment.effectiveSettingsFor(site.getId(), product.getId());
        assertThat(reAssorted.isStocked()).isTrue();
        assertThat(reAssorted.unitCost()).isEqualByComparingTo("8.88");
        assertThat(reAssorted.reorderPoint()).isEqualTo(30);
    }

    /**
     * A product whose global forecasting is off must not silently turn on the moment a site
     * first carries it - the newly-created row has to seed from the global value (matching
     * {@link EffectiveProductSettings#absent}'s fallback and V57's backfill), not the entity's
     * bare {@code forecastingEnabled = true} default (review finding, T-3).
     */
    @Test
    void firstCarryingAProductPreservesItsGlobalForecastingEnabledValue() {
        Category category = categoryRepository.save(Category.builder()
                .name("SiteAssortment IT Category ForecastOff " + System.nanoTime())
                .slug("site-assortment-it-category-forecastoff-" + System.nanoTime())
                .build());
        Product product = productRepository.save(Product.builder()
                .sku("SITE-ASSORTMENT-IT-ForecastOff-" + System.nanoTime())
                .name("SiteAssortment IT Product ForecastOff")
                .category(category)
                .forecastingEnabled(false)
                .build());
        Site site = newSite("ForecastOff");

        EffectiveProductSettings beforeCarrying = siteAssortment.effectiveSettingsFor(site.getId(), product.getId());
        assertThat(beforeCarrying.forecastingEnabled()).isFalse();

        siteProductService.setStocked(site.getId(), product.getId(), true);

        EffectiveProductSettings afterCarrying = siteAssortment.effectiveSettingsFor(site.getId(), product.getId());
        assertThat(afterCarrying.forecastingEnabled()).isFalse();
    }

    /** AC-4: a foreign-site pair never leaks - each site's read is independent. */
    @Test
    void aSitesAssortmentIsInvisibleToAnotherSite() {
        Product product = newProduct("Foreign");
        Site siteA = newSite("Foreign-A");
        Site siteB = newSite("Foreign-B");

        siteProductService.setStocked(siteA.getId(), product.getId(), true);

        assertThat(siteAssortment.isStockedAt(siteA.getId(), product.getId())).isTrue();
        assertThat(siteAssortment.isStockedAt(siteB.getId(), product.getId())).isFalse();
        assertThat(siteAssortment.stockedProductIds(siteB.getId())).isEmpty();
    }

    // ---- AC-6: version staleness ----

    /** AC-6: a stale expectedVersion is rejected with 409-mapped SiteProductVersionConflictException, no write applied. */
    @Test
    void updateSettings_rejectsAStaleVersionAndAppliesNoWrite() {
        Product product = newProduct("StaleVersion");
        Site site = newSite("StaleVersion");
        SiteProduct carried = siteProductService.setStocked(site.getId(), product.getId(), true);
        long staleVersion = carried.getVersion() - 1;

        assertThatThrownBy(() -> siteProductService.updateSettings(site.getId(), product.getId(),
                new SiteProductSettingsUpdate(
                        staleVersion, FieldUpdate.omitted(), FieldUpdate.of(new BigDecimal("50.00")),
                        FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted())))
                .isInstanceOf(SiteProductVersionConflictException.class)
                .hasMessageContaining("current version is " + carried.getVersion());

        // No override was ever applied, so the effective value stays at newProduct()'s global default.
        assertThat(siteAssortment.effectiveSettingsFor(site.getId(), product.getId()).unitCost())
                .isEqualByComparingTo("1.00");
    }

    /** AC-6: a missing expectedVersion on an existing row is rejected the same way, never last-write-wins. */
    @Test
    void updateSettings_rejectsAMissingVersionAndAppliesNoWrite() {
        Product product = newProduct("MissingVersion");
        Site site = newSite("MissingVersion");
        siteProductService.setStocked(site.getId(), product.getId(), true);

        assertThatThrownBy(() -> siteProductService.updateSettings(site.getId(), product.getId(),
                new SiteProductSettingsUpdate(
                        null, FieldUpdate.omitted(), FieldUpdate.of(new BigDecimal("50.00")),
                        FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted())))
                .isInstanceOf(SiteProductVersionConflictException.class);

        assertThat(siteAssortment.effectiveSettingsFor(site.getId(), product.getId()).unitCost())
                .isEqualByComparingTo("1.00");
    }

    /** AC-6: a matching expectedVersion is accepted and advances the row's version. */
    @Test
    void updateSettings_acceptsAMatchingVersionAndAdvancesIt() {
        Product product = newProduct("MatchingVersion");
        Site site = newSite("MatchingVersion");
        SiteProduct carried = siteProductService.setStocked(site.getId(), product.getId(), true);

        SiteProduct updated = siteProductService.updateSettings(site.getId(), product.getId(),
                new SiteProductSettingsUpdate(
                        carried.getVersion(), FieldUpdate.omitted(), FieldUpdate.of(new BigDecimal("50.00")),
                        FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted()));

        assertThat(updated.getUnitCost()).isEqualByComparingTo("50.00");
        assertThat(updated.getVersion()).isGreaterThan(carried.getVersion());
    }
}
