import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGet = vi.fn();
const mockPut = vi.fn();

vi.mock("./client", () => ({
  apiGet: (...args: unknown[]) => mockGet(...args),
  apiPut: (...args: unknown[]) => mockPut(...args),
}));

import {
  getSiteAllUsersForReviewManagement,
  getSiteReviewSummaries,
  getSiteReviewTrackedUsers,
  getSiteUserReviewStats,
  getSiteUserReviews,
  updateSiteUserReviewTracking,
} from "./site-reviews";

const SITE_ID = "site-1";
const BASE = `/api/v1/sites/${SITE_ID}/reviews`;

describe("site-reviews.ts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue([]);
    mockPut.mockResolvedValue({});
  });

  it("reads monthly summaries under the site path", async () => {
    await getSiteReviewSummaries(SITE_ID, 2026, 9);
    expect(mockGet).toHaveBeenCalledWith(`${BASE}/summaries?year=2026&month=9`);
  });

  it("reads tracked and all users under the site path", async () => {
    await getSiteReviewTrackedUsers(SITE_ID);
    await getSiteAllUsersForReviewManagement(SITE_ID);
    expect(mockGet).toHaveBeenNthCalledWith(1, `${BASE}/users/tracked`);
    expect(mockGet).toHaveBeenNthCalledWith(2, `${BASE}/users/all`);
  });

  it("updates tracking under the site path", async () => {
    await updateSiteUserReviewTracking(SITE_ID, "u-1", { isReviewTracked: true });
    expect(mockPut).toHaveBeenCalledWith(`${BASE}/users/u-1/tracking`, { isReviewTracked: true });
  });

  it("builds the stats query only from provided year and month", async () => {
    await getSiteUserReviewStats(SITE_ID, "u-1");
    await getSiteUserReviewStats(SITE_ID, "u-1", 2026, 9);
    expect(mockGet).toHaveBeenNthCalledWith(1, `${BASE}/users/u-1/stats`);
    expect(mockGet).toHaveBeenNthCalledWith(2, `${BASE}/users/u-1/stats?year=2026&month=9`);
  });

  it("builds the paged review query under the site path", async () => {
    await getSiteUserReviews(SITE_ID, "u-1", { fromDate: "2026-09-01", toDate: "2026-09-30", page: 1, size: 10 });
    expect(mockGet).toHaveBeenCalledWith(
      `${BASE}/users/u-1/reviews?fromDate=2026-09-01&toDate=2026-09-30&page=1&size=10`
    );
  });
});
