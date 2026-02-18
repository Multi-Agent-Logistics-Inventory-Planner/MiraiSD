import { apiGet, apiPost, apiPut } from "./client";
import {
  ReviewEmployee,
  ReviewEmployeeRequest,
  ReviewSummary,
  Review,
  PaginatedResponse,
} from "@/types/api";

const BASE_PATH = "/api/reviews";

// Employee endpoints

export async function getReviewEmployees(): Promise<ReviewEmployee[]> {
  return apiGet<ReviewEmployee[]>(`${BASE_PATH}/employees`);
}

export async function getReviewEmployeeById(id: string): Promise<ReviewEmployee> {
  return apiGet<ReviewEmployee>(`${BASE_PATH}/employees/${id}`);
}

export async function createReviewEmployee(
  data: ReviewEmployeeRequest
): Promise<ReviewEmployee> {
  return apiPost<ReviewEmployee, ReviewEmployeeRequest>(
    `${BASE_PATH}/employees`,
    data
  );
}

export async function updateReviewEmployee(
  id: string,
  data: ReviewEmployeeRequest
): Promise<ReviewEmployee> {
  return apiPut<ReviewEmployee, ReviewEmployeeRequest>(
    `${BASE_PATH}/employees/${id}`,
    data
  );
}

// Summary endpoints

export async function getReviewSummaries(
  year: number,
  month: number
): Promise<ReviewSummary[]> {
  return apiGet<ReviewSummary[]>(
    `${BASE_PATH}/summaries?year=${year}&month=${month}`
  );
}

// Individual review endpoints

export interface ReviewSearchParams {
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export async function getEmployeeReviews(
  employeeId: string,
  params: ReviewSearchParams = {}
): Promise<PaginatedResponse<Review>> {
  const searchParams = new URLSearchParams();
  if (params.fromDate) searchParams.set("fromDate", params.fromDate);
  if (params.toDate) searchParams.set("toDate", params.toDate);
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));

  const queryString = searchParams.toString();
  const url = `${BASE_PATH}/employees/${employeeId}/reviews${
    queryString ? `?${queryString}` : ""
  }`;

  return apiGet<PaginatedResponse<Review>>(url);
}
