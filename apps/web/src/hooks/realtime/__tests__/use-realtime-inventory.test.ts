import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockUseCurrentSite = vi.fn();
vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

const mockGetProductById = vi.fn();
vi.mock("@/lib/api/products", () => ({
  getProductById: (...args: unknown[]) => mockGetProductById(...args),
}));

// Capture the hook's onReceive/enabled instead of driving a real Supabase channel.
let capturedOptions: {
  onReceive: (payload: unknown) => void;
  enabled: boolean;
} | null = null;
vi.mock("../use-supabase-realtime", () => ({
  useSupabaseRealtime: (options: { onReceive: (payload: unknown) => void; enabled: boolean }) => {
    capturedOptions = options;
    return () => null;
  },
}));

import { useRealtimeInventory } from "../use-realtime-inventory";

function createWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

function movementPayload(siteId: string | null | undefined) {
  return {
    eventType: "INSERT",
    new: { id: 1, item_id: "p-1", location_type: "RACK", quantity_change: 3, reason: "RESTOCK", at: "2026-01-01T00:00:00Z", site_id: siteId },
    old: {},
    schema: "public",
    table: "stock_movements",
  };
}

describe("useRealtimeInventory (T-6d-11 site scoping)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedOptions = null;
    mockGetProductById.mockResolvedValue({ id: "p-1", name: "Widget" });
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1" });
  });

  it("is disabled until a site is resolved", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined });
    const queryClient = new QueryClient();
    renderHook(() => useRealtimeInventory(), { wrapper: createWrapper(queryClient) });

    expect(capturedOptions?.enabled).toBe(false);
  });

  it("ignores an event for a different site (no invalidation)", () => {
    const queryClient = new QueryClient();
    const spy = vi.spyOn(queryClient, "invalidateQueries");
    renderHook(() => useRealtimeInventory(), { wrapper: createWrapper(queryClient) });

    capturedOptions?.onReceive(movementPayload("site-OTHER"));

    expect(spy).not.toHaveBeenCalled();
  });

  it("processes an event for the current site", () => {
    const queryClient = new QueryClient();
    const spy = vi.spyOn(queryClient, "invalidateQueries");
    renderHook(() => useRealtimeInventory(), { wrapper: createWrapper(queryClient) });

    capturedOptions?.onReceive(movementPayload("site-1"));

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ["locationInventory", "site-1"] })
    );
  });

  it("treats a null site_id (pre-backfill row) as possibly relevant, not ignored", () => {
    const queryClient = new QueryClient();
    const spy = vi.spyOn(queryClient, "invalidateQueries");
    renderHook(() => useRealtimeInventory(), { wrapper: createWrapper(queryClient) });

    capturedOptions?.onReceive(movementPayload(null));

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ["locationInventory", "site-1"] })
    );
  });

  it("never overwrites the site-scoped products query when surgically updating the legacy product cache", async () => {
    const queryClient = new QueryClient();
    // Seed both the legacy list cache and the site-scoped cache with distinctly-shaped data.
    queryClient.setQueryData(["products", { rootOnly: true }], [{ id: "p-1", name: "Old" }]);
    queryClient.setQueryData(
      ["products", "site-1", "site"],
      [{ productId: "p-1", name: "Old", isStocked: true }]
    );

    renderHook(() => useRealtimeInventory(), { wrapper: createWrapper(queryClient) });
    capturedOptions?.onReceive(movementPayload("site-1"));

    await vi.waitFor(() => {
      const legacy = queryClient.getQueryData(["products", { rootOnly: true }]) as Array<{ name: string }>;
      expect(legacy[0].name).toBe("Widget");
    });

    // The site-scoped SiteProduct[] cache must be untouched by the legacy Product[] write.
    const siteScoped = queryClient.getQueryData(["products", "site-1", "site"]);
    expect(siteScoped).toEqual([{ productId: "p-1", name: "Old", isStocked: true }]);
  });

  it("never appends into a different product's children/with-children cache entry (review finding 2)", async () => {
    const queryClient = new QueryClient();
    // Seed an unrelated product's children/with-children caches - these are 3-element keys
    // shaped like ["products", otherProductId, "children"|"with-children"], which the original
    // `queryKey[2] !== "site"` predicate did not exclude (only the sibling
    // ["products", siteId, "site"] key was excluded). surgicalProductUpdate's `index === -1`
    // branch would then append the realtime event's product into these unrelated lists.
    queryClient.setQueryData(["products", "other-product", "children"], [{ id: "child-1", name: "Existing child" }]);
    queryClient.setQueryData(
      ["products", "other-product", "with-children"],
      [{ id: "other-product", name: "Other root" }]
    );

    renderHook(() => useRealtimeInventory(), { wrapper: createWrapper(queryClient) });
    capturedOptions?.onReceive(movementPayload("site-1"));

    await vi.waitFor(() => {
      expect(mockGetProductById).toHaveBeenCalled();
    });

    // Both unrelated per-product caches must be byte-for-byte unchanged - product "p-1" (from
    // the realtime event) must never be appended into them.
    expect(queryClient.getQueryData(["products", "other-product", "children"])).toEqual([
      { id: "child-1", name: "Existing child" },
    ]);
    expect(queryClient.getQueryData(["products", "other-product", "with-children"])).toEqual([
      { id: "other-product", name: "Other root" },
    ]);
  });
});
