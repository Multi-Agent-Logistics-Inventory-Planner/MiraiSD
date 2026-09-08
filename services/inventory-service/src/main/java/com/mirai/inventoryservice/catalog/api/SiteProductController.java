package com.mirai.inventoryservice.catalog.api;

import com.mirai.inventoryservice.catalog.application.EffectiveProductSettings;
import com.mirai.inventoryservice.catalog.application.ProductService;
import com.mirai.inventoryservice.catalog.application.SiteAssortment;
import com.mirai.inventoryservice.catalog.application.SiteProductService;
import com.mirai.inventoryservice.catalog.application.SiteProductSettingsUpdate;
import com.mirai.inventoryservice.catalog.domain.Product;
import com.mirai.inventoryservice.catalog.domain.SiteProduct;
import com.mirai.inventoryservice.identity.domain.Permission;
import com.mirai.inventoryservice.identity.domain.RolePermissions;
import com.mirai.inventoryservice.shared.web.AuthorizedSiteContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Site-scoped assortment and settings routes (spec.md phase-5d AC-2/AC-2b/AC-2c). Reads
 * {@code siteId} from {@link AuthorizedSiteContextHolder}, established by
 * {@code SiteAccessAuthorizationFilter} for every {@code /api/v1/sites/{siteId}/**} request - the
 * {@code siteId} path variable is still declared on every handler so springdoc/the generated
 * client expose it, matching {@code SiteStorageLocationController}'s precedent.
 * <p>
 * {@code SiteAccessAuthorizationFilter} only establishes which site the caller may reach; it does
 * not authorize a mutation on it (AC-2b). Reads require {@code PRODUCTS_VIEW}
 * (ADMIN/ASSISTANT_MANAGER/EMPLOYEE, all three roles hold it); both mutations require
 * {@code PRODUCTS_UPDATE} (ADMIN/ASSISTANT_MANAGER only - EMPLOYEE has view but not update).
 */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/products")
public class SiteProductController {

    private final ProductService productService;
    private final SiteAssortment siteAssortment;
    private final SiteProductService siteProductService;

    public SiteProductController(ProductService productService,
                                  SiteAssortment siteAssortment,
                                  SiteProductService siteProductService) {
        this.productService = productService;
        this.siteAssortment = siteAssortment;
        this.siteProductService = siteProductService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<SiteProductResponse>> getSiteProducts(
            @PathVariable UUID siteId, Authentication authentication) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        List<Product> products = productService.getAllProducts();
        Map<UUID, SiteProduct> rows = siteAssortment.rowsForSite(contextSiteId);

        List<SiteProductResponse> body = products.stream()
                .map(product -> toResponse(contextSiteId, product, rows.get(product.getId())))
                .peek(dto -> applyCostVisibility(dto, authentication))
                .toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<SiteProductResponse> getSiteProduct(
            @PathVariable UUID siteId, @PathVariable UUID productId, Authentication authentication) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        // getProductById throws ProductNotFoundException (-> 404) only when the product doesn't
        // exist globally - a globally-valid product this site has never carried is not 404 (AC-2c).
        Product product = productService.getProductById(productId);
        SiteProduct row = siteAssortment.rowFor(contextSiteId, productId).orElse(null);
        SiteProductResponse dto = toResponse(contextSiteId, product, row);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{productId}/assortment")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<SiteProductResponse> updateAssortment(
            @PathVariable UUID siteId, @PathVariable UUID productId,
            @Valid @RequestBody SiteAssortmentRequest requestDTO, Authentication authentication) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Product product = productService.getProductById(productId);
        SiteProduct row = siteProductService.setStocked(contextSiteId, productId, requestDTO.getIsStocked());
        SiteProductResponse dto = toResponse(contextSiteId, product, row);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{productId}/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_MANAGER')")
    public ResponseEntity<SiteProductResponse> updateSettings(
            @PathVariable UUID siteId, @PathVariable UUID productId,
            @Valid @RequestBody SiteProductSettingsRequest requestDTO, Authentication authentication) {
        UUID contextSiteId = AuthorizedSiteContextHolder.require().siteId();
        Product product = productService.getProductById(productId);

        SiteProductSettingsUpdate update = new SiteProductSettingsUpdate(
                requestDTO.getExpectedVersion(),
                requestDTO.getForecastingEnabled(),
                requestDTO.getUnitCost(),
                requestDTO.getMsrp(),
                requestDTO.getReorderPoint(),
                requestDTO.getTargetStockLevel(),
                requestDTO.getLeadTimeDays());

        SiteProduct row = siteProductService.updateSettings(contextSiteId, productId, update);
        SiteProductResponse dto = toResponse(contextSiteId, product, row);
        applyCostVisibility(dto, authentication);
        return ResponseEntity.ok(dto);
    }

    private SiteProductResponse toResponse(UUID siteId, Product product, SiteProduct row) {
        EffectiveProductSettings effective = row != null
                ? EffectiveProductSettings.from(row, product)
                : EffectiveProductSettings.absent(siteId, product);
        return SiteProductResponse.builder()
                .productId(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .isStocked(effective.isStocked())
                .forecastingEnabled(effective.forecastingEnabled())
                .unitCost(effective.unitCost())
                .msrp(effective.msrp())
                .reorderPoint(effective.reorderPoint())
                .targetStockLevel(effective.targetStockLevel())
                .leadTimeDays(effective.leadTimeDays())
                .version(row != null ? row.getVersion() : null)
                .build();
    }

    private void applyCostVisibility(SiteProductResponse dto, Authentication authentication) {
        if (!RolePermissions.hasPermission(authentication, Permission.COSTS_VIEW)) {
            dto.setUnitCost(null);
        }
        if (!RolePermissions.hasPermission(authentication, Permission.MSRP_VIEW)) {
            dto.setMsrp(null);
        }
    }
}
