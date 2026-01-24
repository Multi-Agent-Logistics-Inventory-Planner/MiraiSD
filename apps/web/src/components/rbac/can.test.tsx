import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { UserRole } from "@/types/api";
import { Permission } from "@/lib/rbac";
import { Can } from "./can";

// Mock useAuth hook
vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from "@/hooks/use-auth";
const mockUseAuth = vi.mocked(useAuth);

describe("Can component", () => {
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

    it("should render children for any permission", () => {
      render(
        <Can permission={Permission.DASHBOARD_VIEW}>
          <button>Admin Button</button>
        </Can>
      );
      expect(screen.getByText("Admin Button")).toBeInTheDocument();
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

    it("should render children when user has permission", () => {
      render(
        <Can permission={Permission.PRODUCTS_VIEW}>
          <button>View Products</button>
        </Can>
      );
      expect(screen.getByText("View Products")).toBeInTheDocument();
    });

    it("should not render children when user lacks permission", () => {
      render(
        <Can permission={Permission.PRODUCTS_CREATE}>
          <button>Add Product</button>
        </Can>
      );
      expect(screen.queryByText("Add Product")).not.toBeInTheDocument();
    });

    it("should render fallback when user lacks permission", () => {
      render(
        <Can
          permission={Permission.PRODUCTS_CREATE}
          fallback={<span>No access</span>}
        >
          <button>Add Product</button>
        </Can>
      );
      expect(screen.queryByText("Add Product")).not.toBeInTheDocument();
      expect(screen.getByText("No access")).toBeInTheDocument();
    });
  });

  describe("with multiple permissions", () => {
    beforeEach(() => {
      mockUseAuth.mockReturnValue({
        user: { role: UserRole.EMPLOYEE, email: "employee@test.com" },
        isLoading: false,
        signOut: vi.fn(),
      } as ReturnType<typeof useAuth>);
    });

    it("should render when user has any permission (default)", () => {
      render(
        <Can
          permission={[Permission.PRODUCTS_CREATE, Permission.PRODUCTS_VIEW]}
        >
          <button>Product Action</button>
        </Can>
      );
      expect(screen.getByText("Product Action")).toBeInTheDocument();
    });

    it("should not render when user has none of the permissions", () => {
      render(
        <Can
          permission={[Permission.PRODUCTS_CREATE, Permission.DASHBOARD_VIEW]}
        >
          <button>Admin Action</button>
        </Can>
      );
      expect(screen.queryByText("Admin Action")).not.toBeInTheDocument();
    });

    it("should render when user has all permissions (all=true)", () => {
      render(
        <Can
          permission={[Permission.PRODUCTS_VIEW, Permission.STORAGE_VIEW]}
          all
        >
          <button>View Both</button>
        </Can>
      );
      expect(screen.getByText("View Both")).toBeInTheDocument();
    });

    it("should not render when user lacks any permission (all=true)", () => {
      render(
        <Can
          permission={[Permission.PRODUCTS_VIEW, Permission.PRODUCTS_CREATE]}
          all
        >
          <button>Full Access</button>
        </Can>
      );
      expect(screen.queryByText("Full Access")).not.toBeInTheDocument();
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

    it("should not render children", () => {
      render(
        <Can permission={Permission.PRODUCTS_VIEW}>
          <button>Products</button>
        </Can>
      );
      expect(screen.queryByText("Products")).not.toBeInTheDocument();
    });

    it("should render fallback", () => {
      render(
        <Can
          permission={Permission.PRODUCTS_VIEW}
          fallback={<span>Please log in</span>}
        >
          <button>Products</button>
        </Can>
      );
      expect(screen.getByText("Please log in")).toBeInTheDocument();
    });
  });
});
