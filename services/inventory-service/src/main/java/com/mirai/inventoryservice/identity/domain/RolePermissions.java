package com.mirai.inventoryservice.identity.domain;

import org.springframework.security.core.Authentication;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.mirai.inventoryservice.identity.domain.Permission.*;

/**
 * Backend-owned role-to-permission matrix. Mirrors
 * {@code apps/web/src/lib/rbac/role-permissions.ts}: ADMIN has every permission,
 * ASSISTANT_MANAGER has everything except cost visibility and user management,
 * EMPLOYEE has a limited operational subset. See RolePermissionsTest for the
 * cross-side parity assertions ported from role-permissions.test.ts.
 */
public final class RolePermissions {

    private static final Map<UserRole, Set<Permission>> BY_ROLE = new EnumMap<>(UserRole.class);

    static {
        BY_ROLE.put(UserRole.ADMIN, EnumSet.allOf(Permission.class));

        BY_ROLE.put(UserRole.ASSISTANT_MANAGER, EnumSet.of(
                DASHBOARD_VIEW,

                PRODUCTS_VIEW, PRODUCTS_CREATE, PRODUCTS_UPDATE, PRODUCTS_DELETE,

                STORAGE_VIEW, STORAGE_CREATE, STORAGE_UPDATE, STORAGE_DELETE,

                INVENTORY_ADJUST, INVENTORY_TRANSFER,

                SHIPMENTS_VIEW, SHIPMENTS_CREATE, SHIPMENTS_UPDATE, SHIPMENTS_DELETE, SHIPMENTS_RECEIVE,

                ANALYTICS_VIEW,

                NOTIFICATIONS_VIEW, NOTIFICATIONS_MANAGE,

                AUDIT_LOG_VIEW,

                TEAM_VIEW,

                SETTINGS_VIEW, SETTINGS_MANAGE,

                REVIEWS_VIEW, REVIEWS_MANAGE,

                MACHINE_DISPLAYS_VIEW, MACHINE_DISPLAYS_MANAGE,

                MSRP_VIEW, KUJI_PRICES_VIEW
                // Excluded (admin-only): COSTS_VIEW, USERS_MANAGE
        ));

        BY_ROLE.put(UserRole.EMPLOYEE, EnumSet.of(
                PRODUCTS_VIEW,

                STORAGE_VIEW,

                INVENTORY_ADJUST, INVENTORY_TRANSFER,

                SHIPMENTS_VIEW, SHIPMENTS_RECEIVE,

                AUDIT_LOG_VIEW,

                REVIEWS_VIEW,

                SETTINGS_VIEW,

                NOTIFICATIONS_VIEW,

                MACHINE_DISPLAYS_VIEW, MACHINE_DISPLAYS_MANAGE,

                TEAM_VIEW
        ));
    }

    private RolePermissions() {
    }

    public static Set<Permission> forRole(UserRole role) {
        if (role == null) {
            return Set.of();
        }
        return BY_ROLE.getOrDefault(role, Set.of());
    }

    public static boolean hasPermission(UserRole role, Permission permission) {
        return forRole(role).contains(permission);
    }

    /**
     * Safe role-name variant: JwtAuthenticationFilter falls back to the literal role "USER"
     * when no backend User record matches the caller (see AuthenticatedPrincipal.role()), which
     * isn't a valid UserRole constant. Treat any unrecognized role name as having no permissions
     * rather than throwing, so an unregistered-but-authenticated caller degrades to "sees nothing
     * gated" instead of a 500.
     */
    public static boolean hasPermission(String roleName, Permission permission) {
        if (roleName == null) {
            return false;
        }
        try {
            return hasPermission(UserRole.valueOf(roleName.toUpperCase()), permission);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean hasPermission(Authentication authentication, Permission permission) {
        if (authentication == null) {
            return false;
        }
        if (!(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            return false;
        }
        return hasPermission(principal.role(), permission);
    }
}
