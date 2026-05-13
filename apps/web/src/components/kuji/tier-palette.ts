import type { KujiBoxTier } from "@/types/api";

// Stable color palette indexed by tier rank (highest-priced tier first).
export const TIER_PALETTE = [
  "#BA7517",
  "#993C1D",
  "#185FA5",
  "#0F6E56",
  "#533AAB",
  "#99355A",
  "#3B6D11",
  "#5F5E5A",
  "#7A5C1A",
  "#1F7A8C",
] as const;

export function tierColor(rank: number): string {
  const idx = ((rank % TIER_PALETTE.length) + TIER_PALETTE.length) % TIER_PALETTE.length;
  return TIER_PALETTE[idx]!;
}

export function hexWithAlpha(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

// Tier sort: highest price first, nulls (priceless tiers) last, then alphabetical
// by displayed name (linkedProductName preferred, falling back to label).
export function compareTiers(
  a: Pick<KujiBoxTier, "price" | "label" | "linkedProductName">,
  b: Pick<KujiBoxTier, "price" | "label" | "linkedProductName">,
): number {
  const ap = a.price ?? null;
  const bp = b.price ?? null;
  if (ap !== bp) {
    if (ap == null) return 1;
    if (bp == null) return -1;
    return bp - ap;
  }
  const an = (a.linkedProductName?.trim() || a.label || "").toLowerCase();
  const bn = (b.linkedProductName?.trim() || b.label || "").toLowerCase();
  return an.localeCompare(bn, undefined, { sensitivity: "base" });
}
