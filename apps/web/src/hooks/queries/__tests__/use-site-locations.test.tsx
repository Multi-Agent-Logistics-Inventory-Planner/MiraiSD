import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { LocationType } from "@/types/api";

const site = vi.hoisted(() => ({ siteId: "main" as string | undefined, isLoading: false }));
const getLocations = vi.hoisted(() => vi.fn());
vi.mock("@/hooks/queries/use-current-site", () => ({ useCurrentSite: () => site }));
vi.mock("@/lib/api/locations", () => ({ getSiteLocations: getLocations }));
import { useSiteLocations } from "../use-locations";

function setup(type?: LocationType) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const hook = renderHook(() => useSiteLocations(type), { wrapper: ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider> });
  return { ...hook, client };
}

describe("site location options", () => {
  beforeEach(() => { vi.clearAllMocks(); site.siteId = "main"; site.isLoading = false; });
  it("does not fetch without a site, type, or for virtual NOT_ASSIGNED", () => {
    site.siteId = undefined;
    expect(setup(LocationType.RACK).result.current.error).toBeTruthy();
    site.siteId = "main";
    setup(); setup(LocationType.NOT_ASSIGNED);
    expect(getLocations).not.toHaveBeenCalled();
  });
  it("isolates late old-site results and maps type to storage code", async () => {
    let resolveMain!: (data: unknown[]) => void;
    getLocations.mockImplementation((id: string) => id === "main" ? new Promise(resolve => { resolveMain = resolve; }) : Promise.resolve([{ id: "second-location" }]));
    const { result, rerender, client } = setup(LocationType.RACK);
    await waitFor(() => expect(getLocations).toHaveBeenCalledWith("main", "RACKS"));
    site.siteId = "second"; rerender();
    await waitFor(() => expect(result.current.data?.[0].id).toBe("second-location"));
    await act(async () => resolveMain([{ id: "main-location" }]));
    expect(result.current.data?.[0].id).toBe("second-location");
    expect(client.getQueryData(["locations", "main", LocationType.RACK])).toEqual([{ id: "main-location" }]);
  });
  it("surfaces a failed read, not a successful empty list", async () => {
    getLocations.mockRejectedValue(new Error("Forbidden"));
    const { result } = setup(LocationType.RACK);
    await waitFor(() => expect(result.current.error?.message).toBe("Forbidden"));
    expect(result.current.data).toBeUndefined();
  });
});
