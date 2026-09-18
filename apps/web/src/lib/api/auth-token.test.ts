import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

const mockGetSession = vi.fn();
let mockSupabaseClient: { auth: { getSession: typeof mockGetSession } } | null = {
  auth: { getSession: mockGetSession },
};

vi.mock("@/lib/supabase", () => ({
  getSupabaseClient: () => mockSupabaseClient,
}));

import { getAuthToken, redirectToLogin } from "./auth-token";

describe("getAuthToken", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSupabaseClient = { auth: { getSession: mockGetSession } };
  });

  it("returns the access token when a session exists", async () => {
    mockGetSession.mockResolvedValue({ data: { session: { access_token: "test-token" } } });

    await expect(getAuthToken()).resolves.toBe("test-token");
  });

  it("returns null when there is no active session", async () => {
    mockGetSession.mockResolvedValue({ data: { session: null } });

    await expect(getAuthToken()).resolves.toBeNull();
  });

  it("returns null when no Supabase client is configured", async () => {
    mockSupabaseClient = null;

    await expect(getAuthToken()).resolves.toBeNull();
    expect(mockGetSession).not.toHaveBeenCalled();
  });
});

describe("redirectToLogin", () => {
  const originalLocation = window.location;

  beforeEach(() => {
    // @ts-expect-error - reassigning window.location for the test
    delete window.location;
    // @ts-expect-error - minimal stub sufficient for asserting the redirect
    window.location = { href: "", pathname: "/products" };
  });

  afterEach(() => {
    // @ts-expect-error - restoring the original Location object after the test stub
    window.location = originalLocation;
  });

  it("navigates to /login", () => {
    redirectToLogin();

    expect(window.location.href).toBe("/login");
  });

  it.each(["/login", "/login/", "/login?redirect=%2Fproducts"])("does not reload %s after a 401", (url) => {
    const setHref = vi.fn();
    Object.defineProperty(window.location, "pathname", { value: new URL(url, "http://localhost").pathname });
    Object.defineProperty(window.location, "href", { set: setHref });
    redirectToLogin();
    expect(setHref).not.toHaveBeenCalled();
  });
});
