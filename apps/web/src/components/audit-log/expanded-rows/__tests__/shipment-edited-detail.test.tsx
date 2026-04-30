import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ShipmentEditedDetail } from "../shipment-edited-detail";
import { AuditLogDetail, StockMovementReason } from "@/types/api";

function buildDetail(fieldChanges: AuditLogDetail["fieldChanges"]): AuditLogDetail {
  return {
    id: "log-1",
    actorName: "Alice",
    reason: StockMovementReason.SHIPMENT_EDITED,
    itemCount: 1,
    totalQuantityMoved: 0,
    createdAt: "2026-04-30T12:00:00Z",
    movements: [],
    fieldChanges,
  };
}

describe("ShipmentEditedDetail", () => {
  it("renders scalar field changes as FROM → TO rows", () => {
    render(
      <ShipmentEditedDetail
        detail={buildDetail([
          { field: "supplier", from: "Acme", to: "Globex" },
          { field: "status", from: "PENDING", to: "RECEIVED" },
        ])}
      />
    );
    expect(screen.getByText("Supplier")).toBeInTheDocument();
    expect(screen.getByText("Acme")).toBeInTheDocument();
    expect(screen.getByText("Globex")).toBeInTheDocument();
    expect(screen.getByText("Status")).toBeInTheDocument();
    expect(screen.getByText("PENDING")).toBeInTheDocument();
    expect(screen.getByText("RECEIVED")).toBeInTheDocument();
  });

  it("renders 'Notes updated' without revealing note contents", () => {
    render(<ShipmentEditedDetail detail={buildDetail([{ field: "notes", changed: true }])} />);
    expect(screen.getByText("Notes updated")).toBeInTheDocument();
  });

  it("renders items added as a list", () => {
    render(
      <ShipmentEditedDetail
        detail={buildDetail([
          {
            field: "items_added",
            from: null,
            to: [
              { name: "Widget A", qty: 3 },
              { name: "Widget B", qty: 5 },
            ],
          },
        ])}
      />
    );
    expect(screen.getByText("Items added")).toBeInTheDocument();
    expect(screen.getByText("Widget A")).toBeInTheDocument();
    expect(screen.getByText("(3)")).toBeInTheDocument();
    expect(screen.getByText("Widget B")).toBeInTheDocument();
    expect(screen.getByText("(5)")).toBeInTheDocument();
  });

  it("renders items removed using the from side", () => {
    render(
      <ShipmentEditedDetail
        detail={buildDetail([
          { field: "items_removed", from: [{ name: "Old Item", qty: 2 }], to: null },
        ])}
      />
    );
    expect(screen.getByText("Items removed")).toBeInTheDocument();
    expect(screen.getByText("Old Item")).toBeInTheDocument();
  });

  it("renders an empty-state when no changes are present", () => {
    render(<ShipmentEditedDetail detail={buildDetail([])} />);
    expect(screen.getByText(/No field changes recorded/i)).toBeInTheDocument();
  });
});
