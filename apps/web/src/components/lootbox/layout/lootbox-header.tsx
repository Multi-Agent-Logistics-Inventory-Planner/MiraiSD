"use client";

import { Coins, History, Inbox, ListChecks, Settings, Sliders } from "lucide-react";
import { Button } from "@/components/ui/button";

export interface LootboxHeaderActions {
  readonly onHistory: () => void;
  readonly onDropRates: () => void;
  readonly onManagePrizes: () => void;
  readonly onAdjustCoins: () => void;
  readonly onRedemptionQueue: () => void;
}

interface LootboxHeaderProps extends LootboxHeaderActions {
  readonly balance: number;
  readonly openedToday: number;
  readonly isAdmin: boolean;
}

export function LootboxHeader({
  balance,
  openedToday,
  isAdmin,
  onHistory,
  onDropRates,
  onManagePrizes,
  onAdjustCoins,
  onRedemptionQueue,
}: LootboxHeaderProps) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3 rounded-xl border border-border bg-card p-5">
      <div className="min-w-0">
        <div className="flex items-baseline gap-2">
          <h2 className="text-[19px] font-medium tracking-tight text-foreground">
            Pito Coin Lootbox
          </h2>
          <span className="rounded bg-foreground px-1.5 py-0.5 font-mono text-[10px] uppercase tracking-[0.16em] text-background">
            S01
          </span>
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 font-mono text-[12px] text-muted-foreground">
          <span className="inline-flex items-center gap-1 text-foreground">
            <Coins className="h-3.5 w-3.5 text-amber-500" />
            <span className="tabular-nums">{balance}</span> coin{balance === 1 ? "" : "s"}
          </span>
          <span aria-hidden>·</span>
          <span className="tabular-nums">{openedToday} opened today</span>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        <Button variant="outline" size="sm" onClick={onHistory}>
          <History className="h-4 w-4" />
          History
        </Button>
        <Button variant="outline" size="sm" onClick={onDropRates}>
          <Sliders className="h-4 w-4" />
          Drop rates
        </Button>
        {isAdmin ? (
          <>
            <Button variant="outline" size="sm" onClick={onManagePrizes}>
              <Settings className="h-4 w-4" />
              Manage prizes
            </Button>
            <Button variant="outline" size="sm" onClick={onAdjustCoins}>
              <ListChecks className="h-4 w-4" />
              Adjust coins
            </Button>
            <Button variant="outline" size="sm" onClick={onRedemptionQueue}>
              <Inbox className="h-4 w-4" />
              Redemption queue
            </Button>
          </>
        ) : null}
      </div>
    </div>
  );
}
