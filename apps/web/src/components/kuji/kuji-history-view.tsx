"use client";

import { useMemo, useState } from "react";
import { ChevronDown, ChevronRight, Loader2 } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { useKujiBoxHistory } from "@/hooks/queries/use-kuji-box";
import type { KujiBox } from "@/types/api";
import { KujiBoxStatus } from "@/types/api";
import { compareTiers } from "./tier-palette";

interface KujiHistoryViewProps {
  readonly productId: string;
}

function formatDate(value?: string | null): string {
  if (!value) return "—";
  return new Date(value).toLocaleString("en-US", {
    month: "2-digit",
    day: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function KujiHistoryView({ productId }: KujiHistoryViewProps) {
  const { data, isLoading, error } = useKujiBoxHistory(productId);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

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
    setExpanded((prev) => ({ ...prev, [id]: !prev[id] }));
  }

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
    <div className="space-y-3">
      {closedBoxes.map((box) => (
        <KujiHistoryRow
          key={box.id}
          box={box}
          expanded={!!expanded[box.id]}
          onToggle={() => toggle(box.id)}
        />
      ))}
    </div>
  );
}

interface RowProps {
  readonly box: KujiBox;
  readonly expanded: boolean;
  readonly onToggle: () => void;
}

function KujiHistoryRow({ box, expanded, onToggle }: RowProps) {
  return (
    <div className="border rounded-md">
      <button
        type="button"
        onClick={onToggle}
        className="w-full flex items-center justify-between gap-3 p-3 text-left hover:bg-muted/40 transition-colors"
      >
        <div className="flex items-center gap-2 min-w-0">
          {expanded ? (
            <ChevronDown className="h-4 w-4 shrink-0" />
          ) : (
            <ChevronRight className="h-4 w-4 shrink-0" />
          )}
          <div className="min-w-0">
            <div className="font-medium truncate">
              {box.label ?? `Box ${box.id.slice(0, 8)}`}
            </div>
            <div className="text-xs text-muted-foreground">
              {formatDate(box.openedAt)} → {formatDate(box.closedAt)}
            </div>
          </div>
        </div>
        <div className="text-xs text-muted-foreground shrink-0 text-right">
          <div>{box.totalCount.toLocaleString()} drawn / total</div>
          <div className="text-[10px]">
            {box.locationCode ?? box.locationName ?? "—"}
          </div>
        </div>
      </button>

      {expanded ? (
        <div className="border-t px-3 py-3 space-y-2 bg-muted/20">
          <div className="grid gap-1 text-xs sm:grid-cols-2">
            <div>
              <span className="text-muted-foreground">Opened by:</span>{" "}
              {box.openedByName ?? box.openedBy ?? "—"}
            </div>
            <div>
              <span className="text-muted-foreground">Closed by:</span>{" "}
              {box.closedByName ?? box.closedBy ?? "—"}
            </div>
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-16">Letter</TableHead>
                <TableHead>Label</TableHead>
                <TableHead className="text-right w-24">Final Count</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {[...box.tiers]
                .sort(compareTiers)
                .map((tier) => (
                  <TableRow key={tier.id}>
                    <TableCell className="font-mono">
                      {tier.letter ?? "—"}
                    </TableCell>
                    <TableCell>{tier.label}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {tier.count.toLocaleString()}
                    </TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>

          {box.notes ? (
            <div className="text-xs text-muted-foreground border-t pt-2">
              <span className="font-medium">Notes:</span> {box.notes}
            </div>
          ) : null}

          <div className="pt-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled
              title="Reopen action available from the Active Box tab when this box is the most recent."
            >
              Reopen
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
