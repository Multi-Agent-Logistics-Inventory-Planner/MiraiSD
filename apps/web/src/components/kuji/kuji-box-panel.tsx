"use client";

import { useMemo, useState } from "react";
import {
  ListTodo,
  Loader2,
  Lock,
  MinusCircle,
  PackageOpen,
  Plus,
  Undo2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { useActiveKujiBox } from "@/hooks/queries/use-kuji-box";
import { useAuditLogs } from "@/hooks/queries/use-audit-log";
import { useKujiDailyPayouts } from "@/hooks/queries/use-kuji-daily-payouts";
import { usePermissions } from "@/hooks/use-permissions";
import { KujiBoxStatus, StockMovementReason, UserRole } from "@/types/api";
import type { KujiBox, KujiBoxTier } from "@/types/api";
import { ManageTiersDialog } from "./manage-tiers-dialog";
import { OpenBoxDialog } from "./open-box-dialog";
import { CloseBoxDialog } from "./close-box-dialog";
import { ReopenBoxDialog } from "./reopen-box-dialog";
import { UndoDrawDialog } from "./undo-draw-dialog";
import { TierEditDialog } from "./tier-edit-dialog";
import { TransferInDialog } from "./transfer-in-dialog";
import { ActivityLogCard } from "./activity-log-card";
import { InlineRecordDrawForm } from "./inline-record-draw-form";
import { UniformBar } from "./uniform-bar";
import { DailyPayoutChart } from "./daily-payout-chart";
import { PrizePoolTable } from "./prize-pool-table";
import { computeBoxValues } from "./kuji-value-rollups";

interface KujiBoxPanelProps {
  readonly productId: string;
  readonly productName: string;
}

function todayLocalDateString(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function KujiBoxPanel({ productId, productName }: KujiBoxPanelProps) {
  const activeQuery = useActiveKujiBox(productId);
  const { role } = usePermissions();

  const canStructural =
    role === UserRole.ADMIN || role === UserRole.ASSISTANT_MANAGER;
  const canDraw =
    role === UserRole.ADMIN ||
    role === UserRole.ASSISTANT_MANAGER ||
    role === UserRole.EMPLOYEE;

  const today = todayLocalDateString();
  const box: KujiBox | null = activeQuery.data ?? null;
  // The server filter is LocalDate-grained, so we fetch from the box's open
  // date and then trim client-side to the exact `openedAt` timestamp below.
  // This prevents events from a same-day previously-closed box from leaking in.
  const auditFromDate = box ? box.openedAt.slice(0, 10) : today;
  // Single multi-reason query: draws, undos, and slip adjustments (stash, promote, add).
  // The session log shows all kuji-relevant activity within the box's open window.
  const sessionLogsQuery = useAuditLogs(
    {
      productId,
      reasons: [
        StockMovementReason.KUJI_PRIZE_WON,
        StockMovementReason.KUJI_DRAW_REVERSED,
        StockMovementReason.KUJI_SLIP_ADJUSTMENT,
      ],
      fromDate: auditFromDate,
    },
    0,
    100,
  );

  const [openBoxOpen, setOpenBoxOpen] = useState(false);
  const [closeBoxOpen, setCloseBoxOpen] = useState(false);
  const [reopenBoxOpen, setReopenBoxOpen] = useState(false);
  const [undoDrawOpen, setUndoDrawOpen] = useState(false);
  const [editTierOpen, setEditTierOpen] = useState(false);
  const [transferInOpen, setTransferInOpen] = useState(false);
  const [recordOpen, setRecordOpen] = useState(false);
  const [activeTier, setActiveTier] = useState<KujiBoxTier | null>(null);
  const [manageTiersOpen, setManageTiersOpen] = useState(false);

  const boxIsOpen = box?.status === KujiBoxStatus.OPEN;

  const recentLogs = useMemo(() => {
    const all = sessionLogsQuery.data?.content ?? [];
    const lo = box?.openedAt;
    const scoped = lo ? all.filter((log) => log.createdAt >= lo) : all;
    return [...scoped].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [sessionLogsQuery.data, box?.openedAt]);

  const valueRollups = useMemo(() => computeBoxValues(box), [box]);

  const tz = useMemo(
    () => Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
    [],
  );
  const dailyFrom = useMemo(() => {
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 6);
    return sevenDaysAgo.toISOString().slice(0, 10);
  }, []);
  const dailyPayoutsQuery = useKujiDailyPayouts(box?.id, {
    from: dailyFrom,
    to: today,
    tz,
  });

  function handleEditTier(tier: KujiBoxTier) {
    setActiveTier(tier);
    setEditTierOpen(true);
  }
  function handleTransferIn(tier: KujiBoxTier) {
    setActiveTier(tier);
    setTransferInOpen(true);
  }

  if (activeQuery.isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-lg font-medium">{productName}</h2>
          {box ? (
            <Badge
              variant={boxIsOpen ? "default" : "secondary"}
              className="text-[10px]"
            >
              {box.status}
            </Badge>
          ) : (
            <Badge variant="outline" className="text-[10px]">
              No active box
            </Badge>
          )}
          {box?.locationCode || box?.locationName ? (
            <span className="text-xs text-muted-foreground">
              · {box.locationCode ?? box.locationName}
            </span>
          ) : null}
          {box?.label ? (
            <span className="text-xs text-muted-foreground">· {box.label}</span>
          ) : null}
        </div>

        <div className="flex flex-wrap gap-2">
          {!box && canStructural ? (
            <Button
              type="button"
              size="sm"
              onClick={() => setOpenBoxOpen(true)}
            >
              <Plus className="mr-1 h-4 w-4" />
              Open Box
            </Button>
          ) : null}
          {boxIsOpen && canDraw ? (
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => setUndoDrawOpen(true)}
              aria-label="Undo Draw"
            >
              <Undo2 className="h-4 w-4 sm:mr-1" />
              <span className="hidden sm:inline">Undo Draw</span>
            </Button>
          ) : null}
          {boxIsOpen && canStructural ? (
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => setManageTiersOpen(true)}
              aria-label="Manage Tiers"
            >
              <ListTodo className="h-4 w-4 sm:mr-1" />
              <span className="hidden sm:inline">Manage Tiers</span>
            </Button>
          ) : null}
          {boxIsOpen && canStructural ? (
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => setCloseBoxOpen(true)}
              aria-label="Close Box"
            >
              <Lock className="h-4 w-4 sm:mr-1" />
              <span className="hidden sm:inline">Close Box</span>
            </Button>
          ) : null}
          {box && !boxIsOpen && canStructural ? (
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => setReopenBoxOpen(true)}
              aria-label="Reopen"
            >
              <MinusCircle className="h-4 w-4 sm:mr-1" />
              <span className="hidden sm:inline">Reopen</span>
            </Button>
          ) : null}
        </div>
      </div>

      {!box ? (
        <div className="rounded-xl border bg-card p-10 text-center dark:border-none">
          <PackageOpen className="mx-auto h-10 w-10 text-muted-foreground/40" />
          <p className="mt-3 text-sm text-muted-foreground">
            No box open for {productName}.
          </p>
          {canStructural ? (
            <Button
              type="button"
              className="mt-4"
              onClick={() => setOpenBoxOpen(true)}
            >
              Open Box
            </Button>
          ) : null}
        </div>
      ) : (
        <>
          <UniformBar valueRollups={valueRollups} />

          <div className="grid grid-cols-1 gap-2 lg:grid-cols-[1.5fr_1fr]">
            <DailyPayoutChart
              data={dailyPayoutsQuery.data?.series ?? []}
              totals={dailyPayoutsQuery.data?.total}
              isLoading={dailyPayoutsQuery.isLoading}
              isError={dailyPayoutsQuery.isError}
            />
            <div className="relative min-h-0">
              <div className="lg:absolute lg:inset-0">
                <ActivityLogCard
                  logs={recentLogs}
                  isLoading={sessionLogsQuery.isLoading}
                />
              </div>
            </div>
          </div>

          <PrizePoolTable tiers={box.tiers} />

          {boxIsOpen && canDraw ? (
            <>
              <Button
                type="button"
                className="w-full bg-brand-primary text-white hover:bg-brand-primary/90"
                onClick={() => setRecordOpen(true)}
              >
                <Plus className="mr-1 h-4 w-4" />
                Record a draw
              </Button>
              <InlineRecordDrawForm
                box={box}
                open={recordOpen}
                onOpenChange={setRecordOpen}
              />
            </>
          ) : null}

        </>
      )}

      <OpenBoxDialog
        open={openBoxOpen}
        onOpenChange={setOpenBoxOpen}
        productId={productId}
        productName={productName}
      />

      {box ? (
        <>
          <CloseBoxDialog
            open={closeBoxOpen}
            onOpenChange={setCloseBoxOpen}
            box={box}
          />
          <ReopenBoxDialog
            open={reopenBoxOpen}
            onOpenChange={setReopenBoxOpen}
            box={box}
          />
          <UndoDrawDialog
            open={undoDrawOpen}
            onOpenChange={setUndoDrawOpen}
            box={box}
          />
          <ManageTiersDialog
            open={manageTiersOpen}
            onOpenChange={setManageTiersOpen}
            box={box}
            canEditStructural={canStructural}
            onEditTier={handleEditTier}
            onTransferIn={handleTransferIn}
          />
          {activeTier ? (
            <>
              <TierEditDialog
                open={editTierOpen}
                onOpenChange={(o) => {
                  setEditTierOpen(o);
                  if (!o) setActiveTier(null);
                }}
                box={box}
                tier={activeTier}
              />
              <TransferInDialog
                open={transferInOpen}
                onOpenChange={(o) => {
                  setTransferInOpen(o);
                  if (!o) setActiveTier(null);
                }}
                box={box}
                tier={activeTier}
              />
            </>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
