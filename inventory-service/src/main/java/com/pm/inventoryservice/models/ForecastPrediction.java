package com.pm.inventoryservice.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "forecast_predictions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @NotNull
    @Column(name = "horizon_days", nullable = false)
    private Integer horizonDays;

    @Column(name = "avg_daily_delta")
    private BigDecimal avgDailyDelta;

    @Column(name = "days_to_stockout")
    private BigDecimal daysToStockout;

    @Column(name = "suggested_reorder_qty")
    private Integer suggestedReorderQty;

    @Column(name = "suggested_order_date")
    private LocalDate suggestedOrderDate;

    @Column
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> features;

    @NotNull
    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;
}