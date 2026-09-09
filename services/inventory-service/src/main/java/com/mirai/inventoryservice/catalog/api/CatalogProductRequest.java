package com.mirai.inventoryservice.catalog.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.mirai.inventoryservice.catalog.domain.KujiType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Master-identity-only request body for the global {@code /api/v1/catalog/products} routes
 * (spec.md "Product decisions"). Deliberately narrower than the legacy {@link ProductRequestDTO}:
 * it has no setters for site-owned fields (isActive, unitCost, msrp, reorderPoint,
 * targetStockLevel, leadTimeDays, forecastingEnabled, quantity, initialStock). The app's Jackson
 * config disables {@code FAIL_ON_UNKNOWN_PROPERTIES} globally (and a class-level
 * {@code @JsonIgnoreProperties(ignoreUnknown = false)} can't override that: Jackson can't tell an
 * explicit "false" from the annotation's own default), which would silently discard those fields
 * instead of rejecting them - the exact outcome AC-1b forbids. {@code @JsonAnySetter} intercepts
 * every unmapped property directly, ahead of that global feature, so it rejects unconditionally.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogProductRequest {

    @JsonAnySetter
    private void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException(
                "Unrecognized field '" + name + "' - global catalog product routes accept only "
                        + "master-identity fields; site-owned fields must be set via "
                        + "/api/v1/sites/{siteId}/products/{productId}/assortment or /settings");
    }
    private String sku;

    /** Required for root products. Optional for prizes (inherits from parent). */
    private UUID categoryId;

    private UUID parentId;

    @Size(max = 50)
    private String letter;

    @Min(value = 0, message = "Template quantity must be 0 or greater")
    private Integer templateQuantity;

    /** Only valid when parentId is null. */
    private KujiType kujiType;

    @Min(value = 1, message = "packsPerBox must be 1 or greater")
    private Integer packsPerBox;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @Pattern(
        regexp = "^(https://\\S+)?$",
        message = "Image URL must be an https URL"
    )
    private String imageUrl;

    private String notes;
}
