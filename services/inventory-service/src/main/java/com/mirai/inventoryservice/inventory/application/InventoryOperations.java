package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.models.enums.LocationType;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.sites.domain.Location;
import com.mirai.inventoryservice.sites.domain.Site;
import com.mirai.inventoryservice.services.EventOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application-facade write surface for {@code location_inventory}/{@code stock_movements}, for
 * production callers outside the {@code inventory} module (see .specs/phase-6-inventory/log.md
 * T-5). Preserves the exact find-or-create/adjust/record-movement/publish-outbox sequence the
 * migrated callers already implemented inline; this class does not change that behavior, only
 * where it lives.
 */
@Service
public class InventoryOperations {
    private final LocationInventoryRepository locationInventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final EventOutboxService eventOutboxService;
    private final StockMovementService stockMovementService;

    public InventoryOperations(
            LocationInventoryRepository locationInventoryRepository,
            StockMovementRepository stockMovementRepository,
            @org.springframework.context.annotation.Lazy EventOutboxService eventOutboxService,
            StockMovementService stockMovementService) {
        this.locationInventoryRepository = locationInventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.eventOutboxService = eventOutboxService;
        this.stockMovementService = stockMovementService;
    }

    /**
     * Result of {@link #adjustQuantity}: the affected inventory row's id (present even when the
     * row was deleted, since the id is read before deletion), and the quantity before/after.
     */
    public record InventoryQuantityChange(UUID inventoryId, int previousQuantity, int currentQuantity) {
    }

    /**
     * Find-or-create the {@link LocationInventory} row for (location, product), apply
     * {@code quantityDelta}, and save it — or delete it if the result is zero. Callers remain
     * responsible for validating the delta (e.g. rejecting a delta that would go negative) before
     * calling this, matching every existing inline caller: this method performs no validation of
     * its own so error messages/types stay exactly what each caller already throws.
     */
    @Transactional
    public InventoryQuantityChange adjustQuantity(Location location, Product product, int quantityDelta) {
        LocationInventory inventory = findOrCreateInventory(location, product);
        int previousQuantity = inventory.getQuantity();
        int currentQuantity = previousQuantity + quantityDelta;

        if (currentQuantity == 0) {
            UUID id = inventory.getId();
            if (id != null) {
                locationInventoryRepository.delete(inventory);
            }
            return new InventoryQuantityChange(id, previousQuantity, currentQuantity);
        }

        inventory.setQuantity(currentQuantity);
        LocationInventory saved = locationInventoryRepository.save(inventory);
        return new InventoryQuantityChange(saved.getId(), previousQuantity, currentQuantity);
    }

    /**
     * Find the existing {@link LocationInventory} row for (location, product), or build a new,
     * unsaved one at quantity zero. Mirrors the find-or-create pattern every migrated caller used
     * inline before T-5.
     */
    public LocationInventory findOrCreateInventory(Location location, Product product) {
        return locationInventoryRepository.findByLocation_IdAndProduct_Id(location.getId(), product.getId())
                .orElseGet(() -> LocationInventory.builder()
                        .location(location)
                        .site(location.getStorageLocation().getSite())
                        .product(product)
                        .quantity(0)
                        .build());
    }

    public Optional<LocationInventory> findInventory(UUID locationId, UUID productId) {
        return locationInventoryRepository.findByLocation_IdAndProduct_Id(locationId, productId);
    }

    /**
     * Site-scoped counterpart to {@link #findInventory(UUID, UUID)} (.specs/phase-6-inventory 6c,
     * T-6c-2, AC-1/AC-3): {@code (UUID siteId, ...)}-first, mirroring
     * {@code LocationService.getLocationById(siteId, id)}'s shape. Returns empty, not the
     * un-scoped row, when the inventory exists but belongs to a different site -- callers map
     * empty to 404, matching {@link InventoryQueries#findInventoryBySite}. Read-only; the
     * site-scoped write path (adjust/transfer with row locking and the same-site transfer
     * precondition) is T-6c-6/T-6c-12's job, not duplicated here ahead of the locking design.
     */
    public Optional<LocationInventory> findInventory(UUID siteId, UUID locationId, UUID productId) {
        return locationInventoryRepository.findByLocation_IdAndProduct_IdAndSite_Id(locationId, productId, siteId);
    }

    @Transactional
    public LocationInventory saveInventory(LocationInventory inventory) {
        return locationInventoryRepository.save(inventory);
    }

    @Transactional
    public void deleteInventory(LocationInventory inventory) {
        locationInventoryRepository.delete(inventory);
    }

