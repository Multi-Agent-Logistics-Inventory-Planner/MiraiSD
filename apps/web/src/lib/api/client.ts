import { getSupabaseClient } from "@/lib/supabase";
import { ApiError } from "@/types/api";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:4000";

export class ApiClientError extends Error {
  status: number;
  error: string;
  fieldErrors?: Record<string, string>;

  constructor(apiError: ApiError) {
    super(apiError.message);
    this.name = "ApiClientError";
    this.status = apiError.status;
    this.error = apiError.error;
    this.fieldErrors = apiError.fieldErrors;
  }
}

async function getAuthToken(): Promise<string | null> {
  const supabase = getSupabaseClient();
  if (!supabase) return null;

  const {
    data: { session },
  } = await supabase.auth.getSession();
  return session?.access_token ?? null;
}

interface FetchOptions extends RequestInit {
  skipAuth?: boolean;
}

export async function apiClient<T>(
  endpoint: string,
  options: FetchOptions = {}
): Promise<T> {
  const { skipAuth = false, ...fetchOptions } = options;

  const headers = new Headers(fetchOptions.headers);

  // Set default content type for JSON requests
  if (
    !headers.has("Content-Type") &&
    fetchOptions.body &&
    typeof fetchOptions.body === "string"
  ) {
    headers.set("Content-Type", "application/json");
  }

  // Add authorization header if not skipped
  if (!skipAuth) {
    const token = await getAuthToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
  }

  const url = `${API_BASE_URL}${endpoint}`;

  const response = await fetch(url, {
    ...fetchOptions,
    headers,
  });

  // Handle 401 Unauthorized - redirect to login
  if (response.status === 401) {
    if (typeof window !== "undefined") {
      window.location.href = "/login";
    }
    throw new ApiClientError({
      timestamp: new Date().toISOString(),
      status: 401,
      error: "Unauthorized",
      message: "Authentication required",
    });
  }

  // Handle empty response (204 No Content, 201 Created with no body, etc.)
  const contentLength = response.headers.get("content-length");
  const contentType = response.headers.get("content-type");
  const isEmptyResponse =
    response.status === 204 ||
    contentLength === "0" ||
    (response.status === 201 && !contentType?.includes("application/json"));

  if (isEmptyResponse) {
    return undefined as T;
  }

  const data = await response.json();

  // Handle error responses
  if (!response.ok) {
    throw new ApiClientError(data as ApiError);
  }

  return data as T;
}

// HTTP method helpers

export async function apiGet<T>(
  endpoint: string,
  options?: FetchOptions
): Promise<T> {
  return apiClient<T>(endpoint, { ...options, method: "GET" });
}

export async function apiPost<T, B = unknown>(
  endpoint: string,
  body?: B,
  options?: FetchOptions
): Promise<T> {
  return apiClient<T>(endpoint, {
    ...options,
    method: "POST",
    body: body ? JSON.stringify(body) : undefined,
  });
}

export async function apiPut<T, B = unknown>(
  endpoint: string,
  body?: B,
  options?: FetchOptions
): Promise<T> {
  return apiClient<T>(endpoint, {
    ...options,
    method: "PUT",
    body: body ? JSON.stringify(body) : undefined,
  });
}

export async function apiDelete<T>(
  endpoint: string,
  options?: FetchOptions
): Promise<T> {
  return apiClient<T>(endpoint, { ...options, method: "DELETE" });
}
