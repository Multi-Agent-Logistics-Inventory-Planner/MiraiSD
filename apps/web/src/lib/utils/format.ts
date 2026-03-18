/**
 * Formats a number to a fixed number of decimal places.
 * Returns "N/A" for null or undefined values.
 */
export function formatNumber(
  value: number | null | undefined,
  decimals: number = 1
): string {
  if (value === null || value === undefined) return "N/A"
  return value.toFixed(decimals)
}
