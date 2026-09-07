package com.mirai.inventoryservice.catalog.api;

import com.mirai.inventoryservice.catalog.application.ProductListItemDTO;
import com.mirai.inventoryservice.identity.domain.Permission;
import com.mirai.inventoryservice.identity.domain.RolePermissions;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * Nulls out cost/MSRP fields the caller's role isn't permitted to see. Promoted out of
 * {@code ProductController}'s package-private statics (docs:
 * .specs/phase-5a-catalog-module-move/spec.md T-7/AC-5) into a real, shared class so both
 * {@code ProductController} and {@code SupplierController} (GET /api/suppliers/{id}/products)
 * apply it consistently — a gap review previously found that endpoint, along with
 * createProduct/updateProduct, bypassing this when it only covered GET-by-id/list.
 *
 * <p>Deliberately a plain static-method class, matching {@link RolePermissions}'s own style,
 * rather than a Spring-managed component — there is no state to inject, and callers already use
 * {@code RolePermissions.hasPermission(...)} the same way.
 */
public final class CostVisibilityPolicy {

    private CostVisibilityPolicy() {
    }

    public static void applyCostVisibility(ProductResponseDTO dto, Authentication authentication) {
        if (dto == null) {
            return;
        }
        if (!RolePermissions.hasPermission(authentication, Permission.COSTS_VIEW)) {
            dto.setUnitCost(null);
        }
        if (!RolePermissions.hasPermission(authentication, Permission.MSRP_VIEW)) {
            dto.setMsrp(null);
        }
    }

    public static void applyCostVisibility(List<ProductResponseDTO> dtos, Authentication authentication) {
        if (dtos != null) {
            dtos.forEach(dto -> applyCostVisibility(dto, authentication));
        }
    }

    public static void applyCostVisibilityToListItem(ProductListItemDTO dto, Authentication authentication) {
        if (dto == null) {
            return;
        }
        if (!RolePermissions.hasPermission(authentication, Permission.COSTS_VIEW)) {
            dto.setUnitCost(null);
        }
        if (!RolePermissions.hasPermission(authentication, Permission.MSRP_VIEW)) {
            dto.setMsrp(null);
        }
    }
}
