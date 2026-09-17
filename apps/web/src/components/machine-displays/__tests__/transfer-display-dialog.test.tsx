import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { LocationType } from "@/types/api";

const api = vi.hoisted(() => ({
  getSiteLocations: vi.fn(), getLocationsByType: vi.fn(), getProducts: vi.fn(),
  getActiveDisplaysByType: vi.fn(), getActiveDisplaysForMachine: vi.fn(),
}));
vi.mock("@/lib/api/locations", () => api);
vi.mock("@/lib/api/products", () => api);
vi.mock("@/lib/api/machine-displays", () => api);
vi.mock("@/hooks/queries/use-current-site", () => ({ useCurrentSite: () => ({ siteId: "main", isLoading: false }) }));
import { TransferDisplayDialog } from "../transfer-display-dialog";

describe("display transfer query lifecycle", () => {
  beforeEach(() => { for (const mock of Object.values(api)) mock.mockReset().mockResolvedValue([]); });
  it("fetches only while open and uses site-scoped destination locations", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const view = (open: boolean, locationType = LocationType.GACHAPON) => (
      <QueryClientProvider client={client}>
        <TransferDisplayDialog open={open} onOpenChange={vi.fn()} locationType={locationType}
          currentMachineId="source" currentMachineCode="G1" currentDisplays={[]}
          onTransferWithMachine={vi.fn()} onSwapWithProducts={vi.fn()} />
      </QueryClientProvider>
    );
    const { rerender } = render(view(false));
    await act(async () => {});
    for (const mock of Object.values(api)) expect(mock).not.toHaveBeenCalled();
    rerender(view(true));
    await waitFor(() => expect(api.getSiteLocations).toHaveBeenCalledWith("main", "GACHAPON"));
    await waitFor(() => expect(api.getProducts).toHaveBeenCalledTimes(1));
    expect(api.getActiveDisplaysByType).toHaveBeenCalledWith(LocationType.GACHAPON);
    expect(api.getLocationsByType).not.toHaveBeenCalled();
    rerender(view(false, LocationType.DOUBLE_CLAW_MACHINE));
    await act(async () => { await client.invalidateQueries(); });
    expect(api.getSiteLocations).toHaveBeenCalledTimes(1);
    expect(api.getProducts).toHaveBeenCalledTimes(1);
    expect(api.getActiveDisplaysByType).toHaveBeenCalledTimes(1);
    expect(api.getActiveDisplaysForMachine).not.toHaveBeenCalled();
  });
});
