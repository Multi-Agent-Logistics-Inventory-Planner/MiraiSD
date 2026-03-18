import type { DemandLeadersPeriod } from "@/types/analytics"

/**
 * Period options for analytics filtering.
 * Used by demand leaders and category demand components.
 */
export const PERIOD_OPTIONS: { value: DemandLeadersPeriod; label: string }[] = [
  { value: "7d", label: "Last 7 Days" },
  { value: "30d", label: "Last 30 Days" },
  { value: "90d", label: "Last 90 Days" },
  { value: "ytd", label: "Year to Date" },
]
