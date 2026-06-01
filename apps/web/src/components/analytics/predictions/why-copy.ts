import type { ActionItem } from "@/types/analytics";

function formatVelocity(velocity: number | null): string {
  if (velocity === null || Number.isNaN(velocity) || velocity <= 0) {
    return "minimal demand";
  }
  if (velocity < 0.1) return "<0.1/day";
  return `~${velocity.toFixed(velocity < 1 ? 2 : 1)}/day`;
}

function formatDaysLeft(days: number | null): string {
  if (days === null) return "an unknown number of days";
  if (days < 1) return "<1 day";
  const rounded = Math.round(days);
  return `~${rounded} day${rounded === 1 ? "" : "s"}`;
}

/**
 * Plain-language explanation of why this item is in its current urgency band.
 * Sentence is generated client-side from the ActionItem fields the prediction
 * service already returns — no extra API surface.
 */
export function buildWhyCopy(item: ActionItem): string {
  const stock = item.currentStock;
  const velocity = formatVelocity(item.demandVelocity);
  const days = formatDaysLeft(item.daysToStockout);
  const lead = item.leadTimeDays;
  const reorder = item.reorderPoint;

  switch (item.urgency) {
    case "CRITICAL":
      return `${stock} left, selling ${velocity} — already inside the ${lead}-day reorder window, so it runs out before new stock can arrive.`;
    case "URGENT":
      return `${stock} left at ${velocity} lasts ${days}. With a ${lead}-day reorder, order this week to avoid a gap.`;
    case "ATTENTION":
      return `${stock} left at ${velocity}. Approaching the reorder point of ${reorder} — no action yet.`;
    case "HEALTHY":
    default:
      return `${stock} left at ${velocity} — comfortable runway, no action needed.`;
  }
}
