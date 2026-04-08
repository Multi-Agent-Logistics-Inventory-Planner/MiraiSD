/**
 * Centralized help content for Stockout Predictions.
 * Provides business-friendly explanations for all metrics and UI elements.
 */

export const METRIC_TOOLTIPS = {
  currentStock: "Current inventory count",
  demandVelocity: "Average units sold per day",
  daysToStockout: "Days until stockout (Stock / Daily Sales)",
  suggestion: "Order quantity and date to prevent stockout",
  accuracy: "How accurate past forecasts were for this item",
} as const;

export const BADGE_TOOLTIPS = {
  "Fast seller": "Selling 3+ units/day",
  Moderate: "Selling 1-3 units/day",
  "Slow mover": "Selling less than 1 unit/day",
  Overdue: "Order date has passed - restock now",
} as const;

export const TAB_TOOLTIPS = {
  ACTION_NEEDED: "Will run out within 7 days",
  WATCH: "Will run out within 14 days",
  HEALTHY: "14+ days of stock remaining",
  RESOLVED: "Items marked as handled",
} as const;

export const CONFIDENCE_TOOLTIPS = {
  High: "Stable demand with accurate past forecasts",
  Moderate: "Reasonable prediction but some variability",
  Low: "Limited data or volatile demand - monitor closely",
} as const;
