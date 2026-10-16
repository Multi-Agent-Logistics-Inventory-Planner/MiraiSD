import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockUseCurrentSite = vi.fn();
const mockGetSiteUsers = vi.fn();
const mockGetSiteLastAudits = vi.fn();
const mockGetPendingInvitations = vi.fn();

vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

vi.mock("@/lib/api/site-users", () => ({
  getSiteUsers: (...args: unknown[]) => mockGetSiteUsers(...args),
  getSiteLastAudits: (...args: unknown[]) => mockGetSiteLastAudits(...args),
}));

vi.mock("@/lib/api/invitations", () => ({
  getPendingInvitations: () => mockGetPendingInvitations(),
}));

import { useTeamData } from "../use-team-data";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useTeamData", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetPendingInvitations.mockResolvedValue([]);
  });

  it("does not fetch until the site resolves", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: true, error: null });

    const { result } = renderHook(() => useTeamData(), { wrapper: createWrapper() });

    expect(result.current.isLoading).toBe(true);
    expect(mockGetSiteUsers).not.toHaveBeenCalled();
    expect(mockGetSiteLastAudits).not.toHaveBeenCalled();
  });

  it("builds member rows from the site's members and last audits", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", isLoading: false, error: null });
    mockGetSiteUsers.mockResolvedValue([
      { id: "u-1", fullName: "Alice", email: "a@x.test", role: "EMPLOYEE", createdAt: "2026-01-01T00:00:00Z" },
    ]);
    mockGetSiteLastAudits.mockResolvedValue({ "u-1": "2026-10-01T00:00:00Z" });

    const { result } = renderHook(() => useTeamData(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(mockGetSiteUsers).toHaveBeenCalledWith("site-1");
    expect(mockGetSiteLastAudits).toHaveBeenCalledWith("site-1");
    expect(result.current.data?.[0]).toMatchObject({ id: "u-1", lastAudit: "2026-10-01T00:00:00Z", type: "member" });
  });
});
