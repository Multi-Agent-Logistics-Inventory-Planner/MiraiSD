import { describe, it, expect } from "vitest";
import { computeBoxValues } from "./kuji-value-rollups";
import type { KujiBox, KujiBoxTier } from "@/types/api";

function tier(overrides: Partial<KujiBoxTier> = {}): KujiBoxTier {
  return {
    id: "t-" + Math.random(),
    label: "A",
    activeCount: 0,
    inactiveCount: 0,
    drawnCount: 0,
    totalCount: 0,
    ...overrides,
  };
}

function box(tiers: KujiBoxTier[]): KujiBox {
  return {
    id: "b1",
    productId: "p1",
    productName: "Test",
    locationId: "l1",
    status: "OPEN" as KujiBox["status"],
    openedAt: new Date().toISOString(),
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    tiers,
    totalCount: tiers.reduce((s, t) => s + t.activeCount, 0),
  };
}

describe("computeBoxValues", () => {
  it("returns zeros for null box", () => {
    const r = computeBoxValues(null);
    expect(r.valueInBox).toBe(0);
    expect(r.evPerDraw).toBeNull();
  });

  it("sums per-tier active*price, held*price, drawn*price", () => {
    const r = computeBoxValues(box([
      tier({ activeCount: 10, inactiveCount: 2, drawnCount: 3, price: 5 }),
      tier({ activeCount: 4, inactiveCount: 0, drawnCount: 1, price: 20 }),
    ]));
    // 10*5 + 4*20 = 50 + 80 = 130
    expect(r.valueInBox).toBe(130);
    // 2*5 = 10
    expect(r.valueHeld).toBe(10);
    // 3*5 + 1*20 = 35
    expect(r.valueDrawn).toBe(35);
    // total original = 50+80 + 10 + 35 = 175
    expect(r.valueOriginal).toBe(175);
    expect(r.evPerDraw).toBeCloseTo(130 / 14);
  });

  it("falls back to linkedProductPrice when tier.price is null", () => {
    const r = computeBoxValues(box([
      tier({ activeCount: 3, price: null, linkedProductPrice: 8 }),
    ]));
    expect(r.valueInBox).toBe(24);
  });

  it("skips tiers with no effective price (contribute 0)", () => {
    const r = computeBoxValues(box([
      tier({ activeCount: 5, price: null, linkedProductPrice: null }),
      tier({ activeCount: 2, price: 10 }),
    ]));
    expect(r.valueInBox).toBe(20);
    expect(r.totalActive).toBe(7);
  });

  it("returns null EV when no active slips remain", () => {
    const r = computeBoxValues(box([
      tier({ activeCount: 0, drawnCount: 5, price: 10 }),
    ]));
    expect(r.evPerDraw).toBeNull();
  });
});
