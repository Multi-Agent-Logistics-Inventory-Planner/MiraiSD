"use client";

import { useState } from "react";
import {
  CheckCircle2,
  Loader2,
  PauseCircle,
  Pencil,
  PlayCircle,
  Plus,
  Trash2,
  Undo2,
  X,
} from "lucide-react";
import { StockMovementReason, type AuditLog } from "@/types/api";

interface ActivityLogCardProps {
  readonly logs: readonly AuditLog[];
  readonly isLoading: boolean;
  readonly activeDateLabel?: string | null;
  readonly onClearDateFilter?: () => void;
}

function formatTime(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleDateString([], {
    month: "short",
    day: "numeric",
  });
}

type SlipAction =
  | "add_slip"
  | "add_inactive"
  | "stash"
  | "promote"
  | "activate"
  | "deactivate"
  | "delete_active"
  | "delete_inactive"
  | "tier_deleted"
  | "tier_edited"
  | "unknown";

function classifySlipAdjustment(notes?: string): SlipAction {
  if (!notes) return "unknown";
  if (notes.startsWith("Kuji prize tier deleted")) return "tier_deleted";
  if (notes.startsWith("Kuji tier edited")) return "tier_edited";
  if (notes.startsWith("Kuji prize stashed")) return "stash";
  if (notes.startsWith("Kuji slip promoted")) return "promote";
  if (notes.startsWith("Kuji slips activated")) return "activate";
  if (notes.startsWith("Kuji slips deactivated")) return "deactivate";
  if (notes.startsWith("Kuji prize deleted from active")) return "delete_active";
  if (notes.startsWith("Kuji prize deleted from inactive")) return "delete_inactive";
  if (notes.startsWith("Kuji slip added (inactive)")) return "add_inactive";
  if (notes.startsWith("Kuji slip added")) return "add_slip";
  return "unknown";
}

/** Pulls the tier label (and rest of the message) out of a kuji-adjustment note. */
function extractAdjustmentDetail(action: SlipAction, notes?: string): string | null {
  if (!notes) return null;
  if (action === "tier_edited") {
    // "Kuji tier edited: <label> — <change1>, <change2>"
    const dash = notes.indexOf(" — ");
    const colon = notes.indexOf(": ");
    if (dash === -1) return null;
    const label = colon === -1 ? "" : notes.slice(colon + 2, dash);
    const changes = notes.slice(dash + 3).split(", ");
    return [label, ...changes].filter(Boolean).join("\n");
  }
  if (action === "tier_deleted") {
    // "Kuji prize tier deleted: <label> (active N, inactive M)"
    const colon = notes.indexOf(": ");
    if (colon === -1) return null;
    const rest = notes.slice(colon + 2);
    const parenOpen = rest.indexOf(" (");
    if (parenOpen === -1) return rest;
    const label = rest.slice(0, parenOpen);
    const counts = rest.slice(parenOpen + 2, -1); // "active N, inactive M"
    return [label, counts].join("\n");
  }
  const colon = notes.indexOf(": ");
  return colon === -1 ? null : notes.slice(colon + 2);
}

function formatSlipAdjustment(action: SlipAction, qty: number): string {
  const slipWord = qty === 1 ? "slip" : "slips";
  const prizeWord = qty === 1 ? "prize" : "prizes";
  switch (action) {
    case "stash":
      return `${qty} ${prizeWord} stashed`;
    case "promote":
      return `${qty} ${slipWord} promoted from stash`;
    case "add_slip":
      return `${qty} ${slipWord} added`;
    case "add_inactive":
      return `${qty} ${slipWord} added to inactive`;
    case "activate":
      return `${qty} ${slipWord} activated`;
    case "deactivate":
      return `${qty} ${slipWord} deactivated`;
    case "delete_active":
      return `${qty} ${prizeWord} deleted from active`;
    case "delete_inactive":
      return `${qty} ${prizeWord} deleted from inactive`;
    case "tier_edited":
      return `Tier edited`;
    case "tier_deleted":
      return `Tier deleted`;
    default:
      return `${qty} ${slipWord} adjusted`;
  }
}

