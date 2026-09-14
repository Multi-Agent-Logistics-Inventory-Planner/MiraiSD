import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockUseProducts = vi.fn();
const mockUseSiteProducts = vi.fn();
const mockGetSiteInventoryTotals = vi.fn();

vi.mock("@/hooks/queries/use-products", () => ({
  useProducts: (...args: unknown[]) => mockUseProducts(...args),
}));

vi.mock("@/hooks/queries/use-site-products", () => ({
  useSiteProducts: () => mockUseSiteProducts(),
}));

vi.mock("@/lib/api/site-inventory", () => ({
  getSiteInventoryTotals: (...args: unknown[]) => mockGetSiteInventoryTotals(...args),
}));

import { useSiteProductInventory } from "../use-product-inventory";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

const catalogProduct = (overrides: Record<string, unknown> = {}) => ({
  id: "p-1",
  name: "Widget",
  sku: "WID-1",
  imageUrl: "https://example.com/widget.png",
  isActive: true,
  quantity: 999, // legacy, site-blind field the join must never surface
  category: { id: "cat-1", name: "Toys" },
  hasChildren: false,
  updatedAt: "2026-01-01T00:00:00Z",
  // Legacy money fields the join must override, never pass through as-is.
  unitCost: 1,
  msrp: 2,
  reorderPoint: 3,
  ...overrides,
});

