import type { SortOption } from "./types";

export const SORT_OPTIONS: { value: SortOption; label: string }[] = [
  { value: "priority-desc", label: "Priority: Highest First" },
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

// Threshold for the "your data is stale" banner at the top of the predictions
// tab. The Kafka-driven worker writes a fresh forecast within seconds of any
// inventory change, so anything older than 30 minutes is a real signal that
// something is wrong (worker down, Kafka stalled, etc.) rather than a normal
// quiet-business window.
export const STALENESS_BANNER_THRESHOLD_MS = 30 * 60 * 1000;

export function isForecastStale(computedAt: string | null): boolean {
  if (!computedAt) return true;
  const date = new Date(computedAt);
  if (isNaN(date.getTime())) return true;
  return Date.now() - date.getTime() > STALE_THRESHOLD_MS;
}

/**
 * Age in ms of a forecast's computed_at, or null if missing/invalid.
 */
export function forecastAgeMs(computedAt: string | null): number | null {
  if (!computedAt) return null;
  const ms = Date.parse(computedAt);
  if (isNaN(ms)) return null;
  return Math.max(0, Date.now() - ms);
}

/**
 * Format a duration in ms as a compact relative-time string used by the per-row
 * "updated X ago" badge. Negative or null inputs render as "—".
 */
export function formatRelativeAge(ms: number | null): string {
  if (ms === null || ms < 0) return "—";
  if (ms < 60_000) return "just now";
  const minutes = Math.floor(ms / 60_000);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  const months = Math.floor(days / 30);
  return `${months}mo ago`;
}
