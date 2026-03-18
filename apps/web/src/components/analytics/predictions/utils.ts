import { STOCKOUT_THRESHOLDS } from "./constants";

export function getDaysToStockoutColor(days: number | null): string {
  if (days == null) return "text-foreground";
  if (days <= STOCKOUT_THRESHOLDS.CRITICAL) return "text-red-600 dark:text-red-400";
  if (days <= STOCKOUT_THRESHOLDS.URGENT) return "text-orange-600 dark:text-orange-400";
  if (days <= STOCKOUT_THRESHOLDS.ATTENTION) return "text-amber-600 dark:text-amber-400";
  return "text-green-600 dark:text-green-400";
}

export function isValidImageUrl(url: string | null | undefined): boolean {
  if (!url) return false;
  return url.startsWith("https://") || url.startsWith("/");
}
