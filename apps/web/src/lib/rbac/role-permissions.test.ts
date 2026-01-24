import { describe, it, expect } from "vitest";
import { UserRole } from "@/types/api";
import { Permission } from "./permissions";
import {
  ROLE_PERMISSIONS,
  hasPermission,
  hasAllPermissions,
  hasAnyPermission,
} from "./role-permissions";

describe("ROLE_PERMISSIONS", () => {
  it("should give ADMIN all permissions", () => {
    const adminPermissions = ROLE_PERMISSIONS[UserRole.ADMIN];
    const allPermissions = Object.values(Permission);

    expect(adminPermissions.size).toBe(allPermissions.length);
    allPermissions.forEach((permission) => {
      expect(adminPermissions.has(permission)).toBe(true);
    });
  });

  it("should give EMPLOYEE limited permissions", () => {
    const employeePermissions = ROLE_PERMISSIONS[UserRole.EMPLOYEE];

    // Should have these permissions
    expect(employeePermissions.has(Permission.PRODUCTS_VIEW)).toBe(true);
    expect(employeePermissions.has(Permission.STORAGE_VIEW)).toBe(true);
    expect(employeePermissions.has(Permission.SHIPMENTS_VIEW)).toBe(true);
    expect(employeePermissions.has(Permission.SHIPMENTS_CREATE)).toBe(true);
    expect(employeePermissions.has(Permission.SHIPMENTS_UPDATE)).toBe(true);

    // Should NOT have these permissions
    expect(employeePermissions.has(Permission.DASHBOARD_VIEW)).toBe(false);
    expect(employeePermissions.has(Permission.PRODUCTS_CREATE)).toBe(false);
    expect(employeePermissions.has(Permission.ANALYTICS_VIEW)).toBe(false);
    expect(employeePermissions.has(Permission.TEAM_VIEW)).toBe(false);
    expect(employeePermissions.has(Permission.SETTINGS_VIEW)).toBe(false);
  });
});

describe("hasPermission", () => {
  it("should return true when role has the permission", () => {
    expect(hasPermission(UserRole.ADMIN, Permission.DASHBOARD_VIEW)).toBe(true);
    expect(hasPermission(UserRole.EMPLOYEE, Permission.PRODUCTS_VIEW)).toBe(
      true
    );
  });

  it("should return false when role lacks the permission", () => {
    expect(hasPermission(UserRole.EMPLOYEE, Permission.DASHBOARD_VIEW)).toBe(
      false
    );
    expect(hasPermission(UserRole.EMPLOYEE, Permission.PRODUCTS_CREATE)).toBe(
      false
    );
  });

  it("should return false when role is undefined", () => {
    expect(hasPermission(undefined, Permission.DASHBOARD_VIEW)).toBe(false);
  });
});

describe("hasAllPermissions", () => {
  it("should return true when role has all permissions", () => {
    expect(
      hasAllPermissions(UserRole.ADMIN, [
        Permission.DASHBOARD_VIEW,
        Permission.ANALYTICS_VIEW,
      ])
    ).toBe(true);

    expect(
      hasAllPermissions(UserRole.EMPLOYEE, [
        Permission.PRODUCTS_VIEW,
        Permission.STORAGE_VIEW,
      ])
    ).toBe(true);
  });

  it("should return false when role lacks any permission", () => {
    expect(
      hasAllPermissions(UserRole.EMPLOYEE, [
        Permission.PRODUCTS_VIEW,
        Permission.PRODUCTS_CREATE, // Employee doesn't have this
      ])
    ).toBe(false);
  });

  it("should return true for empty permissions array", () => {
    expect(hasAllPermissions(UserRole.EMPLOYEE, [])).toBe(true);
  });

  it("should return false when role is undefined", () => {
    expect(hasAllPermissions(undefined, [Permission.PRODUCTS_VIEW])).toBe(
      false
    );
  });
});

describe("hasAnyPermission", () => {
  it("should return true when role has at least one permission", () => {
    expect(
      hasAnyPermission(UserRole.EMPLOYEE, [
        Permission.PRODUCTS_CREATE, // Doesn't have
        Permission.PRODUCTS_VIEW, // Has this
      ])
    ).toBe(true);
  });

  it("should return false when role has none of the permissions", () => {
    expect(
      hasAnyPermission(UserRole.EMPLOYEE, [
        Permission.DASHBOARD_VIEW,
        Permission.ANALYTICS_VIEW,
      ])
    ).toBe(false);
  });

  it("should return false for empty permissions array", () => {
    expect(hasAnyPermission(UserRole.EMPLOYEE, [])).toBe(false);
  });

  it("should return false when role is undefined", () => {
    expect(hasAnyPermission(undefined, [Permission.PRODUCTS_VIEW])).toBe(false);
  });
});
