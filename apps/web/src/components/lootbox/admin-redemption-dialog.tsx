"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { toast } from "@/hooks/use-toast";
import { useAdminPendingPrizes } from "@/hooks/queries/use-lootbox";
import { useMarkRedeemedMutation } from "@/hooks/mutations/use-lootbox-mutations";

interface AdminRedemptionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function formatDate(value: string) {
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

export function AdminRedemptionDialog({
  open,
  onOpenChange,
}: AdminRedemptionDialogProps) {
  const [page, setPage] = useState(0);
  const size = 10;
  const pendingQuery = useAdminPendingPrizes(page, size);
  const redeemMutation = useMarkRedeemedMutation();

  const handleRedeem = async (playId: string) => {
    try {
      await redeemMutation.mutateAsync({ playId });
      toast({ title: "Marked as redeemed.", variant: "success" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to mark redeemed.";
      toast({ title: "Couldn't redeem", description: message });
    }
  };

  const rows = pendingQuery.data?.content ?? [];
  const totalPages = pendingQuery.data?.totalPages ?? 0;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Pending Redemptions</DialogTitle>
        </DialogHeader>
        {pendingQuery.isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : rows.length === 0 ? (
          <p className="text-sm text-muted-foreground py-6 text-center">
            No prizes are pending redemption.
          </p>
        ) : (
          <ul className="divide-y max-h-[60vh] overflow-y-auto">
            {rows.map((p) => (
              <li key={p.id} className="py-3 flex items-center justify-between gap-3">
                <div className="min-w-0 space-y-1">
                  <div className="font-medium truncate">
                    {p.userName} <span className="text-muted-foreground">won</span>{" "}
                    {p.prizeName}
                  </div>
                  <div className="text-xs text-muted-foreground">
                    <Badge variant="secondary" className="mr-2">
                      {p.prizeTierName}
                    </Badge>
                    {formatDate(p.playedAt)}
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
        {totalPages > 1 && (
          <div className="flex items-center justify-between pt-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Prev
            </Button>
            <span className="text-sm text-muted-foreground">
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
        )}
      </DialogContent>
    </Dialog>
  );
}
