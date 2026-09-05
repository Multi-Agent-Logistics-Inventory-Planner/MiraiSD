import { createApiClient, type ApiClient } from "@mirai/api-client";
import { BACKEND_BASE_URL } from "@/lib/api/backend-url";
import { getAuthToken, redirectToLogin } from "@/lib/api/auth-token";

export interface CreateWebApiClientOverrides {
  fetch?: typeof fetch;
}

/**
 * Platform-neutral generated client (see packages/api-client), wired to the same
 * Supabase-token and 401-redirect behavior as the legacy apiClient wrapper in ./client.ts
 * so the two paths never diverge on auth behavior.
 */
export function createWebApiClient(overrides: CreateWebApiClientOverrides = {}): ApiClient {
  return createApiClient({
    baseUrl: BACKEND_BASE_URL,
    fetch: overrides.fetch ?? fetch,
    getAccessToken: getAuthToken,
    onUnauthorized: redirectToLogin,
  });
}

export const webApiClient: ApiClient = createWebApiClient();

export class GeneratedApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "GeneratedApiError";
    this.status = status;
  }
}

interface GeneratedFetchResult<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

/**
 * Unwraps an openapi-fetch response, throwing a real `Error` (with `status`) instead of the
 * raw `error` value (which openapi-fetch types as the response's error-schema shape - a plain
 * object or string, not an `Error` - so code expecting `.message`/error-boundary handling
 * breaks). Also treats a non-ok response with no parsed error as a failure: openapi-fetch
 * returns `{ error: undefined }` for a non-ok response with an empty body (204, HEAD, or
 * Content-Length: 0 - e.g. a 502/504 from a proxy, or an empty 403), which would otherwise
 * read as success with no data.
 */
export function unwrapGeneratedResponse<T>({ data, error, response }: GeneratedFetchResult<T>): T {
  if (error || !response.ok) {
    const message =
      error && typeof error === "object" && "message" in error && typeof error.message === "string"
        ? error.message
        : `Request failed with status ${response.status}`;
    throw new GeneratedApiError(message, response.status);
  }
  return data as T;
}
