import React, { useEffect, useState } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";

// SidebarProvider's useIsMobile reads window.matchMedia, which jsdom doesn't implement.
if (typeof window !== "undefined" && !window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
}
import { render, screen, within, waitFor, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SidebarProvider } from "@/components/ui/sidebar";
import { UserRole } from "@/types/api";
import { Permission, type PermissionKey } from "@/lib/rbac/permissions";

// --- next/dynamic: resolve the loader asynchronously, same shape as the real implementation
// (see kuji-tab-panel.test.tsx), so ProductModal (kept real below) loads through the same
// wiring the page actually uses. ---
vi.mock("next/dynamic", () => ({
  default: (loader: () => Promise<{ default: React.ComponentType<Record<string, unknown>> }>) => {
    return function DynamicStub(props: Record<string, unknown>) {
      const [Comp, setComp] = useState<React.ComponentType<Record<string, unknown>> | null>(null);
      useEffect(() => {
        let mounted = true;
        loader().then((mod) => {
          if (mounted) setComp(() => mod.default);
        });
        return () => {
          mounted = false;
        };
      }, []);
      if (!Comp) return null;
      const C = Comp;
      return <C {...props} />;
    };
  },
}));

// --- Routing/tab state: bypassed (no next/navigation router in this test environment). ---
const mockSetTab = vi.fn();
vi.mock("@/hooks/use-tab-param", () => ({
  useTabParam: () => ({ value: "products", setValue: mockSetTab, mountedValues: new Set(["products"]) }),
}));

// --- Site + product data: real hooks (useSiteProducts/useProducts/useSiteProductInventory) run
// unmocked, driven by these mocked network functions - this is what actually exercises AC-6a's
// join and AC-6c's site-switch behavior end-to-end through the page. ---
const mockUseCurrentSite = vi.fn();
vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

const mockGetProducts = vi.fn();
const mockGetSiteProducts = vi.fn();
const mockGetProductChildren = vi.fn();
vi.mock("@/lib/api/products", () => ({
  getProducts: (...args: unknown[]) => mockGetProducts(...args),
  getSiteProducts: (...args: unknown[]) => mockGetSiteProducts(...args),
  getProductChildren: (...args: unknown[]) => mockGetProductChildren(...args),
  getProductWithChildren: vi.fn(),
  getProductById: vi.fn(),
  getProductBySku: vi.fn(),
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  deleteProduct: vi.fn(),
}));

const mockGetInventoryTotals = vi.fn();
vi.mock("@/lib/api/inventory", () => ({
  getInventoryTotals: () => mockGetInventoryTotals(),
}));

const mockGetCategories = vi.fn();
vi.mock("@/lib/api/categories", () => ({
  getCatalogCategories: (...args: unknown[]) => mockGetCategories(...args),
  getCategories: (...args: unknown[]) => mockGetCategories(...args),
  getCategoryById: vi.fn(),
  getChildCategories: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
}));

// --- Permissions: mocked directly (both ProductFilters' <Can> and ProductModal read this same
// hook), so role-dependent actions can be asserted without a real auth/session context. ---
const mockUsePermissions = vi.fn();
vi.mock("@/hooks/use-permissions", () => ({
  usePermissions: () => mockUsePermissions(),
  Permission,
}));

// --- ProductModal's own tangential data (not part of what T-5 changed) - stubbed to keep this
// test focused on list/detail/role/site behavior, not the modal's unrelated sub-sections. ---
vi.mock("@/hooks/queries/use-product-inventory-entries", () => ({
  useProductInventoryEntries: () => ({ data: { entries: [{ inventoryId: "inv-1", locationId: "rack-16", locationCode: "R16", locationType: "RACK", quantity: 15 }] }, isLoading: false }),
}));
vi.mock("@/hooks/queries/use-kuji-box", () => ({
  useKujiAllocationsByProduct: () => ({ data: [] }),
}));
vi.mock("@/hooks/queries/use-shipments-by-product", () => ({
  useShipmentsByProduct: () => ({ data: [], isLoading: false }),
}));
vi.mock("@/hooks/queries/use-machine-displays", () => ({
  useProductDisplayHistory: () => ({ data: [], isLoading: false }),
}));
vi.mock("@/hooks/mutations/use-product-mutations", () => ({
  useDeleteProductMutation: () => ({ mutate: vi.fn(), isPending: false }),
}));

