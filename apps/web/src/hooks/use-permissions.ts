"use client";

import { useMemo } from "react";
import { useAuth } from "./use-auth";
import { UserRole } from "@/types/api";
import { Permission, ROUTE_PERMISSIONS, type PermissionKey } from "@/lib/rbac";

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
  /** Whether user can view cost fields (admin-only) */
  canViewCosts: boolean;
  /** Whether user can view MSRP (admin and assistant manager) */
  canViewMsrp: boolean;
  /** Whether user can view kuji price values (admin and assistant manager) */
  canViewKujiPrices: boolean;
  /** Whether user can manage users (admin-only) */
  canManageUsers: boolean;
  /** Current user role */
  role: UserRole | undefined;
}

/**
 * Hook for checking user permissions.
 *
 * The permission set is resolved exclusively by the backend (RolePermissions.java) and
 * delivered on the session response - this hook is a lookup against that resolved set,
 * never a local recomputation from role. There is deliberately no role-based fallback
 * table here: a second frontend-side definition of "who can do what" is exactly the
 * drift risk this port was meant to close, since two independently-passing test suites
 * can't prove two independent matrices stay identical. When the session hasn't supplied
 * permissions yet (still loading, or a stale cache from before this field existed), every
 * check safely denies rather than trusting a local guess - callers already gate on
 * useAuth().isLoading to avoid a flash of "no access" during the load window.
 */
export function usePermissions(): UsePermissionsResult {
  const { user } = useAuth();
  const role = user?.role;
  const sessionPermissions = user?.permissions;

  return useMemo(() => {
    const can = (permission: PermissionKey): boolean =>
      sessionPermissions?.includes(permission) ?? false;

    return {
      can,
      canAll: (permissions: readonly PermissionKey[]) =>
        permissions.every((p) => can(p)),
      canAny: (permissions: readonly PermissionKey[]) =>
        permissions.some((p) => can(p)),
      canAccessRoute: (route: string) => {
        const requiredPermission = ROUTE_PERMISSIONS[route];
        if (!requiredPermission) return true;
        return can(requiredPermission);
      },
      isAdmin: role === UserRole.ADMIN,
      canViewCosts: can(Permission.COSTS_VIEW),
      canViewMsrp: can(Permission.MSRP_VIEW),
      canViewKujiPrices: can(Permission.KUJI_PRICES_VIEW),
      canManageUsers: can(Permission.USERS_MANAGE),
      role,
    };
  }, [role, sessionPermissions]);
}

export { Permission };
