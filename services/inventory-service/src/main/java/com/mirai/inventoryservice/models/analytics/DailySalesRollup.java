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
 * Pre-aggregated daily sales data per product.
 * Updated via trigger on stock_movements INSERT or by scheduled job.
 * Reduces 9-table UNION ALL queries to simple rollup lookups.
 */
@Entity
@Table(
    name = "analytics_daily_rollup",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_daily_rollup_item_date",
        columnNames = {"item_id", "rollup_date"}
    ),
    indexes = {
        @Index(name = "idx_daily_rollup_date", columnList = "rollup_date"),
        @Index(name = "idx_daily_rollup_item", columnList = "item_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailySalesRollup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @NotNull
    @Column(name = "rollup_date", nullable = false)
    private LocalDate rollupDate;

    @Column(name = "units_sold", nullable = false)
    @Builder.Default
    private Integer unitsSold = 0;

    @Column(name = "revenue", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal revenue = BigDecimal.ZERO;

    /**
     * Total cost of goods sold = unit_cost x units_sold.
     */
    @Column(name = "cost", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal cost = BigDecimal.ZERO;

    /**
     * Gross profit = revenue - cost = (msrp - unit_cost) x units_sold.
     */
    @Column(name = "profit", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal profit = BigDecimal.ZERO;

    @Column(name = "restock_units", nullable = false)
    @Builder.Default
    private Integer restockUnits = 0;

    @Column(name = "damage_units", nullable = false)
    @Builder.Default
    private Integer damageUnits = 0;

    @Column(name = "movement_count", nullable = false)
    @Builder.Default
    private Integer movementCount = 0;

    /**
     * Demand velocity snapshot (mu_hat) at rollup time.
     * Represents expected daily demand from forecasting model.
     */
    @Column(name = "demand_velocity_snapshot", precision = 10, scale = 4)
    private BigDecimal demandVelocitySnapshot;

    /**
     * Forecast confidence snapshot at rollup time.
     * Value between 0 and 1 representing model confidence.
     */
    @Column(name = "forecast_confidence_snapshot", precision = 5, scale = 4)
    private BigDecimal forecastConfidenceSnapshot;

    @CreationTimestamp
    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;
}
