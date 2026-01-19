package com.mirai.inventoryservice.repositories;

import com.mirai.inventoryservice.models.audit.ForecastPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForecastPredictionRepository extends JpaRepository<ForecastPrediction, UUID> {
    // Find latest prediction for a specific inventory item
    Optional<ForecastPrediction> findFirstByItemIdOrderByComputedAtDesc(UUID itemId);
    
    // Find all predictions for a specific inventory item
    List<ForecastPrediction> findByItemIdOrderByComputedAtDesc(UUID itemId);
    
    // Find predictions with low days until stockout (critical items)
    List<ForecastPrediction> findByDaysToStockoutLessThanOrderByDaysToStockoutAsc(BigDecimal daysThreshold);
}

