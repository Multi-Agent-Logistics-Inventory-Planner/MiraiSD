import type { SortOption } from "./types";

export const SORT_OPTIONS: { value: SortOption; label: string }[] = [
  { value: "daysToStockout-asc", label: "Days to Stockout: Low to High" },
  { value: "daysToStockout-desc", label: "Days to Stockout: High to Low" },
  { value: "demandVelocity-desc", label: "Demand Velocity: High to Low" },
  { value: "demandVelocity-asc", label: "Demand Velocity: Low to High" },
  { value: "suggestedReorderQty-desc", label: "Reorder Qty: High to Low" },
  { value: "suggestedReorderQty-asc", label: "Reorder Qty: Low to High" },
  { value: "name-asc", label: "Name: A to Z" },
  { value: "name-desc", label: "Name: Z to A" },
];

export const PAGE_SIZE = 25;

export const STOCKOUT_THRESHOLDS = {
  CRITICAL: 3,
  URGENT: 7,
  ATTENTION: 14,
} as const;

export const STALE_THRESHOLD_MS = 30 * 24 * 60 * 60 * 1000; // 30 days
export const WELL_STOCKED_THRESHOLD = 30; // daysToStockout cutoff

export function isForecastStale(computedAt: string | null): boolean {
  if (!computedAt) return true;
  const date = new Date(computedAt);
  if (isNaN(date.getTime())) return true;
  return Date.now() - date.getTime() > STALE_THRESHOLD_MS;
}
