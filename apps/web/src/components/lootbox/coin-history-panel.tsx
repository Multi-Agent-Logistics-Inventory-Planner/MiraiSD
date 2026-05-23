"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import type { CoinHistoryEntry } from "@/types/lootbox";

interface CoinHistoryPanelProps {
  history: CoinHistoryEntry[] | undefined;
  isLoading: boolean;
}

function formatDate(value: string) {
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function kindLabel(kind: CoinHistoryEntry["kind"]) {
  switch (kind) {
    case "REVIEW_CREDIT":
      return "Reviews";
    case "PLAY":
      return "Lootbox";
    case "ADJUSTMENT":
      return "Admin";
  }
}

export function CoinHistoryPanel({ history, isLoading }: CoinHistoryPanelProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Coin history</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : !history || history.length === 0 ? (
          <p className="text-sm text-muted-foreground">No coin activity yet.</p>
        ) : (
          <ul className="divide-y max-h-80 overflow-y-auto">
            {history.map((entry, idx) => (
              <li
                key={`${entry.kind}-${entry.refId ?? idx}-${entry.at}`}
                className="py-2 flex items-center justify-between gap-3"
              >
                <div className="min-w-0 space-y-0.5">
                  <div
                    className={cn(
                      "text-sm truncate",
                      entry.expired ? "line-through text-muted-foreground" : undefined
                    )}
                  >
                    {entry.label}
                  </div>
                  <div className="text-xs text-muted-foreground">
                    {kindLabel(entry.kind)} • {formatDate(entry.at)}
                    {entry.expired ? (
                      <span className="ml-1 text-rose-500/80">· expired</span>
                    ) : entry.expiresAt ? (
                      <span className="ml-1">· expires {formatDate(entry.expiresAt)}</span>
                    ) : null}
                  </div>
                </div>
                <div
                  className={cn(
                    "text-sm font-medium tabular-nums",
                    entry.expired
                      ? "text-muted-foreground line-through"
                      : entry.delta > 0
                        ? "text-emerald-600"
                        : "text-rose-600"
                  )}
                >
                  {entry.delta > 0 ? `+${entry.delta}` : entry.delta}
                </div>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
