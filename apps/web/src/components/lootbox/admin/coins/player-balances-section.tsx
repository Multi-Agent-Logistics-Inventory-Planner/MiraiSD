"use client";

import { useMemo, useState } from "react";
import { Plus, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { usePlayerCoinRows } from "@/hooks/queries/use-lootbox";
import type { PlayerCoinRow } from "@/types/lootbox";
import { AvatarTile } from "./avatar-tile";
import { formatRelativeShort } from "./format-relative";
import { GrantCoinsDialog } from "./grant-coins-dialog";

interface PlayerBalancesSectionProps {
  /**
   * Live updates to the user-pool selectable in the Grant dialog. Lifted from
   * the existing review-management endpoint; passed in so the section doesn't
   * own that query.
   */
  readonly grantTargetUsers: readonly { id: string; fullName: string; email: string }[];
}

export function PlayerBalancesSection({ grantTargetUsers }: PlayerBalancesSectionProps) {
  const [search, setSearch] = useState("");
  const [grantOpen, setGrantOpen] = useState(false);
  // Server returns full set today; we filter client-side for instant feedback.
  // When the server starts honoring ?search=, swap to passing `search` into the
  // hook and drop the in-memory filter — the contract already accepts it.
  const playersQuery = usePlayerCoinRows("");

  const filtered = useMemo(() => {
    const rows = playersQuery.data ?? [];
    const q = search.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter(
      (p) =>
        p.fullName.toLowerCase().includes(q) ||
        p.email.toLowerCase().includes(q)
    );
  }, [playersQuery.data, search]);

  const count = filtered.length;

  return (
    <section>
      <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-baseline gap-2">
          <h3 className="text-[15px] font-medium text-foreground">Player balances</h3>
          <span className="font-mono text-[11px] text-muted-foreground">· {count}</span>
        </div>
        <div className="flex w-full items-center gap-2 sm:w-auto">
          <div className="relative flex-1 sm:flex-none">
            <Search
              className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground"
              aria-hidden="true"
            />
            <Input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search players…"
              className="h-8 w-full pl-8 text-[12px] sm:w-[200px]"
            />
          </div>
          <Button
            size="sm"
            onClick={() => setGrantOpen(true)}
            className="h-8 flex-none gap-1"
          >
            <Plus className="h-3.5 w-3.5" />
            Grant
          </Button>
        </div>
      </div>

      <div className="overflow-hidden rounded-[10px] border border-border bg-card">
        <div className="grid grid-cols-[1fr_90px_110px] gap-3 border-b border-border bg-card-foreground/[0.02] px-3.5 py-2.5 font-mono text-[10px] uppercase tracking-[0.08em] text-muted-foreground">
          <span>Player</span>
          <span className="text-right">Balance</span>
          <span>Last change</span>
        </div>
        {playersQuery.isLoading ? (
          <PlayerRowsSkeleton />
        ) : filtered.length === 0 ? (
          <EmptyState hasSearch={search.trim().length > 0} onGrant={() => setGrantOpen(true)} />
        ) : (
          filtered.map((p, idx) => (
            <PlayerRow key={p.userId} player={p} isFirst={idx === 0} />
          ))
        )}
      </div>

      <GrantCoinsDialog
        open={grantOpen}
        onOpenChange={setGrantOpen}
        users={grantTargetUsers}
      />
    </section>
  );
}

function PlayerRow({ player, isFirst }: { readonly player: PlayerCoinRow; readonly isFirst: boolean }) {
  const lastChangeText = formatLastChange(player.lastChangeDelta, player.lastChangeAt);
  const isNegative = player.lastChangeDelta != null && player.lastChangeDelta < 0;

  return (
    <div
      className={`grid grid-cols-[1fr_90px_110px] items-center gap-3 px-3.5 py-2.5 transition-colors hover:bg-foreground/[0.02] ${
        isFirst ? "" : "border-t border-border"
      }`}
    >
      <div className="flex min-w-0 items-center gap-2.5">
        <AvatarTile userId={player.userId} fullName={player.fullName} />
        <div className="min-w-0">
          <div className="truncate text-[13px] text-foreground">{player.fullName}</div>
        </div>
      </div>
      <div className="text-right">
        <span className="font-mono text-[15px] font-medium tabular-nums text-foreground">
          {player.balance}
        </span>
        <div className="font-mono text-[10px] text-muted-foreground/80">coins</div>
      </div>
      <div
        className={`font-mono text-[11px] ${isNegative ? "text-[#ef8a9d]" : "text-muted-foreground"}`}
      >
        {lastChangeText}
      </div>
    </div>
  );
}

function formatLastChange(delta: number | null, at: string | null): string {
  if (delta == null || at == null) return "—";
  // Unicode minus (U+2212), matches handoff convention and trips the color rule.
  const sign = delta < 0 ? "−" : "+";
  return `${sign}${Math.abs(delta)} · ${formatRelativeShort(at)}`;
}

function PlayerRowsSkeleton() {
  return (
    <>
      {Array.from({ length: 6 }).map((_, i) => (
        <div
          key={i}
          className={`grid grid-cols-[1fr_90px_110px] items-center gap-3 px-3.5 py-2.5 ${
            i === 0 ? "" : "border-t border-border"
          }`}
        >
          <div className="flex items-center gap-2.5">
            <Skeleton className="h-7 w-7 rounded-lg" />
            <Skeleton className="h-3.5 w-32" />
          </div>
          <Skeleton className="ml-auto h-4 w-12" />
          <Skeleton className="h-3 w-20" />
        </div>
      ))}
    </>
  );
}

function EmptyState({ hasSearch, onGrant }: { readonly hasSearch: boolean; readonly onGrant: () => void }) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-10 text-center">
      <p className="text-[13px] text-muted-foreground">
        {hasSearch
          ? "No players match your search."
          : "No players yet. Coins appear here once someone earns or is granted one."}
      </p>
      {!hasSearch ? (
        <Button size="sm" onClick={onGrant} className="gap-1">
          <Plus className="h-3.5 w-3.5" />
          Grant
        </Button>
      ) : null}
    </div>
  );
}
