package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.catalog.domain.SiteProductNotFoundException;
import com.mirai.inventoryservice.catalog.domain.SiteProductVersionConflictException;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SiteProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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
    @Mock private MainSiteResolver mainSiteResolver;
    @Mock private ForecastPurgePort forecastPurgePort;
    @Mock private SiteProductVersionReader siteProductVersionReader;

    private SiteProductService siteProductService;
    private UUID siteId;
    private UUID productId;
    private Product product;

    @BeforeEach
    void setUp() {
        siteProductService = new SiteProductService(
                siteProductRepository, productRepository, mainSiteResolver, forecastPurgePort,
                siteProductVersionReader);
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
        SiteProductSettingsUpdate update = settingsUpdate(0L, FieldUpdate.of(new BigDecimal("5.00")));

        assertThatThrownBy(() -> siteProductService.updateSettings(siteId, productId, update))
                .isInstanceOf(SiteProductNotFoundException.class);
        verify(siteProductRepository, never()).save(any());
        verify(siteProductRepository, never()).saveAndFlush(any());
    }

    /** AC-4c: a retained de-assorted row (isStocked = false) still satisfies row-exists and stays editable. */
    @Test
    void updateSettings_onARetainedDeAssortedRowIsAccepted() {
        stubSaveAndFlushReturnsItsArgument();
        SiteProduct deAssorted = SiteProduct.builder()
                .siteId(siteId).productId(productId).isStocked(false).version(0L).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(deAssorted));
        SiteProductSettingsUpdate update = new SiteProductSettingsUpdate(
                0L, FieldUpdate.omitted(), FieldUpdate.of(new BigDecimal("3.50")), FieldUpdate.omitted(),
                FieldUpdate.of(20), FieldUpdate.omitted(), FieldUpdate.omitted());

        SiteProduct result = siteProductService.updateSettings(siteId, productId, update);

        assertThat(result.getIsStocked()).isFalse();
        assertThat(result.getUnitCost()).isEqualByComparingTo("3.50");
        assertThat(result.getReorderPoint()).isEqualTo(20);
    }

    /** AC-6: an omitted forecastingEnabled leaves the row's current value unchanged. */
    @Test
    void updateSettings_omittedForecastingEnabledLeavesTheCurrentValueUnchanged() {
        stubSaveAndFlushReturnsItsArgument();
        SiteProduct existing = SiteProduct.builder()
                .siteId(siteId).productId(productId).forecastingEnabled(false).version(0L).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(existing));
        SiteProductSettingsUpdate update = emptySettingsUpdate(0L);

        SiteProduct result = siteProductService.updateSettings(siteId, productId, update);

        assertThat(result.getForecastingEnabled()).isFalse();
    }

    /**
     * AC-6: forecastingEnabled cannot be cleared (the column is NOT NULL) - an explicit
     * {@code FieldUpdate.of(null)} must behave exactly like omitted, leaving the current value.
     */
    @Test
    void updateSettings_explicitNullForecastingEnabledAlsoLeavesTheCurrentValueUnchanged() {
        stubSaveAndFlushReturnsItsArgument();
        SiteProduct existing = SiteProduct.builder()
                .siteId(siteId).productId(productId).forecastingEnabled(false).version(0L).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(existing));
        SiteProductSettingsUpdate update = new SiteProductSettingsUpdate(
                0L, FieldUpdate.of(null), FieldUpdate.omitted(), FieldUpdate.omitted(),
                FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted());

        SiteProduct result = siteProductService.updateSettings(siteId, productId, update);

        assertThat(result.getForecastingEnabled()).isFalse();
    }

    /** AC-6: an explicit null override field clears it to inherit from the global product. */
    @Test
    void updateSettings_explicitNullOverrideFieldClearsItToInherit() {
        stubSaveAndFlushReturnsItsArgument();
        SiteProduct existing = SiteProduct.builder()
                .siteId(siteId).productId(productId).unitCost(new BigDecimal("1.00")).version(0L).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(existing));
        SiteProductSettingsUpdate clearUnitCost = new SiteProductSettingsUpdate(
                0L, FieldUpdate.omitted(), FieldUpdate.of(null), FieldUpdate.omitted(),
                FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted());

        SiteProduct result = siteProductService.updateSettings(siteId, productId, clearUnitCost);

        assertThat(result.getUnitCost()).isNull();
    }

    /** AC-6: an omitted override field leaves the stored value untouched - distinct from an explicit clear. */
    @Test
    void updateSettings_omittedOverrideFieldLeavesTheStoredValueUntouched() {
        stubSaveAndFlushReturnsItsArgument();
        SiteProduct existing = SiteProduct.builder()
                .siteId(siteId).productId(productId).unitCost(new BigDecimal("1.00")).version(0L).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(existing));

        SiteProduct result = siteProductService.updateSettings(siteId, productId, emptySettingsUpdate(0L));

        assertThat(result.getUnitCost()).isEqualByComparingTo("1.00");
    }

    /** AC-6: a missing (null) version is rejected exactly like a stale one - never last-write-wins. */
    @Test
    void updateSettings_throwsWhenVersionIsMissing() {
        SiteProduct existing = SiteProduct.builder().siteId(siteId).productId(productId).version(3L).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(existing));
        SiteProductSettingsUpdate update = emptySettingsUpdate(null);

        assertThatThrownBy(() -> siteProductService.updateSettings(siteId, productId, update))
                .isInstanceOf(SiteProductVersionConflictException.class)
                .hasMessageContaining("current version is 3");
        verify(siteProductRepository, never()).save(any());
        verify(siteProductRepository, never()).saveAndFlush(any());
    }

    /** AC-6: a stale version is rejected with a message naming the row's current version. */
    @Test
    void updateSettings_throwsWhenVersionIsStale() {
        SiteProduct existing = SiteProduct.builder().siteId(siteId).productId(productId).version(3L).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(existing));
        SiteProductSettingsUpdate update = emptySettingsUpdate(2L);

        assertThatThrownBy(() -> siteProductService.updateSettings(siteId, productId, update))
                .isInstanceOf(SiteProductVersionConflictException.class)
                .hasMessageContaining("expected version 2")
                .hasMessageContaining("current version is 3");
        verify(siteProductRepository, never()).save(any());
        verify(siteProductRepository, never()).saveAndFlush(any());
    }

    /**
     * AC-6: a genuine concurrent race - the explicit pre-check passed, but a competing write won
     * the flush first - must still surface as a version conflict, and the winner's current
     * version must come from a fresh persistence context (the failed flush's context is unusable
     * for further queries), not from the entity or repository used in the failed attempt.
     */
    @Test
    void updateSettings_translatesAConcurrentFlushFailureIntoAVersionConflictUsingAFreshRead() {
        SiteProduct existing = SiteProduct.builder().siteId(siteId).productId(productId).version(3L).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(existing));
        when(siteProductRepository.saveAndFlush(any(SiteProduct.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(SiteProduct.class, productId));
        when(siteProductVersionReader.currentVersion(siteId, productId)).thenReturn(4L);
        SiteProductSettingsUpdate update = settingsUpdate(3L, FieldUpdate.of(new BigDecimal("1.00")));

        assertThatThrownBy(() -> siteProductService.updateSettings(siteId, productId, update))
                .isInstanceOf(SiteProductVersionConflictException.class)
                .hasMessageContaining("expected version 3")
                .hasMessageContaining("current version is 4");
        verify(siteProductVersionReader).currentVersion(siteId, productId);
    }

    /** AC-6: a matching version is accepted and the write proceeds. */
    @Test
    void updateSettings_acceptsWhenVersionMatches() {
        stubSaveAndFlushReturnsItsArgument();
        SiteProduct existing = SiteProduct.builder()
                .siteId(siteId).productId(productId).version(3L).build();
        when(siteProductRepository.findBySiteIdAndProductId(siteId, productId)).thenReturn(Optional.of(existing));

        SiteProduct result = siteProductService.updateSettings(siteId, productId,
                settingsUpdate(3L, FieldUpdate.of(new BigDecimal("9.00"))));

        assertThat(result.getUnitCost()).isEqualByComparingTo("9.00");
    }

    private void stubSaveAndFlushReturnsItsArgument() {
        when(siteProductRepository.saveAndFlush(any(SiteProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static SiteProductSettingsUpdate emptySettingsUpdate(Long expectedVersion) {
        return new SiteProductSettingsUpdate(
                expectedVersion, FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted(),
                FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted());
    }

    private static SiteProductSettingsUpdate settingsUpdate(Long expectedVersion, FieldUpdate<BigDecimal> unitCost) {
        return new SiteProductSettingsUpdate(
                expectedVersion, FieldUpdate.omitted(), unitCost, FieldUpdate.omitted(),
                FieldUpdate.omitted(), FieldUpdate.omitted(), FieldUpdate.omitted());
    }
}
