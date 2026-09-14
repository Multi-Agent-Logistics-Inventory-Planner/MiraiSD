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

const mockUseProducts = vi.fn();
vi.mock("@/hooks/queries/use-products", () => ({
  useProducts: () => mockUseProducts(),
}));

const mockGetSiteLocations = vi.fn();
vi.mock("@/lib/api/locations", () => ({
  getSiteLocations: (...args: unknown[]) => mockGetSiteLocations(...args),
}));

const mockGetSiteLocationInventory = vi.fn();
vi.mock("@/lib/api/site-inventory", () => ({
  getSiteLocationInventory: (...args: unknown[]) => mockGetSiteLocationInventory(...args),
}));

import { useLocationInventory } from "../use-location-inventory";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

const CATEGORY = { id: "cat-1", name: "Toys", slug: "toys", parentId: null, displayOrder: 0, isActive: true, usesPacks: false, children: [], createdAt: "", updatedAt: "" };
const PRODUCT = { id: "p-1", sku: "SKU-1", name: "Widget", isActive: true, quantity: 0, category: CATEGORY, updatedAt: "2026-01-01T00:00:00Z" };

describe("useLocationInventory", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", isLoading: false, error: null });
    mockUseProducts.mockReturnValue({ data: [PRODUCT], isLoading: false, error: null });
  });

  it("fetches by the given real locationId for a normal location type", async () => {
    mockGetSiteLocationInventory.mockResolvedValue([
      { inventoryId: "inv-1", productId: "p-1", quantity: 4, updatedAt: "2026-01-01T00:00:00Z" },
    ]);

    const wrapper = createWrapper();
    const { result } = renderHook(
      () => useLocationInventory(LocationType.RACK, "loc-1", "R01"),
      { wrapper }
    );

    await waitFor(() => expect(result.current.data).toBeDefined());

    expect(mockGetSiteLocations).not.toHaveBeenCalled();
    expect(mockGetSiteLocationInventory).toHaveBeenCalledWith("site-1", "loc-1");
    expect(result.current.data?.[0]).toMatchObject({
      id: "inv-1",
      locationId: "loc-1",
      locationCode: "R01",
      quantity: 4,
    });
  });

  it("resolves the NOT_ASSIGNED virtual id through the site-scoped locations route (T-6d-9)", async () => {
    mockGetSiteLocations.mockResolvedValue([
      { id: "loc-na-1", locationCode: "NA", storageLocationId: "sl-na", storageLocationType: "NOT_ASSIGNED", createdAt: "", updatedAt: "" },
    ]);
    mockGetSiteLocationInventory.mockResolvedValue([
      { inventoryId: "inv-2", productId: "p-1", quantity: 9, updatedAt: "2026-01-02T00:00:00Z" },
    ]);

    const wrapper = createWrapper();
    const { result } = renderHook(
      () => useLocationInventory(LocationType.NOT_ASSIGNED, "__not_assigned__"),
      { wrapper }
    );

    await waitFor(() => expect(result.current.data).toBeDefined());

    expect(mockGetSiteLocations).toHaveBeenCalledWith("site-1", "NOT_ASSIGNED");
    expect(mockGetSiteLocationInventory).toHaveBeenCalledWith("site-1", "loc-na-1");
    expect(result.current.resolvedLocationId).toBe("loc-na-1");
    expect(result.current.data?.[0]).toMatchObject({ locationCode: "NA", quantity: 9 });
  });

  it("stays disabled (no fetch, no data) until siteId resolves", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: true, error: null });

    const wrapper = createWrapper();
    const { result } = renderHook(
      () => useLocationInventory(LocationType.RACK, "loc-1", "R01"),
      { wrapper }
    );

    expect(mockGetSiteLocationInventory).not.toHaveBeenCalled();
    expect(result.current.data).toBeUndefined();
    expect(result.current.isLoading).toBe(true);
  });

  it("stays disabled for a display-only location type", () => {
    const wrapper = createWrapper();
    renderHook(
      () => useLocationInventory(LocationType.GACHAPON, "loc-1", "G01"),
      { wrapper }
    );

    expect(mockGetSiteLocationInventory).not.toHaveBeenCalled();
  });
});
