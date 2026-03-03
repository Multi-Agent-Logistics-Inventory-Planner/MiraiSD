/**
 * Dashboard Metrics Storage
 *
 * Handles localStorage-based storage of metrics snapshots for trend calculations.
 * Stores metrics weekly and retrieves previous snapshots for comparison.
 */

import type { StoredMetricsSnapshot } from "@/types/dashboard";

const METRICS_STORAGE_KEY = "dashboard-metrics-history";

/** Number of days before a stored snapshot is considered stale for updates */
const METRICS_STORAGE_EXPIRY_DAYS = 7;

/** Number of days before a stored snapshot is completely discarded (2x expiry) */
const METRICS_CLEANUP_THRESHOLD_DAYS = 14;

interface PerformanceMetricsInput {
  fillRate: number;
  forecastAccuracy: number;
  stockoutRate: number;
  turnoverRate: number;
}

/**
 * Runtime validation for StoredMetricsSnapshot to guard against corrupted localStorage data.
 */
function isValidSnapshot(data: unknown): data is StoredMetricsSnapshot {
  if (typeof data !== "object" || data === null) return false;

  const obj = data as Record<string, unknown>;

  return (
    typeof obj.timestamp === "string" &&
    typeof obj.fillRate === "number" &&
    typeof obj.forecastAccuracy === "number" &&
    typeof obj.stockoutRate === "number" &&
    typeof obj.turnoverRate === "number"
  );
}

/**
 * Retrieves the previously stored metrics snapshot from localStorage.
 * Returns null if no snapshot exists, is invalid, or is too old (>14 days).
 */
export function getPreviousMetrics(): StoredMetricsSnapshot | null {
  if (typeof window === "undefined") return null;

  try {
    const stored = localStorage.getItem(METRICS_STORAGE_KEY);
    if (!stored) return null;

    const parsed: unknown = JSON.parse(stored);

    if (!isValidSnapshot(parsed)) {
      // Remove corrupted data
      localStorage.removeItem(METRICS_STORAGE_KEY);
      return null;
    }

    const snapshot = parsed;
    const storedDate = new Date(snapshot.timestamp);
    const now = new Date();
    const daysDiff = (now.getTime() - storedDate.getTime()) / (1000 * 60 * 60 * 24);

    // Discard snapshots older than cleanup threshold
    if (daysDiff > METRICS_CLEANUP_THRESHOLD_DAYS) {
      return null;
    }

    return snapshot;
  } catch {
    return null;
  }
}

/**
 * Stores the current metrics as a snapshot in localStorage.
 * Only updates if the previous snapshot is older than the expiry period (7 days).
 * This ensures we're comparing week-over-week data.
 */
export function storeCurrentMetrics(metrics: PerformanceMetricsInput): void {
  if (typeof window === "undefined") return;

  const previous = getPreviousMetrics();
  const now = new Date();

  // Don't update if we have a recent snapshot (within expiry period)
  if (previous) {
    const storedDate = new Date(previous.timestamp);
    const daysDiff = (now.getTime() - storedDate.getTime()) / (1000 * 60 * 60 * 24);
    if (daysDiff < METRICS_STORAGE_EXPIRY_DAYS) {
      return;
    }
  }

  const snapshot: StoredMetricsSnapshot = {
    timestamp: now.toISOString(),
    fillRate: metrics.fillRate,
    forecastAccuracy: metrics.forecastAccuracy,
    stockoutRate: metrics.stockoutRate,
    turnoverRate: metrics.turnoverRate,
  };

  try {
    localStorage.setItem(METRICS_STORAGE_KEY, JSON.stringify(snapshot));
  } catch {
    // Ignore storage errors (quota exceeded, private browsing)
  }
}
