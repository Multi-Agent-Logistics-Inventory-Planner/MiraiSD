"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  useLootboxBalance,
  useLootboxCatalog,
  useMyCoinHistory,
  useMyPrizes,
  useRecentLootboxPlays,
} from "@/hooks/queries/use-lootbox";
import { usePlayLootboxMutation } from "@/hooks/mutations/use-lootbox-mutations";
import { usePermissions } from "@/hooks/use-permissions";
import { toast } from "@/hooks/use-toast";
import { LootboxHeader } from "@/components/lootbox/layout/lootbox-header";
import { DropsTicker } from "@/components/lootbox/layout/drops-ticker";
import { DropRatesSheet } from "@/components/lootbox/layout/drop-rates-sheet";
import { HeroZone } from "@/components/lootbox/layout/hero-zone";
import { PrizeGrid } from "@/components/lootbox/layout/prize-grid";
import { MyPrizesList } from "@/components/lootbox/my-prizes-list";
import { CoinHistoryPanel } from "@/components/lootbox/coin-history-panel";
import { AdminRedemptionDialog } from "@/components/lootbox/admin-redemption-dialog";
import { AdminPrizeManagerDialog } from "@/components/lootbox/admin-prize-manager-dialog";
import { AdminAdjustmentDialog } from "@/components/lootbox/admin-adjustment-dialog";
import {
  REEL_DIM_DESKTOP,
  REEL_DIM_MOBILE,
  useReel,
} from "@/components/lootbox/reel/use-reel";
import type { LootboxPlay, LootboxPrize } from "@/types/lootbox";

const REEL_SPIN_MS = 5600;

function useIsDesktop(): boolean {
  const [isDesktop, setIsDesktop] = useState(true);
  useEffect(() => {
    const mq = window.matchMedia("(min-width: 640px)");
    const apply = () => setIsDesktop(mq.matches);
    apply();
    mq.addEventListener("change", apply);
    return () => mq.removeEventListener("change", apply);
  }, []);
  return isDesktop;
}

