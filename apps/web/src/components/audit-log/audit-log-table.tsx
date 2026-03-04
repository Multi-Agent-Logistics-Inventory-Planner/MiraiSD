"use client";

import { useState } from "react";
import { format } from "date-fns";
import { ChevronRight, Loader2 } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import {
  AuditLog,
  AuditLogMovement,
  StockMovementReason,
  PaginatedResponse,
} from "@/types/api";
import { useAuditLogDetail } from "@/hooks/queries/use-audit-log";
import { cn } from "@/lib/utils";

const REASON_LABELS: Record<StockMovementReason, string> = {
  [StockMovementReason.INITIAL_STOCK]: "Initial Stock",
  [StockMovementReason.RESTOCK]: "Restock",
  [StockMovementReason.SALE]: "Sale",
  [StockMovementReason.DAMAGE]: "Damage",
  [StockMovementReason.ADJUSTMENT]: "Adjustment",
  [StockMovementReason.RETURN]: "Return",
  [StockMovementReason.TRANSFER]: "Transfer",
};

function getReasonStyle(reason: StockMovementReason): string {
  switch (reason) {
    case StockMovementReason.RESTOCK:
    case StockMovementReason.INITIAL_STOCK:
      return "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400";
    case StockMovementReason.TRANSFER:
      return "bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-400";
    case StockMovementReason.SALE:
      return "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400";
    case StockMovementReason.DAMAGE:
      return "bg-red-50 text-red-700 dark:bg-red-950/60 dark:text-red-400";
    case StockMovementReason.RETURN:
      return "bg-purple-50 text-purple-700 dark:bg-purple-950/60 dark:text-purple-400";
    case StockMovementReason.ADJUSTMENT:
    default:
      return "bg-amber-50 text-amber-700 dark:bg-amber-950/60 dark:text-amber-400";
  }
}

function formatLocation(entry: AuditLog): string {
  if (entry.reason === StockMovementReason.TRANSFER) {
    const from = entry.primaryFromLocationCode ?? "NA";
    const to = entry.primaryToLocationCode ?? "NA";
    return `${from} → ${to}`;
  }
  return entry.primaryToLocationCode ?? entry.primaryFromLocationCode ?? "—";
}

function QuantityChange({ change }: { change: number }) {
  if (change > 0)
    return <span className="text-emerald-600 font-medium tabular-nums">+{change}</span>;
  if (change < 0)
    return <span className="text-red-500 font-medium tabular-nums">{change}</span>;
  return <span className="text-muted-foreground tabular-nums">0</span>;
}

/**
 * For transfers, uses AuditLog-level location codes (correctly stored at write-time)
 * rather than per-movement codes which can be wrong due to a locationType mismatch.
 * Withdrawal rows show the FROM location; deposit rows show the TO location.
 */
function movementLocationLabel(
  movement: AuditLogMovement,
  reason: StockMovementReason,
  primaryFrom?: string,
  primaryTo?: string
): string {
  if (reason === StockMovementReason.TRANSFER) {
    return movement.quantityChange < 0
      ? (primaryFrom ?? "NA")
      : (primaryTo ?? "NA");
  }
  return movement.toLocationCode ?? movement.fromLocationCode ?? "—";
}

function ActionPill({ reason }: { reason: StockMovementReason }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap",
        getReasonStyle(reason)
      )}
    >
      {REASON_LABELS[reason]}
    </span>
  );
}

