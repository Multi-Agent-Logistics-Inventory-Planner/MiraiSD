"use client";

import { Coins, Inbox, Settings, Sliders } from "lucide-react";
import { Button } from "@/components/ui/button";

export interface LootboxHeaderActions {
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
  onManagePrizes,
  onAdjustCoins,
  onRedemptionQueue,
}: LootboxHeaderProps) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex flex-wrap items-center gap-3.5">
        <div className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-3 py-1.5">
          <Coins className="h-3.5 w-3.5 text-amber-500" />
          <span className="font-mono text-[12px] tabular-nums text-foreground">{balance}</span>
          <span className="font-mono text-[11px] text-muted-foreground">
            coin{balance === 1 ? "" : "s"}
          </span>
        </div>
        <span className="font-mono text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
          Season 01 · <span className="tabular-nums">{openedToday}</span> opened today
        </span>
      </div>

      {isAdmin ? (
        <div className="flex flex-wrap items-center gap-1.5">
          <Button variant="outline" size="sm" onClick={onManagePrizes}>
            <Settings className="h-4 w-4" />
            Manage prizes
          </Button>
          <Button variant="outline" size="sm" onClick={onAdjustCoins}>
            <Sliders className="h-4 w-4" />
            Adjust coins
          </Button>
          <Button variant="outline" size="sm" onClick={onRedemptionQueue}>
            <Inbox className="h-4 w-4" />
            Redemption queue
          </Button>
        </div>
      ) : null}
    </div>
  );
}
