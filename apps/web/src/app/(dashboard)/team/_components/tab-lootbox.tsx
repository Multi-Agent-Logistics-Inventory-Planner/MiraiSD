"use client";

import { useEffect, useMemo, useState } from "react";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { LootboxHeader } from "@/components/lootbox/layout/lootbox-header";
import { DropsTicker } from "@/components/lootbox/layout/drops-ticker";
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

type LootboxTab = "pool" | "mine" | "history";

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
  const [redemptionOpen, setRedemptionOpen] = useState(false);
  const [prizeManagerOpen, setPrizeManagerOpen] = useState(false);
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<LootboxTab>("pool");

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

  const tierCount = useMemo(
    () => (catalogQuery.data ?? []).filter((t) => t.active).length,
    [catalogQuery.data]
  );

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

  const isOpening = playMutation.isPending;

  return (
    <div className="flex flex-col gap-5">
      <LootboxHeader
        balance={balance}
        openedToday={openedToday}
        isAdmin={isAdmin}
        onManagePrizes={() => setPrizeManagerOpen(true)}
        onAdjustCoins={() => setAdjustOpen(true)}
        onRedemptionQueue={() => setRedemptionOpen(true)}
      />

      <HeroZone
        prizes={allPrizes}
        tierCount={tierCount}
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
        isOpening={isOpening}
        onOpen={triggerOpen}
        onKeep={handleKeep}
        onOpenAgain={handleOpenAgain}
      />

      <DropsTicker drops={recentQuery.data} isLoading={recentQuery.isLoading} />

      <Tabs
        value={activeTab}
        onValueChange={(value) => setActiveTab(value as LootboxTab)}
        className="mt-2 gap-0"
      >
        <TabsList className="h-auto w-full justify-start gap-1 rounded-none border-b border-border bg-transparent p-0 text-muted-foreground dark:bg-transparent">
          <LootboxTabTrigger value="pool">Prize pool</LootboxTabTrigger>
          <LootboxTabTrigger value="mine">My prizes</LootboxTabTrigger>
          <LootboxTabTrigger value="history">Coin history</LootboxTabTrigger>
        </TabsList>
        <TabsContent value="pool" className="mt-6">
          <PrizeGrid tiers={catalogQuery.data} isLoading={catalogQuery.isLoading} />
        </TabsContent>
        <TabsContent value="mine" className="mt-6">
          <MyPrizesList prizes={prizesQuery.data} isLoading={prizesQuery.isLoading} />
        </TabsContent>
        <TabsContent value="history" className="mt-6">
          <CoinHistoryPanel history={historyQuery.data} isLoading={historyQuery.isLoading} />
        </TabsContent>
      </Tabs>

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

function LootboxTabTrigger({
  value,
  children,
}: {
  readonly value: LootboxTab;
  readonly children: React.ReactNode;
}) {
  return (
    <TabsTrigger
      value={value}
      className="-mb-px h-auto flex-none rounded-none border-0 border-b-2 border-transparent bg-transparent px-4 py-3 text-sm font-medium text-muted-foreground shadow-none transition-colors hover:text-foreground data-[state=active]:border-brand-primary data-[state=active]:bg-transparent data-[state=active]:text-foreground data-[state=active]:!text-foreground data-[state=active]:shadow-none"
    >
      {children}
    </TabsTrigger>
  );
}
