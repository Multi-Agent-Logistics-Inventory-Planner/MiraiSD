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
});
