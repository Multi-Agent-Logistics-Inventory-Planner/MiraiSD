"use client";

import { useState } from "react";
import { QuickAdjustCard } from "@/components/lootbox/admin/coins/quick-adjust-card";
import {
  RecentAdjustmentsCard,
  type RecentAdjustmentEntry,
} from "@/components/lootbox/admin/coins/recent-adjustments-card";
import { CoinRateCard } from "@/components/lootbox/admin/coins/coin-rate-card";

const RECENT_MAX = 10;

export function CoinsTab() {
  const [recent, setRecent] = useState<readonly RecentAdjustmentEntry[]>([]);

  const handleAdjusted = (entry: RecentAdjustmentEntry) => {
    setRecent((prev) => [entry, ...prev].slice(0, RECENT_MAX));
  };

  return (
    <div className="flex flex-col gap-5">
      <QuickAdjustCard onAdjusted={handleAdjusted} />
      <RecentAdjustmentsCard entries={recent} />
      <CoinRateCard />
    </div>
  );
}
