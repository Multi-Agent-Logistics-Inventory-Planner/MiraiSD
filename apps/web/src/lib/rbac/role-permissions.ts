import { UserRole } from "@/types/api";
import { Permission, type PermissionKey } from "./permissions";

/**
 * Maps each role to its set of permissions.
 * ADMIN has all permissions.
 * EMPLOYEE has limited permissions.
 */
export const ROLE_PERMISSIONS: Record<UserRole, ReadonlySet<PermissionKey>> = {
  [UserRole.ADMIN]: new Set(Object.values(Permission)),

  [UserRole.EMPLOYEE]: new Set([
    // Products - view only
    Permission.PRODUCTS_VIEW,

    // Storage - view only
    Permission.STORAGE_VIEW,

    // Inventory operations - full access
    Permission.INVENTORY_ADJUST,
    Permission.INVENTORY_TRANSFER,

    // Shipments - full access
    Permission.SHIPMENTS_VIEW,
    Permission.SHIPMENTS_RECEIVE,
  ]),
};

/**
 * Check if a role has a specific permission.
 */
export function hasPermission(
  role: UserRole | undefined,
  permission: PermissionKey
): boolean {
  if (!role) return false;
  const permissions = ROLE_PERMISSIONS[role];
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