// --- Dialogs/forms not under test here (opening them is out of this test's scope) - stubbed so
// their own deep dependencies never load. ---
vi.mock("@/components/products/product-form", () => ({
  ProductForm: () => null,
}));
vi.mock("@/components/products/manage-categories-dialog", () => ({
  ManageCategoriesDialog: () => null,
}));
vi.mock("@/components/stock/adjust-stock-dialog", () => ({
  AdjustStockDialog: () => null,
}));
vi.mock("@/components/stock/transfer-stock-dialog", () => ({
  TransferStockDialog: () => null,
}));
// The AC-6e Kuji-tab gate is covered independently by kuji-tab-panel.test.tsx; stub it here so
// this test stays focused on the Products tab.
vi.mock("@/components/products/kuji-tab-panel", () => ({
  KujiTabPanel: () => null,
}));

import ProductsPage from "../page";

const CATEGORY = { id: "cat-1", name: "Toys", slug: "toys", parentId: null, displayOrder: 0, isActive: true, usesPacks: false, children: [], createdAt: "", updatedAt: "" };

const CATALOG_PRODUCT = {
  id: "p-1",
  name: "Widget",
  sku: "WID-1",
  imageUrl: undefined,
  isActive: true,
  quantity: 999, // legacy, site-blind - must never be rendered by the site-scoped view
  category: CATEGORY,
  hasChildren: false,
  parentId: null,
  updatedAt: "2026-01-01T00:00:00Z",
};

const MAIN_SITE = { siteId: "site-main", siteCode: "MAIN" };
const SECOND_SITE = { siteId: "site-second", siteCode: "SECOND" };

const SITE_PRODUCTS: Record<string, unknown[]> = {
  "site-main": [
    { productId: "p-1", name: "Widget", isStocked: true, unitCost: 10, msrp: 20, reorderPoint: 5, targetStockLevel: 50, leadTimeDays: 7, forecastingEnabled: true, version: 1 },
  ],
  // Realistic, distinct SECOND-site fallback: not carried there, and its settings genuinely
  // differ from MAIN's rather than being an empty/placeholder stand-in.
  "site-second": [
    { productId: "p-1", name: "Widget", isStocked: false, unitCost: 50, msrp: 99, reorderPoint: 9, targetStockLevel: 12, leadTimeDays: 21, forecastingEnabled: false, version: 4 },
  ],
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <SidebarProvider>
        <ProductsPage />
      </SidebarProvider>
    </QueryClientProvider>,
  );
}

