/**
 * Permission constants for RBAC system.
 * Permissions are namespaced by feature area.
 */
export const Permission = {
  // Dashboard
  DASHBOARD_VIEW: "dashboard:view",

  // Products
  PRODUCTS_VIEW: "products:view",
  PRODUCTS_CREATE: "products:create",
  PRODUCTS_UPDATE: "products:update",
  PRODUCTS_DELETE: "products:delete",

  // Storage
  STORAGE_VIEW: "storage:view",
  STORAGE_CREATE: "storage:create",
  STORAGE_UPDATE: "storage:update",
  STORAGE_DELETE: "storage:delete",

  // Shipments
  SHIPMENTS_VIEW: "shipments:view",
  SHIPMENTS_CREATE: "shipments:create",
  SHIPMENTS_UPDATE: "shipments:update",
  SHIPMENTS_DELETE: "shipments:delete",

  // Analytics
  ANALYTICS_VIEW: "analytics:view",

  // Alerts
  ALERTS_VIEW: "alerts:view",
  ALERTS_MANAGE: "alerts:manage",

  // Audit Log
  AUDIT_LOG_VIEW: "audit_log:view",

  // Team
  TEAM_VIEW: "team:view",
  TEAM_MANAGE: "team:manage",

  // Settings
  SETTINGS_VIEW: "settings:view",
  SETTINGS_MANAGE: "settings:manage",
} as const;

export type PermissionKey = (typeof Permission)[keyof typeof Permission];

/**
 * Maps routes to required permissions.
 * Used for route-level access control.
 */
export const ROUTE_PERMISSIONS: Record<string, PermissionKey> = {
  "/": Permission.DASHBOARD_VIEW,
  "/products": Permission.PRODUCTS_VIEW,
  "/storage": Permission.STORAGE_VIEW,
  "/shipments": Permission.SHIPMENTS_VIEW,
  "/analytics": Permission.ANALYTICS_VIEW,
  "/alerts": Permission.ALERTS_VIEW,
  "/audit-log": Permission.AUDIT_LOG_VIEW,
  "/team": Permission.TEAM_VIEW,
  "/settings": Permission.SETTINGS_VIEW,
};
