package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.Supplier;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.catalog.infrastructure.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Narrow, permanent catalog writes exposed to other modules — everything else a caller might
 * want to change on a {@code Product} goes through {@code catalog}'s own controllers/services,
 * not through this facade (docs: .specs/phase-5b-catalog-facade/spec.md, Product decisions).
 *
 * <p>Joins the caller's existing transaction — {@code Propagation.MANDATORY}, not the default
 * {@code REQUIRED}: this write must be atomic with whatever the caller is doing (e.g. the
 * shipment-delivery flow it is called from), so it must fail loudly if invoked with no
 * transaction in progress rather than silently opening an unrelated one of its own.
 */
@Service
public class CatalogCommands {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    public CatalogCommands(ProductRepository productRepository, SupplierRepository supplierRepository) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
    }

    /**
     * Auto-assigns {@code supplierId} as the preferred supplier for every product in
     * {@code candidateProductIds} that is eligible — mirrors {@code ShipmentService
     * .autoAssignPreferredSupplier}'s existing eligibility rule exactly: only updates a product
     * with no preferred supplier yet, or one whose current assignment isn't explicitly manual
     * ({@code preferredSupplierAuto != false}, treating {@code null} as "auto" for backward
     * compatibility with existing data). Batches the write in one {@code saveAll}, matching the
     * caller's existing batching. Returns the ids actually updated.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> assignPreferredSupplierFromDelivery(UUID supplierId, Collection<UUID> candidateProductIds) {
        if (candidateProductIds == null || candidateProductIds.isEmpty()) {
            return List.of();
        }

        Supplier supplierRef = supplierRepository.getReferenceById(supplierId);

        List<Product> toUpdate = new ArrayList<>();
        for (Product product : productRepository.findAllById(candidateProductIds)) {
            boolean eligible = product.getPreferredSupplierId() == null
                    || !Boolean.FALSE.equals(product.getPreferredSupplierAuto());
            if (eligible) {
                product.setPreferredSupplier(supplierRef);
                product.setPreferredSupplierAuto(true);
                toUpdate.add(product);
            }
        }

        if (!toUpdate.isEmpty()) {
            productRepository.saveAll(toUpdate);
        }
        return toUpdate.stream().map(Product::getId).toList();
    }
}
