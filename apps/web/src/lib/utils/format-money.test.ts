import { describe, it, expect } from "vitest";
import { formatMoney, formatMoneyDecimals } from "./format-money";

describe("formatMoney", () => {
  it("formats whole-dollar amounts with comma separators", () => {
    expect(formatMoney(3587)).toBe("$3,587");
    expect(formatMoney(0)).toBe("$0");
  });

  it("returns em-dash for null / undefined / NaN", () => {
    expect(formatMoney(null)).toBe("—");
    expect(formatMoney(undefined)).toBe("—");
    expect(formatMoney(NaN)).toBe("—");
  });
});

describe("formatMoneyDecimals", () => {
  it("always shows two decimal places", () => {
    expect(formatMoneyDecimals(24.5)).toBe("$24.50");
    expect(formatMoneyDecimals(1000)).toBe("$1,000.00");
    expect(formatMoneyDecimals(0)).toBe("$0.00");
  });

  it("returns em-dash for null / undefined / NaN", () => {
    expect(formatMoneyDecimals(null)).toBe("—");
    expect(formatMoneyDecimals(undefined)).toBe("—");
    expect(formatMoneyDecimals(NaN)).toBe("—");
  });
});