    /**
     * Build, save and publish a {@link StockMovement}, then publish its outbox event —
     * the "record a ledger row" half of the sequence every migrated write caller used inline.
     * Use this directly for movements with no {@code location_inventory} change (e.g. a
     * KUJI ledger row); compose with {@link #adjustQuantity} for movements that also change
     * on-hand quantity.
     *
     * <p>{@code site} is required (.specs/phase-6-inventory 6b): every caller of this overload
     * already has a resolvable location (see {@link #applyDelta}) or a site from other context
     * (e.g. {@code ShipmentService}'s destination/source location), so it is supplied explicitly
     * rather than re-derived here from the raw location ids, which carry no FK to look up.
     */
    @Transactional
    public StockMovement recordMovement(
            com.mirai.inventoryservice.models.audit.AuditLog auditLog,
            Product product,
            LocationType locationType,
            UUID fromLocationId,
            UUID toLocationId,
            int previousQuantity,
            int currentQuantity,
            int quantityChange,
            StockMovementReason reason,
            UUID actorId,
            Map<String, Object> metadata,
            Site site) {
        StockMovement movement = StockMovement.builder()
                .auditLog(auditLog)
                .item(product)
                .locationType(locationType)
                .fromLocationId(fromLocationId)
                .toLocationId(toLocationId)
                .previousQuantity(previousQuantity)
                .currentQuantity(currentQuantity)
                .quantityChange(quantityChange)
                .reason(reason)
                .actorId(actorId)
                .at(OffsetDateTime.now())
                .metadata(metadata)
                .site(site)
                .build();
        return recordMovement(movement);
    }

    /**
     * Save an already-built {@link StockMovement} and publish its outbox event. For a caller
     * that already assembles the full {@code StockMovement.builder()} inline (e.g. KUJI ledger
     * rows with kuji-specific metadata), this avoids re-decomposing the movement into individual
     * fields just to hand them back to {@link #recordMovement(AuditLog, Product, LocationType,
     * UUID, UUID, int, int, int, StockMovementReason, UUID, Map, Site)}. Such callers must set
     * {@code .site(...)} on the builder themselves (.specs/phase-6-inventory 6b) — this method
     * does not infer one.
     */
    @Transactional
    public StockMovement recordMovement(StockMovement movement) {
        Objects.requireNonNull(movement.getSite(), "StockMovement.site must be set before recordMovement");
        StockMovement saved = stockMovementRepository.save(movement);
        eventOutboxService.createStockMovementEvent(saved);
        return saved;
    }

    /**
     * Composes {@link #adjustQuantity} and {@link #recordMovement} for the common single-location
     * add/remove shape (e.g. shipment receipt / receipt reversal): find-or-create the inventory
     * row, apply the delta, save-or-delete it, then record and publish the matching movement.
     * {@code fromLocationId}/{@code toLocationId} follow the sign of {@code quantityDelta} — a
     * positive delta sets only {@code toLocationId}, a negative delta sets only
     * {@code fromLocationId} — matching every migrated single-location caller exactly.
     */
    @Transactional
    public StockMovement applyDelta(
            Location location,
            Product product,
            int quantityDelta,
            LocationType locationType,
            com.mirai.inventoryservice.models.audit.AuditLog auditLog,
            StockMovementReason reason,
            UUID actorId,
            Map<String, Object> metadata) {
        InventoryQuantityChange change = adjustQuantity(location, product, quantityDelta);
        UUID fromLocationId = quantityDelta < 0 ? location.getId() : null;
        UUID toLocationId = quantityDelta > 0 ? location.getId() : null;
        return recordMovement(
                auditLog, product, locationType, fromLocationId, toLocationId,
                change.previousQuantity(), change.currentQuantity(), quantityDelta, reason, actorId, metadata,
                location.getStorageLocation().getSite());
    }

    /**
     * Save a single already-built {@link StockMovement} with no outbox publish and no
     * {@code location_inventory} change — the "ledger-only, no event" shape
     * {@code MachineDisplayService} uses for zero-quantity display-lifecycle rows. A pure
     * pass-through to preserve that existing no-outbox behavior exactly.
     */
    @Transactional
    public StockMovement saveMovement(StockMovement movement) {
        Objects.requireNonNull(movement.getSite(), "StockMovement.site must be set before saveMovement");
        return stockMovementRepository.save(movement);
    }

    /** Batch form of {@link #saveMovement}, same no-outbox behavior. */
    @Transactional
    public List<StockMovement> saveMovements(List<StockMovement> movements) {
        movements.forEach(movement -> Objects.requireNonNull(
                movement.getSite(), "StockMovement.site must be set before saveMovements"));
        return stockMovementRepository.saveAll(movements);
    }

    /**
     * Re-derive and persist the denormalized product quantity/active-status totals for the given
     * products, broadcasting a product-updated event for any that changed. Delegates to
     * {@link StockMovementService#syncProductTotals(List)} — external callers use this facade
     * method instead of depending on {@code StockMovementService} directly, per T-5's narrow
     * documented-facade contract (AC-1).
     */
    @Transactional
    public void syncProductTotals(List<UUID> productIds) {
        stockMovementService.syncProductTotals(productIds);
    }
}
