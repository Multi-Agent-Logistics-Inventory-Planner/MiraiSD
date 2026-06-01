"use client";

import { useQuery } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { getForecastExplanation, type ForecastExplanation } from "@/lib/api/forecasts";
import { cn } from "@/lib/utils";

interface WhyThisNumberDrawerProps {
  itemId: string;
  open: boolean;
}

const DOW_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

function asNumber(v: unknown): number | null {
  if (typeof v === "number" && !isNaN(v)) return v;
  if (typeof v === "string") {
    const n = Number(v);
    return isNaN(n) ? null : n;
  }
  return null;
}

function asRecord(v: unknown): Record<string, unknown> | null {
  return v && typeof v === "object" && !Array.isArray(v)
    ? (v as Record<string, unknown>)
    : null;
}

function formatNum(n: number | null, digits = 2): string {
  return n === null ? "—" : n.toFixed(digits);
}

function formatPct(n: number | null): string {
  return n === null ? "—" : `${(n * 100).toFixed(1)}%`;
}

function formatDate(s: string | null): string {
  if (!s) return "—";
  const d = new Date(s);
  return isNaN(d.getTime()) ? "—" : d.toLocaleDateString();
}

function DowMultiplierBar({ multipliers }: { multipliers: Record<string, unknown> }) {
  const values = DOW_LABELS.map((_, i) => {
    const v = asNumber(multipliers[String(i)]) ?? asNumber(multipliers[i]) ?? 1.0;
    return v;
  });
  const maxValue = Math.max(1.0, ...values);
  return (
    <div className="flex items-end gap-1.5 h-16 w-full">
      {values.map((v, i) => {
        const heightPct = Math.min(100, (v / maxValue) * 100);
        const isHigh = v > 1.5;
        const isLow = v < 0.5;
        return (
          <div key={i} className="flex-1 flex flex-col items-center gap-1">
            <div className="w-full h-full flex items-end">
              <div
                className={cn(
                  "w-full rounded-t",
                  isHigh
                    ? "bg-amber-400/70 dark:bg-amber-500/60"
                    : isLow
                    ? "bg-muted-foreground/30"
                    : "bg-primary/60",
                )}
                style={{ height: `${heightPct}%` }}
                title={`${DOW_LABELS[i]}: ${formatNum(v)}x`}
              />
            </div>
            <div className="text-[10px] text-muted-foreground font-mono">{DOW_LABELS[i]}</div>
          </div>
        );
      })}
    </div>
  );
}

function Row({ label, value, mono = true }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <div className="flex justify-between gap-3 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className={cn("font-medium", mono && "font-mono")}>{value}</span>
    </div>
  );
}

function ExplanationBody({ data }: { data: ForecastExplanation }) {
  const f = data.features;
  const muHat = asNumber(f.mu_hat);
  const sigma = asNumber(f.sigma_d_hat);
  const method = typeof f.method === "string" ? f.method : "unknown";
  const regime = typeof f.demand_regime === "string" ? f.demand_regime : null;
  const cv = asNumber(f.cv);
  const leadTimeDays = asNumber(f.lead_time_days);
  const leadTimeSource = typeof f.lead_time_source === "string" ? f.lead_time_source : null;
  const safetyStock = asNumber(f.safety_stock);
  const reorderPoint = asNumber(f.reorder_point);
  const dowMultipliers = asRecord(f.dow_multipliers);
  const eventMultipliers = asRecord(f.event_multipliers);
  const eventDaysSince = asRecord(f.event_days_since);
  const tsbP = asNumber(f.tsb_p);
  const tsbZ = asNumber(f.tsb_z);

  return (
    <div className="space-y-4 text-sm">
      <section className="space-y-1">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Estimate
        </h4>
        <Row label="Method" value={method} mono={false} />
        <Row label="Avg daily demand (μ̂)" value={`${formatNum(muHat)} units/day`} />
        <Row label="Daily std (σ̂)" value={formatNum(sigma)} />
        {regime && (
          <Row
            label="Demand regime"
            value={`${regime}${cv !== null ? ` (CV ${formatNum(cv)})` : ""}`}
            mono={false}
          />
        )}
        {method === "tsb" && (
          <>
            <Row label="Sale probability (p)" value={formatPct(tsbP)} />
            <Row label="Sale-day demand (z)" value={formatNum(tsbZ)} />
          </>
        )}
      </section>

      {dowMultipliers && (
        <section className="space-y-2">
          <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Day-of-week pattern
          </h4>
          <DowMultiplierBar multipliers={dowMultipliers} />
          <p className="text-xs text-muted-foreground">
            Each bar is the demand multiplier for that weekday vs the overall average.
          </p>
        </section>
      )}

      {eventMultipliers && Object.keys(eventMultipliers).length > 0 && (
        <section className="space-y-1">
          <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Event-driven uplift
          </h4>
          {Object.entries(eventMultipliers).map(([col, raw]) => {
            const mult = asNumber(raw) ?? 1.0;
            const daysSince = eventDaysSince ? asNumber(eventDaysSince[col]) : null;
            const label = col.replace("recent_", "").replace("_7d", "").replace(/_/g, " ");
            const active = daysSince !== null && daysSince >= 1 && daysSince <= 7;
            return (
              <Row
                key={col}
                label={label}
                value={
                  <span>
                    <span className={active ? "text-amber-600 dark:text-amber-400" : ""}>
                      {formatNum(mult)}×
                    </span>
                    {daysSince !== null && (
                      <span className="text-muted-foreground ml-2">
                        ({daysSince === 0 ? "today" : `${daysSince}d ago`})
                      </span>
                    )}
                  </span>
                }
              />
            );
          })}
          <p className="text-xs text-muted-foreground">
            Multipliers learned from history apply for 7 days following each event.
          </p>
        </section>
      )}

      <section className="space-y-1">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Reorder policy
        </h4>
        <Row
          label="Lead time"
          value={
            <span>
              {formatNum(leadTimeDays, 1)} days
              {leadTimeSource && (
                <span className="text-muted-foreground ml-1">({leadTimeSource})</span>
              )}
            </span>
          }
        />
        <Row label="Safety stock" value={formatNum(safetyStock, 1)} />
        <Row label="Reorder point" value={formatNum(reorderPoint, 1)} />
      </section>

      <section className="space-y-1">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Accuracy
        </h4>
        <Row
          label="Last restock"
          value={data.lastRestockAt ? formatDate(data.lastRestockAt) : "—"}
        />
        <Row label="Computed at" value={formatDate(data.computedAt)} />
      </section>
    </div>
  );
}

function DrawerContent({ itemId }: { itemId: string }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["forecast-explain", itemId],
    queryFn: () => getForecastExplanation(itemId),
    staleTime: 60_000,
  });
  return (
    <div className="mt-3 rounded-lg border border-border bg-muted/30 p-3 sm:p-4">
      {isLoading && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading explanation…
        </div>
      )}
      {isError && (
        <div className="text-sm text-destructive">Failed to load explanation.</div>
      )}
      {data && <ExplanationBody data={data} />}
    </div>
  );
}

/**
 * Drawer for "Why this number" on a prediction row. The QueryClient hook is
 * deferred to a child that only mounts when ``open`` flips true, so cards in
 * isolated tests (no QueryClientProvider) keep working.
 */
export function WhyThisNumberDrawer({ itemId, open }: WhyThisNumberDrawerProps) {
  if (!open) return null;
  return <DrawerContent itemId={itemId} />;
}