describe("ProductsPage (site-scoped, phase-5d T-5)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetInventoryTotals.mockResolvedValue([{ itemId: "p-1", totalQuantity: 15 }]);
    mockGetProducts.mockResolvedValue([CATALOG_PRODUCT]);
    mockGetCategories.mockResolvedValue([CATEGORY]);
    mockGetSiteProducts.mockImplementation((siteId: string) =>
      Promise.resolve(SITE_PRODUCTS[siteId] ?? []),
    );
    mockUseCurrentSite.mockReturnValue({ ...MAIN_SITE, isLoading: false, error: null });
    mockUsePermissions.mockReturnValue({
      can: (p: string) => p === Permission.PRODUCTS_VIEW,
      canAll: () => false,
      canAny: () => false,
      canAccessRoute: () => true,
      isAdmin: false,
      canViewCosts: false,
      canViewMsrp: false,
      canViewKujiPrices: false,
      canManageUsers: false,
      role: UserRole.EMPLOYEE,
    });
  });

  it.each([true, false])("uses legacy isActive=%s despite conflicting assortment in table and modal", async (isActive) => {
    mockGetProducts.mockResolvedValue([{ ...CATALOG_PRODUCT, isActive }]);
    mockGetSiteProducts.mockResolvedValue([{ productId: "p-1", name: "Widget", isStocked: !isActive }]);
    renderPage();
    const label = isActive ? "Active" : "Inactive";
    expect(await screen.findByText(label)).toBeInTheDocument();
    fireEvent.click(await screen.findByText("Widget"));
    expect(within(screen.getByRole("dialog")).getByText(label)).toBeInTheDocument();
    expect(screen.queryByText("Stocked")).not.toBeInTheDocument();
    expect(screen.queryByText("Not Stocked")).not.toBeInTheDocument();
  });

  it("shows legacy totals and active status", async () => {
    renderPage();

    expect(await screen.findByText("Widget")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    // Counts come from inventory totals, never the catalog quantity field.
    expect(screen.getByText("Stock")).toBeInTheDocument();
    expect(screen.getByText("15")).toBeInTheDocument();
    expect(screen.queryByText("999")).not.toBeInTheDocument();
    expect(
      screen.queryByText(/available after inventory is migrated per site/i),
    ).not.toBeInTheDocument();
  });

  it("opens the detail modal with inventory and role-appropriate actions (EMPLOYEE: no Edit)", async () => {
    renderPage();

    fireEvent.click(await screen.findByText("Widget"));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/current stock/i)).toHaveTextContent("15");
    expect(within(dialog).getByText("R16")).toBeInTheDocument();
    expect(within(dialog).getByText("15")).toBeInTheDocument();
    expect(
      within(dialog).queryByText(/available after inventory is migrated per site/i),
    ).not.toBeInTheDocument();
    expect(within(dialog).getByText("Active")).toBeInTheDocument();
    // canViewMsrp/canViewCosts are false for this EMPLOYEE mock - money fields stay hidden,
    // proving this row's site-scoped msrp isn't leaking around the permission gate.
    expect(within(dialog).queryByText(/MSRP:/i)).not.toBeInTheDocument();
    expect(within(dialog).queryByText(/Unit Cost:/i)).not.toBeInTheDocument();
    expect(within(dialog).queryAllByRole("button", { name: /^edit$/i })).toHaveLength(0);
  });

  it("shows Edit for a role with PRODUCTS_UPDATE and reveals cost/MSRP fields per permission", async () => {
    mockUsePermissions.mockReturnValue({
      can: (p: string) => ([Permission.PRODUCTS_VIEW, Permission.PRODUCTS_UPDATE, Permission.PRODUCTS_DELETE] as PermissionKey[]).includes(p as PermissionKey),
      canAll: () => true,
      canAny: () => true,
      canAccessRoute: () => true,
      isAdmin: true,
      canViewCosts: true,
      canViewMsrp: true,
      canViewKujiPrices: true,
      canManageUsers: true,
      role: UserRole.ADMIN,
    });

    renderPage();
    fireEvent.click(await screen.findByText("Widget"));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getAllByRole("button", { name: /^edit$/i }).length).toBeGreaterThan(0);
    // Site-scoped money fields (MAIN): unitCost from getSiteProducts, not any legacy value.
    expect(within(dialog).getByText("$10.00")).toBeInTheDocument();
    expect(within(dialog).getByText("$20.00")).toBeInTheDocument();
  });

  it("rebinds the open detail modal to the new site's own settings on a site change, without a stale snapshot (AC-6c)", async () => {
    mockUsePermissions.mockReturnValue({
      can: (p: string) => ([Permission.PRODUCTS_VIEW, Permission.PRODUCTS_UPDATE] as PermissionKey[]).includes(p as PermissionKey),
      canAll: () => true,
      canAny: () => true,
      canAccessRoute: () => true,
      isAdmin: false,
      canViewCosts: true,
      canViewMsrp: true,
      canViewKujiPrices: true,
      canManageUsers: false,
      role: UserRole.ASSISTANT_MANAGER,
    });

    const { rerender } = renderPage();
    fireEvent.click(await screen.findByText("Widget"));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Active")).toBeInTheDocument();
    expect(within(dialog).getByText("$20.00")).toBeInTheDocument();

    // Simulate the site resolving to SECOND (the only way this can happen today, since there's
    // no switcher UI yet - see use-current-site.ts) and force a re-render so the mocked hook's
    // new return value takes effect and useSiteProductInventory's query key changes.
    mockUseCurrentSite.mockReturnValue({ ...SECOND_SITE, isLoading: false, error: null });
    rerender(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <SidebarProvider>
          <ProductsPage />
        </SidebarProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      const dialogAfter = screen.getByRole("dialog");
      expect(within(dialogAfter).getByText("$99.00")).toBeInTheDocument();
      expect(within(dialogAfter).getByText("Active")).toBeInTheDocument();
    });
    const dialogAfter = screen.getByRole("dialog");
    // SECOND's own realistic settings, not MAIN's stale $20/$10, and not an empty placeholder.
    expect(within(dialogAfter).getByText("$99.00")).toBeInTheDocument();
    expect(within(dialogAfter).queryByText("$20.00")).not.toBeInTheDocument();
    // Quantity disappears across the switch too.
    expect(within(dialogAfter).queryByText(/current stock/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Stock" })).not.toBeInTheDocument();
    expect(
      within(dialogAfter).queryByText(/available after inventory is migrated per site/i),
    ).not.toBeInTheDocument();
  });
});
