"use client";

import { useMemo, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useAuditLogs } from "@/hooks/queries/use-audit-log";
import { useKujiDailyPayouts } from "@/hooks/queries/use-kuji-daily-payouts";
import { usePermissions } from "@/hooks/use-permissions";
import {
  StockMovementReason,
  type AuditLog,
  type KujiBox,
} from "@/types/api";
import { ActivityLogCard } from "./activity-log-card";
import { DailyPayoutChart } from "./daily-payout-chart";
import { UniformBar } from "./uniform-bar";
import { computeBoxValues } from "./kuji-value-rollups";

interface KujiHistoryDetailDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly productId: string;
  readonly productName: string;
  readonly box: KujiBox | null;
}

function formatStamp(iso: string): string {
  return new Date(iso).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function KujiHistoryDetailDialog({
  open,
  onOpenChange,
  productId,
  productName,
  box,
}: KujiHistoryDetailDialogProps) {
  const subtitle = box
    ? [
        box.label ?? `Box ${box.id.slice(0, 8)}`,
        box.locationCode ?? box.locationName ?? null,
      ]
        .filter(Boolean)
        .join(" · ")
    : null;

  const range = box
    ? `${formatStamp(box.openedAt)} → ${box.closedAt ? formatStamp(box.closedAt) : "—"}`
    : null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-1rem)] sm:w-[calc(100%-2rem)] sm:max-w-4xl max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
        <DialogHeader className="px-4 py-4 sm:px-6 sm:py-5 border-b space-y-1">
          <DialogTitle className="truncate pr-8 text-base sm:text-lg">
            {productName} — Box Summary
          </DialogTitle>
          {range ? (
            <div className="text-[11px] sm:text-xs text-muted-foreground tabular-nums truncate">
              {range}
            </div>
          ) : null}
          {subtitle ? (
            <div className="text-[11px] sm:text-xs text-muted-foreground truncate">
              {subtitle}
            </div>
          ) : null}
        </DialogHeader>

        <div className="flex-1 overflow-y-auto overflow-x-hidden px-4 py-4 sm:px-6 sm:py-5">
          {box ? <DetailBody box={box} productId={productId} /> : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}

interface DetailBodyProps {
  readonly box: KujiBox;
  readonly productId: string;
}

function DetailBody({ box, productId }: DetailBodyProps) {
  const { canViewKujiPrices } = usePermissions();
  const fromDate = box.openedAt.slice(0, 10);
  const toDate = (box.closedAt ?? box.openedAt).slice(0, 10);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);

  const tz = useMemo(
    () => Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
    [],
  );

  const dailyPayoutsQuery = useKujiDailyPayouts(box.id, {
    from: fromDate,
    to: toDate,
    tz,
  });

  const logsQuery = useAuditLogs(
    {
      productId,
      reasons: [
        StockMovementReason.KUJI_PRIZE_WON,
        StockMovementReason.KUJI_DRAW_REVERSED,
        StockMovementReason.KUJI_SLIP_ADJUSTMENT,
      ],
      fromDate,
      toDate,
    },
    0,
    200,
  );

  // Server filters by LocalDate, so trim client-side to this box's window so
  // events from a same-day sibling box don't bleed in.
  const logs = useMemo<AuditLog[]>(() => {
    const all = logsQuery.data?.content ?? [];
    const lo = box.openedAt;
    const hi = box.closedAt ?? new Date().toISOString();
    const scoped = all.filter(
      (log) => log.createdAt >= lo && log.createdAt <= hi,
    );
    const byDay = selectedDate
      ? scoped.filter((log) => log.createdAt.slice(0, 10) === selectedDate)
      : scoped;
    return [...byDay].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [logsQuery.data, box.openedAt, box.closedAt, selectedDate]);

  const valueRollups = useMemo(() => computeBoxValues(box), [box]);

  const selectedDateLabel = useMemo(() => {
    if (!selectedDate) return null;
    const d = new Date(`${selectedDate}T00:00:00`);
    if (Number.isNaN(d.getTime())) return selectedDate;
    return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
  }, [selectedDate]);

  return (
    <div className="min-w-0 space-y-3">
      <UniformBar valueRollups={valueRollups} showPrices={canViewKujiPrices} />

      <div className="grid min-w-0 grid-cols-1 gap-2 lg:grid-cols-[1.5fr_1fr]">
        <div className="min-w-0">
          <DailyPayoutChart
            data={dailyPayoutsQuery.data?.series ?? []}
            totals={dailyPayoutsQuery.data?.total}
            isLoading={dailyPayoutsQuery.isLoading}
            isError={dailyPayoutsQuery.isError}
            range="all"
            canExpandRange={false}
            selectedDate={selectedDate}
            onSelectDate={setSelectedDate}
            compact
            showPrices={canViewKujiPrices}
          />
        </div>
        <div className="relative min-w-0 min-h-0">
          <div className="lg:absolute lg:inset-0">
            <ActivityLogCard
              logs={logs}
              isLoading={logsQuery.isLoading}
              activeDateLabel={selectedDateLabel}
              onClearDateFilter={() => setSelectedDate(null)}
            />
          </div>
        </div>
      </div>

      {box.notes ? (
        <div className="border-t pt-3 text-xs text-muted-foreground">
          <span className="font-medium">Notes:</span> {box.notes}
        </div>
      ) : null}
    </div>
  );
}
