package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogPricingTest {

    @Mock private ProductRepository productRepository;

    private CatalogPricing catalogPricing;

    @BeforeEach
    void setUp() {
        catalogPricing = new CatalogPricing(productRepository);
    }

    private Product product(UUID id, BigDecimal unitCost, BigDecimal msrp) {
        return Product.builder().id(id).name("Widget").unitCost(unitCost).msrp(msrp).build();
    }

    @Test
    void findPricing_returnsCostAndMsrp() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id))
                .thenReturn(Optional.of(product(id, BigDecimal.TEN, BigDecimal.valueOf(20))));

        Optional<ProductPricing> pricing = catalogPricing.findPricing(id);

        assertThat(pricing).isPresent();
        assertThat(pricing.get().unitCost()).isEqualTo(BigDecimal.TEN);
        assertThat(pricing.get().msrp()).isEqualTo(BigDecimal.valueOf(20));
    }

    @Test
    void getPricing_missing_throws() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogPricing.getPricing(id))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void findPricingForIds_batchMapsEveryResult() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(productRepository.findAllById(List.of(id1, id2)))
                .thenReturn(List.of(
                        product(id1, BigDecimal.ONE, BigDecimal.TWO),
                        product(id2, BigDecimal.TWO, BigDecimal.TEN)));

        assertThat(catalogPricing.findPricingForIds(List.of(id1, id2)))
                .extracting(ProductPricing::productId)
                .containsExactlyInAnyOrder(id1, id2);
    }
}
