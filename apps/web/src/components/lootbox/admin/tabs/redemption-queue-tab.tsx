"use client";

import { useMemo, useState } from "react";
import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { toast } from "@/hooks/use-toast";
import { useAdminPendingPrizes } from "@/hooks/queries/use-lootbox";
import { useMarkRedeemedMutation } from "@/hooks/mutations/use-lootbox-mutations";
import type { LootboxPlay } from "@/types/lootbox";
import { AvatarTile } from "@/components/lootbox/admin/coins/avatar-tile";

// Effectively "fetch all" at current scale (under 100 plays/day). The internal
// scroll on the list container handles overflow; pagination chrome would just
// fight with it. If a single tab open ever crosses this, switch to virtualization.
const PAGE_SIZE = 500;

type StatusTab = "WON" | "REDEEMED";

/**
 * Two-tab redemption queue. Matches the visual language of the new Coins tab:
 * bordered list, avatar tiles, mono caption labels, fixed-height scroll area.
 * Pagination collapsed to a single "Load more" button — the dataset is small
 * and a multi-page pager added more chrome than it earned.
 */
export function RedemptionQueueTab() {
  const [status, setStatus] = useState<StatusTab>("WON");
  const [search, setSearch] = useState("");
  const query = useAdminPendingPrizes(0, PAGE_SIZE, status);
  const redeemMutation = useMarkRedeemedMutation();

  const rows = query.data?.content ?? [];
  const totalElements = query.data?.totalElements ?? 0;

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter(
      (p) =>
        p.userName.toLowerCase().includes(q) ||
        p.prizeName.toLowerCase().includes(q)
    );
  }, [rows, search]);

  const handleStatusChange = (next: StatusTab) => {
    setStatus(next);
  };

  const handleRedeem = async (playId: string) => {
    try {
      await redeemMutation.mutateAsync({ playId });
      toast({ title: "Marked as redeemed.", variant: "success" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to mark redeemed.";
      toast({ title: "Couldn't redeem", description: message });
    }
  };

  const emptyCopy =
    status === "WON"
      ? "No prizes are pending redemption."
      : "No prizes have been redeemed yet.";

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-0.5">
        <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
          Redemption queue
        </span>
        <p className="text-[13px] text-muted-foreground">
          Prizes won by team members.
        </p>
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex w-fit gap-1 rounded-[8px] border border-border bg-card p-0.5">
          <StatusPill
            label="Pending"
            active={status === "WON"}
            onClick={() => handleStatusChange("WON")}
          />
          <StatusPill
            label="Redeemed"
            active={status === "REDEEMED"}
            onClick={() => handleStatusChange("REDEEMED")}
          />
        </div>
        <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row sm:items-center">
          <span className="font-mono text-[11px] text-muted-foreground">
            {totalElements} {totalElements === 1 ? "entry" : "entries"}
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
              placeholder="Filter by player or prize…"
              className="h-8 w-full pl-8 text-[12px] sm:w-[220px]"
            />
          </div>
        </div>
      </div>

      <div className="overflow-hidden rounded-[10px] border border-border bg-card">
        <div className="grid grid-cols-[1fr_auto] gap-3 border-b border-border bg-card-foreground/[0.02] px-3.5 py-2.5 font-mono text-[10px] uppercase tracking-[0.08em] text-muted-foreground">
          <span>Player · Prize</span>
          <span>{status === "WON" ? "Action" : "Redeemed"}</span>
        </div>
        <div className="max-h-[520px] overflow-y-auto">
          {query.isLoading ? (
            <QueueSkeleton />
          ) : filtered.length === 0 ? (
            <p className="px-4 py-10 text-center text-[13px] text-muted-foreground">
              {search.trim().length > 0
                ? "No entries match your filter."
                : emptyCopy}
            </p>
          ) : (
            filtered.map((p, idx) => (
              <PlayRow
                key={p.id}
                play={p}
                status={status}
                isFirst={idx === 0}
                onRedeem={handleRedeem}
                redeemPending={redeemMutation.isPending}
              />
            ))
          )}
        </div>
      </div>

    </div>
  );
}

function StatusPill({
  label,
  active,
  onClick,
}: {
  readonly label: string;
  readonly active: boolean;
  readonly onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-[6px] px-3 py-1 text-[12px] font-medium transition-colors ${
        active
          ? "bg-brand-primary text-white"
          : "text-muted-foreground hover:text-foreground"
      }`}
    >
      {label}
    </button>
  );
}

function PlayRow({
  play,
  status,
  isFirst,
  onRedeem,
  redeemPending,
}: {
  readonly play: LootboxPlay;
  readonly status: StatusTab;
  readonly isFirst: boolean;
  readonly onRedeem: (playId: string) => void;
  readonly redeemPending: boolean;
}) {
  return (
    <div
      className={`grid grid-cols-[1fr_auto] items-center gap-3 px-3.5 py-3 transition-colors hover:bg-foreground/[0.02] ${
        isFirst ? "" : "border-t border-border"
      }`}
    >
      <div className="flex min-w-0 items-center gap-2.5">
        <AvatarTile userId={play.userId} fullName={play.userName} />
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="truncate text-[13px] text-foreground">
              {play.userName}
            </span>
            <TierChip name={play.prizeTierName} />
          </div>
          <div className="truncate font-mono text-[10.5px] text-muted-foreground">
            {play.prizeName} · {formatStamp(play.playedAt)}
            {status === "REDEEMED" && play.redeemedAt
              ? ` · redeemed by ${play.redeemedByName ?? "admin"} on ${formatStamp(play.redeemedAt)}`
              : ""}
          </div>
        </div>
      </div>
      {status === "WON" ? (
        <Button
          size="sm"
          variant="outline"
          onClick={() => onRedeem(play.id)}
          disabled={redeemPending}
        >
          Mark redeemed
        </Button>
      ) : (
        <span className="font-mono text-[10.5px] uppercase tracking-[0.08em] text-muted-foreground">
          Done
        </span>
      )}
    </div>
  );
}

function TierChip({ name }: { readonly name: string }) {
  return (
    <span className="rounded border border-border bg-card-foreground/[0.04] px-1.5 py-0.5 font-mono text-[9px] uppercase tracking-[0.06em] text-muted-foreground">
      {name}
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

function QueueSkeleton() {
  return (
    <>
      {Array.from({ length: 6 }).map((_, i) => (
        <div
          key={i}
          className={`grid grid-cols-[1fr_auto] items-center gap-3 px-3.5 py-3 ${
            i === 0 ? "" : "border-t border-border"
          }`}
        >
          <div className="flex items-center gap-2.5">
            <Skeleton className="h-7 w-7 rounded-lg" />
            <div className="space-y-1.5">
              <Skeleton className="h-3 w-28" />
              <Skeleton className="h-2.5 w-52" />
            </div>
          </div>
          <Skeleton className="h-7 w-24" />
        </div>
      ))}
    </>
  );
}
