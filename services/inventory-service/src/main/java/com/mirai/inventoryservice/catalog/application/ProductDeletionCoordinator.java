package com.mirai.inventoryservice.catalog.application;

import com.mirai.inventoryservice.catalog.domain.ProductInUseException;
import com.mirai.inventoryservice.catalog.domain.ProductNotFoundException;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.infrastructure.ProductRepository;
import com.mirai.inventoryservice.services.SupabaseBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates deleting a product: a catalog use case (product identity, hierarchy, and the
 * transaction itself are catalog's own concerns), whose foreign-module cleanup is expressed
 * entirely through catalog-declared ports ({@link ShipmentUsageGuardPort}, {@link
 * ForecastPurgePort}, {@link MachineDisplayCleanupPort}, {@link InventoryCleanupPort}, {@link
 * KujiBoxCleanupPort}) implemented in each owning module's {@code application} package. Docs:
 * .specs/phase-5a-catalog-module-move/spec.md AC-2.
 *
 * <p>Kept as a separate class from {@code ProductService} rather than folded into it, so a
 * product read/write workflow does not have to inject five cleanup ports it never uses.
 * Preserves the exact validation order, deletion order, and after-commit broadcast timing of the
 * former {@code ProductService.deleteProduct} — no behavior change, only where the cross-module
 * calls are expressed.
 */
@Service
public class ProductDeletionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ProductDeletionCoordinator.class);

    private final ProductRepository productRepository;
    private final SupabaseBroadcastService broadcastService;
    private final ShipmentUsageGuardPort shipmentUsageGuardPort;
    private final ForecastPurgePort forecastPurgePort;
    private final MachineDisplayCleanupPort machineDisplayCleanupPort;
    private final InventoryCleanupPort inventoryCleanupPort;
    private final KujiBoxCleanupPort kujiBoxCleanupPort;

    public ProductDeletionCoordinator(
            ProductRepository productRepository,
            SupabaseBroadcastService broadcastService,
            ShipmentUsageGuardPort shipmentUsageGuardPort,
            ForecastPurgePort forecastPurgePort,
            MachineDisplayCleanupPort machineDisplayCleanupPort,
            InventoryCleanupPort inventoryCleanupPort,
            KujiBoxCleanupPort kujiBoxCleanupPort) {
        this.productRepository = productRepository;
        this.broadcastService = broadcastService;
        this.shipmentUsageGuardPort = shipmentUsageGuardPort;
        this.forecastPurgePort = forecastPurgePort;
        this.machineDisplayCleanupPort = machineDisplayCleanupPort;
        this.inventoryCleanupPort = inventoryCleanupPort;
        this.kujiBoxCleanupPort = kujiBoxCleanupPort;
    }

    @Transactional
    public void deleteProduct(UUID id) {
        log.info("[DELETE] Starting deleteProduct for id={}", id);
        Product product = productRepository.findByIdWithCategories(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
        log.info("[DELETE] Found product: name='{}', parentId={}", product.getName(), product.getParentId());

        // Block delete if this product is used in any shipment (FK constraint would fail otherwise)
        boolean usedInShipment = shipmentUsageGuardPort.isUsedInShipment(id);
        log.info("[DELETE] Product used in shipment: {}", usedInShipment);
        if (usedInShipment) {
            log.warn("[DELETE] Cannot delete - product is used in shipments");
            throw new ProductInUseException(
                    "Cannot delete: this product is used in one or more shipments. Remove it from those shipments first.");
        }

        // Collect all product IDs to broadcast after commit
        List<String> deletedProductIds = new java.util.ArrayList<>();
        deletedProductIds.add(id.toString());

        // If parent has children, delete children first (cascade), then parent
        long childCount = productRepository.countChildrenByParentId(id);
        log.info("[DELETE] Child count: {}", childCount);
        if (childCount > 0) {
            List<Product> children = productRepository.findByParentIdWithCategories(id);
            // Validate all children are deletable first
            for (Product child : children) {
                if (shipmentUsageGuardPort.isUsedInShipment(child.getId())) {
                    throw new ProductInUseException(
                            "Cannot delete: prize \"" + child.getName() + "\" is used in one or more shipments. Remove it from those shipments first.");
                }
            }
            // Collect child IDs for batch operations (N+1 optimization)
            Set<UUID> childIds = new HashSet<>();
            for (Product child : children) {
                childIds.add(child.getId());
                deletedProductIds.add(child.getId().toString());
            }
            log.info("[DELETE] Batch deleting dependencies for {} children", childIds.size());
            // Batch delete all child dependencies in single queries instead of N queries.
            // Children can also be linked_product references on kuji_box_tiers — that FK is
            // ON DELETE SET NULL, so no service-level action is required for it.
            forecastPurgePort.purgeForecastsForProducts(childIds);
            machineDisplayCleanupPort.deleteDisplaysForProducts(childIds);
            inventoryCleanupPort.deleteInventoryForProducts(childIds);
            inventoryCleanupPort.deleteStockMovementsForProducts(childIds);
            productRepository.deleteAll(children);
        }

        // Delete kuji boxes that reference this product as the parent kuji.
        log.info("[DELETE] Deleting kuji box tiers and boxes for product id={}", id);
        KujiBoxCleanupPort.Result kujiCleanup = kujiBoxCleanupPort.deleteBoxesAndTiersForProduct(id);
        if (kujiCleanup.deletedBoxes() > 0 || kujiCleanup.deletedTiers() > 0) {
            log.info("[DELETE] Removed {} kuji box(es) and {} tier(s) referencing product {}",
                    kujiCleanup.deletedBoxes(), kujiCleanup.deletedTiers(), id);
        }

        // Delete forecast predictions, machine displays, then parent's (or single product's) inventory and stock movements before deleting the product
        log.info("[DELETE] Deleting forecast predictions for product id={}", id);
        forecastPurgePort.purgeForecastsForProduct(id);
        log.info("[DELETE] Deleting machine displays for product id={}", id);
        machineDisplayCleanupPort.deleteDisplaysForProduct(id);
        log.info("[DELETE] Deleting inventory for product id={}", id);
        inventoryCleanupPort.deleteInventoryForProduct(id);
        log.info("[DELETE] Deleting stock movements for product id={}", id);
        inventoryCleanupPort.deleteStockMovementsForProduct(id);

        // If this is a child product, remove it from parent's children collection
        // to prevent CascadeType.PERSIST from re-saving it
        if (product.getParent() != null) {
            log.info("[DELETE] Removing child from parent's children collection");
            product.getParent().getChildren().remove(product);
            product.setParent(null);
        }

        log.info("[DELETE] Deleting product entity id={}", id);
        productRepository.delete(product);
        log.info("[DELETE] Product entity deleted, registering afterCommit callback");

        // Defer broadcast until after transaction commits to avoid race condition
        // where clients refetch before the delete is committed
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("[DELETE] Transaction committed, broadcasting product_updated for ids={}", deletedProductIds);
                broadcastService.broadcastProductUpdated(deletedProductIds);
            }
        });
        log.info("[DELETE] deleteProduct method complete, waiting for transaction commit");
    }
}
