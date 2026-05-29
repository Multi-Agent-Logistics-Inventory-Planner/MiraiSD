package com.mirai.inventoryservice.services;

import com.mirai.inventoryservice.config.CacheConfig;
import com.mirai.inventoryservice.dtos.responses.ForecastAccuracyDTO;
import com.mirai.inventoryservice.repositories.ForecastPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastAccuracyService {

    private static final int HEADLINE_DAYS = 30;
    private static final int COMPARISON_DAYS = 7;
    private static final int SCALE = 4;

    private final ForecastPredictionRepository forecastPredictionRepository;

    @Transactional(readOnly = true)
    @Cacheable(CacheConfig.FORECAST_ACCURACY_CACHE)
    public ForecastAccuracyDTO getRollingAccuracy() {
        LocalDate today = LocalDate.now();

        List<Object[]> headlineRows = forecastPredictionRepository.aggregateAccuracyByCategory(
            today.minusDays(HEADLINE_DAYS), today.minusDays(1));
        List<Object[]> comparisonRows = forecastPredictionRepository.aggregateAccuracyByCategory(
            today.minusDays(COMPARISON_DAYS), today.minusDays(1));

        return new ForecastAccuracyDTO(
            aggregateWindow(headlineRows, HEADLINE_DAYS),
            aggregateWindow(comparisonRows, COMPARISON_DAYS),
            perCategory(headlineRows)
        );
    }

    private ForecastAccuracyDTO.Window aggregateWindow(List<Object[]> rows, int days) {
        long scoredWindows = 0;
        BigDecimal sumAbsError = BigDecimal.ZERO;
        BigDecimal sumActual = BigDecimal.ZERO;
        BigDecimal sumSignedError = BigDecimal.ZERO;
        long sumDaysObserved = 0;
        long under = 0;
        long over = 0;

        for (Object[] row : rows) {
            scoredWindows    += ((Number) row[1]).longValue();
            sumAbsError       = sumAbsError.add(toBigDecimal(row[2]));
            sumActual         = sumActual.add(toBigDecimal(row[3]));
            sumSignedError    = sumSignedError.add(toBigDecimal(row[4]));
            sumDaysObserved  += ((Number) row[5]).longValue();
            under            += ((Number) row[6]).longValue();
            over             += ((Number) row[7]).longValue();
        }

        BigDecimal ltWape = sumActual.signum() > 0
            ? sumAbsError.divide(sumActual, SCALE, RoundingMode.HALF_UP)
            : null;
        BigDecimal biasUnitsPerDay = sumDaysObserved > 0
            ? sumSignedError.divide(BigDecimal.valueOf(sumDaysObserved), SCALE, RoundingMode.HALF_UP)
            : null;

        return new ForecastAccuracyDTO.Window(
            days, scoredWindows, ltWape, biasUnitsPerDay, sumActual, under, over);
    }

    private List<ForecastAccuracyDTO.CategoryAccuracy> perCategory(List<Object[]> rows) {
        List<ForecastAccuracyDTO.CategoryAccuracy> result = new ArrayList<>(rows.size());

        for (Object[] row : rows) {
            String category = (String) row[0];
            long scoredWindows = ((Number) row[1]).longValue();
            BigDecimal sumAbs = toBigDecimal(row[2]);
            BigDecimal sumActual = toBigDecimal(row[3]);
            BigDecimal sumSigned = toBigDecimal(row[4]);
            long sumDaysObserved = ((Number) row[5]).longValue();

            BigDecimal ltWape = sumActual.signum() > 0
                ? sumAbs.divide(sumActual, SCALE, RoundingMode.HALF_UP)
                : null;
            BigDecimal biasUnitsPerDay = sumDaysObserved > 0
                ? sumSigned.divide(BigDecimal.valueOf(sumDaysObserved), SCALE, RoundingMode.HALF_UP)
                : null;

            result.add(new ForecastAccuracyDTO.CategoryAccuracy(
                category, scoredWindows, ltWape, biasUnitsPerDay, sumActual));
        }

        return result;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
