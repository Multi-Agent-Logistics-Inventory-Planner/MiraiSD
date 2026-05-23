"use client";

import { CoinHistoryPanel } from "@/components/lootbox/coin-history-panel";
import { useMyCoinHistory } from "@/hooks/queries/use-lootbox";

/**
 * Reuses the player-facing CoinHistoryPanel as a stand-in for the cross-team
 * audit log specified in the v2 handoff. A true admin audit log endpoint does
 * not exist yet — when one is added, swap the data source here.
 */
export function HistoryTab() {
  const historyQuery = useMyCoinHistory();

  return (
    <div className="space-y-3">
      <div className="flex flex-col gap-0.5">
        <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
          History
        </span>
        <p className="text-[13px] text-muted-foreground">
          Coin Activity Displayed Here.
        </p>
      </div>
      <CoinHistoryPanel
        history={historyQuery.data}
        isLoading={historyQuery.isLoading}
      />
    </div>
  );
}
