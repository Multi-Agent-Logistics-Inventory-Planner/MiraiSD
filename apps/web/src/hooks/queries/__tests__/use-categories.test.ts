import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";
import type { Category } from "@/types/api";

const mockGetCatalogCategories = vi.fn();
const mockGetCategories = vi.fn();

vi.mock("@/lib/api/categories", () => ({
  getCatalogCategories: (...args: unknown[]) => mockGetCatalogCategories(...args),
  getCategories: (...args: unknown[]) => mockGetCategories(...args),
}));

import { useCategories, useChildCategories } from "../use-categories";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

function makeCategory(overrides: Partial<Category> & { id: string; name: string }): Category {
  return {
    parentId: null,
    slug: overrides.name.toLowerCase(),
    displayOrder: 0,
    isActive: true,
    usesPacks: false,
    children: [],
    createdAt: "",
    updatedAt: "",
    ...overrides,
  };
}

describe("useCategories", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("reads through the global catalog v1 route (getCatalogCategories), not the legacy one (spec.md phase-5d AC-7)", async () => {
    mockGetCatalogCategories.mockResolvedValue([makeCategory({ id: "c-1", name: "Toys" })]);

    const wrapper = createWrapper();
    const { result } = renderHook(() => useCategories(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockGetCatalogCategories).toHaveBeenCalledTimes(1);
    expect(mockGetCategories).not.toHaveBeenCalled();
    expect(result.current.data).toEqual([
      expect.objectContaining({ id: "c-1", name: "Toys" }),
    ]);
  });

  it("sorts root and child categories alphabetically (unchanged behavior)", async () => {
    mockGetCatalogCategories.mockResolvedValue([
      makeCategory({
        id: "c-2",
        name: "Zebras",
        children: [makeCategory({ id: "c-2b", name: "Stripes" }), makeCategory({ id: "c-2a", name: "Black" })],
      }),
      makeCategory({ id: "c-1", name: "Apples" }),
    ]);

    const wrapper = createWrapper();
    const { result } = renderHook(() => useCategories(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.map((c) => c.name)).toEqual(["Apples", "Zebras"]);
    expect(result.current.data?.[1].children.map((c) => c.name)).toEqual(["Black", "Stripes"]);
  });

  it("useChildCategories resolves a parent's children from the same v1-backed data", async () => {
    mockGetCatalogCategories.mockResolvedValue([
      makeCategory({
        id: "parent-1",
        name: "Toys",
        children: [makeCategory({ id: "child-1", name: "Figures" })],
      }),
    ]);

    const wrapper = createWrapper();
    const { result } = renderHook(() => useChildCategories("parent-1"), { wrapper });

    await waitFor(() => {
      expect(result.current.map((c) => c.id)).toEqual(["child-1"]);
    });
  });
});
