import type { ActionUrgency } from "@/types/analytics";

export type UrgencyFilter = ActionUrgency | "ALL" | "RESOLVED";
export type SortField = "daysToStockout" | "demandVelocity" | "suggestedReorderQty" | "name";
export type SortDirection = "asc" | "desc";
export type SortOption = `${SortField}-${SortDirection}`;
