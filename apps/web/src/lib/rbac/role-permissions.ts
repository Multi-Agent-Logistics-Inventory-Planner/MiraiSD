import { UserRole } from "@/types/api";
import { Permission, type PermissionKey } from "./permissions";

/**
 * Maps each role to its set of permissions.
 * ADMIN has all permissions.
 * ASSISTANT_MANAGER has most admin permissions except cost visibility and user management.
 * EMPLOYEE has limited permissions.
 */
export const ROLE_PERMISSIONS: Record<UserRole, ReadonlySet<PermissionKey>> = {
  [UserRole.ADMIN]: new Set(Object.values(Permission)),

  [UserRole.ASSISTANT_MANAGER]: new Set([
    // Dashboard
    Permission.DASHBOARD_VIEW,

    // Products - full CRUD (but no cost visibility)
    Permission.PRODUCTS_VIEW,
    Permission.PRODUCTS_CREATE,
    Permission.PRODUCTS_UPDATE,
    Permission.PRODUCTS_DELETE,

    // Storage - full access
    Permission.STORAGE_VIEW,
    Permission.STORAGE_CREATE,
    Permission.STORAGE_UPDATE,
    Permission.STORAGE_DELETE,

    // Inventory operations - full access
    Permission.INVENTORY_ADJUST,
    Permission.INVENTORY_TRANSFER,

    // Shipments - full CRUD (but no cost visibility)
    Permission.SHIPMENTS_VIEW,
    Permission.SHIPMENTS_CREATE,
    Permission.SHIPMENTS_UPDATE,
    Permission.SHIPMENTS_DELETE,
    Permission.SHIPMENTS_RECEIVE,

    // Analytics
    Permission.ANALYTICS_VIEW,

    // Notifications - full access
    Permission.NOTIFICATIONS_VIEW,
    Permission.NOTIFICATIONS_MANAGE,

    // Audit Log
    Permission.AUDIT_LOG_VIEW,

    // Team - view only (no user management)
    Permission.TEAM_VIEW,

    // Settings - full access
    Permission.SETTINGS_VIEW,
    Permission.SETTINGS_MANAGE,

    // Reviews - full access
    Permission.REVIEWS_VIEW,
    Permission.REVIEWS_MANAGE,

    // Machine Displays - full access
    Permission.MACHINE_DISPLAYS_VIEW,
    Permission.MACHINE_DISPLAYS_MANAGE,

    // EXCLUDED (Admin-only):
    // - Permission.COSTS_VIEW
    // - Permission.USERS_MANAGE
  ]),

  [UserRole.EMPLOYEE]: new Set([
    // Products - view only
    Permission.PRODUCTS_VIEW,

    // Storage - view only
    Permission.STORAGE_VIEW,

    // Inventory operations - full access
    Permission.INVENTORY_ADJUST,
    Permission.INVENTORY_TRANSFER,

    // Shipments - view and receive only (no create, update, or delete)
    Permission.SHIPMENTS_VIEW,
    Permission.SHIPMENTS_RECEIVE,

    // Audit Log - view only
    Permission.AUDIT_LOG_VIEW,

    // Reviews - view only
    Permission.REVIEWS_VIEW,

    // Settings - view only
    Permission.SETTINGS_VIEW,

    // Notifications - view only
    Permission.NOTIFICATIONS_VIEW,

    // Machine Displays - full access for employees to manage displays
    Permission.MACHINE_DISPLAYS_VIEW,
    Permission.MACHINE_DISPLAYS_MANAGE,
  ]),
};

/**
 * Check if a role has a specific permission.
 */
export function hasPermission(
  role: UserRole | string | undefined,
  permission: PermissionKey
): boolean {
  if (!role) return false;
  // Normalize role to uppercase to match UserRole enum keys
  const normalizedRole = (typeof role === "string" ? role.toUpperCase() : role) as UserRole;
  const permissions = ROLE_PERMISSIONS[normalizedRole];
  return permissions?.has(permission) ?? false;
}

/**
 * Check if a role has all of the specified permissions.
 */
export function hasAllPermissions(
  role: UserRole | undefined,
  permissions: readonly PermissionKey[]
): boolean {
  if (!role) return false;
  return permissions.every((p) => hasPermission(role, p));
}

/**
 * Check if a role has any of the specified permissions.
 */
export function hasAnyPermission(
  role: UserRole | undefined,
  permissions: readonly PermissionKey[]
): boolean {
  if (!role) return false;
  return permissions.some((p) => hasPermission(role, p));
}
