export type HealthVerdict = "good" | "warn" | "bad";

export interface HealthBannerInput {
  ltWape: number | null;
  biasUnitsPerDay: number | null;
  underStockedPct: number | null;
  weekOverWeekDelta: number | null;
}

export interface HealthBannerResult {
  verdict: HealthVerdict;
  headline: string;
  body: { text: string; bold?: boolean }[];
}

function pct(value: number | null): string {
  if (value === null || Number.isNaN(value)) return "—";
  return `${(value * 100).toFixed(1)}%`;
}

function verdictFor(ltWape: number | null): HealthVerdict {
  if (ltWape === null || Number.isNaN(ltWape)) return "warn";
  if (ltWape < 0.25) return "good";
  if (ltWape < 0.5) return "warn";
  return "bad";
}

function headlineFor(verdict: HealthVerdict, bias: number | null): string {
  if (verdict === "good") return "Forecasts are tracking actual demand well.";
  if (verdict === "warn") return "Forecasts are drifting from actual demand.";
  if (bias !== null && bias < -0.5) return "Forecasts are running well under actual demand.";
  if (bias !== null && bias > 0.5) return "Forecasts are running well over actual demand.";
  return "Forecasts are well off from actual demand.";
}

function trendClause(delta: number | null): string {
  if (delta === null || Number.isNaN(delta) || Math.abs(delta) < 0.001) {
    return "Accuracy is flat week-over-week.";
  }
  return delta < 0
    ? "Accuracy is improving week-over-week."
    : "Accuracy is worsening week-over-week.";
}

/**
 * Plain-language verdict + body for the accuracy health banner. Body is a list
 * of text spans (some bold) so the banner component can render `<b>` figures
 * without dangerouslySetInnerHTML.
 */
export function buildHealthBanner(input: HealthBannerInput): HealthBannerResult {
  const { ltWape, biasUnitsPerDay, underStockedPct, weekOverWeekDelta } = input;
  const verdict = verdictFor(ltWape);
  const headline = headlineFor(verdict, biasUnitsPerDay);
  const direction =
    biasUnitsPerDay !== null && biasUnitsPerDay < -0.5
      ? "too little"
      : biasUnitsPerDay !== null && biasUnitsPerDay > 0.5
        ? "too much"
        : "off-target";

  const body: { text: string; bold?: boolean }[] = [
    { text: "Missed about " },
    { text: pct(ltWape), bold: true },
    { text: " of units sold and under-stocked " },
    { text: pct(underStockedPct), bold: true },
    { text: ` of reorder windows — the model usually orders ${direction}. ` },
    { text: trendClause(weekOverWeekDelta) },
  ];

  return { verdict, headline, body };
}
