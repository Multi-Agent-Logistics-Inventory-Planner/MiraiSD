import { apiGet, apiPut } from "./client";
import {
  ReviewSummary,
  Review,
  PaginatedResponse,
  User,
  UserReviewTrackingRequest,
  UserReviewStats,
} from "@/types/api";

const BASE_PATH = "/api/reviews";

// Summary endpoints

export async function getReviewSummaries(
  year: number,
  month: number
): Promise<ReviewSummary[]> {
  return apiGet<ReviewSummary[]>(
    `${BASE_PATH}/summaries?year=${year}&month=${month}`
  );
}

// User-based review tracking endpoints

export interface ReviewSearchParams {
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export async function getReviewTrackedUsers(): Promise<User[]> {
  return apiGet<User[]>(`${BASE_PATH}/users/tracked`);
}

export async function getAllUsersForReviewManagement(): Promise<User[]> {
  return apiGet<User[]>(`${BASE_PATH}/users/all`);
}

export async function getUserForReviewTracking(userId: string): Promise<User> {
  return apiGet<User>(`${BASE_PATH}/users/${userId}`);
}

export async function updateUserReviewTracking(
  userId: string,
  data: UserReviewTrackingRequest
): Promise<User> {
  return apiPut<User, UserReviewTrackingRequest>(
    `${BASE_PATH}/users/${userId}/tracking`,
    data
  );
}

// User stats and reviews endpoints

export async function getUserReviewStats(
  userId: string,
  year?: number,
  month?: number
): Promise<UserReviewStats> {
  const params = new URLSearchParams();
  if (year !== undefined) params.set("year", String(year));
  if (month !== undefined) params.set("month", String(month));
  const queryString = params.toString();
  return apiGet<UserReviewStats>(
    `${BASE_PATH}/users/${userId}/stats${queryString ? `?${queryString}` : ""}`
  );
}

export async function getUserReviews(
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
    `${BASE_PATH}/users/${userId}/reviews${queryString ? `?${queryString}` : ""}`
  );
}
