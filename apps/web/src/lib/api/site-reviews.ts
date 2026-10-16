import { apiGet, apiPut } from "./client";
import {
  ReviewSummary,
  Review,
  PaginatedResponse,
  User,
  UserReviewTrackingRequest,
  UserReviewStats,
} from "@/types/api";

// Site-scoped review routes (.specs/phase-7-notifications-reviews-audit). The response shapes match
// the legacy /api/reviews routes; v1 only adds site scoping, so the existing @/types/api
// interfaces are reused.

export interface ReviewSearchParams {
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

function basePath(siteId: string): string {
  return `/api/v1/sites/${siteId}/reviews`;
}

export async function getSiteReviewSummaries(
  siteId: string,
  year: number,
  month: number
): Promise<ReviewSummary[]> {
  return apiGet<ReviewSummary[]>(`${basePath(siteId)}/summaries?year=${year}&month=${month}`);
}

export async function getSiteReviewTrackedUsers(siteId: string): Promise<User[]> {
  return apiGet<User[]>(`${basePath(siteId)}/users/tracked`);
}

export async function getSiteAllUsersForReviewManagement(siteId: string): Promise<User[]> {
  return apiGet<User[]>(`${basePath(siteId)}/users/all`);
}

export async function updateSiteUserReviewTracking(
  siteId: string,
  userId: string,
  data: UserReviewTrackingRequest
): Promise<User> {
  return apiPut<User, UserReviewTrackingRequest>(`${basePath(siteId)}/users/${userId}/tracking`, data);
}

export async function getSiteUserReviewStats(
  siteId: string,
  userId: string,
  year?: number,
  month?: number
): Promise<UserReviewStats> {
  const params = new URLSearchParams();
  if (year !== undefined) params.set("year", String(year));
  if (month !== undefined) params.set("month", String(month));
  const queryString = params.toString();
  return apiGet<UserReviewStats>(
    `${basePath(siteId)}/users/${userId}/stats${queryString ? `?${queryString}` : ""}`
  );
}

export async function getSiteUserReviews(
  siteId: string,
  userId: string,
  params: ReviewSearchParams = {}
): Promise<PaginatedResponse<Review>> {
  const searchParams = new URLSearchParams();
  if (params.fromDate) searchParams.set("fromDate", params.fromDate);
  if (params.toDate) searchParams.set("toDate", params.toDate);
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));
  const queryString = searchParams.toString();
  return apiGet<PaginatedResponse<Review>>(
    `${basePath(siteId)}/users/${userId}/reviews${queryString ? `?${queryString}` : ""}`
  );
}
