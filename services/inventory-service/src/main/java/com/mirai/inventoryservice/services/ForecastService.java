package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.ForecastExplanationDTO;
import com.mirai.inventoryservice.dtos.responses.ForecastPredictionResponseDTO;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import com.mirai.inventoryservice.models.enums.StockMovementReason;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastService {

    private final ForecastPredictionRepository forecastPredictionRepository;
    private final ProductRepository productRepository;
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
                    Product product = productRepository.findById(p.getItemId()).orElse(null);
                    Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();
                    return convertToDTO(p, product, stockMap);
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
                    Product product = productRepository.findById(p.getItemId()).orElse(null);
                    Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();
                    return convertToDTO(p, product, stockMap);
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
        Map<UUID, Product> productMap = getProductMap(predictions.getContent());
        Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();
        return predictions.map(p -> convertToDTO(p, productMap.get(p.getItemId()), stockMap));
    }

    private List<ForecastPredictionResponseDTO> mapToDTOList(List<ForecastPrediction> predictions) {
        Map<UUID, Product> productMap = getProductMap(predictions);
        Map<UUID, Integer> stockMap = inventoryTotalsRepository.findAllStockTotalsMap();
        return predictions.stream()
                .map(p -> convertToDTO(p, productMap.get(p.getItemId()), stockMap))
                .collect(Collectors.toList());
    }

    private Map<UUID, Product> getProductMap(List<ForecastPrediction> predictions) {
        Set<UUID> itemIds = predictions.stream()
                .map(ForecastPrediction::getItemId)
                .collect(Collectors.toSet());

        return productRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
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

    private ForecastPredictionResponseDTO convertToDTO(ForecastPrediction prediction, Product product, Map<UUID, Integer> stockMap) {
        String itemName = product != null ? product.getName() : "Unknown Item";
        String itemSku = product != null ? product.getSku() : "UNKNOWN";
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
            product != null ? product.getUnitCost() : null,
            prediction.getConfidence(),
            prediction.getComputedAt()
        );
    }
}
