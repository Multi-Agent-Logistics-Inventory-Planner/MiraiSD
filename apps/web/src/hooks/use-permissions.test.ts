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

describe("usePermissions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("when user is ADMIN", () => {
    beforeEach(() => {
      mockUseAuth.mockReturnValue({
        user: { role: UserRole.ADMIN, email: "admin@test.com" },
        isLoading: false,
        signOut: vi.fn(),
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
        user: { role: UserRole.EMPLOYEE, email: "employee@test.com" },
        isLoading: false,
        signOut: vi.fn(),
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
      expect(result.current.can(Permission.SHIPMENTS_CREATE)).toBe(true);
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
        isLoading: false,
        signOut: vi.fn(),
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

  describe("canAll and canAny", () => {
    beforeEach(() => {
      mockUseAuth.mockReturnValue({
        user: { role: UserRole.EMPLOYEE, email: "employee@test.com" },
        isLoading: false,
        signOut: vi.fn(),
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
