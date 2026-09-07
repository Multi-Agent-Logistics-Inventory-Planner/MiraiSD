package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AC-2b: every write here preserves the exact current dirty-check — a no-op write is skipped,
 * matching StockMovementService.updateProductActiveStatus / applyProductActiveStatusFromTotals
 * and KujiBoxService's isActive-only flips byte-for-byte.
 */
@ExtendWith(MockitoExtension.class)
class ProductStockStateWriterTest {

    @Mock private ProductRepository productRepository;

    private ProductStockStateWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ProductStockStateWriter(productRepository);
    }

    private Product product(UUID id, int quantity, boolean isActive) {
        return Product.builder().id(id).name("Widget").quantity(quantity).isActive(isActive).build();
    }

    @Test
    void applyStockState_missing_throws() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.applyStockState(id, 5, true))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void applyStockState_changed_savesAndReturnsTrue() {
        UUID id = UUID.randomUUID();
        Product p = product(id, 0, false);
        when(productRepository.findById(id)).thenReturn(Optional.of(p));

        boolean changed = writer.applyStockState(id, 5, true);

        assertThat(changed).isTrue();
        assertThat(p.getQuantity()).isEqualTo(5);
        assertThat(p.getIsActive()).isTrue();
        verify(productRepository, times(1)).save(p);
    }

    @Test
    void applyStockState_unchanged_isNoOp() {
        UUID id = UUID.randomUUID();
        Product p = product(id, 5, true);
        when(productRepository.findById(id)).thenReturn(Optional.of(p));

        boolean changed = writer.applyStockState(id, 5, true);

        assertThat(changed).isFalse();
        verify(productRepository, never()).save(any());
    }

    @Test
    void applyStockStateBatch_empty_isNoOp() {
        assertThat(writer.applyStockStateBatch(Map.of())).isEmpty();
        verify(productRepository, never()).saveAll(any());
    }

    @Test
    void applyStockStateBatch_onlyChangedRowsAreSavedAndReturned() {
        UUID changedId = UUID.randomUUID();
        UUID unchangedId = UUID.randomUUID();
        Product changed = product(changedId, 0, false);
        Product unchanged = product(unchangedId, 3, true);
        when(productRepository.findAllById(Map.of(
                changedId, new ProductStockStateWriter.StockState(10, true),
                unchangedId, new ProductStockStateWriter.StockState(3, true)).keySet()))
                .thenReturn(List.of(changed, unchanged));

        List<UUID> result = writer.applyStockStateBatch(Map.of(
                changedId, new ProductStockStateWriter.StockState(10, true),
                unchangedId, new ProductStockStateWriter.StockState(3, true)));

        assertThat(result).containsExactly(changedId);
        assertThat(changed.getQuantity()).isEqualTo(10);
        assertThat(unchanged.getQuantity()).isEqualTo(3);
        verify(productRepository, times(1)).saveAll(List.of(changed));
    }

    @Test
    void setActive_missing_throws() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.setActive(id, true)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void setActive_changed_savesTheFlip() {
        UUID id = UUID.randomUUID();
        Product p = product(id, 0, false);
        when(productRepository.findById(id)).thenReturn(Optional.of(p));

        writer.setActive(id, true);

        assertThat(p.getIsActive()).isTrue();
        verify(productRepository, times(1)).save(p);
    }

    @Test
    void setActive_unchanged_isNoOp() {
        UUID id = UUID.randomUUID();
        Product p = product(id, 0, true);
        when(productRepository.findById(id)).thenReturn(Optional.of(p));

        writer.setActive(id, true);

        verify(productRepository, never()).save(any());
    }
}
