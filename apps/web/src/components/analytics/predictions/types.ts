export type UrgencyFilter = "ACTION_NEEDED" | "WATCH" | "HEALTHY" | "RESOLVED";
export type SortField = "priority" | "daysToStockout" | "demandVelocity" | "suggestedReorderQty" | "name";
export type SortDirection = "asc" | "desc";
export type SortOption = `${SortField}-${SortDirection}`;
