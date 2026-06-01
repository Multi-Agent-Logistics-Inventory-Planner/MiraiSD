package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.repositories.KujiBoxRepository;
import com.mirai.inventoryservice.repositories.KujiBoxTierRepository;
import com.mirai.inventoryservice.repositories.MachineDisplayRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.ShipmentItemRepository;
import com.mirai.inventoryservice.repositories.ShipmentRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.SupplierRepository;
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
 * forecast_predictions rows; no other transition should touch them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceForecastingToggleTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryService categoryService;
    @Mock private StockMovementService stockMovementService;
    @Mock private SupabaseBroadcastService broadcastService;
    @Mock private InventoryAggregateService inventoryAggregateService;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private ShipmentItemRepository shipmentItemRepository;
    @Mock private MachineDisplayRepository machineDisplayRepository;
    @Mock private ForecastPredictionRepository forecastPredictionRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private ShipmentRepository shipmentRepository;
    @Mock private KujiBoxRepository kujiBoxRepository;
    @Mock private KujiBoxTierRepository kujiBoxTierRepository;

    private ProductService service;
    private UUID productId;

    @BeforeEach
    void setUp() {
        service = new ProductService(
                productRepository,
                categoryService,
                stockMovementService,
                broadcastService,
                inventoryAggregateService,
                stockMovementRepository,
                shipmentItemRepository,
                machineDisplayRepository,
                forecastPredictionRepository,
                supplierRepository,
                shipmentRepository,
                kujiBoxRepository,
                kujiBoxTierRepository);
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
        verify(forecastPredictionRepository, times(1)).deleteByItemId(eq(productId));
    }

    @Test
    void falseToTrue_doesNotDeletePredictions() {
        stubFindForReturn(baseProduct(false));
        invokeUpdate(true);
        verify(forecastPredictionRepository, never()).deleteByItemId(any());
    }

    @Test
    void unchangedFalse_doesNotDelete() {
        stubFindForReturn(baseProduct(false));
        invokeUpdate(false);
        verify(forecastPredictionRepository, never()).deleteByItemId(any());
    }

    @Test
    void unchangedTrue_doesNotDelete() {
        stubFindForReturn(baseProduct(true));
        invokeUpdate(true);
        verify(forecastPredictionRepository, never()).deleteByItemId(any());
    }

    @Test
    void nullForecastingFlag_isANoOp() {
        stubFindForReturn(baseProduct(true));
        invokeUpdate(null);
        verify(forecastPredictionRepository, never()).deleteByItemId(any());
    }
}
