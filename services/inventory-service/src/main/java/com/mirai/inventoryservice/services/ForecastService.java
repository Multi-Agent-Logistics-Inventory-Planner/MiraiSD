package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.dtos.responses.ForecastPredictionResponseDTO;
import com.mirai.inventoryservice.models.Product;
import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import com.mirai.inventoryservice.repositories.ProductRepository;
import com.mirai.inventoryservice.repositories.BoxBinInventoryRepository;
import com.mirai.inventoryservice.repositories.CabinetInventoryRepository;
import com.mirai.inventoryservice.repositories.RackInventoryRepository;
import com.mirai.inventoryservice.repositories.DoubleClawMachineInventoryRepository;
import com.mirai.inventoryservice.repositories.SingleClawMachineInventoryRepository;
import com.mirai.inventoryservice.repositories.KeychainMachineInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    
    // Inventory Repositories
    private final BoxBinInventoryRepository boxBinInventoryRepository;
    private final CabinetInventoryRepository cabinetInventoryRepository;
    private final RackInventoryRepository rackInventoryRepository;
    private final DoubleClawMachineInventoryRepository doubleClawMachineInventoryRepository;
    private final SingleClawMachineInventoryRepository singleClawMachineInventoryRepository;
    private final KeychainMachineInventoryRepository keychainMachineInventoryRepository;

    @Transactional(readOnly = true)
    public Page<ForecastPredictionResponseDTO> getAllForecasts(Pageable pageable) {
        Page<ForecastPrediction> predictions = forecastPredictionRepository.findAll(pageable);
        
        return mapToDTOs(predictions);
    }

    @Transactional(readOnly = true)
    public List<ForecastPredictionResponseDTO> getAtRiskForecasts(int daysThreshold) {
        BigDecimal threshold = BigDecimal.valueOf(daysThreshold);
        List<ForecastPrediction> predictions = forecastPredictionRepository.findByDaysToStockoutLessThanOrderByDaysToStockoutAsc(threshold);
        
        return mapToDTOList(predictions);
    }

    @Transactional(readOnly = true)
    public ForecastPredictionResponseDTO getForecastByItem(UUID itemId) {
        return forecastPredictionRepository.findFirstByItemIdOrderByComputedAtDesc(itemId)
                .map(p -> {
                    Product product = productRepository.findById(p.getItemId()).orElse(null);
                    return convertToDTO(p, product);
                })
                .orElse(null);
    }

    private Page<ForecastPredictionResponseDTO> mapToDTOs(Page<ForecastPrediction> predictions) {
        Map<UUID, Product> productMap = getProductMap(predictions.getContent());
        return predictions.map(p -> convertToDTO(p, productMap.get(p.getItemId())));
    }

    private List<ForecastPredictionResponseDTO> mapToDTOList(List<ForecastPrediction> predictions) {
        Map<UUID, Product> productMap = getProductMap(predictions);
        return predictions.stream()
                .map(p -> convertToDTO(p, productMap.get(p.getItemId())))
                .collect(Collectors.toList());
    }

    private Map<UUID, Product> getProductMap(List<ForecastPrediction> predictions) {
        Set<UUID> itemIds = predictions.stream()
                .map(ForecastPrediction::getItemId)
                .collect(Collectors.toSet());
        
        return productRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }
    
    public Integer getCurrentStockPublic(UUID itemId) {
        if (itemId == null) return 0;
        
        int total = 0;
        total += getSafeSum(boxBinInventoryRepository.sumQuantityByProductId(itemId));
        total += getSafeSum(cabinetInventoryRepository.sumQuantityByProductId(itemId));
        total += getSafeSum(rackInventoryRepository.sumQuantityByProductId(itemId));
        total += getSafeSum(doubleClawMachineInventoryRepository.sumQuantityByProductId(itemId));
        total += getSafeSum(singleClawMachineInventoryRepository.sumQuantityByProductId(itemId));
        total += getSafeSum(keychainMachineInventoryRepository.sumQuantityByProductId(itemId));
        
        return total;
    }
    
    private int getSafeSum(Integer sum) {
        return sum != null ? sum : 0;
    }

    private ForecastPredictionResponseDTO convertToDTO(ForecastPrediction prediction, Product product) {
        String itemName = product != null ? product.getName() : "Unknown Item";
        String itemSku = product != null ? product.getSku() : "UNKNOWN";
        Integer currentStock = getCurrentStockPublic(prediction.getItemId());
        
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
