package com.mirai.inventoryservice.models.analytics;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Daily snapshot of forecast features for historical demand analysis.
 * Captures mu_hat, sigma_d_hat, mape, and other forecast metrics at a point in time.
 * Enables trend analysis and forecast accuracy tracking over time.
 */
@Entity
@Table(
    name = "analytics_forecast_snapshot",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_forecast_snapshot_item_date",
        columnNames = {"item_id", "snapshot_date"}
    ),
    indexes = {
        @Index(name = "idx_forecast_snapshot_date", columnList = "snapshot_date"),
        @Index(name = "idx_forecast_snapshot_item", columnList = "item_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastDailySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @NotNull
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    /**
     * Demand velocity (mu_hat) - expected units sold per day.
     */
    @Column(name = "mu_hat", precision = 10, scale = 4)
    private BigDecimal muHat;

    /**
     * Demand standard deviation (sigma_d_hat) - volatility measure.
     */
    @Column(name = "sigma_d_hat", precision = 10, scale = 4)
    private BigDecimal sigmaDHat;

    /**
     * Model confidence score (0-1).
     */
    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    /**
     * Mean Absolute Percentage Error of the forecast model.
     */
    @Column(precision = 5, scale = 4)
    private BigDecimal mape;

    /**
     * Days until predicted stockout at current consumption rate.
     */
    @Column(name = "days_to_stockout", precision = 10, scale = 2)
    private BigDecimal daysToStockout;

    /**
     * Current stock level at snapshot time.
     */
    @Column(name = "current_stock")
    private Integer currentStock;

    /**
     * JSON string containing day-of-week multipliers (7 values, Sunday=index 0).
     * Example: "[1.0, 1.2, 0.9, 1.1, 1.0, 1.3, 0.8]"
     */
    @Column(name = "dow_multipliers", columnDefinition = "TEXT")
    private String dowMultipliers;

    @CreationTimestamp
    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;
}
