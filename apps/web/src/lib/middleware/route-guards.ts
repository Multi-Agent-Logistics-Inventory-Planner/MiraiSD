import { UserRole } from "@/types/api";
import { ROUTE_PERMISSIONS, hasPermission } from "@/lib/rbac";

/**
 * Routes that don't require authentication.
 * These are always accessible regardless of user role.
 */
export const publicRoutes = ["/login", "/reset-password", "/auth/accept-invite"];

/**
 * Routes that should redirect to dashboard if already authenticated.
 * Example: /login page should redirect logged-in users to dashboard.
 */
export const authRoutes = ["/login"];

/**
 * Check if a user should have access to a specific route based on their role.
 *
 * @param pathname - The route path to check (e.g., "/products", "/analytics")
 * @param userRole - The user's role (ADMIN, EMPLOYEE, or undefined if not authenticated)
 * @returns true if the user should have access, false otherwise
 *
 * @example
 * // Unauthenticated user
 * shouldAllowRouteAccess("/login", undefined) // true (public route)
 * shouldAllowRouteAccess("/products", undefined) // false (requires auth)
 *
 * // Employee user
 * shouldAllowRouteAccess("/products", UserRole.EMPLOYEE) // true (has PRODUCTS_VIEW permission)
 * shouldAllowRouteAccess("/analytics", UserRole.EMPLOYEE) // false (lacks ANALYTICS_VIEW permission)
 *
 * // Admin user
 * shouldAllowRouteAccess("/analytics", UserRole.ADMIN) // true (has all permissions)
 */
export function shouldAllowRouteAccess(
  pathname: string,
  userRole: UserRole | undefined
): boolean {
  // Public routes are always accessible
  if (publicRoutes.some((route) => pathname.startsWith(route))) {
    return true;
  }

  // User must be authenticated for protected routes
  if (!userRole) {
    return false;
  }

  // Check if route requires specific permission
  const requiredPermission = ROUTE_PERMISSIONS[pathname];

  // If no specific permission required, allow access
  if (!requiredPermission) {
    return true;
  }

  // Check if user has required permission
  return hasPermission(userRole, requiredPermission);
}
