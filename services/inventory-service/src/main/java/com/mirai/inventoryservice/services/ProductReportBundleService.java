package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.assistant.ComparisonRowDTO;
import com.mirai.inventoryservice.dtos.assistant.DetailBundleDTO;
import com.mirai.inventoryservice.dtos.assistant.HeaderBundleDTO;
import com.mirai.inventoryservice.dtos.assistant.MovementRowDTO;
import com.mirai.inventoryservice.dtos.assistant.MovementSummaryDTO;
import com.mirai.inventoryservice.models.MachineDisplay;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.analytics.DailySalesRollup;
import com.mirai.inventoryservice.models.analytics.ForecastDailySnapshot;
import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.models.inventory.LocationInventory;
import com.mirai.inventoryservice.models.shipment.ShipmentItem;
import com.mirai.inventoryservice.repositories.DailySalesRollupRepository;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.repositories.ForecastSnapshotRepository;
import com.mirai.inventoryservice.repositories.LocationInventoryRepository;
import com.mirai.inventoryservice.repositories.MachineDisplayRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.ShipmentItemRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.projections.StockMovementHistoryView;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Composes existing repository methods plus the three Product Assistant
 * additions into deterministic bundles. See docs/chatbot-plan.md §Backend
 * for the hard constraint: no new native SQL joining analytics tables;
 * sequential calls inside one read-only transaction so a second admin
 * cannot exhaust the 3-connection Hikari pool.
 */
@Service
@RequiredArgsConstructor
public class ProductReportBundleService {

    private static final int DETAIL_WINDOW_DAYS = 90;
    private static final int SHIPMENT_WINDOW_DAYS = 180;
    private static final int MOVEMENTS_MAX_LIMIT = 200;
    private static final int MAX_DATE_RANGE_DAYS = 365;

    private final ProductRepository productRepository;
    private final LocationInventoryRepository locationInventoryRepository;
    private final DailySalesRollupRepository dailySalesRollupRepository;
    private final ForecastSnapshotRepository forecastSnapshotRepository;
    private final ForecastPredictionRepository forecastPredictionRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final MachineDisplayRepository machineDisplayRepository;
    private final StockMovementRepository stockMovementRepository;

    // ---------- header ----------

    @Transactional(readOnly = true)
    public HeaderBundleDTO getHeader(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

        Integer currentStock = Optional
                .ofNullable(locationInventoryRepository.sumQuantityByProductId(productId))
                .orElse(0);

        LocalDate today = LocalDate.now();
        LocalDate last30Start = today.minusDays(30);
        LocalDate prior30Start = today.minusDays(60);
        LocalDate prior30End = today.minusDays(31);

        List<DailySalesRollup> last30 = dailySalesRollupRepository
                .findByItemIdAndRollupDateBetweenOrderByRollupDateAsc(productId, last30Start, today);
        List<DailySalesRollup> prior30 = dailySalesRollupRepository
                .findByItemIdAndRollupDateBetweenOrderByRollupDateAsc(productId, prior30Start, prior30End);

        int unitsSoldLast30 = last30.stream()
                .mapToInt(r -> r.getUnitsSold() == null ? 0 : r.getUnitsSold()).sum();
        int unitsSoldPrior30 = prior30.stream()
                .mapToInt(r -> r.getUnitsSold() == null ? 0 : r.getUnitsSold()).sum();
        int damageLast30 = last30.stream()
                .mapToInt(r -> r.getDamageUnits() == null ? 0 : r.getDamageUnits()).sum();

        Optional<ForecastPrediction> latestPrediction = forecastPredictionRepository
                .findFirstByItemIdOrderByComputedAtDesc(productId);

        BigDecimal velocity = latestPrediction
                .map(p -> p.getAvgDailyDelta() == null ? null : p.getAvgDailyDelta().negate())
                .orElse(null);
        BigDecimal daysToStockout = latestPrediction.map(ForecastPrediction::getDaysToStockout).orElse(null);
        BigDecimal confidence = latestPrediction.map(ForecastPrediction::getConfidence).orElse(null);

        // Latest forecast snapshot for MAPE
        List<ForecastDailySnapshot> snapshots = forecastSnapshotRepository
                .findByItemIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                        productId, today.minusDays(DETAIL_WINDOW_DAYS), today);
        BigDecimal mape = snapshots.isEmpty()
                ? null
                : snapshots.get(snapshots.size() - 1).getMape();

        // Last restock timestamp via the (item_id, at DESC) index on a single row
        Pageable onePage = PageRequest.of(0, 1);
        List<StockMovementHistoryView> lastRestockRows = stockMovementRepository.findHistoryByItemId(
                productId,
                OffsetDateTime.now().minusYears(10),
                OffsetDateTime.now(),
                List.of(StockMovementReason.RESTOCK, StockMovementReason.SHIPMENT_RECEIPT),
                onePage);
        OffsetDateTime lastRestockAt = lastRestockRows.isEmpty() ? null : lastRestockRows.get(0).getAt();

        boolean onDisplay = !machineDisplayRepository.findActiveByProduct_Id(productId).isEmpty();

