package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure resolution-logic coverage for the one place COALESCE(site_products.X, products.X) is
 * computed (.specs/phase-5c-site-products AC-4).
 */
class EffectiveProductSettingsTest {

    private Product globalProduct(UUID id) {
        return Product.builder()
                .id(id)
                .name("Global Product")
                .forecastingEnabled(true)
                .unitCost(new BigDecimal("1.00"))
                .msrp(new BigDecimal("2.00"))
                .reorderPoint(10)
                .targetStockLevel(50)
                .leadTimeDays(14)
                .build();
    }

    @Test
    void absent_isNeverStockedAndFallsBackToEveryGlobalValue() {
        UUID siteId = UUID.randomUUID();
        Product product = globalProduct(UUID.randomUUID());

        EffectiveProductSettings effective = EffectiveProductSettings.absent(siteId, product);

        assertThat(effective.isStocked()).isFalse();
        assertThat(effective.forecastingEnabled()).isTrue();
        assertThat(effective.unitCost()).isEqualByComparingTo("1.00");
        assertThat(effective.msrp()).isEqualByComparingTo("2.00");
        assertThat(effective.reorderPoint()).isEqualTo(10);
        assertThat(effective.targetStockLevel()).isEqualTo(50);
        assertThat(effective.leadTimeDays()).isEqualTo(14);
    }

    @Test
    void from_withAllOverridesNull_inheritsEveryGlobalValueButKeepsTheRowsOwnIsStocked() {
        UUID productId = UUID.randomUUID();
        Product product = globalProduct(productId);
        SiteProduct siteProduct = SiteProduct.builder()
                .siteId(UUID.randomUUID())
                .productId(productId)
                .isStocked(true)
                .forecastingEnabled(false)
                .build();

        EffectiveProductSettings effective = EffectiveProductSettings.from(siteProduct, product);

        assertThat(effective.isStocked()).isTrue();
        assertThat(effective.forecastingEnabled()).isFalse();
        assertThat(effective.unitCost()).isEqualByComparingTo("1.00");
        assertThat(effective.msrp()).isEqualByComparingTo("2.00");
        assertThat(effective.reorderPoint()).isEqualTo(10);
    }

    @Test
    void from_withAnOverrideSet_usesTheOverrideNotTheGlobalValue() {
        UUID productId = UUID.randomUUID();
        Product product = globalProduct(productId);
        SiteProduct siteProduct = SiteProduct.builder()
                .siteId(UUID.randomUUID())
                .productId(productId)
                .isStocked(true)
                .forecastingEnabled(true)
                .reorderPoint(99)
                .build();

        EffectiveProductSettings effective = EffectiveProductSettings.from(siteProduct, product);

        assertThat(effective.reorderPoint()).isEqualTo(99);
        assertThat(effective.targetStockLevel()).isEqualTo(50);
    }

    @Test
    void from_deAssortedRowWithRetainedOverride_resolvesTheOverrideNotTheGlobalFallback() {
        UUID productId = UUID.randomUUID();
        Product product = globalProduct(productId);
        SiteProduct deAssorted = SiteProduct.builder()
                .siteId(UUID.randomUUID())
                .productId(productId)
                .isStocked(false)
                .forecastingEnabled(true)
                .msrp(new BigDecimal("9.99"))
                .build();

        EffectiveProductSettings effective = EffectiveProductSettings.from(deAssorted, product);

        assertThat(effective.isStocked()).isFalse();
        assertThat(effective.msrp()).isEqualByComparingTo("9.99");
    }
}
