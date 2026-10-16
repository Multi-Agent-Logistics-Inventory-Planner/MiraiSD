import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockUseCurrentSite = vi.fn();
const mockGetSiteUsers = vi.fn();

vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

vi.mock("@/lib/api/site-users", () => ({
  getSiteUsers: (...args: unknown[]) => mockGetSiteUsers(...args),
}));

import { useUsers } from "../use-users";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useUsers", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("does not fetch until the site resolves", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: true, error: null });

    renderHook(() => useUsers(), { wrapper: createWrapper() });

    expect(mockGetSiteUsers).not.toHaveBeenCalled();
  });

  it("loads the current site's members", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", isLoading: false, error: null });
    mockGetSiteUsers.mockResolvedValue([{ id: "u-1", fullName: "Alice" }]);

    const { result } = renderHook(() => useUsers(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.data).toEqual([{ id: "u-1", fullName: "Alice" }]));
    expect(mockGetSiteUsers).toHaveBeenCalledWith("site-1");
  });

  it("fetches again for the new site when the current site changes", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", isLoading: false, error: null });
    mockGetSiteUsers.mockResolvedValueOnce([{ id: "u-1", fullName: "Alice" }]);
    mockGetSiteUsers.mockResolvedValueOnce([{ id: "u-2", fullName: "Bob" }]);

    const { result, rerender } = renderHook(() => useUsers(), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current.data).toEqual([{ id: "u-1", fullName: "Alice" }]));

    mockUseCurrentSite.mockReturnValue({ siteId: "site-2", isLoading: false, error: null });
    rerender();

    await waitFor(() => expect(result.current.data).toEqual([{ id: "u-2", fullName: "Bob" }]));
    expect(mockGetSiteUsers).toHaveBeenLastCalledWith("site-2");
  });
});
