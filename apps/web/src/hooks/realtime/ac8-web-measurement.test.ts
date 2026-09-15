import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

/**
 * .specs/phase-6-inventory 6e, T-6e-10: the AC-8 web-side before/after measurement. Scripted
 * workload against a real QueryClient and a counting `getSiteInventoryTotals` stub, driven
 * through the actual `useCoalescedInventoryRefresh` + `flushInventorySiteRefresh` code this
 * checkpoint shipped ("after") vs. the pre-6e behavior every inventory_updated event used to
 * trigger - an immediate, unscoped `invalidateQueries({queryKey: ["inventoryTotals"]})` per
 * event, with no coalescing and no bounded batch lever ("before" - reconstructed from the
 * pre-6e `use-realtime-broadcast.ts` source in git history, not re-run live).
 *
 * Byte figures are estimates labeled as such, reusing 6c's real measured per-row costs
 * (InventoryEgressAfterIT, .specs/phase-6-inventory/log.md): legacy full totals (25 products)
 * apiBytes=9445 (~378 B/row); v1 full totals apiBytes=2715 (~109 B/row); v1 batch-of-3
 * apiBytes=355 (~118 B/row). This test does not measure a live browser/websocket session - it
 * measures request/query counts and cache-write shape through jsdom + a real QueryClient, per
 * this checkpoint's recorded assumption.
 */

const LEGACY_FULL_TOTALS_BYTES = 9445; // 6c baseline, 25-product catalog, apiBytes
const V1_FULL_TOTALS_BYTES = 2715; // 6c after, 25-product catalog, apiBytes
const V1_PER_PRODUCT_BYTES = 118; // 6c after, batch-of-3 apiBytes(355) / 3, rounded

const mockGetSiteInventoryTotals = vi.fn();
vi.mock("@/lib/api/site-inventory", () => ({
  getSiteInventoryTotals: (...args: unknown[]) => mockGetSiteInventoryTotals(...args),
}));

import { useCoalescedInventoryRefresh } from "./use-coalesced-inventory-refresh";

function wrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

interface Scenario {
  name: string;
  /** Each entry is one realtime notification's productIds (undefined = unknown-ID batch). */
  events: (string[] | undefined)[];
}

const SCENARIOS: Scenario[] = [
  { name: "single known ID", events: [["p1"]] },
  { name: "5-ID batch", events: [["p1", "p2", "p3", "p4", "p5"]] },
  { name: "unknown-ID batch", events: [undefined] },
  { name: "duplicate ID (same event twice)", events: [["p1"], ["p1"]] },
  { name: "reordered pair (two different known IDs)", events: [["p2"], ["p1"]] },
];

interface Measurement {
  scenario: string;
  requestCount: number;
  fullRefreshCount: number;
  estimatedApiBytes: number;
}

/** The pre-6e path: every event is its own immediate, unscoped full-totals invalidation. */
function measureBefore(scenario: Scenario): Measurement {
  const fullRefreshCount = scenario.events.length;
  return {
    scenario: scenario.name,
    requestCount: fullRefreshCount, // one full-catalog refetch per event, no batching lever
    fullRefreshCount,
    estimatedApiBytes: fullRefreshCount * LEGACY_FULL_TOTALS_BYTES,
  };
}

async function measureAfter(scenario: Scenario): Promise<Measurement> {
  mockGetSiteInventoryTotals.mockClear();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  // Seed cached totals so the known-ID path can merge rather than falling back to a full fetch.
  queryClient.setQueryData(["inventoryTotals", "site-1"], [
    { productId: "p1", totalQuantity: 1 },
    { productId: "p2", totalQuantity: 2 },
  ]);
  const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");
  mockGetSiteInventoryTotals.mockResolvedValue([{ productId: "p1", totalQuantity: 99 }]);

  vi.useFakeTimers();
  const { result, unmount } = renderHook(() => useCoalescedInventoryRefresh(300), {
    wrapper: wrapper(queryClient),
  });

  act(() => {
    scenario.events.forEach((ids) => result.current.notify("site-1", ids));
    vi.advanceTimersByTime(300);
  });
  vi.useRealTimers();
  unmount();

  const fullRefreshCount = invalidateSpy.mock.calls.filter(
    (call) => JSON.stringify(call[0]) === JSON.stringify({ queryKey: ["inventoryTotals", "site-1"] })
  ).length;
  const requestCount = mockGetSiteInventoryTotals.mock.calls.length + fullRefreshCount;
  const knownIdCount = fullRefreshCount === 0 ? mockGetSiteInventoryTotals.mock.calls[0]?.[1]?.length ?? 0 : 0;

  return {
    scenario: scenario.name,
    requestCount,
    fullRefreshCount,
    estimatedApiBytes:
      fullRefreshCount > 0
        ? fullRefreshCount * V1_FULL_TOTALS_BYTES
        : knownIdCount * V1_PER_PRODUCT_BYTES,
  };
}

describe("AC-8 web measurement: coalesced targeted refresh vs. pre-6e per-event full refresh", () => {
  beforeEach(() => {
    mockGetSiteInventoryTotals.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it.each(SCENARIOS)("$name: after-6e never issues more requests or more bytes than before", async (scenario) => {
    const before = measureBefore(scenario);
    const after = await measureAfter(scenario);

    console.log("[AC-8 web]", JSON.stringify({ before, after }));

    expect(after.requestCount).toBeLessThanOrEqual(before.requestCount);
    expect(after.estimatedApiBytes).toBeLessThanOrEqual(before.estimatedApiBytes);
  });

  it("known-ID scenarios: exactly one coalesced request, not one per event", async () => {
    const single = await measureAfter(SCENARIOS[0]);
    const batch5 = await measureAfter(SCENARIOS[1]);
    const duplicate = await measureAfter(SCENARIOS[3]);
    const reordered = await measureAfter(SCENARIOS[4]);

    expect(single.requestCount).toBe(1);
    expect(batch5.requestCount).toBe(1);
    expect(duplicate.requestCount).toBe(1); // two events for the same ID coalesce to one
    expect(reordered.requestCount).toBe(1); // two events for different IDs still coalesce to one
  });

  it("unknown-ID batch: falls back to a full refresh, same as before (no regression claimed)", async () => {
    const after = await measureAfter(SCENARIOS[2]);
    expect(after.fullRefreshCount).toBe(1);
    expect(after.requestCount).toBe(1); // still bounded to one request, just not a targeted one
  });

  it("result size follows affected IDs: bytes scale with batch size, not catalog size", async () => {
    const single = await measureAfter(SCENARIOS[0]);
    const batch5 = await measureAfter(SCENARIOS[1]);

    expect(batch5.estimatedApiBytes).toBeGreaterThan(single.estimatedApiBytes);
    expect(batch5.estimatedApiBytes).toBeLessThan(V1_FULL_TOTALS_BYTES); // still far under full-catalog
  });
});
