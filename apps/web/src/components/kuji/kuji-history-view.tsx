"use client";

import { useMemo, useState } from "react";
import { ChevronRight, Loader2 } from "lucide-react";
import { useKujiBoxHistory } from "@/hooks/queries/use-kuji-box";
import type { KujiBox } from "@/types/api";
import { KujiBoxStatus } from "@/types/api";
import { KujiHistoryDetailDialog } from "./kuji-history-detail-dialog";

interface KujiHistoryViewProps {
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

export function KujiHistoryView({ productId, productName }: KujiHistoryViewProps) {
  const { data, isLoading, error } = useKujiBoxHistory(productId);
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

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-sm text-destructive py-4">
        Failed to load history:{" "}
        {error instanceof Error ? error.message : "Unknown error"}
      </div>
    );
  }

  if (closedBoxes.length === 0) {
    return (
      <div className="text-sm text-muted-foreground py-8 text-center">
        No closed boxes yet.
      </div>
    );
  }

  return (
    <>
      <div className="rounded-xl border bg-card overflow-hidden dark:border-none">
        {closedBoxes.map((box) => (
          <KujiHistoryRow
            key={box.id}
            box={box}
            onOpen={() => setDetailBox(box)}
          />
        ))}
      </div>

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
    <div className="border-b last:border-b-0 transition-colors hover:bg-black/[0.03] dark:hover:bg-white/[0.03]">
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
