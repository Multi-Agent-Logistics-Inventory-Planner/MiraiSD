"use client";

import { useMemo } from "react";
import { useAuth } from "./use-auth";
import { UserRole } from "@/types/api";
import {
  Permission,
  ROUTE_PERMISSIONS,
  hasPermission,
  hasAllPermissions,
  hasAnyPermission,
  type PermissionKey,
} from "@/lib/rbac";

export interface UsePermissionsResult {
  /** Check if user has a specific permission */
  can: (permission: PermissionKey) => boolean;
  /** Check if user has all of the specified permissions */
  canAll: (permissions: readonly PermissionKey[]) => boolean;
  /** Check if user has any of the specified permissions */
  canAny: (permissions: readonly PermissionKey[]) => boolean;
  /** Check if user can access a specific route */
  canAccessRoute: (route: string) => boolean;
  /** Whether user is an admin */
  isAdmin: boolean;
  /** Current user role */
  role: UserRole | undefined;
}

/**
 * Hook for checking user permissions.
 * Wraps the RBAC permission checks with the current user's role.
 */
export function usePermissions(): UsePermissionsResult {
  const { user } = useAuth();
  const role = user?.role;

  return useMemo(
    () => ({
      can: (permission: PermissionKey) => hasPermission(role, permission),
      canAll: (permissions: readonly PermissionKey[]) =>
        hasAllPermissions(role, permissions),
      canAny: (permissions: readonly PermissionKey[]) =>
        hasAnyPermission(role, permissions),
      canAccessRoute: (route: string) => {
        const requiredPermission = ROUTE_PERMISSIONS[route];
        if (!requiredPermission) return true;
        return hasPermission(role, requiredPermission);
      },
      isAdmin: role === UserRole.ADMIN,
      role,
    }),
    [role]
  );
}

export { Permission };
