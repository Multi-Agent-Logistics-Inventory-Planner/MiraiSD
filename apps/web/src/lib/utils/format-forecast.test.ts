import { describe, expect, it } from "vitest";
import { computePriorityScore } from "./format-forecast";

describe("computePriorityScore", () => {
  it("uses money-dominant weights when revenueAtRisk is present", () => {
    const score = computePriorityScore({
      daysToStockout: 0,
      demandVelocity: 10,
      confidence: 1,
      demandVolatility: 0,
      revenueAtRisk: 1000,
    });
    expect(score).toBeCloseTo(0.4 + 0.25 + 0.15 + 0.15, 5);
  });

  it("falls back to the legacy weights when revenueAtRisk is null", () => {
    // Items without an MSRP have no money signal; scoring their money term
    // as 0 would structurally demote them (an out-of-stock item capped at
    // ~0.55). The legacy weighting keeps urgency-driven ranking for them.
    const score = computePriorityScore({
      daysToStockout: 0,
      demandVelocity: 2,
      confidence: 0.5,
      demandVolatility: null,
      revenueAtRisk: null,
    });
    // days 1.0*0.4 + velocity 0.2*0.3 + confidence 0.5*0.2
    expect(score).toBeCloseTo(0.56, 5);
  });

  it("treats a missing revenueAtRisk field like null (legacy callers)", () => {
    const score = computePriorityScore({
      daysToStockout: 0,
      demandVelocity: 2,
      confidence: 0.5,
      demandVolatility: null,
    });
    expect(score).toBeCloseTo(0.56, 5);
  });

  it("keeps imminent stockouts ranked above distant ones within null-revenue items", () => {
    const base = {
      demandVelocity: 2,
      confidence: 0.5,
      demandVolatility: null,
      revenueAtRisk: null,
    };
    const outOfStock = computePriorityScore({ ...base, daysToStockout: 0 });
    const distant = computePriorityScore({ ...base, daysToStockout: 13 });
    expect(outOfStock).toBeGreaterThan(distant + 0.15);
  });

  it("applies the legacy volatility penalty on the null-revenue path", () => {
    const steady = computePriorityScore({
      daysToStockout: 5,
      demandVelocity: 2,
      confidence: 0.5,
      demandVolatility: 0,
      revenueAtRisk: null,
    });
    const volatile = computePriorityScore({
      daysToStockout: 5,
      demandVelocity: 2,
      confidence: 0.5,
      demandVolatility: 2,
      revenueAtRisk: null,
    });
    expect(steady - volatile).toBeCloseTo(0.1, 5);
  });
});
