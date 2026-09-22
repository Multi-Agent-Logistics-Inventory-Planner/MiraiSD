package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.catalog.application.CategoryService;
import com.mirai.inventoryservice.catalog.application.ForecastPurgePort;
import com.mirai.inventoryservice.catalog.application.InitialStockPort;
import com.mirai.inventoryservice.catalog.application.MainSiteResolver;
import com.mirai.inventoryservice.catalog.application.OpenKujiBoxPort;
import com.mirai.inventoryservice.catalog.application.ProductListItemDTO;
import com.mirai.inventoryservice.catalog.application.ProductService;
import com.mirai.inventoryservice.catalog.application.SiteProductService;
import com.mirai.inventoryservice.catalog.application.SupplierDeliveryHistoryPort;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regression for a review finding on .specs/catalog-product-egress: consolidating every
 * useProducts caller onto getAllProductsAsListItems() (web-product-list-cache W1) means the
 * rootOnly/kujiOnly web callers that used to route through getRootProductsAsListItems() /
 * getRootKujiProductsAsListItems() -- both of which call withHasActiveBox -- now route through
 * getAllProductsAsListItems() instead. That method never called withHasActiveBox, so
 * hasActiveBox silently went from a real value to always-null/false for every one of those
 * callers, including CustomKujiTabs (apps/web/src/components/kuji/custom-kuji-tabs.tsx), which
 * partitions products into Active/Closed tabs purely on that field -- every product would render
 * as Closed regardless of its actual box status.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceListItemsTest {

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
    }

    private ProductListItemDTO listItem(UUID id) {
        ProductListItemDTO dto = new ProductListItemDTO();
        dto.setId(id);
        dto.setName("Test Product " + id);
        return dto;
    }

    @Test
    void getAllProductsAsListItems_marksProductsWithAnOpenBoxAsHasActiveBox() {
        UUID openBoxProductId = UUID.randomUUID();
        UUID closedBoxProductId = UUID.randomUUID();
        when(productRepository.findAllAsListItems())
                .thenReturn(List.of(listItem(openBoxProductId), listItem(closedBoxProductId)));
        when(openKujiBoxPort.findProductIdsWithOpenBox()).thenReturn(Set.of(openBoxProductId));

        List<ProductListItemDTO> result = service.getAllProductsAsListItems();

        assertThat(result).extracting(ProductListItemDTO::getId, ProductListItemDTO::getHasActiveBox)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(openBoxProductId, true),
                        org.assertj.core.groups.Tuple.tuple(closedBoxProductId, false));
    }
}
