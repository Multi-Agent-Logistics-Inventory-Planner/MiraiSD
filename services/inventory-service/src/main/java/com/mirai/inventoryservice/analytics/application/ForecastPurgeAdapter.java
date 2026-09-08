package com.mirai.inventoryservice.analytics.application;

import com.mirai.inventoryservice.catalog.application.ForecastPurgePort;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

/** {@link ForecastPurgePort} implementation backed by the forecast prediction repository. */
@Component
@RequiredArgsConstructor
class ForecastPurgeAdapter implements ForecastPurgePort {

    private final ForecastPredictionRepository forecastPredictionRepository;

    @Override
    @Transactional
    public void purgeForecastsForProduct(UUID productId) {
        forecastPredictionRepository.deleteByItemId(productId);
    }

    @Override
    @Transactional
    public void purgeForecastsForProducts(Collection<UUID> productIds) {
        forecastPredictionRepository.deleteAllByItemIdIn(productIds);
    }
}
