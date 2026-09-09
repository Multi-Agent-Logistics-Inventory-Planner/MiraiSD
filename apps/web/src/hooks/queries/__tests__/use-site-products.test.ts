import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockUseCurrentSite = vi.fn();
const mockGetSiteProducts = vi.fn();

vi.mock("@/hooks/queries/use-current-site", () => ({
  useCurrentSite: () => mockUseCurrentSite(),
}));

vi.mock("@/lib/api/products", () => ({
  getSiteProducts: (...args: unknown[]) => mockGetSiteProducts(...args),
}));

import { useSiteProducts } from "../use-site-products";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useSiteProducts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("stays disabled and does not call getSiteProducts until siteId resolves", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, siteCode: undefined, isLoading: true, error: null });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProducts(), { wrapper });

    expect(result.current.isLoading).toBe(true);
    expect(mockGetSiteProducts).not.toHaveBeenCalled();
  });

  it("calls getSiteProducts with the resolved siteId once available", async () => {
    mockUseCurrentSite.mockReturnValue({ siteId: "site-1", siteCode: "MAIN", isLoading: false, error: null });
    mockGetSiteProducts.mockResolvedValue([
      { productId: "p-1", name: "Widget", isStocked: true, version: 0 },
    ]);

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProducts(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetSiteProducts).toHaveBeenCalledWith("site-1");
    expect(result.current.data).toEqual([
      expect.objectContaining({ productId: "p-1", isStocked: true }),
    ]);
    expect(result.current.siteCode).toBe("MAIN");
  });

  it("surfaces the site-resolution error even though the site-product query never ran", () => {
    const siteError = new Error("site resolution failed");
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, siteCode: undefined, isLoading: false, error: siteError });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProducts(), { wrapper });

    expect(result.current.error).toBe(siteError);
    expect(mockGetSiteProducts).not.toHaveBeenCalled();
  });

  it("surfaces an error when the site query settles with no membership and no error of its own", () => {
    mockUseCurrentSite.mockReturnValue({ siteId: undefined, siteCode: undefined, isLoading: false, error: null });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProducts(), { wrapper });

    expect(result.current.error).not.toBeNull();
    expect(mockGetSiteProducts).not.toHaveBeenCalled();
  });
});
