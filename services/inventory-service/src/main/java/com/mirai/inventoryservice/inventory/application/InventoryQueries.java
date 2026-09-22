package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.inventory.api.InventoryTotalDTO;
import com.mirai.inventoryservice.inventory.api.SiteInventoryTotalDTO;
import com.mirai.inventoryservice.inventory.domain.InventoryNotFoundException;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.InventoryTotalsRepository;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementSpecifications;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application-facade read surface for {@code location_inventory}/{@code stock_movements}, for
 * production callers outside the {@code inventory} module (see .specs/phase-6-inventory/log.md
 * T-5). Every method is a direct pass-through to the same repository query the migrated caller
 * already ran — no query behavior changes, only where the call is made from.
 */
@Service
public class InventoryQueries {
    private final LocationInventoryRepository locationInventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryTotalsRepository inventoryTotalsRepository;

    public InventoryQueries(
            LocationInventoryRepository locationInventoryRepository,
            StockMovementRepository stockMovementRepository,
            InventoryTotalsRepository inventoryTotalsRepository) {
        this.locationInventoryRepository = locationInventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.inventoryTotalsRepository = inventoryTotalsRepository;
    }

    public Integer sumQuantityByProductId(UUID productId) {
        return locationInventoryRepository.sumQuantityByProductId(productId);
    }

    public List<LocationInventory> findByProductId(UUID productId) {
        return locationInventoryRepository.findByProduct_Id(productId);
    }

    /** Stock totals for every product, keyed by product id, in one query. */
    public Map<UUID, Integer> findAllStockTotalsMap() {
        return inventoryTotalsRepository.findAllStockTotalsMap();
    }

    /**
     * Aggregated inventory totals (one row per product) for the {@code InventoryAggregateController
     * .getInventoryTotals} endpoint (T-6: retires that controller's direct repository access, one
     * of {@code ArchitectureTest}'s frozen {@code repositoriesAreOnlyAccessedByServicesOrRepositories}
     * violations).
     */
    public List<InventoryTotalDTO> findAllInventoryTotals() {
        return inventoryTotalsRepository.findAllInventoryTotals();
    }

    public List<StockMovement> findByAuditLogIdWithItem(UUID auditLogId) {
        return stockMovementRepository.findByAuditLogIdWithItem(auditLogId);
    }

    /**
     * Product Assistant drill-down history: a slim projection over movements for one product in
     * a time window, optionally filtered by reason. Maps the infrastructure-layer
     * {@code StockMovementHistoryView} projection to the application-owned
     * {@link StockMovementHistoryEntry} so callers outside {@code inventory} never hold an
     * infrastructure-package type.
     */
    public List<StockMovementHistoryEntry> findHistoryByItemId(
            UUID productId, OffsetDateTime from, OffsetDateTime to,
            List<StockMovementReason> reasons, Pageable pageable) {
        return stockMovementRepository.findHistoryByItemId(productId, from, to, reasons, pageable)
                .stream()
                .map(StockMovementHistoryEntry::from)
                .toList();
    }

    /** Sales-rollup aggregation: units/revenue/cost/profit per item per day over a window. */
    public List<Object[]> aggregateSalesByItemAndDate(OffsetDateTime startDate, OffsetDateTime endDate) {
        return stockMovementRepository.aggregateSalesByItemAndDate(startDate, endDate);
    }

    /**
     * KUJI daily payout aggregation for one box, bucketed per calendar day in {@code tz}. Kept on
     * this general-purpose read facade rather than a kuji-specific one — see .specs/
     * phase-6-inventory/log.md R-5: the query's kuji-flavored shape living inside inventory's own
     * repository is accepted transitional debt, not something T-5 resolves.
     */
    public List<Object[]> aggregateKujiDailyPayouts(
            UUID boxId, java.time.LocalDate fromDate, java.time.LocalDate toDate, String tz) {
        return stockMovementRepository.aggregateKujiDailyPayouts(boxId, fromDate, toDate, tz);
    }

    // --- Site-scoped overloads (.specs/phase-6-inventory 6c, T-6c-2, AC-1/AC-3) ---
    // (UUID siteId, ...)-first, mirroring LocationService.getLocationById(siteId, id). A
    // foreign-site id raises InventoryNotFoundException (-> 404 per multi-site-data-and-api.md:
    // 63-64), not an authorization error -- the site boundary itself is enforced by the trusted
    // AuthorizedSiteContext at the controller, not here. Existing global methods above are
    // unchanged so no 6a caller is disturbed (P-5: these exist before any controller uses them).

