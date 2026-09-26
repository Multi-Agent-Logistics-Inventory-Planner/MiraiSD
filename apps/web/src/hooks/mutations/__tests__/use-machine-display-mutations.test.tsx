import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { LocationType } from "@/types/api";

const site = vi.hoisted(() => ({ siteId: "main" as string | undefined }));
const api = vi.hoisted(() => ({
  setSiteMachineDisplay: vi.fn(),
  setSiteMachineDisplayBatch: vi.fn(),
  clearSiteMachineDisplay: vi.fn(),
  clearSiteDisplayById: vi.fn(),
  swapSiteMachineDisplay: vi.fn(),
  batchSwapSiteDisplay: vi.fn(),
  batchClearSiteDisplays: vi.fn(),
  renewSiteDisplays: vi.fn(),
  deleteSiteDisplayHistory: vi.fn(),
}));
vi.mock("@/hooks/queries/use-current-site", () => ({ useCurrentSite: () => site }));
vi.mock("@/lib/api/site-machine-displays", () => api);

import {
  useSetMachineDisplayMutation,
  useClearMachineDisplayMutation,
  useDeleteDisplayHistoryMutation,
} from "../use-machine-display-mutations";

function setup() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const hook = renderHook(
    () => ({
      set: useSetMachineDisplayMutation(),
      clear: useClearMachineDisplayMutation(),
      deleteHistory: useDeleteDisplayHistoryMutation(),
    }),
    {
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    }
  );
  return hook;
}

/**
 * .specs/phase-7-displays T-4, external review finding: no test covered the site-scoping added to
 * these 9 rewritten mutation hooks. Covers a payload-shaped hook, a spread-variables hook, the
 * string-variable delete-history hook, and the "no active site" rejection every one of them shares.
 */
describe("use-machine-display-mutations site scoping", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    site.siteId = "main";
    api.setSiteMachineDisplay.mockResolvedValue({ id: "d1" });
    api.clearSiteMachineDisplay.mockResolvedValue(undefined);
    api.deleteSiteDisplayHistory.mockResolvedValue(undefined);
  });

  it("threads the current siteId into a payload-shaped mutation (useSetMachineDisplayMutation)", async () => {
    const { result } = setup();

    await act(async () => {
      await result.current.set.mutateAsync({ locationType: LocationType.SINGLE_CLAW_MACHINE, machineId: "m1", productId: "p1" });
    });

    expect(api.setSiteMachineDisplay).toHaveBeenCalledWith(
      "main",
      expect.objectContaining({ machineId: "m1", productId: "p1" })
    );
  });

  it("threads the current siteId into a spread-variables mutation (useClearMachineDisplayMutation)", async () => {
    const { result } = setup();

    await act(async () => {
      await result.current.clear.mutateAsync({ locationType: LocationType.SINGLE_CLAW_MACHINE, machineId: "m1" });
    });

    expect(api.clearSiteMachineDisplay).toHaveBeenCalledWith("main", LocationType.SINGLE_CLAW_MACHINE, "m1");
  });

  it("threads the current siteId into the string-variable mutation (useDeleteDisplayHistoryMutation)", async () => {
    const { result } = setup();

    await act(async () => {
      await result.current.deleteHistory.mutateAsync("d1");
    });

    expect(api.deleteSiteDisplayHistory).toHaveBeenCalledWith("main", "d1");
  });

  it("rejects with 'No active site' and never calls the API when no site has resolved", async () => {
    site.siteId = undefined;
    const { result } = setup();

    await expect(
      act(async () => {
        await result.current.set.mutateAsync({ locationType: LocationType.SINGLE_CLAW_MACHINE, machineId: "m1", productId: "p1" });
      })
    ).rejects.toThrow("No active site");

    expect(api.setSiteMachineDisplay).not.toHaveBeenCalled();
  });
});
