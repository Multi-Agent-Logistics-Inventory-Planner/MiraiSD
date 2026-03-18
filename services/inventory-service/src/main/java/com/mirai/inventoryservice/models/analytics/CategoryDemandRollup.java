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
 * Pre-aggregated category-level demand metrics for fast insights.
 * Reduces query complexity by pre-computing category aggregations daily.
 * Used by insights dashboard to avoid expensive real-time aggregations.
 */
@Entity
@Table(
    name = "analytics_category_demand_rollup",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_category_demand_cat_date",
        columnNames = {"category_id", "rollup_date"}
    ),
    indexes = {
        @Index(name = "idx_category_demand_date", columnList = "rollup_date"),
        @Index(name = "idx_category_demand_category", columnList = "category_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDemandRollup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @NotNull
    @Column(name = "rollup_date", nullable = false)
    private LocalDate rollupDate;

    /**
     * Sum of mu_hat for all items in category (total expected daily demand).
     */
    @Column(name = "total_demand_velocity", precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal totalDemandVelocity = BigDecimal.ZERO;

    /**
     * Average mu_hat across all items in category.
     */
    @Column(name = "avg_demand_velocity", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal avgDemandVelocity = BigDecimal.ZERO;

    /**
     * Total units sold in the category on this date.
     */
    @Column(name = "total_units_sold")
    @Builder.Default
    private Integer totalUnitsSold = 0;

    /**
     * Total stock across all items in category.
     */
    @Column(name = "total_stock")
    @Builder.Default
    private Integer totalStock = 0;

    /**
     * Average stock velocity (mu_hat / currentStock) across items.
     */
    @Column(name = "avg_stock_velocity", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal avgStockVelocity = BigDecimal.ZERO;

    /**
     * Count of items with ATTENTION urgency level.
     */
    @Column(name = "items_at_risk")
    @Builder.Default
    private Integer itemsAtRisk = 0;

    /**
     * Count of items with CRITICAL or URGENT urgency level.
     */
    @Column(name = "items_critical")
    @Builder.Default
    private Integer itemsCritical = 0;

    /**
     * Count of items with HEALTHY urgency level.
     */
    @Column(name = "items_healthy")
    @Builder.Default
    private Integer itemsHealthy = 0;

    /**
     * Average forecast confidence across items in category.
     */
    @Column(name = "avg_confidence", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal avgConfidence = BigDecimal.ZERO;

    /**
     * Average demand volatility (sigma_d_hat / mu_hat) across items.
     */
    @Column(name = "avg_volatility", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal avgVolatility = BigDecimal.ZERO;

    /**
     * Number of active items in the category.
     */
    @Column(name = "active_item_count")
    @Builder.Default
    private Integer activeItemCount = 0;

    @CreationTimestamp
    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;
}
