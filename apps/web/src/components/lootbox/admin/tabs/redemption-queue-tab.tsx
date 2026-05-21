"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { toast } from "@/hooks/use-toast";
import { useAdminPendingPrizes } from "@/hooks/queries/use-lootbox";
import { useMarkRedeemedMutation } from "@/hooks/mutations/use-lootbox-mutations";

const PAGE_SIZE = 10;

function formatDate(value: string): string {
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

/**
 * Lists pending prize redemptions with a paginated table. Lifted out of the
 * previous AdminRedemptionDialog so the same queue mechanic lives in the unified
 * admin modal's third tab.
 */
export function RedemptionQueueTab() {
  const [page, setPage] = useState(0);
  const pendingQuery = useAdminPendingPrizes(page, PAGE_SIZE);
  const redeemMutation = useMarkRedeemedMutation();

  const rows = pendingQuery.data?.content ?? [];
  const totalPages = pendingQuery.data?.totalPages ?? 0;

  const handleRedeem = async (playId: string) => {
    try {
      await redeemMutation.mutateAsync({ playId });
      toast({ title: "Marked as redeemed.", variant: "success" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to mark redeemed.";
      toast({ title: "Couldn't redeem", description: message });
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-col gap-0.5">
        <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
          Pending redemptions
        </span>
        <p className="text-[13px] text-muted-foreground">
          Prizes won by team members waiting on you to hand them over.
        </p>
      </div>

      {pendingQuery.isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      ) : rows.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">
          No prizes are pending redemption.
        </p>
      ) : (
        <ul className="divide-y divide-border rounded-xl border border-border bg-card/40">
          {rows.map((p) => (
            <li
              key={p.id}
              className="flex items-center justify-between gap-3 px-4 py-3"
            >
              <div className="min-w-0 space-y-1">
                <div className="truncate text-sm font-medium text-foreground">
                  {p.userName}{" "}
                  <span className="text-muted-foreground">won</span> {p.prizeName}
                </div>
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <Badge variant="secondary">{p.prizeTierName}</Badge>
                  <span className="font-mono">{formatDate(p.playedAt)}</span>
                </div>
              </div>
              <Button
                size="sm"
                onClick={() => handleRedeem(p.id)}
                disabled={redeemMutation.isPending}
              >
                Mark redeemed
              </Button>
            </li>
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
