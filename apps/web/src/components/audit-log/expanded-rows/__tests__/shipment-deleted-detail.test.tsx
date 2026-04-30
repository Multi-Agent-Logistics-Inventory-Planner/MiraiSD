import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ShipmentDeletedDetail } from "../shipment-deleted-detail";
import { AuditLogDetail, StockMovementReason } from "@/types/api";

function buildDetail(fieldChanges: AuditLogDetail["fieldChanges"]): AuditLogDetail {
  return {
    id: "log-1",
    actorName: "Carol",
    reason: StockMovementReason.SHIPMENT_DELETED,
    itemCount: 2,
    totalQuantityMoved: 0,
    createdAt: "2026-04-30T12:00:00Z",
    movements: [],
    fieldChanges,
  };
}

describe("ShipmentDeletedDetail", () => {
  it("renders supplier when provided as a value entry", () => {
    render(
      <ShipmentDeletedDetail
        detail={buildDetail([
          { field: "supplier", value: "Acme Corp" },
          { field: "deleted_items", to: [] },
        ])}
      />
    );
    expect(screen.getByText(/Supplier:/)).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
  });

  it("renders deleted items with ordered/received columns", () => {
    render(
      <ShipmentDeletedDetail
        detail={buildDetail([
          {
            field: "deleted_items",
            from: null,
            to: [
              { name: "Widget A", ordered: 10, received: 5 },
              { name: "Widget B", ordered: 20, received: 0 },
            ],
          },
        ])}
      />
    );
    expect(screen.getByText("Widget A")).toBeInTheDocument();
    expect(screen.getByText("Widget B")).toBeInTheDocument();
    expect(screen.getByText("10")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("20")).toBeInTheDocument();
  });

  it("shows an empty-state when no items captured", () => {
    render(<ShipmentDeletedDetail detail={buildDetail([])} />);
    expect(screen.getByText(/No items captured/i)).toBeInTheDocument();
  });
});
