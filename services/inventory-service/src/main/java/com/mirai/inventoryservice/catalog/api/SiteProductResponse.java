package com.mirai.inventoryservice.catalog.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One product's effective, site-scoped view (spec.md phase-5d AC-2). {@code isStocked} and every
 * override field are the resolved ({@code EffectiveProductSettings}) values, not the raw
 * {@code site_products} row - an absent row surfaces here as {@code isStocked = false} with the
 * global product's fallback values, never a 404 (AC-2). {@code version} is {@code null} exactly
 * when the site has never carried this product (no row exists yet) - a settings write against a
 * {@code null} version is always rejected, since there is nothing to version.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteProductResponse {
    private UUID productId;
    private String sku;
    private String name;
    // Lombok's @Data on a primitive "isStocked" boolean generates the JavaBean getter
    // isStocked(), which Jackson serializes as "stocked" (dropping the "is" prefix) unless told
    // otherwise - force the wire name explicitly since AC-2/AC-2c both spell it "isStocked".
    @JsonProperty("isStocked")
    private boolean isStocked;
    private Boolean forecastingEnabled;
    private BigDecimal unitCost;
    private BigDecimal msrp;
    private Integer reorderPoint;
    private Integer targetStockLevel;
    private Integer leadTimeDays;
    private Long version;
}
