package com.mirai.inventoryservice.identity.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Backend-owned permission vocabulary. Mirrors {@code apps/web/src/lib/rbac/permissions.ts} -
 * the two definitions must stay identical; see RolePermissionsTest for the cross-side parity check.
 * The {@link #key} matches the frontend's namespaced string constants (e.g. "products:view") so
 * the wire format is shared, not just the concept. MSRP_VIEW and KUJI_PRICES_VIEW formalize field-
 * level checks that existed only as ad-hoc role comparisons in the frontend hook before this port.
 */
public enum Permission {
    DASHBOARD_VIEW("dashboard:view"),

    PRODUCTS_VIEW("products:view"),
    PRODUCTS_CREATE("products:create"),
    PRODUCTS_UPDATE("products:update"),
    PRODUCTS_DELETE("products:delete"),

    STORAGE_VIEW("storage:view"),
    STORAGE_CREATE("storage:create"),
    STORAGE_UPDATE("storage:update"),
    STORAGE_DELETE("storage:delete"),

    INVENTORY_ADJUST("inventory:adjust"),
    INVENTORY_TRANSFER("inventory:transfer"),

    SHIPMENTS_VIEW("shipments:view"),
    SHIPMENTS_CREATE("shipments:create"),
    SHIPMENTS_UPDATE("shipments:update"),
    SHIPMENTS_DELETE("shipments:delete"),
    SHIPMENTS_RECEIVE("shipments:receive"),

    ANALYTICS_VIEW("analytics:view"),

    NOTIFICATIONS_VIEW("notifications:view"),
    NOTIFICATIONS_MANAGE("notifications:manage"),

    AUDIT_LOG_VIEW("audit_log:view"),

    TEAM_VIEW("team:view"),
    TEAM_MANAGE("team:manage"),

    SETTINGS_VIEW("settings:view"),
    SETTINGS_MANAGE("settings:manage"),

    REVIEWS_VIEW("reviews:view"),
    REVIEWS_MANAGE("reviews:manage"),

    MACHINE_DISPLAYS_VIEW("machine_displays:view"),
    MACHINE_DISPLAYS_MANAGE("machine_displays:manage"),

    COSTS_VIEW("costs:view"),
    MSRP_VIEW("msrp:view"),
    KUJI_PRICES_VIEW("kuji_prices:view"),

    USERS_MANAGE("users:manage");

    private final String key;

    Permission(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public static Permission fromKey(String key) {
        for (Permission permission : values()) {
            if (permission.key.equals(key)) {
                return permission;
            }
        }
        throw new IllegalArgumentException("Unknown permission key: " + key);
    }
}
