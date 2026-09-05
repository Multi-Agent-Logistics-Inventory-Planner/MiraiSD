import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockGet = vi.fn();

vi.mock("@/lib/api/generated-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/generated-client")>();
  return {
    ...actual,
    webApiClient: { GET: (...args: unknown[]) => mockGet(...args) },
  };
});

import { useCurrentSite } from "../use-current-site";

function createWrapper(queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  const Wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { Wrapper, queryClient };
}

function okResponse() {
  return new Response(null, { status: 200 });
}

describe("useCurrentSite", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("resolves the MAIN site when it is present", async () => {
    mockGet.mockResolvedValue({
      data: [
        { siteId: "second-id", siteCode: "SECOND", siteName: "Second Location" },
        { siteId: "main-id", siteCode: "MAIN", siteName: "Main Store" },
      ],
      error: undefined,
      response: okResponse(),
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useCurrentSite(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.siteId).toBe("main-id");
    expect(result.current.siteCode).toBe("MAIN");
  });

  it("fails closed (undefined siteId) rather than picking a non-MAIN membership", async () => {
    // Resolving to an arbitrary non-MAIN membership would mean the tab bar shows one
    // site's categories while the still-legacy rest of the page resolves to MAIN
    // server-side - two sites' data on one screen with no signal to the user.
    mockGet.mockResolvedValue({
      data: [{ siteId: "second-id", siteCode: "SECOND", siteName: "Second Location" }],
      error: undefined,
      response: okResponse(),
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useCurrentSite(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.siteId).toBeUndefined();
  });

  it("resolves undefined siteId with no active memberships, without throwing", async () => {
    mockGet.mockResolvedValue({ data: [], error: undefined, response: okResponse() });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useCurrentSite(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.siteId).toBeUndefined();
    expect(result.current.error).toBeNull();
  });

  it("surfaces an error instead of throwing when the request fails", async () => {
    mockGet.mockResolvedValue({
      data: undefined,
      error: { message: "boom" },
      response: new Response(null, { status: 500 }),
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useCurrentSite(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.siteId).toBeUndefined();
    expect(result.current.error).not.toBeNull();
  });

  it("surfaces an error for a non-ok response with no parsed error body", async () => {
    // e.g. a 502/504 from a proxy: openapi-fetch returns { error: undefined } for a
    // non-ok, empty-body response - this must not read as "resolved, no memberships".
    mockGet.mockResolvedValue({
      data: undefined,
      error: undefined,
      response: new Response(null, { status: 502 }),
    });

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useCurrentSite(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.error).not.toBeNull();
  });

  it("does not refetch on remount within the same session (long staleTime)", async () => {
    mockGet.mockResolvedValue({
      data: [{ siteId: "main-id", siteCode: "MAIN", siteName: "Main Store" }],
      error: undefined,
      response: okResponse(),
    });

    const { Wrapper, queryClient } = createWrapper();
    const first = renderHook(() => useCurrentSite(), { wrapper: Wrapper });
    await waitFor(() => expect(first.result.current.isLoading).toBe(false));

    first.unmount();

    const { Wrapper: SameClientWrapper } = createWrapper(queryClient);
    const second = renderHook(() => useCurrentSite(), { wrapper: SameClientWrapper });
    await waitFor(() => expect(second.result.current.siteId).toBe("main-id"));

    expect(mockGet).toHaveBeenCalledTimes(1);
  });
});
