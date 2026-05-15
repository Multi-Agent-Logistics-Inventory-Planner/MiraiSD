import type { KujiBoxTier } from "@/types/api";
import { TIER_PALETTE } from "./tier-palette";

export function normalizeLabel(label: string | null | undefined): string {
  return (label ?? "").trim().toLowerCase();
}

function hashKey(key: string): number {
  let h = 2166136261;
  for (let i = 0; i < key.length; i += 1) {
    h ^= key.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

export function tierClassColor(label: string | null | undefined): string {
  const key = normalizeLabel(label);
  if (!key) {
    return TIER_PALETTE[TIER_PALETTE.length - 1]!;
  }
  const idx = hashKey(key) % TIER_PALETTE.length;
  return TIER_PALETTE[idx]!;
}

// Build a collision-resolved color map for a specific set of tiers.
// Each unique class label gets a distinct palette slot (starting from its
// preferred hash bucket and walking forward to the next free slot). Falls
// back to the bare hash when the palette is exhausted.
export function buildTierClassColorMap(
  tiers: readonly KujiBoxTier[],
): ReadonlyMap<string, string> {
  const labels: string[] = [];
  const seen = new Set<string>();
  for (const t of tiers) {
    const key = normalizeLabel(t.label);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    labels.push(key);
  }
  labels.sort();

  const result = new Map<string, string>();
  const used = new Set<number>();
  for (const key of labels) {
    const preferred = hashKey(key) % TIER_PALETTE.length;
    let idx = preferred;
    let attempts = 0;
    while (used.has(idx) && attempts < TIER_PALETTE.length) {
      idx = (idx + 1) % TIER_PALETTE.length;
      attempts += 1;
    }
    used.add(idx);
    result.set(key, TIER_PALETTE[idx]!);
  }
  return result;
}

export interface TierClassRollup {
  readonly key: string;
  readonly displayLabel: string;
  readonly count: number;
  readonly active: number;
  readonly inactive: number;
}

export function rollupByClass(
  tiers: readonly KujiBoxTier[],
): readonly TierClassRollup[] {
  const map = new Map<
    string,
    { displayLabel: string; count: number; active: number; inactive: number }
  >();
  for (const tier of tiers) {
    const key = normalizeLabel(tier.label);
    const existing = map.get(key);
    if (existing) {
      existing.count += 1;
      existing.active += tier.activeCount;
      existing.inactive += tier.inactiveCount;
    } else {
      map.set(key, {
        displayLabel: tier.label ?? "",
        count: 1,
        active: tier.activeCount,
        inactive: tier.inactiveCount,
      });
    }
  }
  return Array.from(map.entries()).map(([key, v]) => ({
    key,
    displayLabel: v.displayLabel,
    count: v.count,
    active: v.active,
    inactive: v.inactive,
  }));
}

export function formatChance(active: number, totalActive: number): string {
  if (totalActive <= 0 || active <= 0) return "0%";
  const pct = (active / totalActive) * 100;
  if (pct < 1) return `${pct.toFixed(1)}%`;
  return `${Math.round(pct)}%`;
}
