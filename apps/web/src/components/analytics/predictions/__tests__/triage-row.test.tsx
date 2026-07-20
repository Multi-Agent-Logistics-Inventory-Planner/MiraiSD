import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { TriageRow } from "../triage-row";
import { TooltipProvider } from "@/components/ui/tooltip";
import type { ActionItem } from "@/types/analytics";

function makeItem(overrides?: Partial<ActionItem>): ActionItem {
  return {
    itemId: "item-1",
    name: "Test Product",
    sku: "SKU-001",
    imageUrl: "https://example.com/image.jpg",
    categoryName: "Snacks",
    currentStock: 15,
    reorderPoint: 10,
    targetStockLevel: 50,
    daysToStockout: 5.5,
    avgDailyDelta: -2.5,
    suggestedReorderQty: 40,
    suggestedOrderDate: "2026-06-15",
    leadTimeDays: 3,
    demandVelocity: 2.5,
    demandVolatility: 0.3,
    forecastAccuracy: 0.85,
    confidence: 0.9,
    urgency: "URGENT",
    overdue: false,
    computedAt: new Date().toISOString(),
    demandSegment: null,
    revenueAtRisk: null,
    lastDropSize: null,
    lastDropDays: null,
    onOrderQty: null,
    ...overrides,
  };
}

function renderRow(props: React.ComponentProps<typeof TriageRow>) {
  return render(
    <TooltipProvider>
      <TriageRow {...props} />
    </TooltipProvider>,
  );
}

describe("TriageRow", () => {
  it("renders item name and severity label for URGENT", () => {
    renderRow({ item: makeItem({ urgency: "URGENT" }) });
    expect(screen.getByText("Test Product")).toBeInTheDocument();
    expect(screen.getByText("This week")).toBeInTheDocument();
  });

  it("renders 'Order today' label for CRITICAL", () => {
    renderRow({ item: makeItem({ urgency: "CRITICAL" }) });
    expect(screen.getByText("Order today")).toBeInTheDocument();
  });

  it("shows Overdue badge when overdue", () => {
    renderRow({ item: makeItem({ overdue: true }) });
    expect(screen.getByText("Overdue")).toBeInTheDocument();
  });

  it("renders product image with lazy loading", () => {
    const { container } = renderRow({ item: makeItem() });
    const img = container.querySelector("img");
    expect(img).not.toBeNull();
    expect(img).toHaveAttribute("loading", "lazy");
    expect(img).toHaveAttribute("alt", "Test Product");
  });

  it("renders fallback icon when imageUrl is null", () => {
    const { container } = renderRow({ item: makeItem({ imageUrl: null }) });
    expect(container.querySelector("img")).toBeNull();
  });

  it("calls onDismiss when dismiss button is clicked", () => {
    const onDismiss = vi.fn();
    renderRow({ item: makeItem(), onDismiss });
    fireEvent.click(screen.getByLabelText("Dismiss prediction"));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it("calls onRestore when restore button is clicked", () => {
    const onRestore = vi.fn();
    renderRow({ item: makeItem(), onRestore });
    fireEvent.click(screen.getByLabelText("Restore prediction"));
    expect(onRestore).toHaveBeenCalledTimes(1);
  });

  it("includes plain-language 'Why' copy", () => {
    renderRow({ item: makeItem({ urgency: "URGENT" }) });
    expect(screen.getByText(/order this week/i)).toBeInTheDocument();
  });

  it("shows Drop badge for drop-segment items", () => {
    renderRow({ item: makeItem({ demandSegment: "drop" }) });
    expect(screen.getByText("Drop")).toBeInTheDocument();
  });

  it("does not show Drop badge for continuous items", () => {
    renderRow({ item: makeItem({ demandSegment: "continuous" }) });
    expect(screen.queryByText("Drop")).not.toBeInTheDocument();
  });

  it("shows revenue at risk when positive", () => {
    renderRow({ item: makeItem({ revenueAtRisk: 840 }) });
    expect(screen.getByText("$840")).toBeInTheDocument();
    expect(screen.getByText("at risk")).toBeInTheDocument();
  });

  it("hides revenue at risk when zero or null", () => {
    renderRow({ item: makeItem({ revenueAtRisk: 0 }) });
    expect(screen.queryByText("at risk")).not.toBeInTheDocument();
  });

  it("shows inbound units when on order", () => {
    renderRow({ item: makeItem({ onOrderQty: 30 }) });
    expect(screen.getByText("30")).toBeInTheDocument();
    expect(screen.getByText("inbound")).toBeInTheDocument();
  });

  it("uses drop sell-through copy in Why line for drop items", () => {
    renderRow({
      item: makeItem({
        demandSegment: "drop",
        lastDropSize: 180,
        lastDropDays: 1,
        currentStock: 0,
      }),
    });
    expect(
      screen.getByText(/last drop of 180 units sold out in 1 day/i),
    ).toBeInTheDocument();
  });
});
