package com.mirai.inventoryservice.catalog.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mirai.inventoryservice.catalog.application.FieldUpdate;
import com.mirai.inventoryservice.catalog.application.FieldUpdateDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Request body for {@code PUT /api/v1/sites/{siteId}/products/{productId}/settings} (spec.md
 * phase-5d AC-2, 5c AC-6's tri-state contract). {@code expectedVersion} is a plain nullable
 * {@code Long}: a request that omits it is functionally identical to one that sends it as
 * {@code null} (both are rejected as a version conflict by {@code SiteProductService}), so there
 * is no tri-state to express there - unlike the five override fields below, where "omitted" (leave
 * the stored value untouched) and "explicit null" (clear the override back to global inheritance)
 * are genuinely different outcomes. Each override field defaults to
 * {@link FieldUpdate#omitted()} and is only replaced by {@link FieldUpdateDeserializer} when the
 * JSON body actually contains that key (T-2 review: the previous {@code JsonNode}-based
 * extraction had no typed schema here, exporting as an opaque {@code Record<string, never>} to
 * generated clients). {@code expectedVersion} uses {@link StrictLongDeserializer} to reject a
 * fractional or non-numeric JSON value instead of Jackson's default silent truncation/coercion
 * (T-2 review follow-up).
 * <p>
 * Each override field's {@code @Schema} uses {@code types = {"<type>", "null"}}, not
 * {@code nullable = true} (T-2 review follow-up on the fix above): this project's springdoc emits
 * OpenAPI 3.1, whose JSON-Schema-based nullability is a {@code type} array (e.g.
 * {@code ["number", "null"]}), not the OpenAPI 3.0 {@code nullable} keyword. Verified empirically
 * that {@code nullable = true} alone - with or without {@code implementation}, and regardless of
 * whether the annotation sits on a Lombok-generated or hand-written getter - is silently dropped
 * by this swagger-core version's 3.1 output; only the explicit {@code types} array produces a
 * real {@code T | null} union in the regenerated {@code openapi.json} and
 * {@code packages/api-client/src/schema.d.ts}. {@code types} alone (without {@code implementation})
 * also works for the union itself, but leaves swagger-core to additionally resolve
 * {@code FieldUpdate<T>}'s own fields (`present`/`value`) as a stray {@code $ref} sibling and a
 * matching bogus {@code FieldUpdateBoolean}/{@code FieldUpdateBigDecimal}/{@code FieldUpdateInteger}
 * component schema - {@code implementation} is still required to suppress that and keep the
 * wrapper type itself out of the public contract. Written with plain fields and hand-written
 * getters/setters rather than Lombok {@code @Data} for direct control over exactly which method
 * carries which annotation while diagnosing this.
 */
public class SiteProductSettingsRequest {

    private Long expectedVersion;
    private FieldUpdate<Boolean> forecastingEnabled = FieldUpdate.omitted();
    private FieldUpdate<BigDecimal> unitCost = FieldUpdate.omitted();
    private FieldUpdate<BigDecimal> msrp = FieldUpdate.omitted();
    private FieldUpdate<Integer> reorderPoint = FieldUpdate.omitted();
    private FieldUpdate<Integer> targetStockLevel = FieldUpdate.omitted();
    private FieldUpdate<Integer> leadTimeDays = FieldUpdate.omitted();

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    @JsonDeserialize(using = StrictLongDeserializer.class)
    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    @Schema(types = {"boolean", "null"}, implementation = Boolean.class)
    public FieldUpdate<Boolean> getForecastingEnabled() {
        return forecastingEnabled;
    }

    @JsonDeserialize(using = FieldUpdateDeserializer.class)
    public void setForecastingEnabled(FieldUpdate<Boolean> forecastingEnabled) {
        this.forecastingEnabled = forecastingEnabled;
    }

    @Schema(types = {"number", "null"}, implementation = BigDecimal.class)
    public FieldUpdate<BigDecimal> getUnitCost() {
        return unitCost;
    }

    @JsonDeserialize(using = FieldUpdateDeserializer.class)
    public void setUnitCost(FieldUpdate<BigDecimal> unitCost) {
        this.unitCost = unitCost;
    }

    @Schema(types = {"number", "null"}, implementation = BigDecimal.class)
    public FieldUpdate<BigDecimal> getMsrp() {
        return msrp;
    }

    @JsonDeserialize(using = FieldUpdateDeserializer.class)
    public void setMsrp(FieldUpdate<BigDecimal> msrp) {
        this.msrp = msrp;
    }

    @Schema(types = {"integer", "null"}, implementation = Integer.class)
    public FieldUpdate<Integer> getReorderPoint() {
        return reorderPoint;
    }

    @JsonDeserialize(using = FieldUpdateDeserializer.class)
    public void setReorderPoint(FieldUpdate<Integer> reorderPoint) {
        this.reorderPoint = reorderPoint;
    }

    @Schema(types = {"integer", "null"}, implementation = Integer.class)
    public FieldUpdate<Integer> getTargetStockLevel() {
        return targetStockLevel;
    }

    @JsonDeserialize(using = FieldUpdateDeserializer.class)
    public void setTargetStockLevel(FieldUpdate<Integer> targetStockLevel) {
        this.targetStockLevel = targetStockLevel;
    }

    @Schema(types = {"integer", "null"}, implementation = Integer.class)
    public FieldUpdate<Integer> getLeadTimeDays() {
        return leadTimeDays;
    }

    @JsonDeserialize(using = FieldUpdateDeserializer.class)
    public void setLeadTimeDays(FieldUpdate<Integer> leadTimeDays) {
        this.leadTimeDays = leadTimeDays;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SiteProductSettingsRequest other)) {
            return false;
        }
        return Objects.equals(expectedVersion, other.expectedVersion)
                && Objects.equals(forecastingEnabled, other.forecastingEnabled)
                && Objects.equals(unitCost, other.unitCost)
                && Objects.equals(msrp, other.msrp)
                && Objects.equals(reorderPoint, other.reorderPoint)
                && Objects.equals(targetStockLevel, other.targetStockLevel)
                && Objects.equals(leadTimeDays, other.leadTimeDays);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expectedVersion, forecastingEnabled, unitCost, msrp,
                reorderPoint, targetStockLevel, leadTimeDays);
    }
}
