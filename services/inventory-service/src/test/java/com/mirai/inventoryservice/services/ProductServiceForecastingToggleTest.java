package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.catalog.application.CategoryService;
import com.mirai.inventoryservice.catalog.application.ForecastPurgePort;
import com.mirai.inventoryservice.catalog.application.InitialStockPort;
import com.mirai.inventoryservice.catalog.application.MainSiteResolver;
import com.mirai.inventoryservice.catalog.application.OpenKujiBoxPort;
import com.mirai.inventoryservice.catalog.application.ProductService;
import com.mirai.inventoryservice.catalog.application.SiteProductService;
import com.mirai.inventoryservice.catalog.application.SupplierDeliveryHistoryPort;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the delete-on-toggle behavior for the forecasting_enabled flag.
 * Only a true -> false transition should trigger purging of existing
 * forecast_predictions rows (via {@link ForecastPurgePort}); no other transition should
 * touch them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceForecastingToggleTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryService categoryService;
    @Mock private SupabaseBroadcastService broadcastService;
    @Mock private SupplierRepository supplierRepository;
    @Mock private InitialStockPort initialStockPort;
    @Mock private ForecastPurgePort forecastPurgePort;
    @Mock private SupplierDeliveryHistoryPort supplierDeliveryHistoryPort;
    @Mock private OpenKujiBoxPort openKujiBoxPort;
    @Mock private SiteProductService siteProductService;
    @Mock private MainSiteResolver mainSiteResolver;

    private ProductService service;
    private UUID productId;

    @BeforeEach
    void setUp() {
        service = new ProductService(
                productRepository,
                categoryService,
                broadcastService,
                supplierRepository,
                initialStockPort,
                forecastPurgePort,
                supplierDeliveryHistoryPort,
                openKujiBoxPort,
                siteProductService,
                mainSiteResolver);
        productId = UUID.randomUUID();
    }

    private Product baseProduct(Boolean forecastingEnabled) {
        return Product.builder()
                .id(productId)
                .name("Test")
                .forecastingEnabled(forecastingEnabled)
                .isActive(true)
                .build();
    }

    private void stubFindForReturn(Product product) {
        when(productRepository.findByIdWithCategories(productId)).thenReturn(Optional.of(product));
    }

    private void invokeUpdate(Boolean newForecastingEnabled) {
        service.updateProduct(
                productId,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                newForecastingEnabled);
    }

    @Test
    void trueToFalse_deletesExistingPredictions() {
        stubFindForReturn(baseProduct(true));
        invokeUpdate(false);
        verify(forecastPurgePort, times(1)).purgeForecastsForProduct(eq(productId));
    }

    @Test
    void falseToTrue_doesNotDeletePredictions() {
        stubFindForReturn(baseProduct(false));
        invokeUpdate(true);
        verify(forecastPurgePort, never()).purgeForecastsForProduct(any());
    }

    @Test
    void unchangedFalse_doesNotDelete() {
        stubFindForReturn(baseProduct(false));
        invokeUpdate(false);
        verify(forecastPurgePort, never()).purgeForecastsForProduct(any());
    }

    @Test
    void unchangedTrue_doesNotDelete() {
        stubFindForReturn(baseProduct(true));
        invokeUpdate(true);
        verify(forecastPurgePort, never()).purgeForecastsForProduct(any());
    }

    @Test
    void nullForecastingFlag_isANoOp() {
        stubFindForReturn(baseProduct(true));
        invokeUpdate(null);
        verify(forecastPurgePort, never()).purgeForecastsForProduct(any());
    }
}
