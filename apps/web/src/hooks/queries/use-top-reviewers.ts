"use client";

import { useQuery } from "@tanstack/react-query";
import { getReviewSummaries } from "@/lib/api/reviews";
import type { ReviewSummary } from "@/types/api";

export interface TopReviewer extends ReviewSummary {
  rank: number;
  percentage: number;
}

export interface TopReviewersData {
  reviewers: TopReviewer[];
  totalReviews: number;
  month: number;
  year: number;
}

/**
 * Hook to fetch top reviewers for the current month.
 * Returns top N reviewers with their rank and percentage of total reviews.
 */
export function useTopReviewers(limit: number = 3) {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;

  return useQuery<TopReviewersData>({
    queryKey: ["top-reviewers", year, month, limit],
    queryFn: async () => {
      const summaries = await getReviewSummaries(year, month);

      const totalReviews = summaries.reduce(
        (sum, s) => sum + s.totalReviews,
        0
      );

      const topReviewers: TopReviewer[] = summaries
        .slice(0, limit)
        .map((summary, index) => ({
          ...summary,
          rank: index + 1,
          percentage:
            totalReviews > 0
              ? Math.round((summary.totalReviews / totalReviews) * 100)
              : 0,
        }));

      return {
        reviewers: topReviewers,
        totalReviews,
        month,
        year,
      };
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}
