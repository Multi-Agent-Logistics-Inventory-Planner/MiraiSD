"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { CratesAndPrizesTab } from "@/components/lootbox/admin/tabs/crates-and-prizes-tab";
import { CoinsTab } from "@/components/lootbox/admin/tabs/coins-tab";
import { RedemptionQueueTab } from "@/components/lootbox/admin/tabs/redemption-queue-tab";
import { HistoryTab } from "@/components/lootbox/admin/tabs/history-tab";

export type AdminTab = "prizes" | "coins" | "queue" | "history";

interface AdminModalProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
}

/**
 * Single unified admin surface for lootbox management. Replaces the previous
 * three separate dialogs (Manage Prizes / Adjust Coins / Redemption queue) and
 * the standalone /team/lootbox-admin pages. Tab state + per-tab form state
 * persist for the lifetime of the modal mount.
 */
export function AdminModal({ open, onOpenChange }: AdminModalProps) {
  const [activeTab, setActiveTab] = useState<AdminTab>("prizes");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        showCloseButton
        className={cn(
          "flex h-[min(820px,88vh)] w-[min(1040px,92vw)] max-w-[1040px] flex-col gap-0 overflow-hidden p-0 sm:max-w-[1040px]"
        )}
      >
        <DialogTitle className="sr-only">Box admin console</DialogTitle>
        <div className="flex flex-none items-center justify-between border-b border-border px-4 py-4 pr-12 sm:px-6 sm:py-5">
          <div className="flex flex-col gap-0.5">
            <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
              Admin
            </span>
            <span className="text-[18px] font-semibold tracking-[-0.2px] text-foreground">
              Lootbox console
            </span>
          </div>
        </div>
        <Tabs
          value={activeTab}
          onValueChange={(v) => setActiveTab(v as AdminTab)}
          className="flex min-h-0 flex-1 flex-col gap-0"
        >
          <div className="flex-none overflow-x-auto border-b border-border">
            <TabsList className="h-auto w-max justify-start gap-1 rounded-none border-none bg-transparent px-4 py-0 text-muted-foreground dark:bg-transparent">
              <AdminTabTrigger value="prizes">Boxes & Prizes</AdminTabTrigger>
              <AdminTabTrigger value="coins">Coins</AdminTabTrigger>
              <AdminTabTrigger value="queue">Redemption queue</AdminTabTrigger>
              <AdminTabTrigger value="history">History</AdminTabTrigger>
            </TabsList>
          </div>
          <TabsContent value="prizes" className="min-h-0 flex-1 overflow-hidden p-0">
            <CratesAndPrizesTab />
          </TabsContent>
          <TabsContent value="coins" className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6">
            <CoinsTab onViewAllActivity={() => setActiveTab("history")} />
          </TabsContent>
          <TabsContent value="queue" className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6">
            <RedemptionQueueTab />
          </TabsContent>
          <TabsContent value="history" className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6">
            <HistoryTab />
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}

function AdminTabTrigger({
  value,
  children,
}: {
  readonly value: AdminTab;
  readonly children: React.ReactNode;
}) {
  return (
    <TabsTrigger
      value={value}
      className="-mb-px h-auto flex-none rounded-none border-0 border-b-2 border-transparent bg-transparent px-3.5 py-3.5 text-[13px] font-medium text-muted-foreground shadow-none transition-colors hover:text-foreground data-[state=active]:border-brand-primary data-[state=active]:bg-transparent data-[state=active]:text-foreground data-[state=active]:!text-foreground data-[state=active]:shadow-none"
    >
      {children}
    </TabsTrigger>
  );
}
