/**
 * Forecast display formatting utilities.
 * Provides business-friendly language for technical forecast metrics.
 */

export type DemandCategory = "Fast seller" | "Moderate" | "Slow mover";
export type ConfidenceLevel = "High" | "Moderate" | "Low";

/**
 * Formats demand velocity to a rounded, human-readable string.
 * @example formatDemandVelocity(4.71) => "~5 sold/day"
 * @example formatDemandVelocity(0.3) => "<1 sold/day"
 */
export function formatDemandVelocity(velocity: number | null): string {
  if (velocity === null || velocity === undefined) return "N/A";
  if (velocity < 0.5) return "<1 sold/day";
  return `~${Math.round(velocity)} sold/day`;
}

/**
 * Formats days to stockout as a friendly, rounded string.
 * @example formatDaysRemaining(2.71) => "~3 days left"
 * @example formatDaysRemaining(0.5) => "<1 day left"
 */
export function formatDaysRemaining(days: number | null): string {
  if (days === null || days === undefined) return "N/A";
  if (days <= 0) return "Out of stock";
  if (days < 1) return "<1 day left";
  return `~${Math.round(days)} days left`;
}

/**
 * Categorizes demand velocity into business-friendly labels.
 * @param velocity - Units sold per day (absolute value, negative means sales)
 * @returns Demand category label
 */
export function getDemandCategory(velocity: number | null): DemandCategory {
  if (velocity === null || velocity === undefined) return "Slow mover";
  const absVelocity = Math.abs(velocity);
  if (absVelocity >= 3) return "Fast seller";
  if (absVelocity >= 1) return "Moderate";
  return "Slow mover";
}

/**
 * Returns styling classes for demand category badges.
 */
export function getDemandCategoryStyle(category: DemandCategory): string {
  switch (category) {
    case "Fast seller":
      return "bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400";
    case "Moderate":
      return "bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-400";
    case "Slow mover":
      return "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400";
  }
}

/**
 * Formats the coverage context for a reorder suggestion.
 * @example formatCoverageContext(97, 4.71) => "(3-week supply)"
 * @example formatCoverageContext(14, 2) => "(1-week supply)"
 */
export function formatCoverageContext(
  suggestedQty: number | null,
  demandVelocity: number | null
): string {
  if (!suggestedQty || !demandVelocity || demandVelocity === 0) return "";

  const absVelocity = Math.abs(demandVelocity);
  const daysOfCoverage = suggestedQty / absVelocity;
  const weeksOfCoverage = daysOfCoverage / 7;

  if (weeksOfCoverage < 1) {
    const days = Math.round(daysOfCoverage);
    return `(${days}-day supply)`;
  }

  const weeks = Math.round(weeksOfCoverage);
  return `(${weeks}-week supply)`;
}

/**
 * Categorizes confidence score into business-friendly labels.
 * @param confidence - Confidence score from 0 to 1
 */
export function getConfidenceLevel(confidence: number | null): ConfidenceLevel {
  if (confidence === null || confidence === undefined) return "Low";
  if (confidence >= 0.8) return "High";
  if (confidence >= 0.6) return "Moderate";
  return "Low";
}

/**
 * Returns styling classes for confidence level badges.
 */
export function getConfidenceLevelStyle(level: ConfidenceLevel): string {
  switch (level) {
    case "High":
      return "bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400";
    case "Moderate":
      return "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-400";
    case "Low":
      return "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400";
  }
}

/**
 * Formats forecast accuracy as a percentage string.
 * @example formatAccuracy(0.92) => "92%"
 * @example formatAccuracy(null) => "New"
 */
export function formatAccuracy(accuracy: number | null): string {
  if (accuracy === null || accuracy === undefined) return "New";
  return `${Math.round(accuracy * 100)}%`;
}

/**
 * Returns color class for accuracy display.
 */
export function getAccuracyColor(accuracy: number | null): string {
  if (accuracy === null || accuracy === undefined)
    return "text-muted-foreground";
  if (accuracy >= 0.8) return "text-green-600 dark:text-green-400";
  if (accuracy >= 0.6) return "text-amber-600 dark:text-amber-400";
  return "text-red-600 dark:text-red-400";
}

/**
 * Computes a priority score for an item based on multiple factors.
 * Higher score = higher priority (needs attention sooner).
 *
 * Factors:
 * - Days to stockout (40%): Lower days = higher priority
 * - Demand velocity (30%): Higher velocity = higher priority
 * - Confidence (20%): Higher confidence = more reliable prediction
 * - Volatility penalty (10%): Higher volatility = less reliable
 *
 * @returns Priority score from 0 to 1
 */
export function computePriorityScore(item: {
  daysToStockout: number | null;
  demandVelocity: number | null;
  confidence: number;
  demandVolatility: number | null;
}): number {
  // Days score: 0 days = 1.0, 30+ days = 0.0
  const daysScore =
    item.daysToStockout !== null
      ? Math.max(0, 1 - item.daysToStockout / 30)
      : 0;

  // Velocity score: normalized to 10 units/day max
  const velocityScore =
    item.demandVelocity !== null
      ? Math.min(1, Math.abs(item.demandVelocity) / 10)
      : 0;

  // Confidence score: direct 0-1 value
  const confidenceScore = item.confidence ?? 0.5;

  // Volatility penalty: higher volatility = lower score
  const volatilityPenalty =
    item.demandVolatility !== null
      ? Math.min(0.1, item.demandVolatility * 0.1)
      : 0;

  return (
    daysScore * 0.4 +
    velocityScore * 0.3 +
    confidenceScore * 0.2 -
    volatilityPenalty
  );
}
