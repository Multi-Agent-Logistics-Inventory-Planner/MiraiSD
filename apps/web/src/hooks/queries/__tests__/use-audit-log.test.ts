import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockUseCurrentSite = vi.fn();
const mockGetSiteAuditLogs = vi.fn();
const mockGetSiteAuditLogDetail = vi.fn();

vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

vi.mock("@/lib/api/site-audit-logs", () => ({
  getSiteAuditLogs: (...args: unknown[]) => mockGetSiteAuditLogs(...args),
  getSiteAuditLogDetail: (...args: unknown[]) => mockGetSiteAuditLogDetail(...args),
}));

vi.mock("@/lib/api/stock-movements", () => ({
  getAuditLog: vi.fn(),
}));

import { useAuditLogDetail, useAuditLogs } from "../use-audit-log";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("audit log hooks", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("useAuditLogs waits for the site and then reads that site's logs", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: true, error: null });
    const { result, rerender } = renderHook(() => useAuditLogs({ search: "x" }, 1, 10), {
      wrapper: createWrapper(),
    });
    expect(result.current.isLoading).toBe(true);
    expect(mockGetSiteAuditLogs).not.toHaveBeenCalled();

    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", isLoading: false, error: null });
    mockGetSiteAuditLogs.mockResolvedValue({ content: [] });
    rerender();

    await waitFor(() => expect(mockGetSiteAuditLogs).toHaveBeenCalledWith("site-1", { search: "x" }, 1, 10));
  });

  it("useAuditLogDetail reads the detail within the site and stays idle without an id", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", isLoading: false, error: null });
    mockGetSiteAuditLogDetail.mockResolvedValue({ id: "log-1" });

    const idle = renderHook(() => useAuditLogDetail(null), { wrapper: createWrapper() });
    expect(idle.result.current.fetchStatus).toBe("idle");
    expect(mockGetSiteAuditLogDetail).not.toHaveBeenCalled();

    renderHook(() => useAuditLogDetail("log-1"), { wrapper: createWrapper() });
    await waitFor(() => expect(mockGetSiteAuditLogDetail).toHaveBeenCalledWith("site-1", "log-1"));
  });
});