export function ActivityLogCard({
  logs,
  isLoading,
  activeDateLabel,
  onClearDateFilter,
}: ActivityLogCardProps) {
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set());

  const toggle = (id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const isFiltered = Boolean(activeDateLabel);

  return (
    <div className="flex h-full flex-col rounded-xl border bg-card p-4 dark:border-none">
      <div className="mb-3 flex items-center justify-between gap-2 border-b pb-2.5">
        <span className="flex items-center gap-2 text-sm font-medium">
          <CheckCircle2
            className="h-4 w-4 text-muted-foreground"
            aria-hidden
          />
          Activity log
        </span>
        <div className="flex items-center gap-2">
          {isFiltered && onClearDateFilter ? (
            <button
              type="button"
              onClick={onClearDateFilter}
              className="flex items-center gap-1 rounded-full bg-brand-primary/15 px-2 py-0.5 text-[11px] text-brand-primary hover:bg-brand-primary/25"
              aria-label={`Clear date filter for ${activeDateLabel}`}
            >
              <span className="tabular-nums">{activeDateLabel}</span>
              <X className="h-3 w-3" aria-hidden />
            </button>
          ) : null}
          <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] text-muted-foreground tabular-nums">
            {logs.length} {logs.length === 1 ? "event" : "events"}
          </span>
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-6 text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
        </div>
      ) : logs.length === 0 ? (
        <div className="py-6 text-center text-xs text-muted-foreground">
          {isFiltered ? "No activity on this day." : "No draws recorded yet."}
        </div>
      ) : (
        <div className="min-h-0 max-h-80 flex-1 overflow-y-auto scrollbar-none pr-1 lg:max-h-none">
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
            } else if (slipAction === "stash" || slipAction === "deactivate") {
              icon = (
                <PauseCircle className="mt-1 h-3 w-3 shrink-0 text-amber-600" aria-hidden />
              );
            } else if (slipAction === "promote" || slipAction === "activate") {
              icon = (
                <PlayCircle className="mt-1 h-3 w-3 shrink-0 text-blue-600" aria-hidden />
              );
            } else if (slipAction === "tier_edited") {
              icon = (
                <Pencil className="mt-1 h-3 w-3 shrink-0 text-muted-foreground" aria-hidden />
              );
            } else if (
              slipAction === "delete_active"
              || slipAction === "delete_inactive"
              || slipAction === "tier_deleted"
            ) {
              icon = (
                <Trash2 className="mt-1 h-3 w-3 shrink-0 text-rose-600" aria-hidden />
              );
            } else if (slipAction === "add_slip" || slipAction === "add_inactive") {
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

            // For adjustments, surface the tier label and (for edits) the change-list.
            // For draws/undos `productSummary` carries the tier list.
            const detail = isAdjustment
              ? extractAdjustmentDetail(slipAction, log.notes)
              : log.productSummary || null;

            const isExpanded = expanded.has(log.id);
            const hasDetail = Boolean(detail);

            return (
              <li key={log.id}>
                <button
                  type="button"
                  onClick={() => hasDetail && toggle(log.id)}
                  aria-expanded={hasDetail ? isExpanded : undefined}
                  disabled={!hasDetail}
                  className={`flex w-full items-start gap-3 py-2.5 text-left ${
                    hasDetail ? "cursor-pointer hover:bg-muted/40" : "cursor-default"
                  }`}
                >
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
                    {hasDetail && isExpanded ? (
                      <ul className="mt-1 ml-1 space-y-0.5 text-[11px] text-muted-foreground">
                        {detail!.split("\n").map((line, i) => (
                          <li key={i} className="flex gap-1.5 break-words">
                            <span aria-hidden>•</span>
                            <span>{line}</span>
                          </li>
                        ))}
                      </ul>
                    ) : null}
                  </div>
                  <span className="flex shrink-0 flex-col items-end pt-0.5 text-[11px] text-muted-foreground tabular-nums leading-tight">
                    <span>{formatDate(log.createdAt)}</span>
                    <span>{formatTime(log.createdAt)}</span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
        </div>
      )}
    </div>
  );
}
