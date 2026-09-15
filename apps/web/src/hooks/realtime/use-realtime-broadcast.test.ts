import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockUseCurrentSite = vi.fn();
vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

const mockFlushInventorySiteRefresh = vi.fn().mockResolvedValue(undefined);
vi.mock("./inventory-refresh", () => ({
  flushInventorySiteRefresh: (...args: unknown[]) => mockFlushInventorySiteRefresh(...args),
}));

// Capture the broadcast handler and subscribe-status callback instead of driving a real
// Supabase realtime connection.
let capturedOnReceive: ((payload: { payload: unknown }) => void) | null = null;
let capturedOnStatus: ((status: string) => void) | null = null;

function fakeChannel() {
  const channel = {
    on: (_type: string, _filter: unknown, handler: (payload: { payload: unknown }) => void) => {
      capturedOnReceive = handler;
      return channel;
    },
    subscribe: (statusCallback: (status: string) => void) => {
      capturedOnStatus = statusCallback;
      return channel;
    },
  };
  return channel;
}

const mockGetSupabaseClient = vi.fn();
vi.mock("@/lib/supabase", () => ({
  getSupabaseClient: () => mockGetSupabaseClient(),
}));

import { useRealtimeBroadcast } from "./use-realtime-broadcast";

function createWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

function emit(payload: unknown) {
  capturedOnReceive?.({ payload });
}

describe("useRealtimeBroadcast (.specs/phase-6-inventory 6e, T-6e-2/3/6)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    capturedOnReceive = null;
    capturedOnStatus = null;
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1" });
    mockGetSupabaseClient.mockReturnValue({
      channel: () => fakeChannel(),
      removeChannel: vi.fn(),
    });
  });

  function mount() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");
    renderHook(() => useRealtimeBroadcast(true), { wrapper: createWrapper(queryClient) });
    return { queryClient, invalidateSpy };
  }

  it("drops an inventory_updated event for a different site (no coalesced flush, no invalidation)", () => {
    mount();

    act(() => {
      emit({ type: "inventory_updated", siteId: "site-OTHER", productIds: ["p1"] });
      vi.advanceTimersByTime(500);
    });

    expect(mockFlushInventorySiteRefresh).not.toHaveBeenCalled();
  });

  it("processes an inventory_updated event with no siteId (possibly relevant)", () => {
    mount();

    act(() => {
      emit({ type: "inventory_updated", productIds: ["p1"] });
      vi.advanceTimersByTime(500);
    });

    expect(mockFlushInventorySiteRefresh).toHaveBeenCalledWith(
      expect.anything(),
      "site-1",
      ["p1"]
    );
  });

  it("coalesces two rapid same-site events into one flush with the union of IDs", () => {
    mount();

    act(() => {
      emit({ type: "inventory_updated", siteId: "site-1", productIds: ["p1"] });
      emit({ type: "inventory_updated", siteId: "site-1", productIds: ["p2"] });
    });
    expect(mockFlushInventorySiteRefresh).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(300);
    });

    expect(mockFlushInventorySiteRefresh).toHaveBeenCalledTimes(1);
    const [, , ids] = mockFlushInventorySiteRefresh.mock.calls[0];
    expect(new Set(ids)).toEqual(new Set(["p1", "p2"]));
  });

  it("does not fire a full recovery refresh on the first, normal SUBSCRIBED", () => {
    mount();

    act(() => {
      capturedOnStatus?.("SUBSCRIBED");
    });

    expect(mockFlushInventorySiteRefresh).not.toHaveBeenCalled();
  });

  it("fires exactly one full recovery refresh on SUBSCRIBED following a real interruption", () => {
    mount();

    act(() => {
      capturedOnStatus?.("SUBSCRIBED");
      capturedOnStatus?.("CHANNEL_ERROR");
      capturedOnStatus?.("SUBSCRIBED");
    });

    expect(mockFlushInventorySiteRefresh).toHaveBeenCalledTimes(1);
    expect(mockFlushInventorySiteRefresh).toHaveBeenCalledWith(expect.anything(), "site-1", undefined);
  });

  it("processes a non-inventory event type through the bare query-key table", () => {
    const { invalidateSpy } = mount();

    act(() => {
      emit({ type: "notification_created" });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["notifications"] });
  });

  it("ignores an unknown event type without throwing", () => {
    mount();
    expect(() => act(() => emit({ type: "unknown_event" }))).not.toThrow();
  });

  it("unconditionally invalidates the locationsWithCounts site prefix on inventory_updated, regardless of locationType shape (R-2/Required-3)", () => {
    const { invalidateSpy } = mount();

    // data.locationType is the backend's storage_locations.code vocabulary ("RACKS"), not the
    // frontend LocationType enum the cache key uses - a type-qualified invalidation could never
    // match this. The fix must invalidate unconditionally, and even fire when locationType is
    // entirely absent (Kuji/Shipment producers send none).
    act(() => {
      emit({ type: "inventory_updated", siteId: "site-1", productIds: ["p1"], locationType: "RACKS" });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["locationsWithCounts", "site-1"] });
  });

  it("invalidates the legacy two-element productInventoryEntries key alongside the site-qualified one (Required 4)", () => {
    const { invalidateSpy } = mount();

    act(() => {
      emit({ type: "inventory_updated", siteId: "site-1", productIds: ["p1"] });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["productInventoryEntries", "site-1", "p1"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["productInventoryEntries", "p1"] });
  });

  it("reconnect recovery invalidates locationInventory, locationsWithCounts and productInventoryEntries too, not just totals (Required 5)", () => {
    const { invalidateSpy } = mount();

    act(() => {
      capturedOnStatus?.("SUBSCRIBED");
      capturedOnStatus?.("CHANNEL_ERROR");
      capturedOnStatus?.("SUBSCRIBED");
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["locationInventory", "site-1"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["locationsWithCounts", "site-1"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["productInventoryEntries"] });
  });

  it("does not flush a pending coalesced buffer on unmount (Advisory 7 - matches the coalescing hook's own tested behavior)", () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { unmount } = renderHook(() => useRealtimeBroadcast(true), {
      wrapper: createWrapper(queryClient),
    });

    act(() => {
      emit({ type: "inventory_updated", siteId: "site-1", productIds: ["p1"] });
    });
    unmount();

    act(() => {
      vi.advanceTimersByTime(300);
    });

    expect(mockFlushInventorySiteRefresh).not.toHaveBeenCalled();
  });

  it("a site switch mid-flight still flushes the buffered event against the site it was notified for, never the new site", () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { rerender } = renderHook(() => useRealtimeBroadcast(true), {
      wrapper: createWrapper(queryClient),
    });

    act(() => {
      emit({ type: "inventory_updated", siteId: "site-1", productIds: ["p1"] });
    });

    // Site switches before the coalescing window elapses.
    mockUseCurrentSite.mockReturnValue({ siteId: "site-2" });
    rerender();

    act(() => {
      vi.advanceTimersByTime(300);
    });

    expect(mockFlushInventorySiteRefresh).toHaveBeenCalledTimes(1);
    expect(mockFlushInventorySiteRefresh).toHaveBeenCalledWith(expect.anything(), "site-1", ["p1"]);
  });
});
