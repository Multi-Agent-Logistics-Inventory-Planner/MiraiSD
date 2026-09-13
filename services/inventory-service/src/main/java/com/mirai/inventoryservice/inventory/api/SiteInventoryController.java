package com.mirai.inventoryservice.inventory.api;

import com.mirai.inventoryservice.dtos.requests.AuditLogFilterDTO;
import com.mirai.inventoryservice.inventory.application.InventoryAggregateService;
import com.mirai.inventoryservice.inventory.application.InventoryQueries;
import com.mirai.inventoryservice.inventory.domain.LocationInventory;
import com.mirai.inventoryservice.inventory.domain.StockMovement;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import com.mirai.inventoryservice.sites.application.LocationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Site-scoped inventory/movement read routes (.specs/phase-6-inventory 6c, T-6c-11, AC-5). Reads
 * the site off {@link AuthorizedSiteContextHolder} (already resolved and membership-checked by
 * {@code identity.infrastructure.SiteAccessAuthorizationFilter} for this path) rather than
 * trusting the raw {@code siteId} path variable directly, matching {@code SiteLocationController}.
 * Every handler still declares the {@code siteId} path variable so springdoc/the generated OpenAPI
 * contract exposes it as a required parameter. Handler and DTO names are distinct from
 * {@link InventoryAggregateController}'s/{@link StockMovementController}'s (e.g.
 * {@code getSiteInventoryTotals}, not {@code getInventoryTotals}) so springdoc doesn't collide
 * operation IDs and silently reassign the legacy ones ({@code SiteLocationController:27-38}'s
 * recorded trap). The legacy, unscoped {@code /api/inventory}/{@code /api/stock-movements} routes
 * are untouched.
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
public class SiteInventoryController {

    private final InventoryQueries inventoryQueries;
    private final InventoryAggregateService inventoryAggregateService;
    private final LocationService locationService;

    public SiteInventoryController(
            InventoryQueries inventoryQueries,
            InventoryAggregateService inventoryAggregateService,
            LocationService locationService) {
        this.inventoryQueries = inventoryQueries;
        this.inventoryAggregateService = inventoryAggregateService;
        this.locationService = locationService;
    }

    @GetMapping("/totals")
    public ResponseEntity<List<SiteInventoryTotalDTO>> getSiteInventoryTotals(
            @PathVariable UUID siteId,
            @RequestParam(required = false) List<UUID> productIds) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        List<SiteInventoryTotalDTO> totals = (productIds == null || productIds.isEmpty())
                ? inventoryQueries.findInventoryTotalsBySite(contextSiteId)
                : inventoryQueries.findInventoryTotalsBySite(contextSiteId, productIds);
        return ResponseEntity.ok(totals);
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductInventoryResponseDTO> getSiteInventoryByProduct(
            @PathVariable UUID siteId, @PathVariable UUID productId) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        return ResponseEntity.ok(inventoryAggregateService.getInventoryByProductAndSite(contextSiteId, productId));
    }

    @GetMapping("/locations/{locationId}")
    public ResponseEntity<List<SiteLocationInventoryEntryDTO>> getSiteInventoryByLocation(
            @PathVariable UUID siteId, @PathVariable UUID locationId) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        // 404s for an unknown or foreign-site locationId before returning an (otherwise
        // indistinguishable) empty list for "no inventory at this location".
        locationService.getLocationById(contextSiteId, locationId);
        List<LocationInventory> rows = inventoryQueries.findByLocationIdAndSite(contextSiteId, locationId);
        List<SiteLocationInventoryEntryDTO> entries = rows.stream()
                .map(row -> SiteLocationInventoryEntryDTO.builder()
                        .inventoryId(row.getId())
                        .productId(row.getProduct().getId())
                        .quantity(row.getQuantity())
                        .updatedAt(row.getUpdatedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(entries);
    }

    /**
     * Per-product movement history when {@code itemId} is supplied, otherwise a filtered
     * audit-log page (Q-6c-5: null-{@code site} rows are included and labeled
     * {@code siteAttribution: "UNKNOWN"}, never hidden).
     */
    @GetMapping("/movements")
    public ResponseEntity<Page<SiteStockMovementResponseDTO>> getSiteMovements(
            @PathVariable UUID siteId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) StockMovementReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "at", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Page<StockMovement> movements;
        if (itemId != null) {
            movements = inventoryQueries.findMovementHistoryBySite(contextSiteId, itemId, pageable);
        } else {
            AuditLogFilterDTO filters = AuditLogFilterDTO.builder()
                    .search(search)
                    .actorId(actorId)
                    .reason(reason)
                    .fromDate(fromDate)
                    .toDate(toDate)
                    .build();
            movements = inventoryQueries.findAuditLogPageBySite(contextSiteId, filters, pageable);
        }
        return ResponseEntity.ok(movements.map(SiteInventoryController::toSiteMovementDTO));
    }

    private static SiteStockMovementResponseDTO toSiteMovementDTO(StockMovement movement) {
        return SiteStockMovementResponseDTO.builder()
                .id(movement.getId())
                .locationType(movement.getLocationType())
                .itemId(movement.getItem().getId())
                .fromLocationId(movement.getFromLocationId())
                .toLocationId(movement.getToLocationId())
                .quantityChange(movement.getQuantityChange())
                .reason(movement.getReason())
                .actorId(movement.getActorId())
                .at(movement.getAt())
                .metadata(movement.getMetadata())
                .siteAttribution(movement.getSite() == null ? "UNKNOWN" : null)
                .build();
    }
}
