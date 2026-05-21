"use client";

import { cn } from "@/lib/utils";

export interface RecentAdjustmentEntry {
  readonly userId: string;
  readonly userName: string;
  readonly delta: number;
  readonly reason: string;
  readonly at: string;
}

interface RecentAdjustmentsCardProps {
  readonly entries: readonly RecentAdjustmentEntry[];
}

function formatTimestamp(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

/**
 * Read-only feed of admin coin adjustments. Session-scoped: only shows
 * adjustments made in the current modal session (no backend list endpoint
 * exists yet). Replace once GET /api/admin/coins/adjustments lands.
 */
export function RecentAdjustmentsCard({ entries }: RecentAdjustmentsCardProps) {
  return (
    <section className="rounded-xl border border-border bg-card/40 p-5">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
          Recent adjustments
        </h3>
        <span className="font-mono text-[10.5px] uppercase tracking-[0.12em] text-muted-foreground/70">
          This session only
        </span>
      </div>
      {entries.length === 0 ? (
        <p className="py-3 text-[13px] text-muted-foreground">
          No adjustments made in this session yet.
        </p>
      ) : (
        <ul className="divide-y divide-border">
          {entries.map((entry, idx) => (
            <li
              key={`${entry.userId}-${entry.at}-${idx}`}
              className="flex items-center justify-between gap-3 py-3 first:pt-0 last:pb-0"
            >
              <div className="min-w-0 space-y-0.5">
                <div className="truncate text-[13px] text-foreground">
                  <span className="font-medium">{entry.userName}</span>{" "}
                  <span className="text-muted-foreground">· {entry.reason}</span>
                </div>
                <div className="font-mono text-[10.5px] text-muted-foreground">
                  {formatTimestamp(entry.at)}
                </div>
              </div>
              <div
                className={cn(
                  "font-mono text-[13px] font-medium tabular-nums",
                  entry.delta > 0 ? "text-emerald-500" : "text-rose-500"
                )}
              >
                {entry.delta > 0 ? `+${entry.delta}` : entry.delta}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
