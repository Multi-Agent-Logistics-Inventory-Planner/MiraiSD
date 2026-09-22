import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";
import { LocationType } from "@/types/api";

const mockUseCurrentSite = vi.fn();
vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

const mockGetSiteLocationsWithCounts = vi.fn();
vi.mock("@/lib/api/locations", () => ({
  getSiteLocationsWithCounts: (...args: unknown[]) => mockGetSiteLocationsWithCounts(...args),
}));

import { useLocationsWithCounts, useAllLocationsWithCounts } from "../use-locations-with-counts";

function createWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useLocationsWithCounts (.specs/phase-6-inventory 6e, T-6e-9)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("is disabled until a site and a real location type are known", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined });
    const queryClient = new QueryClient();
    const { result } = renderHook(() => useLocationsWithCounts(LocationType.BOX_BIN), {
      wrapper: createWrapper(queryClient),
    });

    expect(result.current.fetchStatus).toBe("idle");
    expect(mockGetSiteLocationsWithCounts).not.toHaveBeenCalled();
  });

  it("is disabled for NOT_ASSIGNED (uses a different endpoint)", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1" });
    const queryClient = new QueryClient();
    renderHook(() => useLocationsWithCounts(LocationType.NOT_ASSIGNED), {
      wrapper: createWrapper(queryClient),
    });

    expect(mockGetSiteLocationsWithCounts).not.toHaveBeenCalled();
  });

  it("rebinds to the new site's values on a site switch, not the old site's cached data", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1" });
    mockGetSiteLocationsWithCounts.mockImplementation((siteId: string) =>
      Promise.resolve(
        siteId === "site-1"
          ? [{ id: "loc-1", locationType: "BOX_BINS", locationCode: "B1", totalQuantity: 10, inventoryRecords: 1, activeDisplayCount: 0, hasActiveDisplay: false, createdAt: "", updatedAt: "" }]
          : [{ id: "loc-2", locationType: "BOX_BINS", locationCode: "B2", totalQuantity: 999, inventoryRecords: 1, activeDisplayCount: 0, hasActiveDisplay: false, createdAt: "", updatedAt: "" }]
      )
    );

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result, rerender } = renderHook(() => useLocationsWithCounts(LocationType.BOX_BIN), {
      wrapper: createWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.data?.[0]?.totalQuantity).toBe(10));

    mockUseCurrentSite.mockReturnValue({ siteId: "site-2" });
    rerender();

    await waitFor(() => expect(result.current.data?.[0]?.totalQuantity).toBe(999));
    expect(result.current.data?.[0]?.id).toBe("loc-2");
  });
});

describe("useAllLocationsWithCounts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches every type unfiltered once a site is known", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1" });
    mockGetSiteLocationsWithCounts.mockResolvedValue([]);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    renderHook(() => useAllLocationsWithCounts(), { wrapper: createWrapper(queryClient) });

    await waitFor(() => expect(mockGetSiteLocationsWithCounts).toHaveBeenCalledWith("site-1"));
  });

  it("shares its query key family with useLocationsWithCounts (both site-scoped)", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1" });
    mockGetSiteLocationsWithCounts.mockResolvedValue([]);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    renderHook(() => useAllLocationsWithCounts(), { wrapper: createWrapper(queryClient) });

    const matching = queryClient.getQueryCache().findAll({ queryKey: ["locationsWithCounts", "site-1"] });
    expect(matching.length).toBeGreaterThan(0);
  });
});
