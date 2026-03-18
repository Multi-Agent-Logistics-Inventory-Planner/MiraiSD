package com.mirai.inventoryservice.models.analytics;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Pre-aggregated monthly performance metrics by category.
 * Computed nightly from daily rollups.
 * Enables fast category trend analysis without re-aggregating raw data.
 */
@Entity
@Table(
    name = "analytics_monthly_rollup",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_monthly_rollup_category_month",
        columnNames = {"category_id", "rollup_year", "rollup_month"}
    ),
    indexes = {
        @Index(name = "idx_monthly_rollup_period", columnList = "rollup_year, rollup_month"),
        @Index(name = "idx_monthly_rollup_category", columnList = "category_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyPerformanceRollup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @NotNull
    @Column(name = "rollup_year", nullable = false)
    private Integer rollupYear;

    @NotNull
    @Column(name = "rollup_month", nullable = false)
    private Integer rollupMonth;

    @Column(name = "total_units_sold", nullable = false)
    @Builder.Default
    private Integer totalUnitsSold = 0;

    @Column(name = "total_revenue", precision = 14, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_restock_units", nullable = false)
    @Builder.Default
    private Integer totalRestockUnits = 0;

    @Column(name = "total_damage_units", nullable = false)
    @Builder.Default
    private Integer totalDamageUnits = 0;

    @Column(name = "unique_items_sold", nullable = false)
    @Builder.Default
    private Integer uniqueItemsSold = 0;

    @Column(name = "avg_turnover_rate", precision = 8, scale = 4)
    private BigDecimal avgTurnoverRate;

    @Column(name = "avg_fill_rate", precision = 5, scale = 2)
    private BigDecimal avgFillRate;

    @CreationTimestamp
    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;

    /**
     * Helper to get YearMonth representation.
     */
    @Transient
    public YearMonth getYearMonth() {
        return YearMonth.of(rollupYear, rollupMonth);
    }
}