function ExpandedDetail({ auditLogId }: { auditLogId: string }) {
  const { data: detail, isLoading } = useAuditLogDetail(auditLogId);

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 px-4 md:px-12 py-5">
        <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
        <span className="text-sm text-muted-foreground">Loading details…</span>
      </div>
    );
  }

  if (!detail) return null;

  return (
    <div className="px-4 md:px-12 pb-4 pt-1">
      {detail.notes && (
        <p className="text-sm text-muted-foreground mb-3 italic">"{detail.notes}"</p>
      )}
      <div className="space-y-1">
        {/* Sub-header */}
        <div className="grid grid-cols-[1fr_auto_auto] gap-x-4 md:gap-x-8 px-3 pb-1 text-xs font-medium text-muted-foreground uppercase tracking-wide">
          <span>Product</span>
          <span className="text-right w-14 md:w-28">Location</span>
          <span className="text-right w-16">Change</span>
        </div>

        {detail.movements.map((movement) => (
          <div
            key={movement.id}
            className="grid grid-cols-[1fr_auto_auto] gap-x-4 md:gap-x-8 px-3 py-2 rounded-md hover:bg-black/5 dark:hover:bg-white/5 transition-colors"
          >
            <div>
              <p className="text-sm font-medium leading-tight">{movement.itemName}</p>
              <p className="text-xs text-muted-foreground">{movement.itemSku}</p>
            </div>
            <span className="text-sm text-muted-foreground w-14 md:w-28 text-right self-center tabular-nums">
              {movementLocationLabel(
                movement,
                detail.reason,
                detail.primaryFromLocationCode,
                detail.primaryToLocationCode
              )}
            </span>
            <div className="w-16 text-right self-center">
              <QuantityChange change={movement.quantityChange} />
              <p className="text-xs text-muted-foreground tabular-nums">
                {movement.previousQuantity ?? 0} → {movement.currentQuantity ?? 0}
              </p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function RowSkeleton() {
  return (
    <div className="border-b px-4 py-4">
      {/* Mobile skeleton */}
      <div className="flex items-start gap-3 md:hidden">
        <Skeleton className="h-4 w-4 mt-0.5 shrink-0" />
        <div className="flex-1 space-y-2">
          <div className="flex items-center justify-between gap-2">
            <Skeleton className="h-3.5 w-24" />
            <Skeleton className="h-5 w-16 rounded-full" />
          </div>
          <Skeleton className="h-3 w-32" />
          <Skeleton className="h-3 w-20" />
        </div>
      </div>
      {/* Desktop skeleton */}
      <div className="hidden md:grid grid-cols-[2rem_1fr_8rem_1fr_7rem_9rem] gap-x-6 items-center">
        <Skeleton className="h-4 w-4 rounded" />
        <div className="space-y-1.5">
          <Skeleton className="h-3.5 w-28" />
          <Skeleton className="h-3 w-20" />
        </div>
        <Skeleton className="h-6 w-20 rounded-full" />
        <Skeleton className="h-3.5 w-32" />
        <Skeleton className="h-3.5 w-16" />
        <Skeleton className="h-3.5 w-28" />
      </div>
    </div>
  );
}

export interface AuditLogTableProps {
  data?: PaginatedResponse<AuditLog>;
  isLoading: boolean;
  page: number;
  onPageChange: (page: number) => void;
}

export function AuditLogTable({ data, isLoading }: AuditLogTableProps) {
  const entries = data?.content ?? [];
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const toggleRow = (id: string) =>
    setExpandedId((prev) => (prev === id ? null : id));

  return (
    <div className="w-full">
      {/* Desktop-only table header */}
      <div className="hidden md:grid grid-cols-[2rem_1fr_8rem_1fr_7rem_9rem] gap-x-6 items-center px-4 py-2.5 border-b text-xs font-medium text-muted-foreground uppercase tracking-wide select-none">
        <div />
        <div>User</div>
        <div>Action</div>
        <div>Product</div>
        <div>Location</div>
        <div>Time</div>
      </div>

      {isLoading ? (
        Array.from({ length: 10 }).map((_, i) => <RowSkeleton key={i} />)
      ) : entries.length === 0 ? (
        <div className="flex items-center justify-center h-40 text-sm text-muted-foreground">
          No audit log entries found.
        </div>
      ) : (
        entries.map((entry) => {
          const isExpanded = expandedId === entry.id;
          return (
            // Outer wrapper owns the border-b and the shared background.
            // This makes the expanded detail feel like part of the same row —
            // no divider between the header and the detail, only at the very bottom.
            <div
              key={entry.id}
              className={cn(
                "border-b transition-colors",
                isExpanded ? "bg-muted/20" : "hover:bg-muted/10"
              )}
            >
              {/* ── Mobile row ── */}
              <div
                className="flex items-start gap-3 px-4 py-4 cursor-pointer select-none md:hidden"
                onClick={() => toggleRow(entry.id)}
              >
                <ChevronRight
                  className={cn(
                    "h-4 w-4 mt-0.5 shrink-0 text-muted-foreground transition-transform duration-150",
                    isExpanded && "rotate-90"
                  )}
                />
                <div className="flex-1 min-w-0 space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-medium truncate">
                      {entry.actorName ?? "—"}
                    </p>
                    <ActionPill reason={entry.reason} />
                  </div>
                  <p className="text-sm text-muted-foreground truncate">
                    {entry.productSummary ?? "—"}
                  </p>
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-xs text-muted-foreground">
                      {formatLocation(entry)}
                    </p>
                    <p className="text-xs text-muted-foreground tabular-nums whitespace-nowrap">
                      {format(new Date(entry.createdAt), "MM-dd-yyyy h:mm a")}
                    </p>
                  </div>
                </div>
              </div>

              {/* ── Desktop row ── */}
              <div
                className="hidden md:grid grid-cols-[2rem_1fr_8rem_1fr_7rem_9rem] gap-x-6 items-center px-4 py-4 cursor-pointer select-none"
                onClick={() => toggleRow(entry.id)}
              >
                <ChevronRight
                  className={cn(
                    "h-4 w-4 text-muted-foreground transition-transform duration-150",
                    isExpanded && "rotate-90"
                  )}
                />

                {/* User */}
                <div className="min-w-0">
                  <p className="text-sm font-medium leading-tight truncate">
                    {entry.actorName ?? "—"}
                  </p>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {format(new Date(entry.createdAt), "MMM d, yyyy")}
                  </p>
                </div>

                {/* Action */}
                <div>
                  <ActionPill reason={entry.reason} />
                </div>

                {/* Product */}
                <div className="min-w-0">
                  <p className="text-sm font-medium truncate">
                    {entry.productSummary ?? "—"}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {entry.itemCount > 1
                      ? `${entry.itemCount} items · ${entry.totalQuantityMoved} units`
                      : `${entry.totalQuantityMoved} units`}
                  </p>
                </div>

                {/* Location */}
                <p className="text-sm text-muted-foreground truncate">
                  {formatLocation(entry)}
                </p>

                {/* Time */}
                <p className="text-sm text-muted-foreground whitespace-nowrap tabular-nums">
                  {format(new Date(entry.createdAt), "MM-dd-yyyy h:mm a")}
                </p>
              </div>

              {/* Expanded detail — no border or background needed, inherits from wrapper */}
              {isExpanded && <ExpandedDetail auditLogId={entry.id} />}
            </div>
          );
        })
      )}
    </div>
  );
}
