/**
 * Forecast display formatting utilities.
 * Provides business-friendly language for technical forecast metrics.
 */

export type DemandCategory = "Fast seller" | "Moderate" | "Slow mover";

/**
 * Formats demand velocity to a rounded, human-readable string.
 * @example formatDemandVelocity(4.71) => "~5/day"
 * @example formatDemandVelocity(0.3) => "<1/day"
 */
export function formatDemandVelocity(velocity: number | null): string {
  if (velocity === null || velocity === undefined) return "N/A";
  if (velocity < 0.5) return "<1/day";
  return `~${Math.round(velocity)}/day`;
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