export function TabLootbox() {
  const { isAdmin } = usePermissions();
  const balanceQuery = useLootboxBalance();
  const catalogQuery = useLootboxCatalog();
  const prizesQuery = useMyPrizes();
  const historyQuery = useMyCoinHistory();
  const recentQuery = useRecentLootboxPlays(20);
  const playMutation = usePlayLootboxMutation();

  const [lastWin, setLastWin] = useState<LootboxPlay | null>(null);
  const [dropRatesOpen, setDropRatesOpen] = useState(false);
  const [redemptionOpen, setRedemptionOpen] = useState(false);
  const [prizeManagerOpen, setPrizeManagerOpen] = useState(false);
  const [adjustOpen, setAdjustOpen] = useState(false);
  const historyAnchorRef = useRef<HTMLDivElement | null>(null);

  const balance = balanceQuery.data?.balance ?? 0;

  const allPrizes: LootboxPrize[] = useMemo(() => {
    const tiers = catalogQuery.data ?? [];
    const base = tiers.flatMap((t) =>
      t.prizes.map((p) => ({
        ...p,
        tierColor: p.tierColor ?? t.displayColor ?? null,
        tierName: p.tierName ?? t.name,
      }))
    );
    if (lastWin && !base.some((p) => p.id === lastWin.prizeId)) {
      // Server rolled a prize not in the cached catalog (e.g. just activated). Use the
      // play snapshot so the reel card and lookups don't fall back to the "—" placeholder.
      base.push({
        id: lastWin.prizeId,
        name: lastWin.prizeName,
        description: lastWin.prizeDescription,
        imageUrl: lastWin.prizeImageUrl,
        tierId: "",
        tierName: lastWin.prizeTierName,
        tierColor: null,
        active: true,
      });
    }
    return base;
  }, [catalogQuery.data, lastWin]);

  const isDesktop = useIsDesktop();
  const reelDim = isDesktop ? REEL_DIM_DESKTOP : REEL_DIM_MOBILE;

  const reel = useReel({
    prizes: allPrizes,
    cardWidth: reelDim.cardW,
    cardGap: reelDim.gap,
    spinMs: REEL_SPIN_MS,
  });

  const openedToday = useMemo(() => {
    const entries = historyQuery.data ?? [];
    const todayStart = new Date();
    todayStart.setHours(0, 0, 0, 0);
    return entries.filter(
      (e) => e.kind === "PLAY" && new Date(e.at).getTime() >= todayStart.getTime()
    ).length;
  }, [historyQuery.data]);

  const triggerOpen = async () => {
    if (allPrizes.length === 0) {
      toast({
        title: "No prizes configured",
        description: "Ask an admin to add prizes before opening the lootbox.",
      });
      return;
    }
    if (balance < 1) {
      toast({ title: "Out of coins", description: "Earn more coins to open the lootbox." });
      return;
    }
    try {
      const result = await playMutation.mutateAsync();
      // Set the winner first so allPrizes includes it via fallback before reel.open
      // reads byId in the render after setStrip.
      setLastWin(result.play);
      reel.open(result.play.prizeId);
    } catch (err) {
      reel.reset();
      const message = err instanceof Error ? err.message : "Failed to open lootbox.";
      toast({ title: "Couldn't open lootbox", description: message });
    }
  };

  const handleKeep = () => {
    reel.reset();
    setLastWin(null);
  };

  const handleOpenAgain = () => {
    reel.reset();
    setLastWin(null);
    setTimeout(() => {
      void triggerOpen();
    }, 50);
  };

  const handleHistory = () => {
    historyAnchorRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const isOpening = playMutation.isPending;

  return (
    <div className="flex flex-col gap-4">
      <LootboxHeader
        balance={balance}
        openedToday={openedToday}
        isAdmin={isAdmin}
        onHistory={handleHistory}
        onDropRates={() => setDropRatesOpen(true)}
        onManagePrizes={() => setPrizeManagerOpen(true)}
        onAdjustCoins={() => setAdjustOpen(true)}
        onRedemptionQueue={() => setRedemptionOpen(true)}
      />

      <DropsTicker drops={recentQuery.data} isLoading={recentQuery.isLoading} />

      <HeroZone
        prizes={allPrizes}
        phase={reel.phase}
        strip={reel.strip}
        translate={reel.translate}
        spinMs={reel.spinMs}
        viewportRef={reel.viewportRef}
        winnerIdx={reel.winnerIdx}
        winnerPrizeId={reel.winnerPrizeId}
        isDesktop={isDesktop}
        lastWin={lastWin}
        balance={balance}
        openedToday={openedToday}
        isOpening={isOpening}
        onOpen={triggerOpen}
        onKeep={handleKeep}
        onOpenAgain={handleOpenAgain}
      />

      <PrizeGrid tiers={catalogQuery.data} isLoading={catalogQuery.isLoading} />

      <div ref={historyAnchorRef} className="grid gap-4 lg:grid-cols-2">
        <MyPrizesList prizes={prizesQuery.data} isLoading={prizesQuery.isLoading} />
        <CoinHistoryPanel history={historyQuery.data} isLoading={historyQuery.isLoading} />
      </div>

      <DropRatesSheet
        open={dropRatesOpen}
        onOpenChange={setDropRatesOpen}
        tiers={catalogQuery.data}
      />

      {isAdmin ? (
        <>
          <AdminRedemptionDialog open={redemptionOpen} onOpenChange={setRedemptionOpen} />
          <AdminPrizeManagerDialog open={prizeManagerOpen} onOpenChange={setPrizeManagerOpen} />
          <AdminAdjustmentDialog open={adjustOpen} onOpenChange={setAdjustOpen} />
        </>
      ) : null}
    </div>
  );
}
