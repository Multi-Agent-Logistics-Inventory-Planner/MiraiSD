import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";

// --- Inventory-entries hook: this is the review finding under test (P2, 6d external review) -
// a failed read must never render identically to a genuine empty state. ---
const mockUseSiteProductInventoryEntries = vi.fn();
vi.mock("@/hooks/queries/use-product-inventory-entries", () => ({
  useSiteProductInventoryEntries: (...args: unknown[]) =>
    mockUseSiteProductInventoryEntries(...args),
}));

vi.mock("@/hooks/queries/use-kuji-box", () => ({
  useKujiAllocationsByProduct: () => ({ data: [] }),
}));

vi.mock("@/hooks/mutations/use-product-mutations", () => ({
  useDeleteProductMutation: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/queries/use-shipments-by-product", () => ({
  useShipmentsByProduct: () => ({ data: [], isLoading: false }),
}));

vi.mock("@/hooks/queries/use-machine-displays", () => ({
  useProductDisplayHistory: () => ({ data: [], isLoading: false }),
}));

vi.mock("@/hooks/use-permissions", () => ({
  usePermissions: () => ({
    canViewCosts: false,
    canViewMsrp: false,
    can: () => true,
  }),
}));

const mockToast = vi.fn();
vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: mockToast }),
}));

vi.mock("@/lib/api/products", () => ({
  getProductChildren: vi.fn(),
}));

vi.mock("@/lib/storage/images", () => ({
  deleteProductImage: vi.fn(),
}));

// --- Deep subcomponents not under test here: stubbed so the test stays focused on the
// inventory-table error/empty rendering (review finding P2), not the modal's other UI. ---
vi.mock("./delete-product-dialog", () => ({
  DeleteProductDialog: () => null,
}));
vi.mock("./kuji-prizes-dialog", () => ({
  KujiPrizesDialog: () => null,
}));
vi.mock("@/components/kuji", () => ({
  KujiBoxView: () => null,
}));
vi.mock("./product-image-lightbox", () => ({
  ProductImageLightbox: () => null,
}));

import { ProductModal } from "../product-modal";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";

const PRODUCT: ProductWithInventory = {
  product: {
    id: "p-1",
    sku: "SKU-1",
    name: "Widget",
    isActive: true,
    quantity: 12,
    category: { id: "cat-1", name: "Toys", slug: "toys" } as never,
    hasChildren: false,
    updatedAt: "2026-01-01T00:00:00Z",
  } as never,
  totalQuantity: 12,
};

function renderModal() {
  return render(
    <ProductModal
      open={true}
      onOpenChange={vi.fn()}
      product={PRODUCT}
      onAdjustClick={vi.fn()}
      onTransferClick={vi.fn()}
    />
  );
}

describe("ProductModal - inventory read error handling (review finding P2)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows an error/retry affordance, not the empty state, when the inventory read fails despite a nonzero totalQuantity", () => {
    const refetch = vi.fn();
    mockUseSiteProductInventoryEntries.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error("network error"),
      refetch,
    });

    renderModal();

    expect(screen.getByText("Couldn't load inventory")).toBeInTheDocument();
    expect(
      screen.queryByText("No inventory at any location")
    ).not.toBeInTheDocument();
    // The header total (from a different, still-successful query) still shows 12 - the bug this
    // fix closes is exactly that contradiction rendering silently.
    expect(screen.getByText("(12)")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it("disables Adjust and Transfer while the inventory read has failed, so a user can't act on a table that's actually just erroring", () => {
    mockUseSiteProductInventoryEntries.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error("network error"),
      refetch: vi.fn(),
    });

    renderModal();

    for (const button of screen.getAllByRole("button", { name: /transfer/i })) {
      expect(button).toBeDisabled();
    }
    for (const button of screen.getAllByRole("button", { name: /adjust/i })) {
      expect(button).toBeDisabled();
    }
  });

  it("still renders the genuine empty state when there is no error and no inventory entries", () => {
    mockUseSiteProductInventoryEntries.mockReturnValue({
      data: { entries: [] },
      isLoading: false,
      error: undefined,
      refetch: vi.fn(),
    });

    renderModal();

    expect(screen.getByText("No inventory at any location")).toBeInTheDocument();
    expect(screen.queryByText("Couldn't load inventory")).not.toBeInTheDocument();
  });
});
