package com.mirai.inventoryservice.inventory.application;

import com.mirai.inventoryservice.inventory.api.InventoryTotalDTO;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.inventory.infrastructure.InventoryTotalsRepository;
import com.mirai.inventoryservice.inventory.infrastructure.LocationInventoryRepository;
import com.mirai.inventoryservice.inventory.infrastructure.StockMovementRepository;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
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
}
