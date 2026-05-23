"use client";

import { QuickAdjustCard } from "@/components/lootbox/admin/coins/quick-adjust-card";
import { CoinRateCard } from "@/components/lootbox/admin/coins/coin-rate-card";

export function CoinsTab() {
  return (
    <div className="flex flex-col gap-5">
      <QuickAdjustCard />
      <CoinRateCard />
    </div>
  );
}
