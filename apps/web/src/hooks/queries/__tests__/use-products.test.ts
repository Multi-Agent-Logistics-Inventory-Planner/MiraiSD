import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";
import { KujiType, type ProductListItem } from "@/types/api";

const mockGetProducts = vi.fn();

vi.mock("@/lib/api/products", () => ({
  getProducts: (...args: unknown[]) => mockGetProducts(...args),
  getProductWithChildren: vi.fn(),
  getProductChildren: vi.fn(),
  getProductById: vi.fn(),
}));

import { useProducts } from "../use-products";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    queryClient,
    Wrapper: function Wrapper({ children }: { children: ReactNode }) {
      return createElement(QueryClientProvider, { client: queryClient }, children);
    },
  };
}

function makeProduct(overrides: Partial<ProductListItem> & { id: string; name: string }): ProductListItem {
  return {
    isActive: true,
    quantity: 0,
    parentId: null,
    kujiType: null,
    category: { id: "cat-1", name: "Cat", parentId: null, slug: "cat", displayOrder: 0, isActive: true, usesPacks: false, children: [], createdAt: "", updatedAt: "" },
    hasChildren: false,
    updatedAt: "",
    ...overrides,
  };
}

describe("useProducts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches the unfiltered list exactly once for three differently-optioned callers mounted together (AC-1)", async () => {
    mockGetProducts.mockResolvedValue([
      makeProduct({ id: "p1", name: "Alpha" }),
      makeProduct({ id: "p2", name: "Beta", parentId: "p1" }),
    ]);
    const { Wrapper } = createWrapper();

    const all = renderHook(() => useProducts(), { wrapper: Wrapper });
    const rootOnly = renderHook(() => useProducts(true), { wrapper: Wrapper });
    const rootExcludeKuji = renderHook(
      () => useProducts({ rootOnly: true, excludeCustomKuji: true }),
      { wrapper: Wrapper }
    );

    await waitFor(() => {
      expect(all.result.current.isSuccess).toBe(true);
      expect(rootOnly.result.current.isSuccess).toBe(true);
      expect(rootExcludeKuji.result.current.isSuccess).toBe(true);
    });

    expect(mockGetProducts).toHaveBeenCalledTimes(1);
    expect(mockGetProducts).toHaveBeenCalledWith({});
  });

  it("rootOnly filters to products with no parent and sorts by name, matching the server's ORDER BY p.name (AC-1)", async () => {
    mockGetProducts.mockResolvedValue([
      makeProduct({ id: "p1", name: "Zebra" }),
      makeProduct({ id: "p2", name: "Apple" }),
      makeProduct({ id: "p3", name: "Child", parentId: "p1" }),
    ]);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useProducts(true), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.map((p) => p.name)).toEqual(["Apple", "Zebra"]);
  });

  it("excludeCustomKuji drops CUSTOM kuji-type products without resorting (AC-1)", async () => {
    mockGetProducts.mockResolvedValue([
      makeProduct({ id: "p1", name: "Regular" }),
      makeProduct({ id: "p2", name: "Custom Box", kujiType: KujiType.CUSTOM }),
      makeProduct({ id: "p3", name: "Premade Box", kujiType: KujiType.PREMADE }),
    ]);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useProducts({ excludeCustomKuji: true }), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.map((p) => p.id)).toEqual(["p1", "p3"]);
  });

  it("kujiOnly (with rootOnly) keeps only root products that have children", async () => {
    mockGetProducts.mockResolvedValue([
      makeProduct({ id: "p1", name: "Parent With Child", hasChildren: true }),
      makeProduct({ id: "p2", name: "Lonely Root", hasChildren: false }),
      makeProduct({ id: "p3", name: "Child", parentId: "p1" }),
    ]);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useProducts({ rootOnly: true, kujiOnly: true }), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.map((p) => p.id)).toEqual(["p1"]);
  });

  it("preserves hasActiveBox through rootOnly filtering (regression: CustomKujiTabs partitions purely on this field)", async () => {
    mockGetProducts.mockResolvedValue([
      makeProduct({ id: "p1", name: "Open Box Product", hasActiveBox: true }),
      makeProduct({ id: "p2", name: "Closed Box Product", hasActiveBox: false }),
    ]);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(() => useProducts(true), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.find((p) => p.id === "p1")?.hasActiveBox).toBe(true);
    expect(result.current.data?.find((p) => p.id === "p2")?.hasActiveBox).toBe(false);
  });

  it("respects the enabled option, matching the closed-dialog gating each direct caller previously implemented itself", async () => {
    mockGetProducts.mockResolvedValue([]);
    const { Wrapper } = createWrapper();

    const { result } = renderHook(
      () => useProducts({ rootOnly: true }, { enabled: false }),
      { wrapper: Wrapper }
    );

    expect(result.current.isPending).toBe(true);
    expect(mockGetProducts).not.toHaveBeenCalled();
  });
});
