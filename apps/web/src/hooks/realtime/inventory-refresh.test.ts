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

  it("zeroes a requested ID absent from the response, rather than keeping its stale non-zero quantity (Blocker 1)", async () => {
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [
      { productId: "p1", totalQuantity: 10 },
      { productId: "p2", totalQuantity: 20 },
    ]);
    // p1 went out of stock: per InventoryQueries.java's batched-mode contract, an absent id
    // means quantity 0, not "unchanged".
    mockGetSiteInventoryTotals.mockResolvedValue([]);

    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 0 },
      { productId: "p2", totalQuantity: 20 },
    ]);
  });

  it("invalidates both the site-qualified and the legacy two-element productInventoryEntries key (Required 4)", async () => {
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);
    mockGetSiteInventoryTotals.mockResolvedValue([{ productId: "p1", totalQuantity: 2 }]);
    const invalidateSpy = vi.spyOn(qc, "invalidateQueries");

    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["productInventoryEntries", "site-1", "p1"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["productInventoryEntries", "p1"] });
  });

  it("keeps the newer cached entry when two overlapping flushes for the same product resolve out of order (Advisory 8)", async () => {
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [
      { productId: "p1", totalQuantity: 1, lastUpdatedAt: "2026-01-01T00:00:10.000Z" },
    ]);
    // A stale, older response resolves second (out of order) - it must not overwrite the
    // newer cached value.
    mockGetSiteInventoryTotals.mockResolvedValue([
      { productId: "p1", totalQuantity: 999, lastUpdatedAt: "2026-01-01T00:00:05.000Z" },
    ]);

    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 1, lastUpdatedAt: "2026-01-01T00:00:10.000Z" },
    ]);
  });

  it("applies a newer fetched entry over an older cached one (Advisory 8, the normal case)", async () => {
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [
      { productId: "p1", totalQuantity: 1, lastUpdatedAt: "2026-01-01T00:00:05.000Z" },
    ]);
    mockGetSiteInventoryTotals.mockResolvedValue([
      { productId: "p1", totalQuantity: 999, lastUpdatedAt: "2026-01-01T00:00:10.000Z" },
    ]);

    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 999, lastUpdatedAt: "2026-01-01T00:00:10.000Z" },
    ]);
  });
});