        String categoryName = product.getCategory() != null ? product.getCategory().getName() : null;

        return new HeaderBundleDTO(
                productId,
                product.getName(),
                categoryName,
                currentStock,
                unitsSoldLast30,
                unitsSoldPrior30,
                velocity,
                daysToStockout,
                confidence,
                mape,
                lastRestockAt,
                damageLast30,
                onDisplay);
    }

    // ---------- detail ----------

    @Transactional(readOnly = true)
    public DetailBundleDTO getDetail(UUID productId, int days) {
        int clampedDays = Math.max(1, Math.min(days, 365));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

        Integer currentStock = Optional
                .ofNullable(locationInventoryRepository.sumQuantityByProductId(productId))
                .orElse(0);

        List<LocationInventory> inventoryRows = locationInventoryRepository.findByProduct_Id(productId);

        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(clampedDays);
        List<DailySalesRollup> rollups = dailySalesRollupRepository
                .findByItemIdAndRollupDateBetweenOrderByRollupDateAsc(productId, windowStart, today);
        List<ForecastDailySnapshot> snapshots = forecastSnapshotRepository
                .findByItemIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(productId, windowStart, today);
        Optional<ForecastPrediction> latestPrediction = forecastPredictionRepository
                .findFirstByItemIdOrderByComputedAtDesc(productId);
        List<ShipmentItem> shipmentItems = shipmentItemRepository
                .findRecentByItemIdWithShipment(productId, today.minusDays(SHIPMENT_WINDOW_DAYS));
        List<MachineDisplay> displays = machineDisplayRepository.findActiveByProduct_Id(productId);

        String categoryName = product.getCategory() != null ? product.getCategory().getName() : null;

        DetailBundleDTO.ProductSummary productSummary = new DetailBundleDTO.ProductSummary(
                product.getId(),
                product.getSku(),
                product.getName(),
                categoryName,
                product.getImageUrl(),
                product.getReorderPoint(),
                product.getTargetStockLevel(),
                product.getLeadTimeDays(),
                product.getUnitCost(),
                currentStock);

        List<DetailBundleDTO.InventoryByLocation> inventoryByLocation = inventoryRows.stream()
                .map(li -> new DetailBundleDTO.InventoryByLocation(
                        li.getLocation() != null ? li.getLocation().getId() : null,
                        li.getLocation() != null ? li.getLocation().getLocationCode() : null,
                        li.getLocation() != null && li.getLocation().getStorageLocation() != null
                                ? li.getLocation().getStorageLocation().getCode() : null,
                        li.getQuantity()))
                .toList();

        List<DetailBundleDTO.DailyRollupPoint> dailyPoints = rollups.stream()
                .map(r -> new DetailBundleDTO.DailyRollupPoint(
                        r.getRollupDate(),
                        r.getUnitsSold(),
                        r.getRevenue(),
                        r.getRestockUnits(),
                        r.getDamageUnits()))
                .toList();

        List<DetailBundleDTO.ForecastSnapshotPoint> snapshotPoints = snapshots.stream()
                .map(s -> new DetailBundleDTO.ForecastSnapshotPoint(
                        s.getSnapshotDate(),
                        s.getMuHat(),
                        s.getConfidence(),
                        s.getMape(),
                        s.getDaysToStockout(),
                        s.getCurrentStock()))
                .toList();

        DetailBundleDTO.LatestPrediction latest = latestPrediction
                .map(p -> new DetailBundleDTO.LatestPrediction(
                        p.getHorizonDays(),
                        p.getAvgDailyDelta(),
                        p.getDaysToStockout(),
                        p.getSuggestedReorderQty(),
                        p.getSuggestedOrderDate(),
                        p.getConfidence(),
                        p.getComputedAt()))
                .orElse(null);

        List<DetailBundleDTO.RecentShipment> recentShipments = shipmentItems.stream()
                .map(si -> new DetailBundleDTO.RecentShipment(
                        si.getId(),
                        si.getShipment() != null ? si.getShipment().getId() : null,
                        si.getShipment() != null ? si.getShipment().getActualDeliveryDate() : null,
                        si.getOrderedQuantity(),
                        si.getReceivedQuantity(),
                        si.getDamagedQuantity(),
                        si.getUnitCost()))
                .toList();

        List<DetailBundleDTO.ActiveDisplay> activeDisplays = displays.stream()
                .map(md -> new DetailBundleDTO.ActiveDisplay(
                        md.getId(),
                        md.getLocation() != null ? md.getLocation().getId() : null,
                        md.getLocationType() != null ? md.getLocationType().name() : null,
                        md.getMachineId(),
                        md.getStartedAt()))
                .toList();

        return new DetailBundleDTO(
                productSummary,
                inventoryByLocation,
                dailyPoints,
                snapshotPoints,
                latest,
                recentShipments,
                activeDisplays);
    }

    // ---------- movements drill-down ----------

    @Transactional(readOnly = true)
    public List<MovementRowDTO> getMovements(
            UUID productId,
            OffsetDateTime from,
            OffsetDateTime to,
            List<StockMovementReason> reasons,
            int limit) {
        validateDateRange(from, to);
        int clampedLimit = Math.max(1, Math.min(limit, MOVEMENTS_MAX_LIMIT));
        List<StockMovementReason> reasonFilter = (reasons == null || reasons.isEmpty()) ? null : reasons;
        List<StockMovementHistoryView> rows = stockMovementRepository.findHistoryByItemId(
                productId,
                from,
                to,
                reasonFilter,
                PageRequest.of(0, clampedLimit));
        return rows.stream()
                .map(v -> new MovementRowDTO(
                        v.getId(),
                        v.getAt(),
                        v.getReason(),
                        v.getQuantityChange(),
                        v.getPreviousQuantity(),
                        v.getCurrentQuantity(),
                        v.getFromLocationId(),
                        v.getToLocationId()))
                .toList();
    }

    // ---------- movements summary ----------

    @Transactional(readOnly = true)
    public MovementSummaryDTO getMovementSummary(
            UUID productId,
            OffsetDateTime from,
            OffsetDateTime to) {
        validateDateRange(from, to);
        // Pull up to the MOVEMENTS_MAX_LIMIT most-recent rows via the indexed path;
        // at the documented <100 moves/day volume this comfortably covers the
        // typical 30- and 90-day windows the assistant asks about.
        List<StockMovementHistoryView> rows = stockMovementRepository.findHistoryByItemId(
                productId, from, to, null, PageRequest.of(0, MOVEMENTS_MAX_LIMIT));

        Map<StockMovementReason, Long> byReason = new EnumMap<>(StockMovementReason.class);
        Map<StockMovementReason, OffsetDateTime> lastByReason = new EnumMap<>(StockMovementReason.class);
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();

        for (StockMovementHistoryView row : rows) {
            StockMovementReason reason = row.getReason();
            if (reason != null) {
                byReason.merge(reason, 1L, Long::sum);
                lastByReason.merge(reason, row.getAt(),
                        (existing, candidate) -> candidate.isAfter(existing) ? candidate : existing);
            }
            LocalDate day = row.getAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
            long units = Math.abs(row.getQuantityChange() == null ? 0 : row.getQuantityChange());
            byDay.merge(day, units, Long::sum);
        }

        MovementSummaryDTO.BiggestSingleDay biggest = byDay.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> new MovementSummaryDTO.BiggestSingleDay(e.getKey(), e.getValue()))
                .orElse(null);

        return new MovementSummaryDTO(byReason, lastByReason, biggest);
    }

    // ---------- category comparison ----------

    @Transactional(readOnly = true)
    public List<ComparisonRowDTO> getComparison(UUID productId, String metric, int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, 20));
        Product anchor = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
        UUID categoryId = anchor.getCategory() != null ? anchor.getCategory().getId() : null;
        if (categoryId == null) {
            return List.of();
        }

        List<Product> categoryPeers = productRepository.findByCategoryIdAndIsActiveTrue(categoryId);
        Map<UUID, Product> productsById = categoryPeers.stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        List<UUID> peerIds = new java.util.ArrayList<>(productsById.keySet());
        List<ForecastPrediction> latestAll = peerIds.isEmpty()
                ? List.of()
                : forecastPredictionRepository.findLatestByItemIds(peerIds);

        Comparator<ForecastPrediction> comparator = switch (metric == null ? "" : metric) {
            case "sales_velocity" ->
                    // more negative avgDailyDelta = higher demand; invert to sort "fastest mover first"
                    Comparator.comparing(
                            (ForecastPrediction fp) -> fp.getAvgDailyDelta() == null
                                    ? BigDecimal.ZERO : fp.getAvgDailyDelta(),
                            Comparator.naturalOrder());
            case "days_to_stockout" ->
                    Comparator.comparing(
                            (ForecastPrediction fp) -> fp.getDaysToStockout() == null
                                    ? new BigDecimal(Integer.MAX_VALUE) : fp.getDaysToStockout(),
                            Comparator.naturalOrder());
            default -> throw new IllegalArgumentException(
                    "Unsupported metric: " + metric + " (allowed: sales_velocity, days_to_stockout)");
        };

        List<ForecastPrediction> filtered = latestAll.stream()
                .filter(fp -> productsById.containsKey(fp.getItemId()))
                .sorted(comparator)
                .limit(clampedLimit)
                .toList();

        List<ComparisonRowDTO> result = new java.util.ArrayList<>(filtered.size());
        for (int i = 0; i < filtered.size(); i++) {
            ForecastPrediction fp = filtered.get(i);
            Product p = productsById.get(fp.getItemId());
            BigDecimal value = switch (metric) {
                case "sales_velocity" -> fp.getAvgDailyDelta() == null
                        ? BigDecimal.ZERO : fp.getAvgDailyDelta().negate();
                case "days_to_stockout" -> fp.getDaysToStockout();
                default -> null;
            };
            result.add(new ComparisonRowDTO(p.getId(), p.getName(), value, i + 1));
        }
        return result;
    }

    private static void validateDateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
        long days = Duration.between(from, to).toDays();
        if (days > MAX_DATE_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "Date range exceeds maximum of " + MAX_DATE_RANGE_DAYS + " days (requested " + days + ")");
        }
    }
}
