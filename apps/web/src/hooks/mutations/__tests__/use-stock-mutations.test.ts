import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";
import { LocationType, StockMovementReason } from "@/types/api";

const mockUseCurrentSite = vi.fn();
vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

const mockAdjustSiteInventory = vi.fn();
vi.mock("@/lib/api/site-inventory", () => ({
  adjustSiteInventory: (...args: unknown[]) => mockAdjustSiteInventory(...args),
  transferSiteInventory: vi.fn(),
  batchTransferSiteInventory: vi.fn(),
  newIdempotencyKey: () => crypto.randomUUID(),
}));

import { useBatchAdjustStockMutation } from "../use-stock-mutations";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

const PAYLOAD = {
  locationType: LocationType.RACK,
  locationId: "loc-1",
  adjustments: [{ inventoryId: "inv-1", quantityChange: -2 }],
  reason: StockMovementReason.SALE,
};

describe("useBatchAdjustStockMutation (T-6d-2 idempotency)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", isLoading: false, error: null });
    mockAdjustSiteInventory.mockResolvedValue(undefined);
  });

  it("sends no client-supplied actorId and a freshly generated idempotency key per mutate() call", async () => {
    const wrapper = createWrapper();
    const { result } = renderHook(() => useBatchAdjustStockMutation(), { wrapper });

    result.current.mutate({ payload: PAYLOAD, productIds: ["p-1"] });

    await waitFor(() => expect(mockAdjustSiteInventory).toHaveBeenCalledTimes(1));
    const [siteId, key1, payload1] = mockAdjustSiteInventory.mock.calls[0];
    expect(siteId).toBe("site-1");
    expect(typeof key1).toBe("string");
    expect(key1.length).toBeGreaterThan(0);
    expect(payload1).not.toHaveProperty("actorId");

    // A second, separate user-initiated call gets a distinct key.
    result.current.mutate({ payload: PAYLOAD, productIds: ["p-1"] });
    await waitFor(() => expect(mockAdjustSiteInventory).toHaveBeenCalledTimes(2));
    const [, key2] = mockAdjustSiteInventory.mock.calls[1];
    expect(key2).not.toBe(key1);
  });

  it("rejects when no site is resolved, without calling the API", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: false, error: null });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useBatchAdjustStockMutation(), { wrapper });

    result.current.mutate({ payload: PAYLOAD, productIds: ["p-1"] });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(mockAdjustSiteInventory).not.toHaveBeenCalled();
  });
});
