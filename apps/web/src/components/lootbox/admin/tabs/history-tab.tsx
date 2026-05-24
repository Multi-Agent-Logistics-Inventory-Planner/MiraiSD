"use client";

import { useMemo, useState } from "react";
import { Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useAdminCoinActivity } from "@/hooks/queries/use-lootbox";
import type { AdminCoinActivity } from "@/types/lootbox";
import { AvatarTile } from "@/components/lootbox/admin/coins/avatar-tile";

/**
 * Full cross-user coin audit feed. Backed by the same /admin/activity endpoint
 * as the Coins-tab "Recent activity" snapshot, but with a larger window, a
 * client-side name filter, and a fixed-height scrollable list (no pagination —
 * the dataset is bounded by employee activity, not customer count, so internal
 * scroll is cheaper than a pager).
 */
export function HistoryTab() {
  const query = useAdminCoinActivity(200);
  const [search, setSearch] = useState("");

  const filtered = useMemo(() => {
    const rows = query.data ?? [];
    const q = search.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) => r.userName.toLowerCase().includes(q));
  }, [query.data, search]);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-0.5">
        <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
          History
        </span>
        <p className="text-[13px] text-muted-foreground">
          Every coin grant, deduction, and spend across all players.
        </p>
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <span className="font-mono text-[11px] text-muted-foreground">
          {filtered.length} {filtered.length === 1 ? "entry" : "entries"}
        </span>
        <div className="relative w-full sm:w-auto">
          <Search
            className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter by player name…"
            className="h-8 w-full pl-8 text-[12px] sm:w-[220px]"
          />
        </div>
      </div>

      <div className="overflow-hidden rounded-[10px] border border-border bg-card">
        <div className="grid grid-cols-[60px_1fr_auto] gap-3 border-b border-border bg-card-foreground/[0.02] px-3.5 py-2.5 font-mono text-[10px] uppercase tracking-[0.08em] text-muted-foreground">
          <span>Δ</span>
          <span>Player · Reason</span>
          <span>When</span>
        </div>
        <div className="max-h-[480px] overflow-y-auto">
          {query.isLoading ? (
            <HistorySkeleton />
          ) : filtered.length === 0 ? (
            <p className="px-4 py-10 text-center text-[13px] text-muted-foreground">
              {search.trim().length > 0
                ? "No entries match your filter."
                : "No coin activity yet."}
            </p>
          ) : (
            filtered.map((row, idx) => (
              <HistoryRow key={row.id} row={row} isFirst={idx === 0} />
            ))
          )}
        </div>
      </div>
    </div>
  );
}

function HistoryRow({ row, isFirst }: { readonly row: AdminCoinActivity; readonly isFirst: boolean }) {
  const negative = row.delta < 0;
  const deltaText = negative ? `−${Math.abs(row.delta)}` : `+${row.delta}`;
  return (
    <div
      className={`grid grid-cols-[60px_1fr_auto] items-center gap-3 px-3.5 py-2.5 transition-colors hover:bg-foreground/[0.02] ${
        isFirst ? "" : "border-t border-border"
      }`}
    >
      <span
        className="font-mono text-[13px] font-medium tabular-nums"
        style={{ color: negative ? "#ef8a9d" : "#7fd99a" }}
      >
        {deltaText}
      </span>
      <div className="flex min-w-0 items-center gap-2.5">
        <AvatarTile userId={row.userId} fullName={row.userName} />
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="truncate text-[13px] text-foreground">{row.userName}</span>
            <KindChip kind={row.kind} />
          </div>
          <div className="truncate font-mono text-[10.5px] text-muted-foreground">
            {row.reason}
          </div>
        </div>
      </div>
      <span className="whitespace-nowrap font-mono text-[10.5px] text-muted-foreground/80">
        {formatStamp(row.occurredAt)}
      </span>
    </div>
  );
}

function KindChip({ kind }: { readonly kind: AdminCoinActivity["kind"] }) {
  const label = kind === "ADJUSTMENT" ? "Grant" : "Play";
  return (
    <span className="rounded border border-border bg-card-foreground/[0.04] px-1.5 py-0.5 font-mono text-[9px] uppercase tracking-[0.06em] text-muted-foreground">
      {label}
    </span>
  );
}

function formatStamp(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const date = d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  const time = d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
  return `${date} · ${time}`;
}

function HistorySkeleton() {
  return (
    <>
      {Array.from({ length: 8 }).map((_, i) => (
        <div
          key={i}
          className={`grid grid-cols-[60px_1fr_auto] items-center gap-3 px-3.5 py-2.5 ${
            i === 0 ? "" : "border-t border-border"
          }`}
        >
          <Skeleton className="h-4 w-10" />
          <div className="flex items-center gap-2.5">
            <Skeleton className="h-7 w-7 rounded-lg" />
            <div className="space-y-1.5">
              <Skeleton className="h-3 w-28" />
              <Skeleton className="h-2.5 w-44" />
            </div>
          </div>
          <Skeleton className="h-3 w-20" />
        </div>
      ))}
    </>
  );
}
