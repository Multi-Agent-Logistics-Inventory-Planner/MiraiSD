/**
 * Predefined palette of visually distinct, contrasting colors for categories.
 * Colors are ordered to maximize contrast between adjacent items.
 */
export const CATEGORY_COLORS = [
  "#3b82f6", // blue
  "#ef4444", // red
  "#22c55e", // green
  "#f59e0b", // amber
  "#8b5cf6", // violet
  "#06b6d4", // cyan
  "#f97316", // orange
  "#ec4899", // pink
  "#14b8a6", // teal
  "#a855f7", // purple
  "#eab308", // yellow
  "#64748b", // slate
  "#84cc16", // lime
  "#e11d48", // rose
  "#0ea5e9", // sky
  "#d946ef", // fuchsia
  "#10b981", // emerald
  "#f43f5e", // red-rose
  "#6366f1", // indigo
  "#78716c", // stone
] as const;

/**
 * Gets a color for a category by index.
 * Cycles through the palette if index exceeds available colors.
 */
export function getCategoryColor(index: number): string {
  return CATEGORY_COLORS[index % CATEGORY_COLORS.length];
}
