import createFetchClient, { type Middleware } from "openapi-fetch";
import type { paths } from "./schema.js";

export type { paths } from "./schema.js";

export interface CreateApiClientOptions {
  /** Base URL of the inventory-service, e.g. https://api.mirai-inventory.com */
  baseUrl: string;
  /** Injected fetch implementation. Callers provide this - no browser/Node global is assumed. */
  fetch: typeof fetch;
  /** Returns the current access token, or null/undefined if the caller is unauthenticated. */
  getAccessToken?: () => string | null | undefined | Promise<string | null | undefined>;
  /** Called when the server responds 401. Does not throw or redirect itself. */
  onUnauthorized?: () => void;
  /** Correlation ID to attach to every request (see shared/correlation on the backend). */
  correlationId?: string;
}

/**
 * Platform-neutral generated API client. Must not import browser globals, Next.js routing,
 * Supabase browser clients or React Native secure storage - see docs/specs/client-applications.md
 * section 4. Web and mobile each provide their own fetch/token/redirect behavior.
 */
export function createApiClient(options: CreateApiClientOptions) {
  const { baseUrl, fetch, getAccessToken, onUnauthorized, correlationId } = options;

  const client = createFetchClient<paths>({ baseUrl, fetch });

  const middleware: Middleware = {
    async onRequest({ request }) {
      const token = await getAccessToken?.();
      if (token) {
        request.headers.set("Authorization", `Bearer ${token}`);
      }
      if (correlationId) {
        request.headers.set("X-Correlation-Id", correlationId);
      }
      return request;
    },
    async onResponse({ response }) {
      if (response.status === 401) {
        onUnauthorized?.();
      }
      return response;
    },
  };

  client.use(middleware);

  return client;
}

export type ApiClient = ReturnType<typeof createApiClient>;
