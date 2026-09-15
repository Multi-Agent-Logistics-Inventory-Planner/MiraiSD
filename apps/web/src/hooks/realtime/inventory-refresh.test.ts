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

  it("a later-issued flush always wins for the same product, even if it resolves before an earlier, still-pending one (follow-up review finding, P1)", async () => {
    // Replaces the old lastUpdatedAt-based race test: that guard compared each fetched row's
    // own server timestamp, which is not monotonic with correctness (a deleted newest row
    // legitimately lowers it; an absent-id zero-default carries none at all). Sequencing by
    // request issuance order instead - regardless of what either response contains - is what
    // the follow-up review asked for.
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);

    let resolveEarlierFetch!: (value: unknown) => void;
    const earlierFetch = new Promise((resolve) => {
      resolveEarlierFetch = resolve;
    });
    mockGetSiteInventoryTotals.mockImplementationOnce(() => earlierFetch);
    // Starts first (claims the lower sequence number), but its own fetch stays pending.
    const earlierFlush = flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    mockGetSiteInventoryTotals.mockImplementationOnce(async () => [
      { productId: "p1", totalQuantity: 999 },
    ]);
    // Starts second (claims the higher sequence number) and resolves immediately.
    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 999 },
    ]);

    // The earlier flush's request finally resolves, with a different, stale value - it must
    // not overwrite what the later flush already applied.
    resolveEarlierFetch([{ productId: "p1", totalQuantity: 42 }]);
    await earlierFlush;

    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 999 },
    ]);
  });

  it("chunks the fetch into bounded batches when more IDs are buffered than the backend's single-request limit (follow-up review finding, P2)", async () => {
    const qc = client();
    const ids = Array.from({ length: 501 }, (_, i) => `p${i}`);
    qc.setQueryData(
      ["inventoryTotals", "site-1"],
      ids.map((id) => ({ productId: id, totalQuantity: 1 }))
    );
    mockGetSiteInventoryTotals.mockImplementation(async (_siteId: string, chunkIds: string[]) =>
      chunkIds.map((id) => ({ productId: id, totalQuantity: 2 }))
    );

    await flushInventorySiteRefresh(qc, "site-1", ids);

    expect(mockGetSiteInventoryTotals).toHaveBeenCalledTimes(2);
    expect(mockGetSiteInventoryTotals.mock.calls[0][1]).toHaveLength(500);
    expect(mockGetSiteInventoryTotals.mock.calls[1][1]).toHaveLength(1);
    const data = qc.getQueryData(["inventoryTotals", "site-1"]) as Array<{
      productId: string;
      totalQuantity: number;
    }>;
    expect(data.every((t) => t.totalQuantity === 2)).toBe(true);
    expect(data).toHaveLength(501);
  });
});
