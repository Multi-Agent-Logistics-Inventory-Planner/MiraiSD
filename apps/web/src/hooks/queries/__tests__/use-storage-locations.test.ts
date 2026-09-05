import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockUseCurrentSite = vi.fn();
const mockGetSiteStorageLocations = vi.fn();

vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

vi.mock("@/lib/api/locations", () => ({
  getSiteStorageLocations: (...args: unknown[]) => mockGetSiteStorageLocations(...args),
}));

import { useStorageLocations } from "../use-storage-locations";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useStorageLocations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("stays disabled and does not call getSiteStorageLocations until siteId resolves", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: true, error: null });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useStorageLocations(), { wrapper });

    expect(result.current.isLoading).toBe(true);
    expect(mockGetSiteStorageLocations).not.toHaveBeenCalled();
  });

  it("calls getSiteStorageLocations with the resolved siteId once available", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", isLoading: false, error: null });
    mockGetSiteStorageLocations.mockResolvedValue([
      { id: "sl-1", code: "BOX_BINS", name: "Box Bins", hasDisplay: false, isDisplayOnly: false, displayOrder: 0 },
    ]);

    const wrapper = createWrapper();
    const { result } = renderHook(() => useStorageLocations(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetSiteStorageLocations).toHaveBeenCalledWith("site-1");
    expect(result.current.data).toEqual([
      expect.objectContaining({ id: "sl-1", code: "BOX_BINS" }),
    ]);
  });

  it("reports isLoading true while the site is still resolving, even before the storage-location query starts", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: true, error: null });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useStorageLocations(), { wrapper });

    // Regression: a disabled TanStack Query reports isLoading: false (fetchStatus "idle"),
    // which would otherwise read as "loaded, zero results" while /api/v1/me/sites is
    // still in flight - this must reflect the site resolution's own loading state instead.
    expect(result.current.isLoading).toBe(true);
  });

  it("surfaces the site-resolution error even though the storage-location query never ran", () => {
    const siteError = new Error("site resolution failed");
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: false, error: siteError });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useStorageLocations(), { wrapper });

    expect(result.current.error).toBe(siteError);
    expect(mockGetSiteStorageLocations).not.toHaveBeenCalled();
  });

  it("surfaces an error when the site query settles with no membership and no error of its own", () => {
    // A real access outcome (no active membership), not a request failure - must not read
    // as "resolved, zero storage locations", which LocationTabs would render as
    // "run the database seeder" instead of a real access/authorization problem.
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: false, error: null });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useStorageLocations(), { wrapper });

    expect(result.current.error).not.toBeNull();
    expect(mockGetSiteStorageLocations).not.toHaveBeenCalled();
  });
});
