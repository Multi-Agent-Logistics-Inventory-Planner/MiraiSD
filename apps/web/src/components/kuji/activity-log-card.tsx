"use client";

import { ArrowUpFromLine, CheckCircle2, Loader2, PackagePlus, Plus, Undo2 } from "lucide-react";
import { StockMovementReason, type AuditLog } from "@/types/api";

interface ActivityLogCardProps {
  readonly logs: readonly AuditLog[];
  readonly isLoading: boolean;
}

function formatTime(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

type SlipAction = "add_slip" | "stash" | "promote" | "unknown";

function classifySlipAdjustment(notes?: string): SlipAction {
  if (!notes) return "unknown";
  if (notes.startsWith("Kuji prize stashed")) return "stash";
  if (notes.startsWith("Kuji slip promoted")) return "promote";
  if (notes.startsWith("Kuji slip added")) return "add_slip";
  return "unknown";
}

function formatSlipAdjustment(action: SlipAction, qty: number): string {
  const slipWord = qty === 1 ? "slip" : "slips";
  switch (action) {
    case "stash":
      return `${qty} ${qty === 1 ? "prize" : "prizes"} stashed`;
    case "promote":
      return `${qty} ${slipWord} promoted from stash`;
    case "add_slip":
      return `${qty} ${slipWord} added`;
    default:
      return `${qty} ${slipWord} adjusted`;
  }
}

export function ActivityLogCard({ logs, isLoading }: ActivityLogCardProps) {
  return (
    <div className="rounded-xl border bg-card p-4 dark:border-none">
      <div className="mb-3 flex items-center justify-between border-b pb-2.5">
        <span className="flex items-center gap-2 text-sm font-medium">
          <CheckCircle2
            className="h-4 w-4 text-muted-foreground"
            aria-hidden
          />
          Activity log
        </span>
        <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] text-muted-foreground tabular-nums">
          {logs.length} {logs.length === 1 ? "event" : "events"}
        </span>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-6 text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
        </div>
      ) : logs.length === 0 ? (
        <div className="py-6 text-center text-xs text-muted-foreground">
          No draws recorded yet.
        </div>
      ) : (
        <ul className="divide-y">
          {logs.map((log) => {
            const qty = log.totalQuantityMoved ?? 0;
            const isUndo = log.reason === StockMovementReason.KUJI_DRAW_REVERSED;
            const isAdjustment = log.reason === StockMovementReason.KUJI_SLIP_ADJUSTMENT;
            const slipAction = isAdjustment ? classifySlipAdjustment(log.notes) : "unknown";

            let icon;
            if (isUndo) {
              icon = (
                <Undo2 className="mt-1 h-3 w-3 shrink-0 text-muted-foreground" aria-hidden />
              );
            } else if (slipAction === "stash") {
              icon = (
                <PackagePlus className="mt-1 h-3 w-3 shrink-0 text-amber-600" aria-hidden />
              );
            } else if (slipAction === "promote") {
              icon = (
                <ArrowUpFromLine className="mt-1 h-3 w-3 shrink-0 text-blue-600" aria-hidden />
              );
            } else if (slipAction === "add_slip") {
              icon = (
                <Plus className="mt-1 h-3 w-3 shrink-0 text-muted-foreground" aria-hidden />
              );
            } else {
              icon = (
                <span
                  className="mt-1.5 inline-block h-2 w-2 shrink-0 rounded-full bg-brand-primary"
                  aria-hidden
                />
              );
            }

            const label = isAdjustment
              ? formatSlipAdjustment(slipAction, qty)
              : `${qty} ${qty === 1 ? "slip" : "slips"} ${isUndo ? "returned" : "drawn"}`;

            // For adjustments the tier label lives in `notes` ("Kuji slip added: <label>");
            // for draws/undos `productSummary` carries the tier list.
            const detail = isAdjustment
              ? log.notes?.split(": ").slice(1).join(": ") || null
              : log.productSummary || null;

            return (
              <li key={log.id} className="flex items-start gap-3 py-2.5">
                {icon}
                <div className="min-w-0 flex-1">
                  <div className={`text-xs ${log.reversed ? "line-through text-muted-foreground" : ""}`}>
                    {label}
                    {log.actorName ? (
                      <span className="text-muted-foreground">
                        {" "}
                        · {log.actorName}
                      </span>
                    ) : null}
                  </div>
                  {detail ? (
                    <div className="mt-0.5 truncate text-[11px] text-muted-foreground">
                      {detail}
                    </div>
                  ) : null}
                </div>
                <span className="shrink-0 pt-0.5 text-[11px] text-muted-foreground tabular-nums">
                  {formatTime(log.createdAt)}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