describe("useSiteProductInventory", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetSiteInventoryTotals.mockResolvedValue([]);
  });

  it("stays null until the catalog list, the site list, and totals have all loaded", () => {
    mockUseProducts.mockReturnValue({ data: undefined, isLoading: true, error: null });
    mockUseSiteProducts.mockReturnValue({
      data: undefined,
      siteId: undefined,
      siteCode: undefined,
      isLoading: true,
      error: null,
    });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });

    expect(result.current.data).toBeNull();
    expect(result.current.isLoading).toBe(true);
  });

  it("joins by product ID: catalog fields from legacy getProducts, site-owned fields from getSiteProducts, quantity/status from site totals (T-6d-4)", async () => {
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });
    mockUseSiteProducts.mockReturnValue({
      data: [
        {
          productId: "p-1",
          name: "Widget",
          isStocked: true,
          unitCost: 10,
          msrp: 20,
          reorderPoint: 5,
          targetStockLevel: 50,
          leadTimeDays: 7,
          forecastingEnabled: true,
          version: 3,
        },
      ],
      siteId: "site-main",
      siteCode: "MAIN",
      isLoading: false,
      error: null,
    });
    mockGetSiteInventoryTotals.mockResolvedValue([
      { productId: "p-1", totalQuantity: 12, lastUpdatedAt: "2026-02-01T00:00:00Z" },
    ]);

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });

    await waitFor(() => {
      expect(result.current.data?.[0]?.totalQuantity).toBe(12);
    });

    const row = result.current.data?.[0];
    expect(row).toBeDefined();
    // Catalog/display fields: untouched, from legacy.
    expect(row?.product.name).toBe("Widget");
    expect(row?.product.imageUrl).toBe("https://example.com/widget.png");
    expect(row?.product.category).toEqual({ id: "cat-1", name: "Toys" });
    // Site-owned fields: overridden from the site response, not the legacy (1/2/3) values.
    expect(row?.product.unitCost).toBe(10);
    expect(row?.product.msrp).toBe(20);
    expect(row?.product.reorderPoint).toBe(5);
    expect(row?.product.targetStockLevel).toBe(50);
    expect(row?.product.leadTimeDays).toBe(7);
    expect(row?.product.forecastingEnabled).toBe(true);
    expect(row?.isStocked).toBe(true);
    expect(row?.siteProductVersion).toBe(3);
    // Quantity/status: restored from the site-scoped totals route (T-6d-4, no longer withheld).
    expect(row?.totalQuantity).toBe(12);
    expect(row?.lastUpdatedAt).toBe("2026-02-01T00:00:00Z");
    expect(row?.status).toBe("good");
    expect(mockGetSiteInventoryTotals).toHaveBeenCalledWith("site-main");
  });

  it("a product with no totals row reports zero quantity, never undefined (AC-5's zero-stock correctness)", async () => {
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });
    mockUseSiteProducts.mockReturnValue({
      data: [{ productId: "p-1", name: "Widget", isStocked: true, version: 1 }],
      siteId: "site-main",
      siteCode: "MAIN",
      isLoading: false,
      error: null,
    });
    mockGetSiteInventoryTotals.mockResolvedValue([]);

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });

    await waitFor(() => {
      expect(result.current.data).not.toBeNull();
    });

    expect(result.current.data?.[0]?.totalQuantity).toBe(0);
    expect(result.current.data?.[0]?.status).toBe("out-of-stock");
  });

  it("renders a product's own site state at each site when the site query result changes (site switch), never mixing rows", async () => {
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });

    // Site A carries the product with its own settings and its own quantity.
    mockUseSiteProducts.mockReturnValue({
      data: [{ productId: "p-1", name: "Widget", isStocked: true, msrp: 20, reorderPoint: 5, version: 1 }],
      siteId: "site-a",
      siteCode: "MAIN",
      isLoading: false,
      error: null,
    });
    mockGetSiteInventoryTotals.mockResolvedValue([{ productId: "p-1", totalQuantity: 40 }]);

    const wrapper = createWrapper();
    const { result, rerender } = renderHook(() => useSiteProductInventory(true), { wrapper });

    await waitFor(() => {
      expect(result.current.data?.[0]?.totalQuantity).toBe(40);
    });
    expect(result.current.data?.[0]).toMatchObject({
      isStocked: true,
      product: expect.objectContaining({ msrp: 20, reorderPoint: 5, name: "Widget" }),
    });

    // Site B has never carried the product: absent from the site list entirely (defensive case -
    // the real endpoint always returns an entry per AC-2, but the join must still degrade safely)
    // and has its own, different totals.
    mockUseSiteProducts.mockReturnValue({
      data: [],
      siteId: "site-b",
      siteCode: "SECOND",
      isLoading: false,
      error: null,
    });
    mockGetSiteInventoryTotals.mockResolvedValue([{ productId: "p-1", totalQuantity: 7 }]);
    rerender();

    await waitFor(() => {
      expect(result.current.data?.[0]).toMatchObject({
        isStocked: false,
        siteProductVersion: null,
        totalQuantity: 7,
        // Catalog fields are unchanged across the switch - proving the join keys by product ID
        // rather than accidentally depending on which site query happened to run.
        product: expect.objectContaining({ name: "Widget", category: { id: "cat-1", name: "Toys" } }),
      });
    });
    expect(mockGetSiteInventoryTotals).toHaveBeenCalledWith("site-a");
    expect(mockGetSiteInventoryTotals).toHaveBeenCalledWith("site-b");
  });

  it("stays null while totals are still loading, never fabricating totalQuantity: 0 (review finding 5)", async () => {
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });
    mockUseSiteProducts.mockReturnValue({
      data: [{ productId: "p-1", name: "Widget", isStocked: true, version: 1 }],
      siteId: "site-main",
      siteCode: "MAIN",
      isLoading: false,
      error: null,
    });
    // Totals never resolve during this assertion window - products and siteProducts are both
    // already loaded, which used to be enough to compute (and fabricate) a zero-quantity row.
    let resolveTotals: (value: unknown[]) => void = () => {};
    mockGetSiteInventoryTotals.mockReturnValue(
      new Promise((resolve) => {
        resolveTotals = resolve;
      })
    );

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });

    // Give the totals query a tick to start (and NOT resolve) before asserting.
    await Promise.resolve();
    expect(result.current.data).toBeNull();

    resolveTotals([{ productId: "p-1", totalQuantity: 12 }]);
    await waitFor(() => expect(result.current.data).not.toBeNull());
    expect(result.current.data?.[0]?.totalQuantity).toBe(12);
  });

  it("rejects a late-resolving totals response from the previous site after switching sites (review finding 4 - AC-6 late-result rejection)", async () => {
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });

    let resolveSiteA: (value: unknown[]) => void = () => {};
    const siteAPromise = new Promise<unknown[]>((resolve) => {
      resolveSiteA = resolve;
    });

    mockUseSiteProducts.mockReturnValue({
      data: [{ productId: "p-1", name: "Widget", isStocked: true, msrp: 20, version: 1 }],
      siteId: "site-a",
      siteCode: "MAIN",
      isLoading: false,
      error: null,
    });
    mockGetSiteInventoryTotals.mockImplementation((siteId: string) =>
      siteId === "site-a" ? siteAPromise : Promise.resolve([{ productId: "p-1", totalQuantity: 7 }])
    );

    const wrapper = createWrapper();
    const { result, rerender } = renderHook(() => useSiteProductInventory(true), { wrapper });

    // Site A's request is still in flight (never resolved yet) when the user switches to site B.
    mockUseSiteProducts.mockReturnValue({
      data: [{ productId: "p-1", name: "Widget", isStocked: false, msrp: 99, version: 4 }],
      siteId: "site-b",
      siteCode: "SECOND",
      isLoading: false,
      error: null,
    });
    rerender();

    await waitFor(() => expect(result.current.data?.[0]?.totalQuantity).toBe(7));
    expect(result.current.data?.[0]?.product.msrp).toBe(99);

    // Site A's request finally resolves late, after the switch to B. Because the totals query
    // key is site-qualified (["inventoryTotals", siteId]), this late result lands in site A's
    // own now-orphaned cache entry, not site B's - the rendered data must stay B's.
    resolveSiteA([{ productId: "p-1", totalQuantity: 999 }]);
    await Promise.resolve();
    await Promise.resolve();

    expect(result.current.data?.[0]?.totalQuantity).toBe(7);
    expect(result.current.data?.[0]?.product.msrp).toBe(99);
  });

  it("propagates the site query's error (e.g. unresolved site membership)", () => {
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });
    const siteError = new Error("No active site membership for the current user");
    mockUseSiteProducts.mockReturnValue({
      data: undefined,
      siteId: undefined,
      siteCode: undefined,
      isLoading: false,
      error: siteError,
    });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });

    expect(result.current.error).toBe(siteError);
  });
});
