import { describe, it, expect } from "vitest";
import { buildHealthBanner } from "../health-banner-copy";

describe("buildHealthBanner", () => {
  it("returns good verdict for low WAPE", () => {
    const r = buildHealthBanner({
      ltWape: 0.1,
      biasUnitsPerDay: 0,
      underStockedPct: 0.05,
      weekOverWeekDelta: 0,
    });
    expect(r.verdict).toBe("good");
    expect(r.headline.toLowerCase()).toContain("tracking actual demand");
  });

  it("returns warn verdict for mid WAPE", () => {
    const r = buildHealthBanner({
      ltWape: 0.35,
      biasUnitsPerDay: -1,
      underStockedPct: 0.4,
      weekOverWeekDelta: -0.02,
    });
    expect(r.verdict).toBe("warn");
  });

  it("returns bad verdict for high WAPE under-forecasting", () => {
    const r = buildHealthBanner({
      ltWape: 0.72,
      biasUnitsPerDay: -2.5,
      underStockedPct: 0.89,
      weekOverWeekDelta: -0.014,
    });
    expect(r.verdict).toBe("bad");
    expect(r.headline.toLowerCase()).toContain("under actual demand");
  });

  it("flags over-forecasting when bias is positive", () => {
    const r = buildHealthBanner({
      ltWape: 0.8,
      biasUnitsPerDay: 4,
      underStockedPct: 0.1,
      weekOverWeekDelta: 0,
    });
    expect(r.verdict).toBe("bad");
    expect(r.headline.toLowerCase()).toContain("over actual demand");
  });

  it("uses null WAPE to default to warn verdict", () => {
    const r = buildHealthBanner({
      ltWape: null,
      biasUnitsPerDay: 0,
      underStockedPct: null,
      weekOverWeekDelta: null,
    });
    expect(r.verdict).toBe("warn");
  });

  it("emits trend clause based on weekOverWeekDelta sign", () => {
    const improving = buildHealthBanner({
      ltWape: 0.7,
      biasUnitsPerDay: -2,
      underStockedPct: 0.8,
      weekOverWeekDelta: -0.02,
    });
    expect(improving.body.map((s) => s.text).join("")).toContain("improving");

    const worsening = buildHealthBanner({
      ltWape: 0.7,
      biasUnitsPerDay: -2,
      underStockedPct: 0.8,
      weekOverWeekDelta: 0.02,
    });
    expect(worsening.body.map((s) => s.text).join("")).toContain("worsening");
  });
});
