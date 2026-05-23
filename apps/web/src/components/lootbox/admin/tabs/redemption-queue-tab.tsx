"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { toast } from "@/hooks/use-toast";
import { useAdminPendingPrizes } from "@/hooks/queries/use-lootbox";
import { useMarkRedeemedMutation } from "@/hooks/mutations/use-lootbox-mutations";
import type { LootboxPlay } from "@/types/lootbox";

const PAGE_SIZE = 10;

type StatusTab = "WON" | "REDEEMED";

function formatDate(value: string): string {
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

/**
 * Two-tab redemption queue: Pending (status=WON, with a Mark-redeemed action) and
 * Redeemed (status=REDEEMED, read-only with redeemer + timestamp). Same backend
 * endpoint, parameterised by `status`.
 */
export function RedemptionQueueTab() {
  const [status, setStatus] = useState<StatusTab>("WON");
  const [page, setPage] = useState(0);
  const query = useAdminPendingPrizes(page, PAGE_SIZE, status);
  const redeemMutation = useMarkRedeemedMutation();

  const rows = query.data?.content ?? [];
  const totalPages = query.data?.totalPages ?? 0;

  const handleStatusChange = (value: string) => {
    setStatus(value as StatusTab);
    setPage(0);
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
    <div className="space-y-3">
      <div className="flex flex-col gap-0.5">
        <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
          Redemption queue
        </span>
        <p className="text-[13px] text-muted-foreground">
          Prizes won by team members.
        </p>
      </div>

      <Tabs value={status} onValueChange={handleStatusChange}>
        <TabsList>
          <TabsTrigger value="WON" className="cursor-pointer">Pending</TabsTrigger>
          <TabsTrigger value="REDEEMED" className="cursor-pointer">Redeemed</TabsTrigger>
        </TabsList>
      </Tabs>

      {query.isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      ) : rows.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">
          {emptyCopy}
        </p>
      ) : (
        <ul className="divide-y divide-border rounded-xl border border-border bg-card/40">
          {rows.map((p) => (
            <PlayRow
              key={p.id}
              play={p}
              status={status}
              onRedeem={handleRedeem}
              redeemPending={redeemMutation.isPending}
            />
          ))}
        </ul>
      )}

      {totalPages > 1 ? (
        <div className="flex items-center justify-between pt-1">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Prev
          </Button>
          <span className="text-xs text-muted-foreground">
            Page {page + 1} of {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      ) : null}
    </div>
  );
}

function PlayRow({
  play,
  status,
  onRedeem,
  redeemPending,
}: {
  readonly play: LootboxPlay;
  readonly status: StatusTab;
  readonly onRedeem: (playId: string) => void;
  readonly redeemPending: boolean;
}) {
  return (
    <li className="flex items-center justify-between gap-3 px-4 py-3">
      <div className="min-w-0 space-y-1">
        <div className="truncate text-sm font-medium text-foreground">
          {play.userName}{" "}
          <span className="text-muted-foreground">won</span> {play.prizeName}
        </div>
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <Badge variant="secondary">{play.prizeTierName}</Badge>
          <span className="font-mono">{formatDate(play.playedAt)}</span>
          {status === "REDEEMED" && play.redeemedAt ? (
            <span className="font-mono">
              · Redeemed by {play.redeemedByName ?? "admin"} on{" "}
              {formatDate(play.redeemedAt)}
            </span>
          ) : null}
        </div>
      </div>
      {status === "WON" ? (
        <Button
          size="sm"
          onClick={() => onRedeem(play.id)}
          disabled={redeemPending}
        >
          Mark redeemed
        </Button>
      ) : (
        <Badge variant="secondary">Redeemed</Badge>
      )}
    </li>
  );
}
