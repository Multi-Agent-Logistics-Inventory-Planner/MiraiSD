package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SiteProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteAssortmentTest {

    @Mock private SiteProductRepository siteProductRepository;
    @Mock private ProductRepository productRepository;

    private SiteAssortment siteAssortment;
    private UUID siteId;
    private UUID otherSiteId;
    private UUID productId;
    private Product product;

    @BeforeEach
    void setUp() {
        siteAssortment = new SiteAssortment(siteProductRepository, productRepository);
        siteId = UUID.randomUUID();
        otherSiteId = UUID.randomUUID();
        productId = UUID.randomUUID();
        product = Product.builder().id(productId).name("Widget").forecastingEnabled(true).build();
    }

    @Test
    void effectiveSettingsFor_throwsWhenTheProductDoesNotExistAtAll() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> siteAssortment.effectiveSettingsFor(siteId, productId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    /** AC-4b: an absent row (SECOND's normal state) resolves is_stocked = false with global fallback, not a failure. */
    @Test
    void effectiveSettingsFor_absentRow_resolvesNotStockedWithGlobalFallback() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.empty());

        EffectiveProductSettings effective = siteAssortment.effectiveSettingsFor(siteId, productId);

        assertThat(effective.isStocked()).isFalse();
        assertThat(effective.forecastingEnabled()).isTrue();
    }

    /** AC-4: a foreign-site pair must resolve as absent, never load-then-check on ids alone. */
    @Test
    void effectiveSettingsFor_foreignSiteRow_isNotVisibleFromAnotherSite() {
        SiteProduct rowAtOtherSite = SiteProduct.builder().siteId(otherSiteId).productId(productId).isStocked(true).build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.empty());
        when(siteProductRepository.findBySiteIdAndProductId(otherSiteId, productId)).thenReturn(Optional.of(rowAtOtherSite));

        assertThat(siteAssortment.effectiveSettingsFor(siteId, productId).isStocked()).isFalse();
        assertThat(siteAssortment.effectiveSettingsFor(otherSiteId, productId).isStocked()).isTrue();
    }

    @Test
    void isStockedAt_returnsFalseWhenNoRowExists() {
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.empty());

        assertThat(siteAssortment.isStockedAt(siteId, productId)).isFalse();
    }

    @Test
    void stockedProductIds_returnsTheRepositoryProjectionAsASet() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(siteProductRepository.findStockedProductIdsBySiteId(siteId)).thenReturn(List.of(a, b));

        assertThat(siteAssortment.stockedProductIds(siteId)).isEqualTo(Set.of(a, b));
    }
}
