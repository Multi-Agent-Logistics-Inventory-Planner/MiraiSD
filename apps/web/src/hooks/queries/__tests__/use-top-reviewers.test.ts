import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockUseCurrentSite = vi.fn();
const mockGetSiteReviewSummaries = vi.fn();

vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

vi.mock("@/lib/api/site-reviews", () => ({
  getSiteReviewSummaries: (...args: unknown[]) => mockGetSiteReviewSummaries(...args),
}));

import { useTopReviewers } from "../use-top-reviewers";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useTopReviewers", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("does not fetch until the site resolves", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, isLoading: true, error: null });

    const { result } = renderHook(() => useTopReviewers(3), { wrapper: createWrapper() });

    expect(result.current.isLoading).toBe(true);
    expect(mockGetSiteReviewSummaries).not.toHaveBeenCalled();
  });

  it("ranks the site's summaries once the site is known", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", isLoading: false, error: null });
    mockGetSiteReviewSummaries.mockResolvedValue([
      { userId: "u-1", userName: "Alice", totalReviews: 3, averageReviewsPerDay: 1 },
      { userId: "u-2", userName: "Bob", totalReviews: 1, averageReviewsPerDay: 1 },
    ]);

    const { result } = renderHook(() => useTopReviewers(1), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(mockGetSiteReviewSummaries).toHaveBeenCalledWith("site-1", expect.any(Number), expect.any(Number));
    expect(result.current.data?.reviewers).toHaveLength(1);
    expect(result.current.data?.reviewers[0]).toMatchObject({ rank: 1, percentage: 75 });
  });
});
