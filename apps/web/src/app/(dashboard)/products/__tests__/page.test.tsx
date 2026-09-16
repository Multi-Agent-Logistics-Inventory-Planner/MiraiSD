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

// --- Site-scoped inventory totals (T-6d-4): the only source for quantity/status on this page. ---
const mockGetSiteInventoryTotals = vi.fn();
vi.mock("@/lib/api/site-inventory", () => ({
  getSiteInventoryTotals: (...args: unknown[]) => mockGetSiteInventoryTotals(...args),
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

// --- ProductModal's own tangential data (not part of what T-5/T-6d changed) - stubbed to keep
// this test focused on list/detail/role/site behavior, not the modal's unrelated sub-sections. ---
vi.mock("@/hooks/queries/use-product-inventory-entries", () => ({
  useProductInventoryEntries: () => ({ data: { entries: [] }, isLoading: false }),
  useSiteProductInventoryEntries: () => ({ data: { entries: [] }, isLoading: false }),
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

// Distinct per-site totals (T-6d-4) - proves the Stock column/detail quantity come from the
// site-scoped totals route, keyed by the resolved siteId, not a shared/global source.
const SITE_TOTALS: Record<string, unknown[]> = {
  "site-main": [{ productId: "p-1", totalQuantity: 42, lastUpdatedAt: "2026-01-02T00:00:00Z" }],
  "site-second": [{ productId: "p-1", totalQuantity: 3, lastUpdatedAt: "2026-01-03T00:00:00Z" }],
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <SidebarProvider>
          <ProductsPage />
        </SidebarProvider>
      </QueryClientProvider>,
    ),
  };
}

describe("ProductsPage (site-scoped, phase-5d T-5)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetProducts.mockResolvedValue([CATALOG_PRODUCT]);
    mockGetCategories.mockResolvedValue([CATEGORY]);
    mockGetSiteProducts.mockImplementation((siteId: string) =>
      Promise.resolve(SITE_PRODUCTS[siteId] ?? []),
    );
    mockGetSiteInventoryTotals.mockImplementation((siteId: string) =>
      Promise.resolve(SITE_TOTALS[siteId] ?? []),
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

  it("shows scoped quantity/stock-status in the list from the site totals route (T-6d-4)", async () => {
    renderPage();

    expect(await screen.findByText("Widget")).toBeInTheDocument();
    expect(screen.getByText("Stocked")).toBeInTheDocument();
    // T-6d-4: quantity is restored from the site-scoped totals route (MAIN's 42), and the
    // legacy, site-blind catalog quantity (999) never renders.
    expect(await screen.findByText("42")).toBeInTheDocument();
    expect(screen.queryByText("999")).not.toBeInTheDocument();
    expect(
      screen.queryByText(/available after inventory is migrated per site/i),
    ).not.toBeInTheDocument();
    expect(mockGetSiteInventoryTotals).toHaveBeenCalledWith("site-main");
  });

  it("opens the detail modal with scoped inventory and role-appropriate actions (EMPLOYEE: no Edit)", async () => {
    renderPage();

    fireEvent.click(await screen.findByText("Widget"));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/current stock/i)).toBeInTheDocument();
    // T-6d-4: the modal renders the real, scoped quantity (MAIN's 42), not the withheld-state copy.
    expect(within(dialog).getByText(/\(42\)/)).toBeInTheDocument();
    expect(
      within(dialog).queryByText(/available after inventory is migrated per site/i),
    ).not.toBeInTheDocument();
    expect(within(dialog).getByText("Stocked")).toBeInTheDocument();
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

    const { rerender, queryClient } = renderPage();
    fireEvent.click(await screen.findByText("Widget"));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Stocked")).toBeInTheDocument();
    expect(within(dialog).getByText("$20.00")).toBeInTheDocument();

    // Simulate the site resolving to SECOND (the only way this can happen today, since there's
    // no switcher UI yet - see use-current-site.ts) and force a re-render so the mocked hook's
    // new return value takes effect and useSiteProductInventory's query key changes. Reuses the
    // SAME QueryClient instance, matching real production behavior - the app creates one
    // QueryClient at the root and never swaps it; a genuinely different instance is not
    // representative and, independent of anything this checkpoint changed, hits a well-known
    // React Query limitation where an already-mounted useQuery's underlying observer stays
    // bound to whichever client instance was current at its own mount and never rebinds to a
    // later one without an actual unmount (useBaseQuery.js's `const [observer] = useState(() =>
    // new Observer(client, ...))`). The property this test actually verifies - that MAIN's
    // cached data never leaks into SECOND's view - comes from the site-qualified query keys
    // (T-6d-3/AC-7), not from swapping client instances, so reusing the same client still fully
    // exercises it.
    mockUseCurrentSite.mockReturnValue({ ...SECOND_SITE, isLoading: false, error: null });
    rerender(
      <QueryClientProvider client={queryClient}>
        <SidebarProvider>
          <ProductsPage />
        </SidebarProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      const dialogAfter = screen.getByRole("dialog");
      expect(within(dialogAfter).getByText("Not Stocked")).toBeInTheDocument();
    }, { timeout: 5000 });
    const dialogAfter = screen.getByRole("dialog");
    // SECOND's own realistic settings, not MAIN's stale $20/$10, and not an empty placeholder.
    expect(within(dialogAfter).getByText("$99.00")).toBeInTheDocument();
    expect(within(dialogAfter).queryByText("$20.00")).not.toBeInTheDocument();
    // Quantity rebinds to SECOND's own totals (3), not MAIN's stale 42.
    expect(within(dialogAfter).getByText(/\(3\)/)).toBeInTheDocument();
    expect(within(dialogAfter).queryByText(/\(42\)/)).not.toBeInTheDocument();
  });
});
