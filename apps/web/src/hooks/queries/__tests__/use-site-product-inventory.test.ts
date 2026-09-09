import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import type { ReactNode } from "react";

const mockUseProducts = vi.fn();
const mockUseSiteProducts = vi.fn();

vi.mock("@/hooks/queries/use-products", () => ({
  useProducts: (...args: unknown[]) => mockUseProducts(...args),
}));

vi.mock("@/hooks/queries/use-site-products", () => ({
  useSiteProducts: () => mockUseSiteProducts(),
}));

import { useSiteProductInventory } from "../use-product-inventory";

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

const catalogProduct = (overrides: Record<string, unknown> = {}) => ({
  id: "p-1",
  name: "Widget",
  sku: "WID-1",
  imageUrl: "https://example.com/widget.png",
  isActive: true,
  quantity: 999, // legacy, site-blind field the join must never surface
  category: { id: "cat-1", name: "Toys" },
  hasChildren: false,
  updatedAt: "2026-01-01T00:00:00Z",
  // Legacy money fields the join must override, never pass through as-is.
  unitCost: 1,
  msrp: 2,
  reorderPoint: 3,
  ...overrides,
});

describe("useSiteProductInventory", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("stays null until both the catalog list and the site list have loaded", () => {
    mockUseProducts.mockReturnValue({ data: undefined, isLoading: true, error: null });
    mockUseSiteProducts.mockReturnValue({ data: undefined, siteCode: undefined, isLoading: true, error: null });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });

    expect(result.current.data).toBeNull();
    expect(result.current.isLoading).toBe(true);
  });

  it("joins by product ID: catalog fields from legacy getProducts, site-owned fields from getSiteProducts only (AC-6a)", () => {
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });
    mockUseSiteProducts.mockReturnValue({
      data: [
        {
          productId: "p-1",
          name: "Widget",
          isStocked: true,
          unitCost: 10,
          msrp: 20,
          reorderPoint: 5,
          targetStockLevel: 50,
          leadTimeDays: 7,
          forecastingEnabled: true,
          version: 3,
        },
      ],
      siteCode: "MAIN",
      isLoading: false,
      error: null,
    });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });

    const row = result.current.data?.[0];
    expect(row).toBeDefined();
    // Catalog/display fields: untouched, from legacy.
    expect(row?.product.name).toBe("Widget");
    expect(row?.product.imageUrl).toBe("https://example.com/widget.png");
    expect(row?.product.category).toEqual({ id: "cat-1", name: "Toys" });
    // Site-owned fields: overridden from the site response, not the legacy (1/2/3) values.
    expect(row?.product.unitCost).toBe(10);
    expect(row?.product.msrp).toBe(20);
    expect(row?.product.reorderPoint).toBe(5);
    expect(row?.product.targetStockLevel).toBe(50);
    expect(row?.product.leadTimeDays).toBe(7);
    expect(row?.product.forecastingEnabled).toBe(true);
    expect(row?.isStocked).toBe(true);
    expect(row?.siteProductVersion).toBe(3);
    // Quantity is withheld entirely (AC-6b) - not present, not zeroed.
    expect(row?.totalQuantity).toBeUndefined();
    expect(row?.status).toBeUndefined();
  });

  it("renders a product's own site state at each site when the site query result changes (site switch), never mixing rows", async () => {
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });

    // Site A carries the product with its own settings.
    mockUseSiteProducts.mockReturnValue({
      data: [{ productId: "p-1", name: "Widget", isStocked: true, msrp: 20, reorderPoint: 5, version: 1 }],
      siteCode: "MAIN",
      isLoading: false,
      error: null,
    });

    const wrapper = createWrapper();
    const { result, rerender } = renderHook(() => useSiteProductInventory(true), { wrapper });

    expect(result.current.data?.[0]).toMatchObject({
      isStocked: true,
      product: expect.objectContaining({ msrp: 20, reorderPoint: 5, name: "Widget" }),
    });

    // Site B has never carried the product: absent from the site list entirely (defensive case -
    // the real endpoint always returns an entry per AC-2, but the join must still degrade safely).
    mockUseSiteProducts.mockReturnValue({
      data: [],
      siteCode: "SECOND",
      isLoading: false,
      error: null,
    });
    rerender();

    await waitFor(() => {
      expect(result.current.data?.[0]).toMatchObject({
        isStocked: false,
        siteProductVersion: null,
        // Catalog fields are unchanged across the switch - proving the join keys by product ID
        // rather than accidentally depending on which site query happened to run.
        product: expect.objectContaining({ name: "Widget", category: { id: "cat-1", name: "Toys" } }),
      });
    });
  });

  it("propagates the site query's error (e.g. unresolved site membership)", () => {
    mockUseProducts.mockReturnValue({ data: [catalogProduct()], isLoading: false, error: null });
    const siteError = new Error("No active site membership for the current user");
    mockUseSiteProducts.mockReturnValue({ data: undefined, siteCode: undefined, isLoading: false, error: siteError });

    const wrapper = createWrapper();
    const { result } = renderHook(() => useSiteProductInventory(true), { wrapper });

    expect(result.current.error).toBe(siteError);
  });
});
