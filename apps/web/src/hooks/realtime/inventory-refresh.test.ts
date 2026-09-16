import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient } from "@tanstack/react-query";

const mockGetSiteInventoryTotals = vi.fn();
vi.mock("@/lib/api/site-inventory", () => ({
  getSiteInventoryTotals: (...args: unknown[]) => mockGetSiteInventoryTotals(...args),
}));

import { flushInventorySiteRefresh, fetchSequencedInventoryTotals } from "./inventory-refresh";

function client() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

describe("flushInventorySiteRefresh (.specs/phase-6-inventory 6e, AC-7)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("unknown IDs (undefined) fetch and apply the full site totals, sequenced like a targeted flush (follow-up review finding, P1)", async () => {
    // Previously a bare invalidateQueries call, which bypassed sequencing entirely - now
    // fetches and applies through the same claim/apply mechanism as a targeted flush, so it can
    // be ordered against one.
    const qc = client();
    mockGetSiteInventoryTotals.mockResolvedValue([{ productId: "p1", totalQuantity: 42 }]);

    await flushInventorySiteRefresh(qc, "site-1", undefined);

    expect(mockGetSiteInventoryTotals).toHaveBeenCalledWith("site-1");
    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 42 },
    ]);
  });

  it("known IDs with nothing cached yet fetch and apply the full site totals rather than caching a partial list", async () => {
    const qc = client();
    mockGetSiteInventoryTotals.mockResolvedValue([
      { productId: "p1", totalQuantity: 5 },
      { productId: "p2", totalQuantity: 7 },
    ]);

    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    // The full, unfiltered fetch, not a productIds-scoped one - "nothing cached yet" falls back
    // to the same full refresh path as an unknown-ID flush.
    expect(mockGetSiteInventoryTotals).toHaveBeenCalledWith("site-1");
    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 5 },
      { productId: "p2", totalQuantity: 7 },
    ]);
  });

  it("an older full refresh does not overwrite a newer targeted write for the same product (follow-up review finding, P1)", async () => {
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);

    let resolveFullFetch!: (value: unknown) => void;
    const fullFetch = new Promise((resolve) => {
      resolveFullFetch = resolve;
    });
    mockGetSiteInventoryTotals.mockImplementationOnce(() => fullFetch);
    // Full refresh starts first (claims every currently-cached id, including p1), but its own
    // fetch stays pending.
    const fullFlush = flushInventorySiteRefresh(qc, "site-1", undefined);

    mockGetSiteInventoryTotals.mockImplementationOnce(async () => [
      { productId: "p1", totalQuantity: 777 },
    ]);
    // Targeted flush for p1 starts second (claims a higher sequence for p1) and resolves first.
    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 777 },
    ]);

    // The full refresh's request finally resolves, with a stale value for p1 - it must not
    // overwrite what the newer targeted flush already applied.
    resolveFullFetch([{ productId: "p1", totalQuantity: 5 }]);
    await fullFlush;

    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 777 },
    ]);
  });

  it("a full refresh's merge decision happens atomically with its own write, not computed earlier and applied several microtask hops later (follow-up fourth-round review, P1)", async () => {
    // The test above fully awaits the targeted flush to completion BEFORE resolving the full
    // refresh's fetch, so the full refresh's merge always reads an already-correct cache and
    // never exercises the actual bug: computing the merge decision right after the fetch
    // resolves, then writing that precomputed value several `await`/`.then()` hops later,
    // during which a concurrent write can land and get silently clobbered by the stale,
    // already-decided value. This test resolves both fetches back-to-back, in the same
    // microtask window, so any extra hop between "decide" and "write" in the full-refresh path
    // would let the targeted write land in that gap and then get overwritten.
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);

    let resolveFullFetch!: (value: unknown) => void;
    const fullFetch = new Promise((resolve) => {
      resolveFullFetch = resolve;
    });
    mockGetSiteInventoryTotals.mockImplementationOnce(() => fullFetch);
    const fullFlush = flushInventorySiteRefresh(qc, "site-1", undefined);

    let resolveTargetedFetch!: (value: unknown) => void;
    const targetedFetch = new Promise((resolve) => {
      resolveTargetedFetch = resolve;
    });
    mockGetSiteInventoryTotals.mockImplementationOnce(() => targetedFetch);
    const targetedFlush = flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    resolveFullFetch([{ productId: "p1", totalQuantity: 5 }]);
    resolveTargetedFetch([{ productId: "p1", totalQuantity: 10 }]);
    await Promise.all([fullFlush, targetedFlush]);

    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 10 },
    ]);
  });

  it("commits the full-refresh result through setQueryData's updater-callback form, never a separately-computed static value (follow-up fourth-round review, P1)", async () => {
    // Structural guarantee behind the fix above: the updater form is the one primitive React
    // Query gives us that's atomic with the live cache (invoked synchronously with whatever
    // `old` truly is, no `await` between reading it and writing the result). Asserting the call
    // shape - not just one timing-dependent outcome - pins that the merge genuinely happens
    // inside the commit, not merely "happens to look right for this particular interleaving."
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);
    mockGetSiteInventoryTotals.mockResolvedValue([{ productId: "p1", totalQuantity: 5 }]);
    const setDataSpy = vi.spyOn(qc, "setQueryData");

    await flushInventorySiteRefresh(qc, "site-1", undefined);

    const totalsCalls = setDataSpy.mock.calls.filter(
      (call) => JSON.stringify(call[0]) === JSON.stringify(["inventoryTotals", "site-1"])
    );
    expect(totalsCalls).toHaveLength(1);
    expect(typeof totalsCalls[0][1]).toBe("function");
  });

  it("an older targeted response does not overwrite a newer full refresh for the same product (follow-up review finding, P1)", async () => {
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);

    let resolveTargetedFetch!: (value: unknown) => void;
    const targetedFetch = new Promise((resolve) => {
      resolveTargetedFetch = resolve;
    });
    mockGetSiteInventoryTotals.mockImplementationOnce(() => targetedFetch);
    // Targeted flush for p1 starts first (claims the lower sequence), but its own fetch stays
    // pending.
    const targetedFlush = flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    mockGetSiteInventoryTotals.mockImplementationOnce(async () => [
      { productId: "p1", totalQuantity: 50 },
    ]);
    // Full refresh starts second (re-claims p1 at a higher sequence, since p1 is still cached
    // at this point) and resolves first.
    await flushInventorySiteRefresh(qc, "site-1", undefined);

    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 50 },
    ]);

    // The earlier targeted flush's request finally resolves, with a stale value - it must not
    // overwrite the newer full refresh's value.
    resolveTargetedFetch([{ productId: "p1", totalQuantity: 3 }]);
    await targetedFlush;

    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 50 },
    ]);
  });

  it("fetchSequencedInventoryTotals (the real query's own queryFn) does not overwrite a newer targeted write, even though its own fetch resolves later (follow-up third-round review, P1)", async () => {
    // The actual useQuery backing this cache key (use-product-inventory.ts) used to call
    // getSiteInventoryTotals directly with no sequencing at all, so ANY of its own lifecycle
    // refetches (mount, window focus, staleTime, a manual refetch) could overwrite a newer
    // targeted write unconditionally - not just the explicit full-refresh path this file already
    // covers. fetchSequencedInventoryTotals is what the queryFn now calls; this proves it
    // participates in the same ordering as an explicit flush.
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);

    let resolveQueryFetch!: (value: unknown) => void;
    const queryFetch = new Promise((resolve) => {
      resolveQueryFetch = resolve;
    });
    mockGetSiteInventoryTotals.mockImplementationOnce(() => queryFetch);
    // The query's own refetch starts first (claims the lower sequence), fetch stays pending.
    const queryRefetch = fetchSequencedInventoryTotals(qc, "site-1");

    mockGetSiteInventoryTotals.mockImplementationOnce(async () => [
      { productId: "p1", totalQuantity: 10 },
    ]);
    // A targeted flush starts second (claims a higher sequence for p1) and resolves first.
    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);
    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 10 },
    ]);

    // The query's own refetch finally resolves, with a stale value for p1 - since its result
    // is applied by whatever *called* fetchSequencedInventoryTotals (React Query's own
    // queryFn-success handler in production; here, the test itself), the merge it returns must
    // already reflect that it lost the race, not just "the caller happens not to apply it."
    resolveQueryFetch([{ productId: "p1", totalQuantity: 2 }]);
    const result = await queryRefetch;
    expect(result).toEqual([{ productId: "p1", totalQuantity: 10 }]);
  });

  it("fetchSequencedInventoryTotals's own commit is atomic even when its fetch and a targeted flush's fetch resolve back-to-back (follow-up fifth-round review, P1)", async () => {
    // Fifth-round review found the fourth round's "yield one more tick before returning"
    // mitigation was just a narrower version of the same race - a longer competing delay always
    // defeats a shorter one, so it could never be a real fix. This function no longer feeds
    // React Query's own write to the display key at all (see use-product-inventory.ts's
    // trigger/mirror split) - its return value is inert, so what matters now is only that its
    // OWN commit (via commitFullTotals, already asserted structurally above) is correct, not
    // what it happens to return.
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);

    let resolveQueryFetch!: (value: unknown) => void;
    const queryFetch = new Promise((resolve) => {
      resolveQueryFetch = resolve;
    });
    mockGetSiteInventoryTotals.mockImplementationOnce(() => queryFetch);
    const queryRefetch = fetchSequencedInventoryTotals(qc, "site-1");

    let resolveTargetedFetch!: (value: unknown) => void;
    const targetedFetch = new Promise((resolve) => {
      resolveTargetedFetch = resolve;
    });
    mockGetSiteInventoryTotals.mockImplementationOnce(() => targetedFetch);
    const targetedFlush = flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    resolveQueryFetch([{ productId: "p1", totalQuantity: 5 }]);
    resolveTargetedFetch([{ productId: "p1", totalQuantity: 10 }]);
    await Promise.all([queryRefetch, targetedFlush]);

    // The cache itself must reflect the targeted flush's win - this is the only thing anything
    // reads for display now, since nothing applies fetchSequencedInventoryTotals's return value
    // to this key.
    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 10 },
    ]);
  });

  it("an older full refresh preserves a newer product entry that's absent from its response, rather than deleting it (follow-up third-round review, P1)", async () => {
    // The previous round's merge built its result exclusively from the fetched rows, so a
    // product created after an older full read's server-side snapshot but before that read
    // resolves - and already cached by a newer targeted flush in the meantime - would be
    // silently dropped: absent from the older response, and never carried forward from `old`.
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);

    let resolveFullFetch!: (value: unknown) => void;
    const fullFetch = new Promise((resolve) => {
      resolveFullFetch = resolve;
    });
    mockGetSiteInventoryTotals.mockImplementationOnce(() => fullFetch);
    // Full refresh starts first (claims p1 only - "p2" doesn't exist in the cache yet), fetch
    // stays pending.
    const fullFlush = flushInventorySiteRefresh(qc, "site-1", undefined);

    mockGetSiteInventoryTotals.mockImplementationOnce(async () => [
      { productId: "p2", totalQuantity: 20 },
    ]);
    // A targeted flush for the brand-new product p2 starts second and resolves first.
    await flushInventorySiteRefresh(qc, "site-1", ["p2"]);
    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 1 },
      { productId: "p2", totalQuantity: 20 },
    ]);

    // The full refresh's response finally arrives - a snapshot taken before p2 existed, so it
    // contains only p1. It must not delete p2, which a newer flush already correctly cached.
    resolveFullFetch([{ productId: "p1", totalQuantity: 1 }]);
    await fullFlush;

    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual(
      expect.arrayContaining([
        { productId: "p1", totalQuantity: 1 },
        { productId: "p2", totalQuantity: 20 },
      ])
    );
  });

  it("recovers with a fresh, sequenced full fetch when the latest (superseding) flush's request fails, even though an earlier flush would have succeeded (follow-up review finding, P2)", async () => {
    // Recovery no longer uses a bare invalidateQueries (follow-up review, second round) - that
    // bypassed sequencing just like the bug it was meant to fix. It now issues its own freshly
    // claimed, sequenced full fetch through the same merge path as everything else.
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);

    let resolveEarlierFetch!: (value: unknown) => void;
    const earlierFetch = new Promise((resolve) => {
      resolveEarlierFetch = resolve;
    });
    mockGetSiteInventoryTotals.mockImplementationOnce(() => earlierFetch);
    // Flush A starts first (claims p1 at the lower sequence), fetch stays pending.
    const flushA = flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    const networkError = new Error("network blip");
    mockGetSiteInventoryTotals.mockImplementationOnce(() => Promise.reject(networkError));
    // Flush B starts second (supersedes A's claim on p1) and its own targeted fetch fails.
    mockGetSiteInventoryTotals.mockImplementationOnce(async () => [
      { productId: "p1", totalQuantity: 2 },
    ]);
    // B's recovery attempt (a full, unfiltered fetch) succeeds with the correct current value.
    const flushB = flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    await expect(flushB).rejects.toThrow("network blip");
    // B still owned the claim on p1 when its own fetch failed (nothing even newer superseded
    // it), so its failure triggered a corrective recovery fetch that corrected the cache.
    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 2 },
    ]);

    // A's response finally arrives with a now-stale value - A no longer owns the claim (B's
    // recovery superseded it too), so A must not overwrite what recovery already applied.
    resolveEarlierFetch([{ productId: "p1", totalQuantity: 42 }]);
    await flushA;
    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 2 },
    ]);
  });

  it("does not recover when a failing flush was already superseded by an even newer one before it failed", async () => {
    const qc = client();
    qc.setQueryData(["inventoryTotals", "site-1"], [{ productId: "p1", totalQuantity: 1 }]);

    let rejectFailingFetch!: (error: unknown) => void;
    const failingFetch = new Promise((_resolve, reject) => {
      rejectFailingFetch = reject;
    });
    mockGetSiteInventoryTotals.mockImplementationOnce(() => failingFetch);
    // Flush A starts first, fetch stays pending.
    const flushA = flushInventorySiteRefresh(qc, "site-1", ["p1"]);

    mockGetSiteInventoryTotals.mockImplementationOnce(async () => [
      { productId: "p1", totalQuantity: 9 },
    ]);
    // Flush C supersedes A and succeeds before A fails.
    await flushInventorySiteRefresh(qc, "site-1", ["p1"]);
    mockGetSiteInventoryTotals.mockClear();

    // A now fails, but C (not A) is the current claim holder - A's failure must not trigger a
    // redundant recovery fetch that could race C's already-applied, correct write.
    rejectFailingFetch(new Error("stale failure"));
    await expect(flushA).rejects.toThrow("stale failure");

    expect(mockGetSiteInventoryTotals).not.toHaveBeenCalled();
    expect(qc.getQueryData(["inventoryTotals", "site-1"])).toEqual([
      { productId: "p1", totalQuantity: 9 },
    ]);
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
