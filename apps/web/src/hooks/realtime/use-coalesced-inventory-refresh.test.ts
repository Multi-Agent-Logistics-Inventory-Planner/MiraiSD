import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockFlush = vi.fn().mockResolvedValue(undefined);
vi.mock("./inventory-refresh", () => ({
  flushInventorySiteRefresh: (...args: unknown[]) => mockFlush(...args),
}));

import { useCoalescedInventoryRefresh } from "./use-coalesced-inventory-refresh";

function wrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useCoalescedInventoryRefresh (.specs/phase-6-inventory 6e, AC-7)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("N events in one window produce exactly one flush with the union of IDs", () => {
    const { result } = renderHook(() => useCoalescedInventoryRefresh(300), { wrapper: wrapper() });

    act(() => {
      result.current.notify("site-1", ["p1"]);
      result.current.notify("site-1", ["p2"]);
      result.current.notify("site-1", ["p1"]); // duplicate
    });
    expect(mockFlush).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(300);
    });

    expect(mockFlush).toHaveBeenCalledTimes(1);
    const [, siteId, ids] = mockFlush.mock.calls[0];
    expect(siteId).toBe("site-1");
    expect(new Set(ids)).toEqual(new Set(["p1", "p2"]));
  });

  it("a mid-window unknown-ID notification escalates the whole flush to full refresh", () => {
    const { result } = renderHook(() => useCoalescedInventoryRefresh(300), { wrapper: wrapper() });

    act(() => {
      result.current.notify("site-1", ["p1"]);
      result.current.notify("site-1", undefined);
      vi.advanceTimersByTime(300);
    });

    expect(mockFlush).toHaveBeenCalledTimes(1);
    const [, , ids] = mockFlush.mock.calls[0];
    expect(ids).toBeUndefined();
  });

  it("events for two different sites flush separately and never merge", () => {
    const { result } = renderHook(() => useCoalescedInventoryRefresh(300), { wrapper: wrapper() });

    act(() => {
      result.current.notify("site-1", ["p1"]);
      // A different site's notification flushes the pending site-1 buffer immediately.
      result.current.notify("site-2", ["p2"]);
    });

    expect(mockFlush).toHaveBeenCalledTimes(1);
    expect(mockFlush.mock.calls[0][1]).toBe("site-1");
    expect(mockFlush.mock.calls[0][2]).toEqual(["p1"]);

    act(() => {
      vi.advanceTimersByTime(300);
    });

    expect(mockFlush).toHaveBeenCalledTimes(2);
    expect(mockFlush.mock.calls[1][1]).toBe("site-2");
    expect(mockFlush.mock.calls[1][2]).toEqual(["p2"]);
  });

  it("flushNow flushes immediately without waiting for the window", () => {
    const { result } = renderHook(() => useCoalescedInventoryRefresh(300), { wrapper: wrapper() });

    act(() => {
      result.current.notify("site-1", ["p1"]);
      result.current.flushNow();
    });

    expect(mockFlush).toHaveBeenCalledTimes(1);
  });

  it("unmount cancels a pending flush timer", () => {
    const { result, unmount } = renderHook(() => useCoalescedInventoryRefresh(300), {
      wrapper: wrapper(),
    });

    act(() => {
      result.current.notify("site-1", ["p1"]);
    });
    unmount();

    act(() => {
      vi.advanceTimersByTime(300);
    });

    expect(mockFlush).not.toHaveBeenCalled();
  });
});
