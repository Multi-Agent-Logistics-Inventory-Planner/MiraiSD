"use client";

import type { ReactNode } from "react";
import { usePermissions } from "@/hooks/use-permissions";
import type { PermissionKey } from "@/lib/rbac";

interface CanProps {
  /** Single permission or array of permissions to check */
  permission: PermissionKey | readonly PermissionKey[];
  /** If true, user must have ALL permissions. If false (default), user needs ANY permission */
  all?: boolean;
  /** Content to render if user has permission */
  children: ReactNode;
  /** Optional fallback to render if user lacks permission */
  fallback?: ReactNode;
}

/**
 * Conditional render component for RBAC.
 * Renders children only if user has the required permission(s).
 *
 * @example
 * // Single permission
 * <Can permission={Permission.PRODUCTS_CREATE}>
 *   <Button>Add Product</Button>
 * </Can>
 *
 * @example
 * // Multiple permissions (any)
 * <Can permission={[Permission.PRODUCTS_CREATE, Permission.PRODUCTS_UPDATE]}>
 *   <Button>Modify Product</Button>
 * </Can>
 *
 * @example
 * // Multiple permissions (all required)
 * <Can permission={[Permission.PRODUCTS_CREATE, Permission.PRODUCTS_DELETE]} all>
 *   <Button>Full Product Access</Button>
 * </Can>
 */
export function Can({
  permission,
  all = false,
  children,
  fallback = null,
}: CanProps) {
  const { can, canAll, canAny } = usePermissions();

  const permissions = Array.isArray(permission) ? permission : [permission];
  const hasAccess = all ? canAll(permissions) : canAny(permissions);

  return hasAccess ? <>{children}</> : <>{fallback}</>;
}
