package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.catalog.domain.SiteProductNotFoundException;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SiteProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteProductServiceTest {

    @Mock private SiteProductRepository siteProductRepository;
    @Mock private ProductRepository productRepository;

    private SiteProductService siteProductService;
    private UUID siteId;
    private UUID productId;
    private Product product;

    @BeforeEach
    void setUp() {
        siteProductService = new SiteProductService(siteProductRepository, productRepository);
        siteId = UUID.randomUUID();
        productId = UUID.randomUUID();
        product = Product.builder().id(productId).name("Widget").build();
    }

    private void stubSaveReturnsItsArgument() {
        when(siteProductRepository.save(any(SiteProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void setStocked_throwsWhenTheProductDoesNotExist() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> siteProductService.setStocked(siteId, productId, true))
                .isInstanceOf(ProductNotFoundException.class);
        verify(siteProductRepository, never()).save(any());
    }

    /** AC-4b: an assortment write is an upsert - it creates the row when absent. */
    @Test
    void setStocked_createsARowWhenNoneExistsYet() {
        stubSaveReturnsItsArgument();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.empty());

        ArgumentCaptor<SiteProduct> captor = ArgumentCaptor.forClass(SiteProduct.class);
        SiteProduct saved = siteProductService.setStocked(siteId, productId, true);

        verify(siteProductRepository).save(captor.capture());
        assertThat(captor.getValue().getSiteId()).isEqualTo(siteId);
        assertThat(captor.getValue().getProductId()).isEqualTo(productId);
        assertThat(saved.getIsStocked()).isTrue();
    }

    /** AC-4c: de-assorting flips is_stocked on the existing row and never touches its overrides. */
    @Test
    void setStocked_deAssortingAnExistingRowLeavesItsOverridesUntouched() {
        stubSaveReturnsItsArgument();
        SiteProduct existing = SiteProduct.builder()
                .siteId(siteId).productId(productId).isStocked(true).msrp(new BigDecimal("9.99")).build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(existing));

        SiteProduct result = siteProductService.setStocked(siteId, productId, false);

        assertThat(result.getIsStocked()).isFalse();
        assertThat(result.getMsrp()).isEqualByComparingTo("9.99");
    }

    /** AC-4b: a settings write against a product the site has never carried is rejected, not partially applied. */
    @Test
    void updateSettings_throwsWhenNoRowExistsForThisSite() {
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.empty());
        SiteProductSettingsUpdate update = new SiteProductSettingsUpdate(null, new BigDecimal("5.00"), null, null, null, null);

        assertThatThrownBy(() -> siteProductService.updateSettings(siteId, productId, update))
                .isInstanceOf(SiteProductNotFoundException.class);
        verify(siteProductRepository, never()).save(any());
    }

    /** AC-4c: a retained de-assorted row (isStocked = false) still satisfies row-exists and stays editable. */
    @Test
    void updateSettings_onARetainedDeAssortedRowIsAccepted() {
        stubSaveReturnsItsArgument();
        SiteProduct deAssorted = SiteProduct.builder().siteId(siteId).productId(productId).isStocked(false).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(deAssorted));
        SiteProductSettingsUpdate update = new SiteProductSettingsUpdate(null, new BigDecimal("3.50"), null, 20, null, null);

        SiteProduct result = siteProductService.updateSettings(siteId, productId, update);

        assertThat(result.getIsStocked()).isFalse();
        assertThat(result.getUnitCost()).isEqualByComparingTo("3.50");
        assertThat(result.getReorderPoint()).isEqualTo(20);
    }

    @Test
    void updateSettings_nullForecastingEnabledLeavesTheCurrentValueUnchanged() {
        stubSaveReturnsItsArgument();
        SiteProduct existing = SiteProduct.builder().siteId(siteId).productId(productId).forecastingEnabled(false).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(existing));
        SiteProductSettingsUpdate update = new SiteProductSettingsUpdate(null, null, null, null, null, null);

        SiteProduct result = siteProductService.updateSettings(siteId, productId, update);

        assertThat(result.getForecastingEnabled()).isFalse();
    }

    @Test
    void updateSettings_explicitNullOverrideFieldsClearThemToInherit() {
        stubSaveReturnsItsArgument();
        SiteProduct existing = SiteProduct.builder()
                .siteId(siteId).productId(productId).unitCost(new BigDecimal("1.00")).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(existing));
        SiteProductSettingsUpdate clearUnitCost = new SiteProductSettingsUpdate(null, null, null, null, null, null);

        SiteProduct result = siteProductService.updateSettings(siteId, productId, clearUnitCost);

        assertThat(result.getUnitCost()).isNull();
    }
}
