"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
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
import { AdminModal } from "@/components/lootbox/admin/admin-modal";
import {
  REEL_DIM_DESKTOP,
  REEL_DIM_MOBILE,
  useReel,
} from "@/components/lootbox/reel/use-reel";
import type { Lootbox, LootboxPlay, LootboxPrize } from "@/types/lootbox";

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

  const router = useRouter();
  const searchParams = useSearchParams();
  const crateParam = searchParams.get("crate");

  const [lastWin, setLastWin] = useState<LootboxPlay | null>(null);
  const [adminOpen, setAdminOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<LootboxTab>("pool");

  const balance = balanceQuery.data?.balance ?? 0;
  const crates = useMemo<Lootbox[]>(
    () => catalogQuery.data ?? [],
    [catalogQuery.data]
  );

  // Active crate is driven by ?crate=<id>; fall back to the first available crate
  // when the param is missing or stale. Rendered without setState to avoid an
  // update-during-render bounce.
  const selectedCrateId =
    crateParam && crates.some((c) => c.id === crateParam)
      ? crateParam
      : crates[0]?.id ?? null;

  const selectedCrate: Lootbox | null = useMemo(
    () => crates.find((c) => c.id === selectedCrateId) ?? null,
    [crates, selectedCrateId]
  );

  const allPrizes: LootboxPrize[] = useMemo(() => {
    const tiers = selectedCrate?.tiers ?? [];
    const base = tiers.flatMap((t) =>
      t.prizes.map((p) => ({
        ...p,
        tierColor: p.tierColor ?? t.displayColor ?? null,
        tierName: p.tierName ?? t.name,
      }))
    );
    if (lastWin && !base.some((p) => p.id === lastWin.prizeId)) {
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
  }, [selectedCrate, lastWin]);

  const tierCount = useMemo(
    () => (selectedCrate?.tiers ?? []).filter((t) => t.active).length,
    [selectedCrate]
  );

  const isDesktop = useIsDesktop();
  const reelDim = isDesktop ? REEL_DIM_DESKTOP : REEL_DIM_MOBILE;

  const reel = useReel({
    prizes: allPrizes,
    cardWidth: reelDim.cardW,
    cardGap: reelDim.gap,
    spinMs: REEL_SPIN_MS,
  });

  // Reset reel + win state whenever the active crate changes so a stale modal
  // from a previous crate doesn't carry across the flip.
  useEffect(() => {
    setLastWin(null);
    reel.reset();
    // reel.reset has a stable identity (closure over refs); intentionally
    // depend only on the active crate id to fire exactly once per flip.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCrateId]);

  const handleSelectCrate = (id: string) => {
    const params = new URLSearchParams(searchParams?.toString() ?? "");
    params.set("crate", id);
    router.replace(`?${params.toString()}`, { scroll: false });
  };

  const openedToday = useMemo(() => {
    const entries = historyQuery.data ?? [];
    const todayStart = new Date();
    todayStart.setHours(0, 0, 0, 0);
    return entries.filter(
      (e) => e.kind === "PLAY" && new Date(e.at).getTime() >= todayStart.getTime()
    ).length;
  }, [historyQuery.data]);

  const cost = selectedCrate?.cost ?? 1;

  const triggerOpen = async () => {
    if (!selectedCrate) {
      toast({
        title: "No crate selected",
        description: "Pick a crate to open.",
      });
      return;
    }
    if (allPrizes.length === 0) {
      toast({
        title: "No prizes configured",
        description: "Ask an admin to add prizes before opening this crate.",
      });
      return;
    }
    if (balance < cost) {
      toast({
        title: "Out of coins",
        description: `Need ${cost} coin${cost === 1 ? "" : "s"} to open this crate.`,
      });
      return;
    }
    try {
      const result = await playMutation.mutateAsync({ crateId: selectedCrate.id });
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
        onOpenAdmin={() => setAdminOpen(true)}
      />

      <HeroZone
        crate={selectedCrate}
        crates={crates}
        onSelectCrate={handleSelectCrate}
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
          <PrizeGrid
            tiers={selectedCrate?.tiers}
            isLoading={catalogQuery.isLoading}
          />
        </TabsContent>
        <TabsContent value="mine" className="mt-6">
          <MyPrizesList prizes={prizesQuery.data} isLoading={prizesQuery.isLoading} />
        </TabsContent>
        <TabsContent value="history" className="mt-6">
          <CoinHistoryPanel history={historyQuery.data} isLoading={historyQuery.isLoading} />
        </TabsContent>
      </Tabs>

      {isAdmin ? (
        <AdminModal open={adminOpen} onOpenChange={setAdminOpen} />
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
