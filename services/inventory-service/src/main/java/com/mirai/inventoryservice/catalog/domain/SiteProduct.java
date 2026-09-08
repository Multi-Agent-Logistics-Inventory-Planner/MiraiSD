package com.mirai.inventoryservice.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One product's assortment and setting overrides at one site. {@code siteId} is a plain UUID
 * rather than a JPA relation to {@code sites.domain.Site}: catalog and sites are separate modules
 * and new cross-module JPA entity relationships are forbidden (docs/specs/
 * spring-domain-modular-monolith.md rule 8). {@code productId} is likewise a plain UUID to keep
 * this entity independent of {@link Product}'s lazy-loading graph - every read goes through
 * {@code catalog.application.SiteAssortment}, not entity navigation.
 * <p>
 * Absent row for a (site, product) pair means "never carried at this site" - see
 * .specs/phase-5c-site-products/spec.md. A row with {@code isStocked = false} is a distinct,
 * intentional "de-assorted, overrides retained" state.
 */
@Entity
@Table(name = "site_products",
        uniqueConstraints = @UniqueConstraint(columnNames = {"site_id", "product_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "is_stocked", nullable = false)
    @Builder.Default
    private Boolean isStocked = false;

    @Column(name = "forecasting_enabled", nullable = false)
    @Builder.Default
    private Boolean forecastingEnabled = true;

    @Column(name = "unit_cost", precision = 10, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "msrp", precision = 10, scale = 2)
    private BigDecimal msrp;

    @Column(name = "reorder_point")
    private Integer reorderPoint;

    @Column(name = "target_stock_level")
    private Integer targetStockLevel;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    private Long version;
}
