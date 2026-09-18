import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { LocationType, StockMovementReason } from "@/types/api";

const mockUseAuth = vi.fn();
vi.mock("@/hooks/use-auth", () => ({ useAuth: () => mockUseAuth() }));
vi.mock("@/hooks/queries/use-current-site", () => ({ useCurrentSite: () => ({ siteId: "site-1" }) }));

const mockToast = vi.fn();
vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: mockToast }) }));

vi.mock("@/hooks/queries/use-categories", () => ({
  useCategories: () => ({ data: [] }),
}));

const mockUseLocationInventory = vi.fn();
vi.mock("@/hooks/queries/use-location-inventory", () => ({
  useLocationInventory: (...args: unknown[]) => mockUseLocationInventory(...args),
}));

const mockBatchAdjustMutateAsync = vi.fn();
vi.mock("@/hooks/mutations/use-stock-mutations", () => ({
  useBatchAdjustStockMutation: () => ({
    mutateAsync: mockBatchAdjustMutateAsync,
    isPending: false,
  }),
}));

const mockCreateInventoryMutateAsync = vi.fn();
vi.mock("@/hooks/mutations/use-location-mutations", () => ({
  useCreateInventoryMutation: () => ({
    mutateAsync: mockCreateInventoryMutateAsync,
    isPending: false,
  }),
}));

// AddInventoryDialog is a real, separately-tested component with its own form UI; stubbed here
// to directly exercise the isUpdate branch handleAddNewInventory feeds it into, without driving
// its own internal add/update-detection UI.
vi.mock("@/components/locations/add-inventory-dialog", () => ({
  AddInventoryDialog: ({
    onSubmit,
  }: {
    onSubmit: (payload: { itemId: string; quantity: number }, isUpdate: boolean, inventoryId?: string) => void;
  }) => (
    <button
      type="button"
      onClick={() => onSubmit({ itemId: "p-1", quantity: 8 }, true, "inv-1")}
    >
      Simulate update existing row to 8
    </button>
  ),
}));

import { AdjustStockDialog } from "../adjust-stock-dialog";

const CATEGORY = { id: "cat-1", name: "Toys", slug: "toys", parentId: null, displayOrder: 0, isActive: true, usesPacks: false, children: [], createdAt: "", updatedAt: "" };

const EXISTING_INVENTORY_ROW = {
  id: "inv-1",
  locationId: "loc-1",
  locationCode: "R01",
  storageLocationType: "RACK",
  item: {
    id: "p-1",
    sku: "WID-1",
    name: "Widget",
    category: CATEGORY,
    imageUrl: undefined,
  },
  quantity: 5,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const PRESELECTED_PRODUCT = {
  product: { id: "p-1", name: "Widget", sku: "WID-1", imageUrl: undefined, category: CATEGORY },
  inventoryEntries: [
    {
      inventoryId: "inv-1",
      locationType: LocationType.RACK,
      locationId: "loc-1",
      locationCode: "R01",
      locationLabel: "R01",
      quantity: 5,
      updatedAt: "2026-01-01T00:00:00Z",
    },
  ],
};

const INITIAL_LOCATION = {
  locationType: LocationType.RACK,
  locationId: "loc-1",
  locationCode: "R01",
};

function renderDialog(props: Partial<React.ComponentProps<typeof AdjustStockDialog>> = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AdjustStockDialog
        open={true}
        onOpenChange={vi.fn()}
        initialLocation={INITIAL_LOCATION}
        preselectedProduct={PRESELECTED_PRODUCT}
        {...props}
      />
    </QueryClientProvider>
  );
}

describe("AdjustStockDialog (review finding 4 - rendered submit workflow)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue({ user: { id: "u-1", name: "Alex" } });
    mockUseLocationInventory.mockReturnValue({
      data: [EXISTING_INVENTORY_ROW],
      resolvedLocationId: "loc-1",
      isLoading: false,
    });
    mockBatchAdjustMutateAsync.mockResolvedValue(undefined);
  });

  it("submits a subtract adjustment for the selected row and shows success", async () => {
    renderDialog();

    // Single-product mode auto-selects the preselected product once inventory loads.
    await screen.findByText("Widget");

    fireEvent.click(await screen.findByRole("button", { name: "Increase quantity" }));
    fireEvent.click(screen.getByRole("button", { name: /adjust stock/i }));

    await waitFor(() => expect(mockBatchAdjustMutateAsync).toHaveBeenCalledTimes(1));
    expect(mockBatchAdjustMutateAsync).toHaveBeenCalledWith({
      payload: {
        locationType: LocationType.RACK,
        locationId: "loc-1",
        adjustments: [
          {
            inventoryId: "inv-1",
            quantityChange: -1,
            intakeUnit: undefined,
            intakeQty: undefined,
          },
        ],
        reason: StockMovementReason.SALE,
      },
      productIds: ["p-1"],
    });

    await waitFor(() =>
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: "Stock adjusted", variant: "success" })
      )
    );
  });

  it("computes a signed delta (not an absolute set) when updating an existing row's quantity (T-6d-9's replacement for the dropped legacy PUT)", async () => {
    // New quantity 8, existing row's quantity 5 (EXISTING_INVENTORY_ROW) - must submit delta
    // +3 through the audited batch-adjust mutation, never the raw absolute value 8.
    renderDialog();
    await screen.findByText("Widget");

    fireEvent.click(screen.getByRole("button", { name: "Simulate update existing row to 8" }));

    await waitFor(() => expect(mockBatchAdjustMutateAsync).toHaveBeenCalledTimes(1));
    expect(mockBatchAdjustMutateAsync).toHaveBeenCalledWith({
      payload: {
        locationType: LocationType.RACK,
        locationId: "loc-1",
        adjustments: [
          {
            inventoryId: "inv-1",
            quantityChange: 3,
            intakeUnit: undefined,
            intakeQty: undefined,
          },
        ],
        reason: StockMovementReason.ADJUSTMENT,
      },
      productIds: ["p-1"],
    });
    await waitFor(() =>
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: "Inventory added successfully", variant: "success" })
      )
    );
  });
});
