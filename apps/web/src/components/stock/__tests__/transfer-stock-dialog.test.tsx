import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { LocationType } from "@/types/api";
import type { LocationSelection } from "@/types/transfer";

const mockUseAuth = vi.fn();
vi.mock("@/hooks/use-auth", () => ({ useAuth: () => mockUseAuth() }));

const mockToast = vi.fn();
vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: mockToast }) }));

const mockUseLocationInventory = vi.fn();
vi.mock("@/hooks/queries/use-location-inventory", () => ({
  useLocationInventory: (...args: unknown[]) => mockUseLocationInventory(...args),
}));

const mockBatchTransferMutateAsync = vi.fn();
vi.mock("@/hooks/mutations/use-stock-mutations", () => ({
  useBatchTransferMutation: () => ({
    mutateAsync: mockBatchTransferMutateAsync,
    isPending: false,
  }),
}));

// The "To" LocationSelector is a real Radix-backed component that also calls useLocations() (a
// real network hook, unmocked here) - stubbed to a plain button that sets the destination
// directly, since driving its own UI isn't what this test is proving.
vi.mock("../location-selector", () => ({
  LocationSelector: ({
    label,
    onChange,
  }: {
    label: string;
    onChange: (v: LocationSelection) => void;
  }) =>
    label === "To" ? (
      <button
        type="button"
        onClick={() =>
          onChange({ locationType: LocationType.RACK, locationId: "loc-2", locationCode: "R02" })
        }
      >
        Set destination to R02
      </button>
    ) : null,
}));

import { TransferStockDialog } from "../transfer-stock-dialog";

const CATEGORY = { id: "cat-1", name: "Toys", slug: "toys", parentId: null, displayOrder: 0, isActive: true, usesPacks: false, children: [], createdAt: "", updatedAt: "" };

const SOURCE_INVENTORY_ROW = {
  id: "inv-1",
  locationId: "loc-1",
  locationCode: "R01",
  storageLocationType: "RACK",
  item: { id: "p-1", sku: "WID-1", name: "Widget", category: CATEGORY, imageUrl: undefined },
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

const INITIAL_SOURCE_LOCATION = {
  locationType: LocationType.RACK,
  locationId: "loc-1",
  locationCode: "R01",
};

function renderDialog() {
  return render(
    <TransferStockDialog
      open={true}
      onOpenChange={vi.fn()}
      initialSourceLocation={INITIAL_SOURCE_LOCATION}
      preselectedProduct={PRESELECTED_PRODUCT}
    />
  );
}

describe("TransferStockDialog (review finding 4 - rendered submit workflow)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue({ user: { id: "u-1", name: "Alex" } });
    mockUseLocationInventory.mockImplementation(
      (_locationType: LocationType | undefined, locationId: string | undefined) => {
        if (locationId === "loc-1") {
          return { data: [SOURCE_INVENTORY_ROW], resolvedLocationId: "loc-1", isLoading: false };
        }
        if (locationId === "loc-2") {
          return { data: [], resolvedLocationId: "loc-2", isLoading: false };
        }
        return { data: [], resolvedLocationId: undefined, isLoading: false };
      }
    );
    mockBatchTransferMutateAsync.mockResolvedValue(undefined);
  });

  it("fills source/destination/quantity and submits with the correct transfer payload", async () => {
    renderDialog();

    await screen.findByText("Widget");
    fireEvent.click(screen.getByRole("button", { name: "Set destination to R02" }));

    fireEvent.click(
      await screen.findByRole("button", { name: "Increase quantity for Widget" })
    );

    fireEvent.click(screen.getByRole("button", { name: /^transfer/i }));

    await waitFor(() => expect(mockBatchTransferMutateAsync).toHaveBeenCalledTimes(1));
    expect(mockBatchTransferMutateAsync).toHaveBeenCalledWith({
      transfers: [
        {
          payload: {
            sourceLocationType: LocationType.RACK,
            sourceInventoryId: "inv-1",
            destinationLocationType: LocationType.RACK,
            destinationLocationId: "loc-2",
            quantity: 1,
          },
          productId: "p-1",
          productName: "Widget",
        },
      ],
      sourceLocationId: "loc-1",
      destinationLocationId: "loc-2",
      sourceLocationType: LocationType.RACK,
      destinationLocationType: LocationType.RACK,
    });

    await waitFor(() =>
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: "Transfer complete" })
      )
    );
  });
});
