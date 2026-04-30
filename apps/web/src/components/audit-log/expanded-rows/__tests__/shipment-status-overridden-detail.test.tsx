import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ShipmentStatusOverriddenDetail } from "../shipment-status-overridden-detail";
import { AuditLogDetail, StockMovementReason } from "@/types/api";

function buildDetail(overrides: Partial<AuditLogDetail> = {}): AuditLogDetail {
  return {
    id: "log-1",
    actorName: "Bob",
    reason: StockMovementReason.SHIPMENT_STATUS_OVERRIDDEN,
    itemCount: 1,
    totalQuantityMoved: 0,
    createdAt: "2026-04-30T12:00:00Z",
    movements: [],
    ...overrides,
  };
}

describe("ShipmentStatusOverriddenDetail", () => {
  it("renders the previous → new status diff", () => {
    render(
      <ShipmentStatusOverriddenDetail
        detail={buildDetail({ previousStatus: "PENDING", newStatus: "RECEIVED" })}
      />
    );
    expect(screen.getByText("PENDING")).toBeInTheDocument();
    expect(screen.getByText("RECEIVED")).toBeInTheDocument();
  });

  it("renders the override reason when present", () => {
    render(
      <ShipmentStatusOverriddenDetail
        detail={buildDetail({
          previousStatus: "PENDING",
          newStatus: "RECEIVED",
          overrideReason: "missing tracking; items received",
        })}
      />
    );
    expect(screen.getByText(/Reason/i)).toBeInTheDocument();
    expect(screen.getByText("missing tracking; items received")).toBeInTheDocument();
  });

  it("hides the reason block when no override reason is set", () => {
    render(
      <ShipmentStatusOverriddenDetail
        detail={buildDetail({ previousStatus: "PENDING", newStatus: "RECEIVED" })}
      />
    );
    expect(screen.queryByText(/Reason/i)).not.toBeInTheDocument();
  });

  it("falls back to em-dash when status fields are missing", () => {
    render(<ShipmentStatusOverriddenDetail detail={buildDetail()} />);
    const dashes = screen.getAllByText("—");
    expect(dashes.length).toBeGreaterThanOrEqual(2);
  });
});
