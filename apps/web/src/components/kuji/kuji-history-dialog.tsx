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
import { KujiBoxStatus, type KujiBox } from "@/types/api";
import { KujiHistoryDetailDialog } from "./kuji-history-detail-dialog";

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
  const [detailBox, setDetailBox] = useState<KujiBox | null>(null);

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

  return (
    <>
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
                  onOpen={() => setDetailBox(box)}
                />
              ))
            )}
          </div>
        </DialogContent>
      </Dialog>

      <KujiHistoryDetailDialog
        open={!!detailBox}
        onOpenChange={(o) => {
          if (!o) setDetailBox(null);
        }}
        productId={productId}
        productName={productName}
        box={detailBox}
      />
    </>
  );
}

interface RowProps {
  readonly box: KujiBox;
  readonly onOpen: () => void;
}

function KujiHistoryRow({ box, onOpen }: RowProps) {
  const subtitle = [
    box.label ?? `Box ${box.id.slice(0, 8)}`,
    box.locationCode ?? box.locationName ?? null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div className="border-b transition-colors hover:bg-black/[0.03] dark:hover:bg-white/[0.03]">
      <button
        type="button"
        onClick={onOpen}
        className="flex w-full items-center gap-3 px-4 py-3 text-left"
      >
        <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" />
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
    </div>
  );
}
