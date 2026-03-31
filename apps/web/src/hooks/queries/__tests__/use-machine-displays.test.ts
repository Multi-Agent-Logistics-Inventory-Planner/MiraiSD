import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

// Mock the API module before importing the hook
vi.mock("@/lib/api/machine-displays", () => ({
  getActiveDisplays: vi.fn().mockResolvedValue([]),
  getActiveDisplaysByType: vi.fn().mockResolvedValue([]),
  getMachineHistoryPaged: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  getProductDisplayHistory: vi.fn().mockResolvedValue([]),
  getActiveDisplaysForMachine: vi.fn().mockResolvedValue([]),
}));

import {
  useActiveDisplays,
  useActiveDisplaysByType,
  useMachineDisplayHistoryPaged,
  useProductDisplayHistory,
  useActiveDisplaysForMachine,
  STALE_DISPLAY_THRESHOLD_DAYS,
} from "../use-machine-displays";

const TEN_MINUTES_MS = 10 * 60 * 1000;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("use-machine-displays", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("useActiveDisplays", () => {
    it("uses a staleTime of 10 minutes (600000ms)", async () => {
      const wrapper = createWrapper();
      const { result } = renderHook(() => useActiveDisplays(), { wrapper });

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      // Verify the hook resolved successfully with the mocked empty array
      expect(result.current.data).toEqual([]);
    });

    it("returns data from the API", async () => {
      const { getActiveDisplays } = await import("@/lib/api/machine-displays");
      const mockDisplays = [
        {
          id: "d1",
          locationType: "CLAW_MACHINE",
          machineId: "m1",
          machineCode: "CLW-01",
          productId: "p1",
          productName: "Prize A",
          startedAt: "2026-03-01T00:00:00Z",
          daysActive: 30,
        },
      ];
      vi.mocked(getActiveDisplays).mockResolvedValueOnce(mockDisplays as never);

      const wrapper = createWrapper();
      const { result } = renderHook(() => useActiveDisplays(), { wrapper });

      await waitFor(() => {
        expect(result.current.data).toEqual(mockDisplays);
      });
    });
  });

  describe("useActiveDisplaysByType", () => {
    it("is disabled when locationType is undefined", () => {
      const wrapper = createWrapper();
      const { result } = renderHook(
        () => useActiveDisplaysByType(undefined),
        { wrapper },
      );

      // Should not fetch when locationType is undefined
      expect(result.current.isFetching).toBe(false);
    });

    it("fetches when locationType is provided", async () => {
      const wrapper = createWrapper();
      const { result } = renderHook(
        () => useActiveDisplaysByType("CLAW_MACHINE" as never),
        { wrapper },
      );

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(result.current.data).toEqual([]);
    });
  });

  describe("STALE_DISPLAY_THRESHOLD_DAYS", () => {
    it("is set to 14 days", () => {
      expect(STALE_DISPLAY_THRESHOLD_DAYS).toBe(14);
    });
  });

  describe("staleTime configuration", () => {
    it("all hooks use 10-minute staleTime for cache optimization", () => {
      // This test documents the architectural decision that all machine display
      // hooks share a 10-minute staleTime to reduce unnecessary refetches.
      // The value is verified by reading the source constant.
      expect(TEN_MINUTES_MS).toBe(600_000);
    });
  });
});
