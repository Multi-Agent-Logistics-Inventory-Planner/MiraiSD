"use client";

import { ArrowRight } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { useAdminCoinActivity } from "@/hooks/queries/use-lootbox";
import type { AdminCoinActivity } from "@/types/lootbox";
import { formatClockTime } from "./format-relative";

interface RecentActivitySectionProps {
  readonly onViewAll: () => void;
  readonly limit?: number;
}

export function RecentActivitySection({ onViewAll, limit = 4 }: RecentActivitySectionProps) {
  // Fetch one extra slot so a single row delete/dedupe never blanks the list.
  const query = useAdminCoinActivity(Math.max(limit, 8));
  const rows = (query.data ?? []).slice(0, limit);

  return (
    <section>
      <div className="mb-2.5 flex items-baseline justify-between">
        <h3 className="text-[14px] font-medium text-foreground">Recent activity</h3>
        <button
          type="button"
          onClick={onViewAll}
          className="flex items-center gap-1 font-mono text-[11px] uppercase tracking-[0.06em] text-muted-foreground transition-colors hover:text-foreground"
        >
          View all <ArrowRight className="h-3 w-3" />
        </button>
      </div>

      {query.isLoading ? (
        <ActivitySkeleton />
      ) : rows.length === 0 ? (
        <p className="py-6 text-center text-[13px] text-muted-foreground">
          No coin activity in the last 30 days.
        </p>
      ) : (
        <div>
          {rows.map((row, i) => (
            <ActivityRow key={row.id} row={row} isFirst={i === 0} />
          ))}
        </div>
      )}
    </section>
  );
}

function ActivityRow({ row, isFirst }: { readonly row: AdminCoinActivity; readonly isFirst: boolean }) {
  const isNegative = row.delta < 0;
  const deltaText = isNegative ? `−${Math.abs(row.delta)}` : `+${row.delta}`;
  return (
    <div
      className={`grid grid-cols-[46px_1fr_auto] items-center gap-3.5 py-2.5 ${
        isFirst ? "" : "border-t border-border"
      }`}
    >
      <span
        className="font-mono text-[13px] font-medium tabular-nums"
        style={{ color: isNegative ? "#ef8a9d" : "#7fd99a" }}
      >
        {deltaText}
      </span>
      <div className="min-w-0">
        <div className="truncate text-[13px] text-foreground">{row.userName}</div>
        <div className="truncate font-mono text-[10.5px] text-muted-foreground">{row.reason}</div>
      </div>
      <span className="font-mono text-[10.5px] text-muted-foreground/80">
        {formatClockTime(row.occurredAt)}
      </span>
    </div>
  );
}

function ActivitySkeleton() {
  return (
    <div>
      {Array.from({ length: 4 }).map((_, i) => (
        <div
          key={i}
          className={`grid grid-cols-[46px_1fr_auto] items-center gap-3.5 py-2.5 ${
            i === 0 ? "" : "border-t border-border"
          }`}
        >
          <Skeleton className="h-3.5 w-8" />
          <div className="space-y-1.5">
            <Skeleton className="h-3 w-28" />
            <Skeleton className="h-2.5 w-40" />
          </div>
          <Skeleton className="h-2.5 w-12" />
        </div>
      ))}
    </div>
  );
}
