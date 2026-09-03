import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { UserRole } from "@/types/api";
import { Permission } from "@/lib/rbac";
import { usePermissions } from "./use-permissions";

// Mock useAuth hook
vi.mock("./use-auth", () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from "./use-auth";
const mockUseAuth = vi.mocked(useAuth);

// Permissions an ADMIN/EMPLOYEE session response would actually carry, per
// RolePermissions.java. Kept local to this test file rather than importing
// lib/rbac/role-permissions.ts's ROLE_PERMISSIONS, since that table is no longer
// usePermissions()'s source of truth (see the "no permissions array" describe block
// below) - these mocks stand in for what the backend would send.
const ADMIN_PERMISSIONS = Object.values(Permission);
const EMPLOYEE_PERMISSIONS = [
  Permission.PRODUCTS_VIEW,
  Permission.STORAGE_VIEW,
  Permission.INVENTORY_ADJUST,
  Permission.INVENTORY_TRANSFER,
  Permission.SHIPMENTS_VIEW,
  Permission.SHIPMENTS_RECEIVE,
  Permission.AUDIT_LOG_VIEW,
  Permission.REVIEWS_VIEW,
  Permission.SETTINGS_VIEW,
  Permission.NOTIFICATIONS_VIEW,
  Permission.MACHINE_DISPLAYS_VIEW,
  Permission.MACHINE_DISPLAYS_MANAGE,
  Permission.TEAM_VIEW,
];

describe("usePermissions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("when user is ADMIN", () => {
    beforeEach(() => {
      mockUseAuth.mockReturnValue({
        user: {
          id: "admin-1",
          role: UserRole.ADMIN,
          email: "admin@test.com",
          permissions: ADMIN_PERMISSIONS,
        },
        session: null,
        isLoading: false,
        signOut: vi.fn(),
        refreshAuth: vi.fn(),
      } as ReturnType<typeof useAuth>);
    });

    it("should return isAdmin as true", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.isAdmin).toBe(true);
    });

    it("should return role as ADMIN", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.role).toBe(UserRole.ADMIN);
    });

    it("can() should return true for all permissions", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.can(Permission.DASHBOARD_VIEW)).toBe(true);
      expect(result.current.can(Permission.PRODUCTS_CREATE)).toBe(true);
      expect(result.current.can(Permission.SETTINGS_MANAGE)).toBe(true);
    });

    it("canAccessRoute() should return true for all routes", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.canAccessRoute("/")).toBe(true);
      expect(result.current.canAccessRoute("/analytics")).toBe(true);
      expect(result.current.canAccessRoute("/settings")).toBe(true);
    });
  });

  describe("when user is EMPLOYEE", () => {
    beforeEach(() => {
      mockUseAuth.mockReturnValue({
        user: {
          id: "employee-1",
          role: UserRole.EMPLOYEE,
          email: "employee@test.com",
          permissions: EMPLOYEE_PERMISSIONS,
        },
        session: null,
        isLoading: false,
        signOut: vi.fn(),
        refreshAuth: vi.fn(),
      } as ReturnType<typeof useAuth>);
    });

    it("should return isAdmin as false", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.isAdmin).toBe(false);
    });

    it("should return role as EMPLOYEE", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.role).toBe(UserRole.EMPLOYEE);
    });

    it("can() should return true for allowed permissions", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.can(Permission.PRODUCTS_VIEW)).toBe(true);
      expect(result.current.can(Permission.STORAGE_VIEW)).toBe(true);
      expect(result.current.can(Permission.SHIPMENTS_VIEW)).toBe(true);
    });

    it("can() should return false for restricted permissions", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.can(Permission.DASHBOARD_VIEW)).toBe(false);
      expect(result.current.can(Permission.PRODUCTS_CREATE)).toBe(false);
      expect(result.current.can(Permission.ANALYTICS_VIEW)).toBe(false);
    });

    it("canAccessRoute() should return false for admin-only routes", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.canAccessRoute("/")).toBe(false);
      expect(result.current.canAccessRoute("/analytics")).toBe(false);
    });

    it("canAccessRoute() should return true for allowed routes", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.canAccessRoute("/products")).toBe(true);
      expect(result.current.canAccessRoute("/storage")).toBe(true);
      expect(result.current.canAccessRoute("/shipments")).toBe(true);
    });

    it("canAccessRoute() should return true for unknown routes", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.canAccessRoute("/unknown-route")).toBe(true);
    });
  });

  describe("when user is not logged in", () => {
    beforeEach(() => {
      mockUseAuth.mockReturnValue({
        user: null,
        session: null,
        isLoading: false,
        signOut: vi.fn(),
        refreshAuth: vi.fn(),
      } as ReturnType<typeof useAuth>);
    });

    it("should return isAdmin as false", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.isAdmin).toBe(false);
    });

    it("should return role as undefined", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.role).toBeUndefined();
    });

    it("can() should return false for all permissions", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.can(Permission.PRODUCTS_VIEW)).toBe(false);
      expect(result.current.can(Permission.DASHBOARD_VIEW)).toBe(false);
    });
  });

  describe("when the session supplies a backend-resolved permissions array", () => {
    it("uses the array as the sole source of truth, not a local role table", () => {
      // The backend (RolePermissions.java) is authoritative. usePermissions() must
      // reflect exactly what it sent, with no local recomputation from role that
      // could disagree with it.
      mockUseAuth.mockReturnValue({
        user: {
          id: "employee-1",
          role: UserRole.EMPLOYEE,
          email: "employee@test.com",
          permissions: [Permission.PRODUCTS_VIEW, Permission.ANALYTICS_VIEW],
        },
        session: null,
        isLoading: false,
        signOut: vi.fn(),
        refreshAuth: vi.fn(),
      } as ReturnType<typeof useAuth>);

      const { result } = renderHook(() => usePermissions());

      expect(result.current.can(Permission.PRODUCTS_VIEW)).toBe(true);
      // Backend granted this even though it's unusual for EMPLOYEE - the hook must
      // honor it rather than silently overriding with a local guess.
      expect(result.current.can(Permission.ANALYTICS_VIEW)).toBe(true);
      // Not in the array - denied, full stop.
      expect(result.current.can(Permission.STORAGE_VIEW)).toBe(false);
    });

    it("derives canViewCosts/canViewMsrp/canManageUsers from the array", () => {
      mockUseAuth.mockReturnValue({
        user: {
          id: "assistant-1",
          role: UserRole.ASSISTANT_MANAGER,
          email: "assistant@test.com",
          permissions: [Permission.MSRP_VIEW, Permission.KUJI_PRICES_VIEW],
        },
        session: null,
        isLoading: false,
        signOut: vi.fn(),
        refreshAuth: vi.fn(),
      } as ReturnType<typeof useAuth>);

      const { result } = renderHook(() => usePermissions());

      expect(result.current.canViewMsrp).toBe(true);
      expect(result.current.canViewKujiPrices).toBe(true);
      expect(result.current.canViewCosts).toBe(false);
      expect(result.current.canManageUsers).toBe(false);
    });
  });

  describe("when the session has not supplied a permissions array (loading, or stale cache)", () => {
    // Deliberately deny-all, not a fallback to a local role table: a second,
    // independently-maintained frontend matrix is exactly the drift risk this port
    // was meant to close (two suites can each pass while silently disagreeing).
    // Components are expected to gate on useAuth().isLoading to avoid a visible
    // flash of "no access" during the load window this covers.
    beforeEach(() => {
      mockUseAuth.mockReturnValue({
        user: { id: "employee-1", role: UserRole.EMPLOYEE, email: "employee@test.com" },
        session: null,
        isLoading: false,
        signOut: vi.fn(),
        refreshAuth: vi.fn(),
      } as ReturnType<typeof useAuth>);
    });

    it("can() denies every permission", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.can(Permission.PRODUCTS_VIEW)).toBe(false);
      expect(result.current.can(Permission.DASHBOARD_VIEW)).toBe(false);
    });

    it("canViewCosts/canViewMsrp/canViewKujiPrices/canManageUsers are all false", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.canViewCosts).toBe(false);
      expect(result.current.canViewMsrp).toBe(false);
      expect(result.current.canViewKujiPrices).toBe(false);
      expect(result.current.canManageUsers).toBe(false);
    });

    it("canAccessRoute() denies gated routes but still allows ungated ones", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.canAccessRoute("/products")).toBe(false);
      // No entry in ROUTE_PERMISSIONS for this path - canAccessRoute only denies
      // routes it actually gates, not everything by default.
      expect(result.current.canAccessRoute("/unknown-route")).toBe(true);
    });

    it("role and isAdmin are unaffected - they come from the session directly, not the permission set", () => {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.role).toBe(UserRole.EMPLOYEE);
      expect(result.current.isAdmin).toBe(false);
    });
  });

  describe("canAll and canAny", () => {
    beforeEach(() => {
      mockUseAuth.mockReturnValue({
        user: {
          id: "employee-1",
          role: UserRole.EMPLOYEE,
          email: "employee@test.com",
          permissions: EMPLOYEE_PERMISSIONS,
        },
        session: null,
        isLoading: false,
        signOut: vi.fn(),
        refreshAuth: vi.fn(),
      } as ReturnType<typeof useAuth>);
    });

    it("canAll() should return true when user has all permissions", () => {
      const { result } = renderHook(() => usePermissions());
      expect(
        result.current.canAll([
          Permission.PRODUCTS_VIEW,
          Permission.STORAGE_VIEW,
        ])
      ).toBe(true);
    });

    it("canAll() should return false when user lacks any permission", () => {
      const { result } = renderHook(() => usePermissions());
      expect(
        result.current.canAll([
          Permission.PRODUCTS_VIEW,
          Permission.PRODUCTS_CREATE,
        ])
      ).toBe(false);
    });

    it("canAny() should return true when user has at least one permission", () => {
      const { result } = renderHook(() => usePermissions());
      expect(
        result.current.canAny([
          Permission.PRODUCTS_VIEW,
          Permission.PRODUCTS_CREATE,
        ])
      ).toBe(true);
    });

    it("canAny() should return false when user has none of the permissions", () => {
      const { result } = renderHook(() => usePermissions());
      expect(
        result.current.canAny([
          Permission.DASHBOARD_VIEW,
          Permission.ANALYTICS_VIEW,
        ])
      ).toBe(false);
    });
  });
});
