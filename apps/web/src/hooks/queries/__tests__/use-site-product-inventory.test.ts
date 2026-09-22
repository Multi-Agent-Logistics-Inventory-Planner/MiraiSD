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
import { flushInventorySiteRefresh } from "@/hooks/realtime/inventory-refresh";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    queryClient,
    Wrapper: function Wrapper({ children }: { children: ReactNode }) {
      return createElement(QueryClientProvider, { client: queryClient }, children);
    },
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

    const { Wrapper: wrapper } = createWrapper();
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

    const { Wrapper: wrapper } = createWrapper();
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

    const { Wrapper: wrapper } = createWrapper();
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

    const { Wrapper: wrapper } = createWrapper();
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

    const { Wrapper: wrapper } = createWrapper();
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

    const { Wrapper: wrapper } = createWrapper();
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

    const { Wrapper: wrapper } = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });

    expect(result.current.error).toBe(siteError);
  });

  it("clears the trigger's stale error once the mirror receives fresh data from a successful recovery elsewhere (follow-up review, sixth round)", async () => {
    // The trigger's error is only ever about its own attempt; it stays set even after some
    // other writer (a targeted flush, a realtime notification, or recovery) has already
    // committed fresh, authoritative data straight into the mirror - since recovery writes the
    // mirror directly, not the trigger, nothing about a successful recovery ever clears the
    // trigger's own stale error. Before this fix, the hook kept reporting that stale error even
    // once `data` had real, recovered rows in it - the Products page (which replaces its whole
    // table with "Could not load products" whenever `error` is truthy, regardless of `data`)
    // would stay stuck on the error screen forever despite having successfully recovered.
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });
    mockUseSiteProducts.mockReturnValue({
      data: [{ productId: "p-1", name: "Widget", isStocked: true, version: 1 }],
      siteId: "site-main",
      siteCode: "MAIN",
      isLoading: false,
      error: null,
    });

    const fetchError = new Error("network down");
    // First call (the trigger's own fetch) fails; the second (recovery's full-refresh fallback,
    // since nothing was ever cached to merge a targeted response into) succeeds.
    mockGetSiteInventoryTotals.mockRejectedValueOnce(fetchError);
    mockGetSiteInventoryTotals.mockResolvedValueOnce([{ productId: "p-1", totalQuantity: 1 }]);

    const { queryClient, Wrapper: wrapper } = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });

    // The trigger's initial fetch fails: no data yet, so the error must surface.
    await waitFor(() => expect(result.current.error).toBe(fetchError));
    expect(result.current.data).toBeNull();

    // Recovery (or a targeted flush, or a realtime notification) commits fresh data straight
    // into the mirror - not through the trigger, which is still sitting in its own error state.
    await flushInventorySiteRefresh(queryClient, "site-main", ["p-1"]);

    await waitFor(() => expect(result.current.data?.[0]?.totalQuantity).toBe(1));
    // The stale error must no longer be reported now that real data is showing.
    expect(result.current.error).toBeNull();
  });

  it("an external writer's update to the shared key sticks - the mirror never re-derives its own value", async () => {
    // Companion to the structural test below: this one documents the functional behavior
    // (external write wins and is displayed), while the structural test proves *why* it always
    // will, independent of timing.
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });
    mockUseSiteProducts.mockReturnValue({
      data: [{ productId: "p-1", name: "Widget", isStocked: true, version: 1 }],
      siteId: "site-main",
      siteCode: "MAIN",
      isLoading: false,
      error: null,
    });
    mockGetSiteInventoryTotals.mockResolvedValue([{ productId: "p-1", totalQuantity: 1 }]);

    const { queryClient, Wrapper: wrapper } = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });

    await waitFor(() => expect(result.current.data?.[0]?.totalQuantity).toBe(1));

    // The mirror received its initial data (from the trigger's atomic commit), but never by
    // fetching it itself.
    expect(queryClient.getQueryState(["inventoryTotals", "site-main"])?.fetchStatus).toBe("idle");

    // An independent writer (a targeted flush, in production) overwrites the shared key
    // directly. Nothing about the trigger query's own, now-stale return value can ever be
    // "reapplied" over this, because nothing ever wrote it there in the first place except this
    // one atomic commit path.
    mockGetSiteInventoryTotals.mockResolvedValueOnce([{ productId: "p-1", totalQuantity: 99 }]);
    await flushInventorySiteRefresh(queryClient, "site-main", ["p-1"]);
    await waitFor(() => expect(result.current.data?.[0]?.totalQuantity).toBe(99));
    expect(queryClient.getQueryState(["inventoryTotals", "site-main"])?.fetchStatus).toBe("idle");
  });

  it("invalidating the shared display key never triggers a network fetch - it has no queryFn of its own to run (fifth-round review, P1, structural)", async () => {
    // Fourth round's fix made the query's own fetch commit atomically, but React Query still
    // applied that same queryFn's (possibly stale) return value as a second, separate write on
    // its own schedule, sometime after the function returned. That round's mitigation - yielding
    // one extra microtask tick before returning - was just a narrower version of the same race:
    // the reviewer defeated it by delaying the competing write two or three ticks instead of
    // one, and any fixed number of ticks can always be defeated by one more. No timing-based
    // mitigation can close this from inside a queryFn, because React Query's own reapplication
    // schedule isn't something a queryFn can observe or control.
    //
    // Fifth round removes the mechanism this exploited entirely: use-product-inventory.ts splits
    // the single query into a fetch-trigger (queryFn does the real work, on its own private key
    // nothing reads for display) and a pure mirror on the real key (queryFn: skipToken). Proving
    // this structurally, rather than by racing a specific timing, is what makes the guarantee
    // hold regardless of delay length: skipToken means React Query can never call a fetcher for
    // this key at all, under any circumstance, including an explicit invalidation - verified by
    // running this exact assertion against the pre-fifth-round code, where it fails (the single
    // query's queryFn does re-run on invalidation, exactly the second write path this removes).
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });
    mockUseSiteProducts.mockReturnValue({
      data: [{ productId: "p-1", name: "Widget", isStocked: true, version: 1 }],
      siteId: "site-main",
      siteCode: "MAIN",
      isLoading: false,
      error: null,
    });
    mockGetSiteInventoryTotals.mockResolvedValue([{ productId: "p-1", totalQuantity: 1 }]);

    const { queryClient, Wrapper: wrapper } = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });
    await waitFor(() => expect(result.current.data?.[0]?.totalQuantity).toBe(1));

    mockGetSiteInventoryTotals.mockClear();
    await queryClient.invalidateQueries({ queryKey: ["inventoryTotals", "site-main"] });
    await new Promise((resolve) => setTimeout(resolve, 10));

    expect(mockGetSiteInventoryTotals).not.toHaveBeenCalled();
  });
});
