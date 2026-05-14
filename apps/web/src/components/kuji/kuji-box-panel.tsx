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
import { KujiStatTile } from "./kuji-stat-tile";
import { BoxOfSlipsCard } from "./box-of-slips-card";
import { NotInBoxCard } from "./not-in-box-card";
import { ActivityLogCard } from "./activity-log-card";
import { InlineRecordDrawForm } from "./inline-record-draw-form";

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

  const stats = useMemo(() => {
    if (!box) {
      return { total: 0, slips: 0, notInBox: 0 };
    }
    let total = 0;
    let notInBox = 0;
    for (const tier of box.tiers) {
      total += tier.activeCount + tier.inactiveCount;
      notInBox += tier.inactiveCount;
    }
    return { total, slips: box.totalCount, notInBox };
  }, [box]);

  const recentLogs = useMemo(() => {
    const all = sessionLogsQuery.data?.content ?? [];
    const lo = box?.openedAt;
    const scoped = lo ? all.filter((log) => log.createdAt >= lo) : all;
    return [...scoped].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [sessionLogsQuery.data, box?.openedAt]);

  // "Draws today" = today's drawn slips minus today's reversed slips.
  // Slip adjustments (stash/promote/add-slip) are not draws and are excluded.
  const drawsToday = useMemo(() => {
    return recentLogs.reduce((sum, log) => {
      const isToday = log.createdAt.slice(0, 10) === today;
      if (!isToday) return sum;
      const qty = log.totalQuantityMoved ?? 0;
      if (log.reason === StockMovementReason.KUJI_PRIZE_WON) return sum + qty;
      if (log.reason === StockMovementReason.KUJI_DRAW_REVERSED) return sum - qty;
      return sum;
    }, 0);
  }, [recentLogs, today]);

  // Total slips drawn for the current open session of the box (since openedAt).
  const totalDrawn = useMemo(() => {
    return recentLogs.reduce((sum, log) => {
      const qty = log.totalQuantityMoved ?? 0;
      if (log.reason === StockMovementReason.KUJI_PRIZE_WON) return sum + qty;
      if (log.reason === StockMovementReason.KUJI_DRAW_REVERSED) return sum - qty;
      return sum;
    }, 0);
  }, [recentLogs]);

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
            >
              <Undo2 className="mr-1 h-4 w-4" />
              Undo Draw
            </Button>
          ) : null}
          {boxIsOpen && canStructural ? (
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => setManageTiersOpen(true)}
            >
              <ListTodo className="mr-1 h-4 w-4" />
              Manage Tiers
            </Button>
          ) : null}
          {boxIsOpen && canStructural ? (
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => setCloseBoxOpen(true)}
            >
              <Lock className="mr-1 h-4 w-4" />
              Close Box
            </Button>
          ) : null}
          {box && !boxIsOpen && canStructural ? (
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => setReopenBoxOpen(true)}
            >
              <MinusCircle className="mr-1 h-4 w-4" />
              Reopen
            </Button>
          ) : null}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2 md:grid-cols-5">
        <KujiStatTile
          label="Total prizes"
          value={stats.total.toLocaleString()}
          sub="in machine"
        />
        <KujiStatTile
          label="Active Slips"
          value={stats.slips.toLocaleString()}
          sub="ready to draw"
        />
        <KujiStatTile
          label="Inactive Slips"
          value={stats.notInBox.toLocaleString()}
          sub="held back"
        />
        <KujiStatTile
          label="Total drawn"
          value={totalDrawn.toLocaleString()}
          sub="this session"
        />
        <KujiStatTile
          label="Draws today"
          value={drawsToday.toLocaleString()}
          sub="today"
        />
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
          <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
            <BoxOfSlipsCard tiers={box.tiers} totalCount={box.totalCount} />
            <NotInBoxCard tiers={box.tiers} />
          </div>

          <ActivityLogCard
            logs={recentLogs}
            isLoading={sessionLogsQuery.isLoading}
          />

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
