package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.Supplier;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors ShipmentService.autoAssignPreferredSupplier's existing eligibility rule exactly
 * (docs: .specs/phase-5b-catalog-facade/spec.md).
 */
@ExtendWith(MockitoExtension.class)
class CatalogCommandsTest {

    @Mock private ProductRepository productRepository;
    @Mock private SupplierRepository supplierRepository;

    private CatalogCommands catalogCommands;
    private UUID supplierId;
    private Supplier supplierRef;

    @BeforeEach
    void setUp() {
        catalogCommands = new CatalogCommands(productRepository, supplierRepository);
        supplierId = UUID.randomUUID();
        supplierRef = Supplier.builder().id(supplierId).displayName("Acme").build();
    }

    private Product product(UUID id, UUID currentSupplierId, Boolean auto) {
        Product p = Product.builder().id(id).name("Widget").preferredSupplierAuto(auto).build();
        // preferredSupplierId is a read-only shadow column (insertable/updatable = false);
        // set it directly since the entity has no setter that also assigns the FK.
        p.setPreferredSupplierId(currentSupplierId);
        return p;
    }

    @Test
    void emptyCandidates_isANoOp() {
        List<UUID> result = catalogCommands.assignPreferredSupplierFromDelivery(supplierId, List.of());

        assertThat(result).isEmpty();
        verify(productRepository, never()).saveAll(any());
    }

    @Test
    void noPriorSupplier_isAssigned() {
        UUID productId = UUID.randomUUID();
        when(supplierRepository.getReferenceById(supplierId)).thenReturn(supplierRef);
        when(productRepository.findAllById(List.of(productId)))
                .thenReturn(List.of(product(productId, null, null)));

        List<UUID> updated = catalogCommands.assignPreferredSupplierFromDelivery(supplierId, List.of(productId));

        assertThat(updated).containsExactly(productId);
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());
        Product saved = captor.getValue().get(0);
        assertThat(saved.getPreferredSupplier()).isEqualTo(supplierRef);
        assertThat(saved.getPreferredSupplierAuto()).isTrue();
    }

    @Test
    void priorAutoAssignment_isOverwritten() {
        UUID productId = UUID.randomUUID();
        when(supplierRepository.getReferenceById(supplierId)).thenReturn(supplierRef);
        when(productRepository.findAllById(List.of(productId)))
                .thenReturn(List.of(product(productId, UUID.randomUUID(), true)));

        List<UUID> updated = catalogCommands.assignPreferredSupplierFromDelivery(supplierId, List.of(productId));

        assertThat(updated).containsExactly(productId);
    }

    @Test
    void nullAutoFlag_treatedAsAutoAndOverwritten() {
        UUID productId = UUID.randomUUID();
        when(supplierRepository.getReferenceById(supplierId)).thenReturn(supplierRef);
        when(productRepository.findAllById(List.of(productId)))
                .thenReturn(List.of(product(productId, UUID.randomUUID(), null)));

        List<UUID> updated = catalogCommands.assignPreferredSupplierFromDelivery(supplierId, List.of(productId));

        assertThat(updated).containsExactly(productId);
    }

    @Test
    void explicitManualAssignment_isRespectedAndSkipped() {
        UUID productId = UUID.randomUUID();
        when(supplierRepository.getReferenceById(supplierId)).thenReturn(supplierRef);
        when(productRepository.findAllById(List.of(productId)))
                .thenReturn(List.of(product(productId, UUID.randomUUID(), false)));

        List<UUID> updated = catalogCommands.assignPreferredSupplierFromDelivery(supplierId, List.of(productId));

        assertThat(updated).isEmpty();
        verify(productRepository, never()).saveAll(any());
    }
}
