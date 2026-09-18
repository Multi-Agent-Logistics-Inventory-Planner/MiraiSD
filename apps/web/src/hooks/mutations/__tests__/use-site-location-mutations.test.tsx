import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { LocationType } from "@/types/api";
const site = vi.hoisted(() => ({ siteId: "main" as string | undefined, siteCode: "MAIN" }));
const api = vi.hoisted(() => ({ getSiteStorageLocations: vi.fn(), createSiteLocation: vi.fn(), updateSiteLocation: vi.fn(), deleteSiteLocation: vi.fn() }));
vi.mock("@/hooks/queries/use-current-site", () => ({ useCurrentSite: () => site }));
vi.mock("@/lib/api/locations", () => api);
import { useCreateLocationMutation, useUpdateLocationMutation, useDeleteLocationMutation } from "../use-location-mutations";

function setup() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  const invalidate = vi.spyOn(client, "invalidateQueries");
  const hook = renderHook(() => ({ create: useCreateLocationMutation(LocationType.RACK), update: useUpdateLocationMutation(LocationType.RACK), remove: useDeleteLocationMutation(LocationType.RACK) }), { wrapper: ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider> });
  return { ...hook, invalidate, client };
}
describe("Storage v1 mutations", () => {
  beforeEach(() => { vi.clearAllMocks(); site.siteId = "main"; site.siteCode = "MAIN"; api.getSiteStorageLocations.mockResolvedValue([{ id: "main-storage", code: "RACKS" }]); });
  it("rejects all writes before HTTP when site is unresolved", async () => {
    site.siteId = undefined;
    const { result } = setup();
    await act(async () => {
      await expect(result.current.create.mutateAsync({ locationCode: "R1" })).rejects.toThrow("No active site");
      await expect(result.current.update.mutateAsync({ id: "loc", payload: { locationCode: "R2" } })).rejects.toThrow("No active site");
      await expect(result.current.remove.mutateAsync({ id: "loc" })).rejects.toThrow("No active site");
    });
    for (const mock of Object.values(api)) expect(mock).not.toHaveBeenCalled();
  });
  it("keeps creation and invalidation on its original site across a pending lookup", async () => {
    let resolve!: (rows: unknown[]) => void;
    api.getSiteStorageLocations.mockReturnValue(new Promise(r => { resolve = r; }));
    api.createSiteLocation.mockResolvedValue({ id: "new" });
    const { result, rerender, invalidate } = setup();
    let request!: Promise<unknown>;
    await act(async () => { request = result.current.create.mutateAsync({ locationCode: "R1" }); });
    site.siteId = "second"; site.siteCode = "SECOND"; rerender();
    await act(async () => { resolve([{ id: "main-storage", code: "RACKS" }]); await request; });
    expect(api.getSiteStorageLocations).toHaveBeenCalledWith("main");
    expect(api.createSiteLocation).toHaveBeenCalledWith("main", { locationCode: "R1", storageLocationId: "main-storage" });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["locations", "main"] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["locationsWithCounts", "main"] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["locations", LocationType.RACK] });
    expect(invalidate.mock.calls.some(([arg]) => arg?.queryKey?.includes("second"))).toBe(false);
  });
  it("fails missing category without posting and sends rename/delete to selected site", async () => {
    site.siteId = "second"; site.siteCode = "SECOND";
    api.getSiteStorageLocations.mockResolvedValue([]);
    const { result } = setup();
    await act(async () => {
      await expect(result.current.create.mutateAsync({ locationCode: "R1" })).rejects.toThrow("Storage category");
      await result.current.update.mutateAsync({ id: "loc-second", payload: { locationCode: "R2" } });
      await result.current.remove.mutateAsync({ id: "loc-second" });
    });
    expect(api.createSiteLocation).not.toHaveBeenCalled();
    expect(api.updateSiteLocation).toHaveBeenCalledWith("second", "loc-second", { locationCode: "R2" });
    expect(api.deleteSiteLocation).toHaveBeenCalledWith("second", "loc-second");
  });
  it.each(["MAIN", "SECOND"])("refreshes legacy picker caches only for %s-origin writes", async (code) => {
    site.siteCode = code;
    site.siteId = code.toLowerCase();
    const { result, client } = setup();
    const scopedKey = ["locations", site.siteId, LocationType.RACK];
    const legacyKey = ["locations", LocationType.RACK];
    client.setQueryData(scopedKey, [{ id: "loc" }]);
    client.setQueryData(legacyKey, [{ id: "legacy-loc" }]);
    await act(async () => { await result.current.update.mutateAsync({ id: "loc", payload: { locationCode: "R2" } }); });
    expect(client.getQueryState(scopedKey)?.isInvalidated).toBe(true);
    expect(client.getQueryState(legacyKey)?.isInvalidated).toBe(code === "MAIN");
  });

});
