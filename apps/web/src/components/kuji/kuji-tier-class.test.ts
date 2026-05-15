import { describe, it, expect } from "vitest";
import type { KujiBoxTier } from "@/types/api";
import {
  normalizeLabel,
  tierClassColor,
  rollupByClass,
  formatChance,
} from "./kuji-tier-class";
import { TIER_PALETTE } from "./tier-palette";

function makeTier(overrides: Partial<KujiBoxTier>): KujiBoxTier {
  return {
    id: "t-" + Math.random().toString(36).slice(2),
    label: "1pk",
    letter: null,
    linkedProductId: null,
    linkedProductName: null,
    linkedProductImageUrl: null,
    linkedProductPacksPerBox: null,
    activeCount: 0,
    inactiveCount: 0,
    drawnCount: 0,
    totalCount: 0,
    price: null,
    linkedProductPrice: null,
    autoCreatedProduct: false,
    ...overrides,
  } as KujiBoxTier;
}

describe("normalizeLabel", () => {
  it("lowercases and trims", () => {
    expect(normalizeLabel("  1PK ")).toBe("1pk");
    expect(normalizeLabel("Sealed")).toBe("sealed");
  });

  it("returns empty for null/undefined/empty", () => {
    expect(normalizeLabel(null)).toBe("");
    expect(normalizeLabel(undefined)).toBe("");
    expect(normalizeLabel("")).toBe("");
  });
});

describe("tierClassColor", () => {
  it("returns the same color for the same label across casing/whitespace", () => {
    const a = tierClassColor("1pk");
    const b = tierClassColor("  1PK ");
    const c = tierClassColor("1Pk");
    expect(a).toBe(b);
    expect(a).toBe(c);
  });

  it("returns a color from TIER_PALETTE", () => {
    const color = tierClassColor("sealed");
    expect(TIER_PALETTE).toContain(color);
  });

  it("is deterministic across calls", () => {
    const first = tierClassColor("chase");
    for (let i = 0; i < 10; i += 1) {
      expect(tierClassColor("chase")).toBe(first);
    }
  });

  it("falls back for empty / null labels", () => {
    const fallback = TIER_PALETTE[TIER_PALETTE.length - 1];
    expect(tierClassColor(null)).toBe(fallback);
    expect(tierClassColor(undefined)).toBe(fallback);
    expect(tierClassColor("   ")).toBe(fallback);
  });
});

describe("rollupByClass", () => {
  it("groups duplicate labels case-insensitively and sums counts", () => {
    const rollup = rollupByClass([
      makeTier({ label: "1pk", activeCount: 5, inactiveCount: 1 }),
      makeTier({ label: "1PK", activeCount: 3, inactiveCount: 0 }),
      makeTier({ label: "sealed", activeCount: 2, inactiveCount: 4 }),
    ]);
    expect(rollup).toHaveLength(2);
    const onePk = rollup.find((r) => r.key === "1pk");
    expect(onePk).toBeDefined();
    expect(onePk!.count).toBe(2);
    expect(onePk!.active).toBe(8);
    expect(onePk!.inactive).toBe(1);
  });

  it("preserves the first occurrence's display casing", () => {
    const rollup = rollupByClass([
      makeTier({ label: "Sealed" }),
      makeTier({ label: "sealed" }),
    ]);
    expect(rollup[0]!.displayLabel).toBe("Sealed");
  });

  it("returns an empty array for no tiers", () => {
    expect(rollupByClass([])).toEqual([]);
  });
});

describe("formatChance", () => {
  it("rounds to 0 decimals when >= 1%", () => {
    expect(formatChance(50, 100)).toBe("50%");
    expect(formatChance(33, 100)).toBe("33%");
    expect(formatChance(1, 100)).toBe("1%");
  });

  it("uses 1 decimal when < 1%", () => {
    expect(formatChance(1, 200)).toBe("0.5%");
    expect(formatChance(3, 1000)).toBe("0.3%");
  });

  it("returns 0% for zero active or zero total", () => {
    expect(formatChance(0, 100)).toBe("0%");
    expect(formatChance(5, 0)).toBe("0%");
  });
});
