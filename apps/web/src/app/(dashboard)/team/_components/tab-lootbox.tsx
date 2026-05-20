"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Coins, Settings, Inbox } from "lucide-react";
import {
  useLootboxBalance,
  useLootboxCatalog,
  useMyCoinHistory,
  useMyPrizes,
} from "@/hooks/queries/use-lootbox";
import { usePlayLootboxMutation } from "@/hooks/mutations/use-lootbox-mutations";
import { usePermissions } from "@/hooks/use-permissions";
import { toast } from "@/hooks/use-toast";
import { UnboxingAnimation } from "@/components/lootbox/unboxing-animation";
import { MyPrizesList } from "@/components/lootbox/my-prizes-list";
import { CoinHistoryPanel } from "@/components/lootbox/coin-history-panel";
import { DropRatesPanel } from "@/components/lootbox/drop-rates-panel";
import { AdminRedemptionDialog } from "@/components/lootbox/admin-redemption-dialog";
import { AdminPrizeManagerDialog } from "@/components/lootbox/admin-prize-manager-dialog";
import { AdminAdjustmentDialog } from "@/components/lootbox/admin-adjustment-dialog";
import type { LootboxPlay } from "@/types/lootbox";

export function TabLootbox() {
  const { isAdmin } = usePermissions();
  const balanceQuery = useLootboxBalance();
  const catalogQuery = useLootboxCatalog();
  const prizesQuery = useMyPrizes();
  const historyQuery = useMyCoinHistory();
  const playMutation = usePlayLootboxMutation();

  const [lastWin, setLastWin] = useState<LootboxPlay | null>(null);
  const [redemptionOpen, setRedemptionOpen] = useState(false);
  const [prizeManagerOpen, setPrizeManagerOpen] = useState(false);
  const [adjustOpen, setAdjustOpen] = useState(false);

  const balance = balanceQuery.data?.balance ?? 0;
  const canPlay = balance >= 1 && !playMutation.isPending;

  const handlePlay = async () => {
    try {
      const result = await playMutation.mutateAsync();
      setLastWin(result.play);
      toast({
        title: `You won: ${result.play.prizeName}!`,
        description: `${result.play.prizeTierName} tier. Balance: ${result.newBalance}.`,
        variant: "success",
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to open lootbox.";
      toast({ title: "Couldn't open lootbox", description: message });
    }
  };

  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <div className="lg:col-span-2 space-y-4">
        <Card>
          <CardContent className="p-4 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <Coins className="h-5 w-5 text-amber-500" />
              {balanceQuery.isLoading ? (
                <Skeleton className="h-7 w-32" />
              ) : (
                <div>
                  <div className="text-2xl font-semibold tabular-nums">{balance}</div>
                  <div className="text-xs text-muted-foreground">
                    Pito Coins {balanceQuery.data
                      ? `(reviews ${balanceQuery.data.reviewCredits}, grants ${balanceQuery.data.totalAdjustments}, spent ${balanceQuery.data.totalSpent})`
                      : ""}
                  </div>
                </div>
              )}
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {isAdmin && (
                <>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setRedemptionOpen(true)}
                  >
                    <Inbox className="h-4 w-4" />
                    Redemption queue
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPrizeManagerOpen(true)}
                  >
                    <Settings className="h-4 w-4" />
                    Manage prizes
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setAdjustOpen(true)}
                  >
                    <Coins className="h-4 w-4" />
                    Adjust coins
                  </Button>
                </>
              )}
              <Button onClick={handlePlay} disabled={!canPlay}>
                {playMutation.isPending ? "Opening…" : "Open Lootbox (1 coin)"}
              </Button>
            </div>
          </CardContent>
        </Card>

        <UnboxingAnimation result={lastWin} pending={playMutation.isPending} />

        <MyPrizesList prizes={prizesQuery.data} isLoading={prizesQuery.isLoading} />
      </div>

      <div className="space-y-4">
        <DropRatesPanel tiers={catalogQuery.data} isLoading={catalogQuery.isLoading} />
        <CoinHistoryPanel history={historyQuery.data} isLoading={historyQuery.isLoading} />
      </div>

      {isAdmin && (
        <>
          <AdminRedemptionDialog open={redemptionOpen} onOpenChange={setRedemptionOpen} />
          <AdminPrizeManagerDialog open={prizeManagerOpen} onOpenChange={setPrizeManagerOpen} />
          <AdminAdjustmentDialog open={adjustOpen} onOpenChange={setAdjustOpen} />
        </>
      )}
    </div>
  );
}
