"use client";

import { useMemo, useState } from "react";
import { ChevronRight, Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useKujiBoxHistory } from "@/hooks/queries/use-kuji-box";
import { useAuditLogs } from "@/hooks/queries/use-audit-log";
import {
  KujiBoxStatus,
  StockMovementReason,
  type AuditLog,
  type KujiBox,
} from "@/types/api";
import { cn } from "@/lib/utils";
import { ActivityLogCard } from "./activity-log-card";

interface KujiHistoryDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly productId: string;
  readonly productName: string;
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

function formatRange(openedAt: string, closedAt?: string | null): string {
  return `${formatStamp(openedAt)} → ${closedAt ? formatStamp(closedAt) : "—"}`;
}

export function KujiHistoryDialog({
  open,
  onOpenChange,
  productId,
  productName,
}: KujiHistoryDialogProps) {
  const { data, isLoading, error } = useKujiBoxHistory(
    open ? productId : null,
  );
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const closedBoxes = useMemo(() => {
    if (!data) return [];
    return data
      .filter((b) => b.status === KujiBoxStatus.CLOSED)
      .sort((a, b) => {
        const aT = a.closedAt ?? a.openedAt;
        const bT = b.closedAt ?? b.openedAt;
        return new Date(bT).getTime() - new Date(aT).getTime();
      });
  }, [data]);

  function toggle(id: string) {
    setExpandedId((prev) => (prev === id ? null : id));
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[calc(100%-2rem)] sm:max-w-2xl max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
        <DialogHeader className="p-6 pb-3 border-b">
          <DialogTitle className="truncate">
            {productName} — History
          </DialogTitle>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto">
          {isLoading ? (
            <div className="flex items-center justify-center py-10">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : error ? (
            <div className="px-6 py-6 text-sm text-destructive">
              Failed to load history:{" "}
              {error instanceof Error ? error.message : "Unknown error"}
            </div>
          ) : closedBoxes.length === 0 ? (
            <div className="py-10 text-center text-sm text-muted-foreground">
              No closed boxes yet.
            </div>
          ) : (
            closedBoxes.map((box) => (
              <KujiHistoryRow
                key={box.id}
                box={box}
                productId={productId}
                expanded={expandedId === box.id}
                onToggle={() => toggle(box.id)}
              />
            ))
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

interface RowProps {
  readonly box: KujiBox;
  readonly productId: string;
  readonly expanded: boolean;
  readonly onToggle: () => void;
}

function KujiHistoryRow({ box, productId, expanded, onToggle }: RowProps) {
  const subtitle = [
    box.label ?? `Box ${box.id.slice(0, 8)}`,
    box.locationCode ?? box.locationName ?? null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div
      className={cn(
        "border-b transition-colors",
        expanded
          ? "bg-black/[0.06] dark:bg-white/[0.06]"
          : "hover:bg-black/[0.03] dark:hover:bg-white/[0.03]",
      )}
    >
      <button
        type="button"
        onClick={onToggle}
        className="flex w-full items-center gap-3 px-4 py-3 text-left"
        aria-expanded={expanded}
      >
        <ChevronRight
          className={cn(
            "h-4 w-4 shrink-0 text-muted-foreground transition-transform duration-150",
            expanded && "rotate-90",
          )}
        />
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium tabular-nums">
            {formatRange(box.openedAt, box.closedAt)}
          </div>
          {subtitle ? (
            <div className="truncate text-xs text-muted-foreground">
              {subtitle}
            </div>
          ) : null}
        </div>
        <div className="shrink-0 text-xs tabular-nums text-muted-foreground">
          Closed with {box.totalCount.toLocaleString()}{" "}
          {box.totalCount === 1 ? "slip" : "slips"}
        </div>
      </button>

      {expanded ? (
        <div className="px-4 pb-4">
          <KujiHistoryRowDetail box={box} productId={productId} />
        </div>
      ) : null}
    </div>
  );
}

interface RowDetailProps {
  readonly box: KujiBox;
  readonly productId: string;
}

function KujiHistoryRowDetail({ box, productId }: RowDetailProps) {
  const fromDate = box.openedAt.slice(0, 10);
  const toDate = (box.closedAt ?? box.openedAt).slice(0, 10);
  const { data, isLoading } = useAuditLogs(
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

  // The server filters by LocalDate, so a same-day sibling box could overlap
  // this window. Trim client-side to the exact open/close timestamps.
  const logs = useMemo<AuditLog[]>(() => {
    const all = data?.content ?? [];
    const lo = box.openedAt;
    const hi = box.closedAt ?? new Date().toISOString();
    return all
      .filter((log) => log.createdAt >= lo && log.createdAt <= hi)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [data, box.openedAt, box.closedAt]);

  return <ActivityLogCard logs={logs} isLoading={isLoading} />;
}
