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
const mockUseAuth = vi.fn();
vi.mock("@/hooks/use-auth", () => ({ useAuth: () => mockUseAuth() }));

const mockAdjustSiteInventory = vi.fn();
vi.mock("@/lib/api/site-inventory", () => ({
  adjustSiteInventory: (...args: unknown[]) => mockAdjustSiteInventory(...args),
  transferSiteInventory: vi.fn(),
  batchTransferSiteInventory: vi.fn(),
  newIdempotencyKey: () => crypto.randomUUID(),
}));

const mockFlushInventorySiteRefresh = vi.fn();
vi.mock("@/hooks/realtime/inventory-refresh", () => ({
  flushInventorySiteRefresh: (...args: unknown[]) => mockFlushInventorySiteRefresh(...args),
}));

import { useBatchAdjustStockMutation } from "../use-stock-mutations";
import { clearUncertainStockSubmission, readUncertainStockSubmission } from "@/lib/stock-submission-recovery";

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
    sessionStorage.clear();
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", isLoading: false, error: null });
    mockUseAuth.mockReturnValue({ user: { id: "user-1" } });
    mockAdjustSiteInventory.mockResolvedValue(undefined);
    mockFlushInventorySiteRefresh.mockResolvedValue(undefined);
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

  it("a failed totals refresh does not fail the mutation (Blocker 2) - the write already committed", async () => {
    mockFlushInventorySiteRefresh.mockRejectedValue(new Error("network down"));

    const wrapper = createWrapper();
    const { result } = renderHook(() => useBatchAdjustStockMutation(), { wrapper });

    result.current.mutate({ payload: PAYLOAD, productIds: ["p-1"] });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.isError).toBe(false);
  });

  it("retains a response-lost command for explicit retry and never mints a second key", async () => {
    mockAdjustSiteInventory.mockRejectedValueOnce(new Error("response lost")).mockResolvedValueOnce(undefined);
    const wrapper = createWrapper();
    const { result } = renderHook(() => useBatchAdjustStockMutation(), { wrapper });

    await expect(result.current.mutateAsync({ payload: PAYLOAD, productIds: ["p-1"] })).rejects.toThrow("response lost");
    const recovery = readUncertainStockSubmission("user-1", "site-1", "adjust");
    expect(recovery).toMatchObject({ kind: "adjust", siteId: "site-1" });
    const originalKey = recovery?.idempotencyKey;

    await expect(result.current.mutateAsync({ payload: PAYLOAD, productIds: ["p-1"] })).rejects.toThrow("awaiting explicit recovery");
    await result.current.retryUncertain();
    expect(mockAdjustSiteInventory.mock.calls.map((call) => call[1])).toEqual([originalKey, originalKey]);
    expect(readUncertainStockSubmission("user-1", "site-1", "adjust")).toBeNull();
    if (recovery) clearUncertainStockSubmission(recovery);
  });

  it("clears a definitive client rejection so a corrected adjustment can be submitted", async () => {
    const rejection = Object.assign(new Error("insufficient stock"), { status: 400 });
    mockAdjustSiteInventory.mockRejectedValueOnce(rejection).mockResolvedValueOnce(undefined);
    const { result } = renderHook(() => useBatchAdjustStockMutation(), { wrapper: createWrapper() });
    await expect(result.current.mutateAsync({ payload: PAYLOAD, productIds: ["p-1"] })).rejects.toThrow("insufficient stock");
    expect(readUncertainStockSubmission("user-1", "site-1", "adjust")).toBeNull();
    await expect(result.current.mutateAsync({ payload: PAYLOAD, productIds: ["p-1"] })).resolves.toBeUndefined();
    expect(mockAdjustSiteInventory).toHaveBeenCalledTimes(2);
  });

  it("unblocks a corrected submission when an explicit recovery retry receives a 400", async () => {
    const rejection = Object.assign(new Error("insufficient stock"), { status: 400 });
    mockAdjustSiteInventory.mockRejectedValueOnce(new Error("response lost")).mockRejectedValueOnce(rejection).mockResolvedValueOnce(undefined);
    const { result } = renderHook(() => useBatchAdjustStockMutation(), { wrapper: createWrapper() });

    await expect(result.current.mutateAsync({ payload: PAYLOAD, productIds: ["p-1"] })).rejects.toThrow("response lost");
    await expect(result.current.retryUncertain()).rejects.toThrow("insufficient stock");
    expect(readUncertainStockSubmission("user-1", "site-1", "adjust")).toBeNull();

    await expect(result.current.mutateAsync({ payload: { ...PAYLOAD, adjustments: [{ inventoryId: "inv-1", quantityChange: -1 }] }, productIds: ["p-1"] })).resolves.toBeUndefined();
    expect(mockAdjustSiteInventory).toHaveBeenCalledTimes(3);
  });
});
