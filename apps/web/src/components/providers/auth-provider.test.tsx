import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, waitFor, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock("@/lib/api/auth", () => ({
  getAuthSession: vi.fn(),
}));

vi.mock("@/lib/auth-cache", () => ({
  getCachedSession: vi.fn(() => null),
  setCachedSession: vi.fn(),
  clearSessionCache: vi.fn(),
}));

let authStateCallback:
  | ((event: string, session: { user?: unknown; access_token?: string } | null) => void)
  | null = null;

vi.mock("@/lib/supabase", () => ({
  getSupabaseClient: () => ({
    auth: {
      getSession: () => Promise.resolve({ data: { session: null } }),
      signOut: vi.fn(),
      onAuthStateChange: (
        callback: (event: string, session: unknown) => void
      ) => {
        authStateCallback = callback as typeof authStateCallback;
        return { data: { subscription: { unsubscribe: vi.fn() } } };
      },
    },
  }),
}));

import { AuthProvider } from "./auth-provider";

describe("AuthProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authStateCallback = null;
  });

  it("clears the query cache on an externally triggered SIGNED_OUT event", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    // Seed a cache entry as if a previous user's session had populated it (e.g.
    // useCurrentSite's ["me","sites"]).
    queryClient.setQueryData(["me", "sites"], { siteId: "stale-user-site" });

    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <div>child</div>
        </AuthProvider>
      </QueryClientProvider>
    );

    await waitFor(() => expect(authStateCallback).not.toBeNull());
    expect(queryClient.getQueryData(["me", "sites"])).toBeDefined();

    // Simulate Supabase firing SIGNED_OUT on its own - token expiry, revocation, or a
    // sign-out in another tab - not the app's own signOut() callback.
    act(() => {
      authStateCallback?.("SIGNED_OUT", null);
    });

    await waitFor(() => {
      expect(queryClient.getQueryData(["me", "sites"])).toBeUndefined();
    });
    expect(mockPush).toHaveBeenCalledWith("/login");
  });
});
