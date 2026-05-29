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
        long scored = 0;
        BigDecimal sumAbsError = BigDecimal.ZERO;
        long sumActual = 0;
        BigDecimal sumSignedError = BigDecimal.ZERO;
        BigDecimal mapeWeightedNumerator = BigDecimal.ZERO;
        long mapeWeightedDenominator = 0;
        long under = 0;
        long over = 0;

        for (Object[] row : rows) {
            long rowScored = ((Number) row[1]).longValue();
            BigDecimal rowSumAbs = toBigDecimal(row[2]);
            long rowSumActual = ((Number) row[3]).longValue();
            BigDecimal rowSumSigned = toBigDecimal(row[4]);
            BigDecimal rowMape = toBigDecimal(row[5]);
            long rowUnder = ((Number) row[6]).longValue();
            long rowOver = ((Number) row[7]).longValue();

            scored += rowScored;
            sumAbsError = sumAbsError.add(rowSumAbs);
            sumActual += rowSumActual;
            sumSignedError = sumSignedError.add(rowSumSigned);
            under += rowUnder;
            over += rowOver;
            if (rowMape != null) {
                mapeWeightedNumerator = mapeWeightedNumerator.add(rowMape.multiply(BigDecimal.valueOf(rowScored)));
                mapeWeightedDenominator += rowScored;
            }
        }

        BigDecimal wape = sumActual > 0
            ? sumAbsError.divide(BigDecimal.valueOf(sumActual), SCALE, RoundingMode.HALF_UP)
            : null;
        BigDecimal mape = mapeWeightedDenominator > 0
            ? mapeWeightedNumerator.divide(BigDecimal.valueOf(mapeWeightedDenominator), SCALE, RoundingMode.HALF_UP)
            : null;
        BigDecimal bias = scored > 0
            ? sumSignedError.divide(BigDecimal.valueOf(scored), SCALE, RoundingMode.HALF_UP)
            : null;

        return new ForecastAccuracyDTO.Window(days, scored, wape, mape, bias, sumActual, under, over);
    }

    private List<ForecastAccuracyDTO.CategoryAccuracy> perCategory(List<Object[]> rows) {
        List<ForecastAccuracyDTO.CategoryAccuracy> result = new ArrayList<>(rows.size());

        for (Object[] row : rows) {
            String category = (String) row[0];
            long scored = ((Number) row[1]).longValue();
            BigDecimal sumAbs = toBigDecimal(row[2]);
            long sumActual = ((Number) row[3]).longValue();
            BigDecimal sumSigned = toBigDecimal(row[4]);
            BigDecimal mape = toBigDecimal(row[5]);

            BigDecimal wape = sumActual > 0
                ? sumAbs.divide(BigDecimal.valueOf(sumActual), SCALE, RoundingMode.HALF_UP)
                : null;
            BigDecimal bias = scored > 0
                ? sumSigned.divide(BigDecimal.valueOf(scored), SCALE, RoundingMode.HALF_UP)
                : null;
            BigDecimal mapeScaled = mape != null ? mape.setScale(SCALE, RoundingMode.HALF_UP) : null;

            result.add(new ForecastAccuracyDTO.CategoryAccuracy(
                category, scored, wape, mapeScaled, bias, sumActual));
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
