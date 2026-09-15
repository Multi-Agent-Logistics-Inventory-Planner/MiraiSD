import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient } from "@tanstack/react-query";

const mockGetSiteInventoryTotals = vi.fn();
vi.mock("@/lib/api/site-inventory", () => ({
  getSiteInventoryTotals: (...args: unknown[]) => mockGetSiteInventoryTotals(...args),
}));

import { flushInventorySiteRefresh } from "./inventory-refresh";

function client() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

describe("flushInventorySiteRefresh (.specs/phase-6-inventory 6e, AC-7)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("unknown IDs (undefined) fall back to a full site-scoped invalidation, no fetch", async () => {
    const qc = client();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");

    await flushInventorySiteRefresh(qc, "site-1", undefined);

    expect(mockGetSiteInventoryTotals).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["inventoryTotals", "site-1"] });
  });

  it("known IDs with nothing cached yet fall back to a full invalidation rather than caching a partial list", async () => {
    const qc = client();
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");

    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    expect(mockGetSiteInventoryTotals).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["inventoryTotals", "site-1"] });
  });

  it("known IDs with existing cached data issue exactly one bounded fetch and merge, preserving untouched products", async () => {
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [
      { productId: "p1", totalQuantity: 10 },
      { productId: "p2", totalQuantity: 20 },
    ]);
    mockGetSiteInventoryTotals.mockResolvedValue([{ productId: "p1", totalQuantity: 99 }]);

    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    expect(mockGetSiteInventoryTotals).toHaveBeenCalledTimes(1);
    expect(mockGetSiteInventoryTotals).toHaveBeenCalledWith("site-1", ["p1"]);
    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 99 },
      { productId: "p2", totalQuantity: 20 },
    ]);
  });

  it("never writes to a different site's cache key", async () => {
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 10 }]);
    qc.setQueryData(["inventoryTotals", "site-2"], [{ productId: "p1", totalQuantity: 999 }]);
    mockGetSiteInventoryTotals.mockResolvedValue([{ productId: "p1", totalQuantity: 50 }]);

    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    expect(qc.getQueryData(["inventoryTotals", "site-2"])).toEqual([
      { productId: "p1", totalQuantity: 999 },
    ]);
  });

  it("duplicate IDs in the input dedupe to one fetch call", async () => {
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);
    mockGetSiteInventoryTotals.mockResolvedValue([{ productId: "p1", totalQuantity: 2 }]);

    await flushInventorySiteRefresh(qc, "site-1", ["p1", "p1", "p1"]);

    expect(mockGetSiteInventoryTotals).toHaveBeenCalledWith("site-1", ["p1"]);
  });
});
