"use client";

import { useMemo } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceArea,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { TooltipProps } from "recharts";

import { cn } from "@/lib/utils";
import { useProductReportBundle } from "@/hooks/queries/use-product-report-bundle";

interface ProductReportPanelProps {
  productId: string;
}

/**
 * Deterministic Product Assistant report rendered above the chat panel. Data
 * comes from the detail bundle endpoint, fetched once per session.
 */
export function ProductReportPanel({ productId }: ProductReportPanelProps) {
  const { data, isLoading, error } = useProductReportBundle(productId);

  const chartData = useMemo(() => {
    if (!data) return [];

    const rollups = data.dailyRollups90d;
    if (rollups.length === 0) return [];

    // Reconstruct daily stock level by working backwards from current stock.
    // Each day's net change: +restock - sold - damaged
    const netDeltas = rollups.map(
      (r) => (r.restockUnits ?? 0) - (r.unitsSold ?? 0) - (r.damageUnits ?? 0),
    );

    // Sum of all deltas from day 0 to last day equals the total change over the window.
    // currentStock = stockAtDay0 + totalDelta  =>  stockAtDay0 = currentStock - totalDelta
    const totalDelta = netDeltas.reduce((sum, d) => sum + d, 0);
    let running = data.product.currentStock - totalDelta;

    return rollups.map((row, i) => {
      running += netDeltas[i];
      return {
        date: formatDate(row.date),
        stock: running,
        change: netDeltas[i],
      };
    });
  }, [data]);

  if (isLoading) {
    return (
      <div className="rounded-lg border bg-card p-6 text-sm text-muted-foreground">
        Loading product report...
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-6 text-sm text-destructive">
        Failed to load product report.
      </div>
    );
  }

  const totalStock = data.product.currentStock;
  const latest = data.latestPrediction;
  const reorderPoint = data.product.reorderPoint;

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Kpi label="Total Stock" value={totalStock} />
        <Kpi label="Reorder pt" value={reorderPoint ?? "—"} />
        <Kpi
          label="Days to stockout"
          value={formatNumber(latest?.daysToStockout)}
        />
        <Kpi
          label="Confidence"
          value={latest?.confidence != null ? `${Math.round(latest.confidence * 100)}%` : "—"}
        />
      </div>

      <div className="rounded-lg border bg-card p-4">
        <div className="mb-2 flex items-center gap-2 text-sm font-semibold">
          Stock level — last 90 days
          {reorderPoint != null && (
            <span className="inline-flex items-center gap-1 rounded-full bg-red-500/10 px-2 py-0.5 text-[10px] font-medium text-red-400">
              <span className="inline-block h-1.5 w-1.5 rounded-full bg-red-400" />
              Reorder at {reorderPoint}
            </span>
          )}
        </div>
        <div className="h-56">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
              <XAxis dataKey="date" fontSize={10} interval="preserveStartEnd" />
              <YAxis
                allowDecimals={false}
                fontSize={11}
                domain={[0, "auto"]}
                label={{ value: "Units", angle: -90, position: "insideLeft", fontSize: 11 }}
              />

              {reorderPoint != null && (
                <ReferenceArea
                  y1={0}
                  y2={reorderPoint}
                  fill="#ef4444"
                  fillOpacity={0.06}
                  ifOverflow="hidden"
                />
              )}
              {reorderPoint != null && (
                <ReferenceLine
                  y={reorderPoint}
                  stroke="#ef4444"
                  strokeDasharray="6 3"
                  strokeOpacity={0.4}
                />
              )}

              <Tooltip
                content={<StockTooltip reorderPoint={reorderPoint} />}
                cursor={{ stroke: "hsl(var(--muted-foreground))", strokeWidth: 1, strokeDasharray: "4 4" }}
              />
              <Line type="monotone" dataKey="stock" name="Stock level" stroke="var(--brand-primary)" dot={false} strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>

      {data.inventoryByLocation.length > 0 && (
        <div className="rounded-lg border bg-card p-4">
          <div className="mb-2 text-sm font-semibold">Inventory by location</div>
          <ul className="space-y-1 text-sm">
            {data.inventoryByLocation.map((row) => (
              <li key={`${row.locationId}-${row.storageLocationCode}`} className="flex justify-between">
                <span className="text-muted-foreground">
                  {row.storageLocationCode ?? "?"} · {row.locationCode ?? "—"}
                </span>
                <span className="font-medium">{row.quantity}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {data.recentShipments.length > 0 && (
        <div className="rounded-lg border bg-card p-4">
          <div className="mb-2 text-sm font-semibold">Recent shipments</div>
          <ul className="space-y-1 text-sm">
            {data.recentShipments.slice(0, 5).map((s) => (
              <li key={s.shipmentItemId} className="flex justify-between">
                <span className="text-muted-foreground">{s.deliveredOn ?? "pending"}</span>
                <span>
                  {s.receivedQuantity ?? 0}/{s.orderedQuantity ?? 0}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {data.activeDisplays.length > 0 && (
        <div className="rounded-lg border bg-card p-4 text-sm">
          <span className="font-semibold">On display:</span>{" "}
          <span className="text-muted-foreground">
            {data.activeDisplays.length} active machine(s)
          </span>
        </div>
      )}
    </div>
  );
}

interface ChartPoint {
  date: string;
  stock: number;
  change: number;
}

function StockTooltip({
  active,
  payload,
  reorderPoint,
}: TooltipProps<number, string> & { reorderPoint: number | null }) {
  if (!active || !payload?.length) return null;

  const point = payload[0].payload as ChartPoint;
  const belowReorder = reorderPoint != null && point.stock <= reorderPoint;

  return (
    <div className="rounded-lg border bg-popover px-3 py-2.5 text-sm shadow-lg">
      <div className="font-medium text-popover-foreground">{point.date}</div>
      <div className="mt-1.5 space-y-1">
        <div className="flex items-center justify-between gap-6">
          <span className="text-muted-foreground">Stock</span>
          <span
            className={cn(
              "tabular-nums font-semibold",
              belowReorder ? "text-red-400" : "text-popover-foreground",
            )}
          >
            {point.stock}
          </span>
        </div>
        <div className="flex items-center justify-between gap-6">
          <span className="text-muted-foreground">Change</span>
          <span
            className={cn(
              "tabular-nums font-medium",
              point.change > 0 && "text-emerald-400",
              point.change < 0 && "text-red-400",
              point.change === 0 && "text-muted-foreground",
            )}
          >
            {point.change > 0 ? "+" : ""}
            {point.change}
          </span>
        </div>
      </div>
      {belowReorder && (
        <div className="mt-2 flex items-center gap-1.5 border-t border-red-500/20 pt-2 text-xs text-red-400">
          <span className="inline-block h-1.5 w-1.5 rounded-full bg-red-400" />
          Below reorder point
        </div>
      )}
    </div>
  );
}

function Kpi({ label, value }: { label: string; value: string | number | null }) {
  return (
    <div className="rounded-lg border bg-card p-3">
      <div className="text-xs uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className="mt-1 text-lg font-semibold">{value ?? "—"}</div>
    </div>
  );
}

function formatNumber(n: number | null | undefined): string {
  if (n == null) return "—";
  return n.toFixed(1);
}

/** Format ISO date string to short readable form (e.g. "Mar 15"). */
function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}
