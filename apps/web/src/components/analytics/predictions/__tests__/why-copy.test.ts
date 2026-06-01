import { describe, it, expect } from "vitest";
import { buildWhyCopy } from "../why-copy";
import type { ActionItem } from "@/types/analytics";

function makeItem(overrides: Partial<ActionItem>): ActionItem {
  return {
    itemId: "i",
    name: "n",
    sku: "s",
    imageUrl: null,
    categoryName: "c",
    currentStock: 10,
    reorderPoint: 5,
    targetStockLevel: 30,
    daysToStockout: 4,
    avgDailyDelta: -1,
    suggestedReorderQty: 0,
    suggestedOrderDate: null,
    leadTimeDays: 7,
    demandVelocity: 2,
    demandVolatility: 0,
    forecastAccuracy: null,
    confidence: 0.8,
    urgency: "HEALTHY",
    overdue: false,
    computedAt: null,
    ...overrides,
  };
}

describe("buildWhyCopy", () => {
  it("CRITICAL mentions reorder window and runs out", () => {
    const out = buildWhyCopy(makeItem({ urgency: "CRITICAL", currentStock: 2, leadTimeDays: 7 }));
    expect(out).toContain("2 left");
    expect(out).toContain("7-day reorder window");
    expect(out).toContain("runs out");
  });

  it("URGENT recommends ordering this week", () => {
    const out = buildWhyCopy(makeItem({ urgency: "URGENT", daysToStockout: 5, leadTimeDays: 3 }));
    expect(out).toContain("order this week");
    expect(out).toMatch(/~5 days/);
  });

  it("ATTENTION names the reorder point", () => {
    const out = buildWhyCopy(makeItem({ urgency: "ATTENTION", reorderPoint: 12 }));
    expect(out).toContain("reorder point of 12");
    expect(out).toContain("no action yet");
  });

  it("HEALTHY says comfortable runway", () => {
    const out = buildWhyCopy(makeItem({ urgency: "HEALTHY", currentStock: 50 }));
    expect(out).toContain("comfortable runway");
    expect(out).toContain("50 left");
  });

  it("handles null demand velocity gracefully", () => {
    const out = buildWhyCopy(makeItem({ urgency: "URGENT", demandVelocity: null }));
    expect(out).toContain("minimal demand");
  });

  it("handles null daysToStockout gracefully", () => {
    const out = buildWhyCopy(makeItem({ urgency: "URGENT", daysToStockout: null }));
    expect(out).toContain("an unknown number of days");
  });

  it("uses <1 day phrasing for sub-day stockout", () => {
    const out = buildWhyCopy(makeItem({ urgency: "URGENT", daysToStockout: 0.4 }));
    expect(out).toContain("<1 day");
  });
});