    /** Site-scoped inventory-row lookup by id. 404s (via the thrown exception) if the row exists but belongs to a different site. */
    public LocationInventory findInventoryBySite(UUID siteId, UUID inventoryId) {
        return locationInventoryRepository.findByIdAndSite_Id(inventoryId, siteId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found with id: " + inventoryId));
    }

    /** Site-scoped inventory row for one (location, product) pair. */
    public LocationInventory findInventoryBySite(UUID siteId, UUID locationId, UUID productId) {
        return locationInventoryRepository.findByLocation_IdAndProduct_IdAndSite_Id(locationId, productId, siteId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found for location " + locationId + " and product " + productId));
    }

    /**
     * Site-scoped, displayable-only inventory rows at one location (same child/CUSTOM-kuji
     * exclusion filter as {@code LocationInventoryRepository.findByLocation_Id}).
     */
    public List<LocationInventory> findByLocationIdAndSite(UUID siteId, UUID locationId) {
        return locationInventoryRepository.findByLocation_IdAndSite_Id(locationId, siteId);
    }

    /**
     * Batch inventory-row lookup by id, scoped to one site. A foreign-site id is silently omitted
     * from the result (matches {@code findAllByIdInAndSite_IdWithGraph}'s repository contract) --
     * callers that need "all requested ids resolved" must check the returned size against the
     * requested id count themselves (T-6c-6's lock query does this).
     */
    public List<LocationInventory> findAllByIdInAndSite(UUID siteId, Collection<UUID> ids) {
        return locationInventoryRepository.findAllByIdInAndSite_IdWithGraph(ids, siteId);
    }

    /** Site-scoped counterpart to {@link #sumQuantityByProductId}. */
    public Integer sumQuantityByProductIdAndSite(UUID siteId, UUID productId) {
        return locationInventoryRepository.sumQuantityByProductIdAndSiteId(productId, siteId);
    }

    /**
     * Site-scoped counterpart to summing quantities for a batch of products (see
     * {@code LocationInventoryRepository.sumQuantitiesByProductIdsAndSiteId}), returned as
     * [productId, totalQuantity] rows. Missing products mean total = 0, matching the un-scoped
     * batch sum's contract (F-6c-3's zero-stock hazard: absence is zero, not "not found").
     */
    public List<Object[]> sumQuantitiesByProductIdsAndSite(UUID siteId, Collection<UUID> productIds) {
        return locationInventoryRepository.sumQuantitiesByProductIdsAndSiteId(productIds, siteId);
    }

    /**
     * Site-scoped, paginated per-product movement history. Per Q-6c-5, rows whose {@code site}
     * is still null (pre-backfill compatibility window) are included, not hidden -- same
     * "include and label" contract {@link #findAuditLogPageBySite} already gives the audit-log
     * branch of the same v1 endpoint.
     */
    public Page<StockMovement> findMovementHistoryBySite(UUID siteId, UUID productId, Pageable pageable) {
        return stockMovementRepository.findByItem_IdAndSiteOrUnknownOrderByAtDesc(productId, siteId, pageable);
    }

    /**
     * Site-scoped audit-log page: composes {@code filters} with a mandatory site predicate via
     * {@link StockMovementSpecifications#withSiteFilter}. Per Q-6c-5, rows whose {@code site} is
     * still null (pre-backfill compatibility window) are included, not hidden -- callers must
     * label them, not filter them.
     */
    public Page<StockMovement> findAuditLogPageBySite(UUID siteId, AuditLogFilterDTO filters, Pageable pageable) {
        return stockMovementRepository.findAll(
                StockMovementSpecifications.withSiteFilter(filters, siteId), pageable);
    }

    /**
     * Slim, site-scoped totals for the entire catalog (.specs/phase-6-inventory 6c, T-6c-5,
     * AC-5). One row per catalog product; a zero-stock product at this site still appears with
     * {@code totalQuantity = 0} (see {@link InventoryTotalsRepository
     * #findAllInventoryTotalsBySite}'s row-per-product contract). {@code INVENTORY_TOTALS_SQL}
     * (behind {@link #findAllInventoryTotals()}) is unaffected -- this is a new, additive path.
     */
    public List<SiteInventoryTotalDTO> findInventoryTotalsBySite(UUID siteId) {
        return inventoryTotalsRepository.findAllInventoryTotalsBySite(siteId);
    }

    /**
     * Slim, site-scoped, bounded-batch totals for known product ids (AC-5/AC-7). A requested id
     * absent from the result means quantity 0 (the batched-mode contract documented on
     * {@link InventoryTotalsRepository#findInventoryTotalsBySiteAndProductIds}, deliberately
     * different from the full-catalog mode above). Rejects a batch larger than
     * {@link InventoryTotalsRepository#MAX_PRODUCT_IDS_BATCH_SIZE} with
     * {@code InvalidInventoryOperationException} (-> 400).
     */
    public List<SiteInventoryTotalDTO> findInventoryTotalsBySite(UUID siteId, Collection<UUID> productIds) {
        return inventoryTotalsRepository.findInventoryTotalsBySiteAndProductIds(siteId, productIds);
    }
}
