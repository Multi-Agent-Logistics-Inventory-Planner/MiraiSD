package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.ForecastExplanationDTO;
import com.mirai.inventoryservice.dtos.responses.ForecastPredictionResponseDTO;
import com.mirai.inventoryservice.catalog.application.CatalogPricing;
import com.mirai.inventoryservice.catalog.application.CatalogQueries;
import com.mirai.inventoryservice.catalog.application.ProductPricing;
import com.mirai.inventoryservice.catalog.application.ProductRef;
import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.repositories.InventoryTotalsRepository;
import com.mirai.inventoryservice.repositories.StockMovementRepository;
import com.mirai.inventoryservice.repositories.projections.StockMovementHistoryView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastService {

    private final ForecastPredictionRepository forecastPredictionRepository;
    private final CatalogQueries catalogQueries;
    private final CatalogPricing catalogPricing;
    private final InventoryTotalsRepository inventoryTotalsRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public Page<ForecastPredictionResponseDTO> getAllForecasts(Pageable pageable) {
        Page<ForecastPrediction> predictions = forecastPredictionRepository.findLatestPerItem(pageable);

        return mapToDTOs(predictions);
    }

    @Transactional(readOnly = true)
    public List<ForecastPredictionResponseDTO> getAtRiskForecasts(int daysThreshold) {
        List<ForecastPrediction> predictions = forecastPredictionRepository.findLatestAtRisk(daysThreshold);

        return mapToDTOList(predictions);
    }

    @Transactional(readOnly = true)
    public List<ForecastPredictionResponseDTO> getAllForecastsUnpaginated() {
        List<ForecastPrediction> predictions = forecastPredictionRepository.findAllLatest();
        return mapToDTOList(predictions);
    }

    @Transactional(readOnly = true)
    public ForecastPredictionResponseDTO getForecastByItem(UUID itemId) {
        return forecastPredictionRepository.findFirstByItemIdOrderByComputedAtDesc(itemId)
                .map(p -> {
                    ProductRef product = catalogQueries.findById(p.getItemId()).orElse(null);
                    BigDecimal unitCost = catalogPricing.findPricing(p.getItemId())
                            .map(ProductPricing::unitCost).orElse(null);
                    Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();
                    return convertToDTO(p, product, unitCost, stockMap);
                })
                .orElse(null);
    }

    /**
     * Get the forecast for the item with highest demand (most negative avgDailyDelta).
     * Returns null if no consuming forecasts exist.
     */
    @Transactional(readOnly = true)
    public ForecastPredictionResponseDTO getHighestDemandForecast() {
        return forecastPredictionRepository.findHighestDemandForecast()
                .map(p -> {
                    ProductRef product = catalogQueries.findById(p.getItemId()).orElse(null);
                    BigDecimal unitCost = catalogPricing.findPricing(p.getItemId())
                            .map(ProductPricing::unitCost).orElse(null);
                    Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();
                    return convertToDTO(p, product, unitCost, stockMap);
                })
                .orElse(null);
    }

    /**
     * Bundle of forecast features + most-recent restock for the
     * "Why this number" drawer on the predictions tab. Reads the full
     * features JSONB (mu_hat, dow_multipliers, event_multipliers,
     * event_days_since, demand_regime, lead_time_source, tsb_p/z, mape,
     * etc.) plus the latest RESTOCK or SHIPMENT_RECEIPT timestamp.
     */
    @Transactional(readOnly = true)
    public ForecastExplanationDTO getForecastExplanation(UUID itemId) {
        ForecastPrediction prediction = forecastPredictionRepository
                .findFirstByItemIdOrderByComputedAtDesc(itemId)
                .orElse(null);
        if (prediction == null) {
            return null;
        }
        List<StockMovementHistoryView> recent = stockMovementRepository.findHistoryByItemId(
                itemId,
                OffsetDateTime.now().minusYears(10),
                OffsetDateTime.now(),
                List.of(StockMovementReason.RESTOCK, StockMovementReason.SHIPMENT_RECEIPT),
                PageRequest.of(0, 1));
        OffsetDateTime lastRestockAt = recent.isEmpty() ? null : recent.get(0).getAt();
        return new ForecastExplanationDTO(
                prediction.getItemId(),
                prediction.getComputedAt(),
                prediction.getFeatures(),
                lastRestockAt
        );
    }

    private Page<ForecastPredictionResponseDTO> mapToDTOs(Page<ForecastPrediction> predictions) {
        Map<UUID, ProductRef> productMap = getProductMap(predictions.getContent());
        Map<UUID, ProductPricing> pricingMap = getPricingMap(productMap.keySet());
        Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();
        return predictions.map(p -> convertToDTO(
                p, productMap.get(p.getItemId()), unitCostOf(pricingMap, p.getItemId()), stockMap));
    }

    private List<ForecastPredictionResponseDTO> mapToDTOList(List<ForecastPrediction> predictions) {
        Map<UUID, ProductRef> productMap = getProductMap(predictions);
        Map<UUID, ProductPricing> pricingMap = getPricingMap(productMap.keySet());
        Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();
        return predictions.stream()
                .map(p -> convertToDTO(
                        p, productMap.get(p.getItemId()), unitCostOf(pricingMap, p.getItemId()), stockMap))
                .collect(Collectors.toList());
    }

    private Map<UUID, ProductRef> getProductMap(List<ForecastPrediction> predictions) {
        Set<UUID> itemIds = predictions.stream()
                .map(ForecastPrediction::getItemId)
                .collect(Collectors.toSet());

        return catalogQueries.findAllByIds(itemIds).stream()
                .collect(Collectors.toMap(ProductRef::id, ref -> ref));
    }

    private Map<UUID, ProductPricing> getPricingMap(Collection<UUID> itemIds) {
        return catalogPricing.findPricingForIds(itemIds).stream()
                .collect(Collectors.toMap(ProductPricing::productId, p -> p));
    }

    private BigDecimal unitCostOf(Map<UUID, ProductPricing> pricingMap, UUID itemId) {
        ProductPricing pricing = pricingMap.get(itemId);
        return pricing != null ? pricing.unitCost() : null;
    }

    /**
     * Get all stock totals as a map. Use this for batch operations.
     */
    public Map<UUID, Integer> getAllStockTotals() {
        return inventoryTotalsRepository.findAllStockTotalsMap();
    }

    /**
     * Get current stock for a single item. For batch operations, use getAllStockTotals() instead.
     */
    public Integer getCurrentStockPublic(UUID itemId) {
        if (itemId == null) return 0;
        Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();
        return stockMap.getOrDefault(itemId, 0);
    }

    private ForecastPredictionResponseDTO convertToDTO(
            ForecastPrediction prediction, ProductRef product, BigDecimal unitCost, Map<UUID, Integer> stockMap) {
        String itemName = product != null ? product.name() : "Unknown Item";
        String itemSku = product != null ? product.sku() : "UNKNOWN";
        Integer currentStock = stockMap.getOrDefault(prediction.getItemId(), 0);

        return new ForecastPredictionResponseDTO(
            prediction.getId(),
            prediction.getItemId(),
            itemName,
            itemSku,
            currentStock,
            prediction.getHorizonDays(),
            prediction.getAvgDailyDelta(),
            prediction.getDaysToStockout(),
            prediction.getSuggestedReorderQty(),
            prediction.getSuggestedOrderDate(),
            unitCost,
            prediction.getConfidence(),
            prediction.getComputedAt()
        );
    }
}
