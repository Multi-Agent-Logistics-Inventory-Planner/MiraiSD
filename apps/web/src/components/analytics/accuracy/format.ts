export function formatPercent(value: number | null): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return `${(value * 100).toFixed(1)}%`;
}

export function formatBias(value: number | null, suffix = "units/day"): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(2)} ${suffix}`;
}

export function biasDirectionLabel(bias: number | null): string {
  if (bias === null || Math.abs(bias) < 0.5) return "balanced";
  return bias < 0 ? "under-forecasting demand" : "over-forecasting demand";
}
