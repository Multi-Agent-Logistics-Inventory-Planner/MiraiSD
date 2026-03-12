"use client";

import { useState } from "react";
import { format } from "date-fns";
import { ChevronRight, Loader2, ArrowRight, Minus, Plus } from "lucide-react";
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
  [StockMovementReason.SHIPMENT_RECEIPT]: "Shipment",
  [StockMovementReason.SHIPMENT_RECEIPT_REVERSED]: "Shipment Reversal",
  [StockMovementReason.SALE]: "Sale",
  [StockMovementReason.DAMAGE]: "Damage",
  [StockMovementReason.ADJUSTMENT]: "Adjustment",
  [StockMovementReason.RETURN]: "Return",
  [StockMovementReason.TRANSFER]: "Transfer",
  [StockMovementReason.DISPLAY_SET]: "Display Set",
  [StockMovementReason.DISPLAY_REMOVED]: "Display Removed",
  [StockMovementReason.DISPLAY_SWAP]: "Display Swap",
};

const DISPLAY_REASONS = new Set<StockMovementReason>([
  StockMovementReason.DISPLAY_SET,
  StockMovementReason.DISPLAY_REMOVED,
  StockMovementReason.DISPLAY_SWAP,
]);

function getReasonStyle(reason: StockMovementReason): string {
  switch (reason) {
    case StockMovementReason.RESTOCK:
    case StockMovementReason.INITIAL_STOCK:
    case StockMovementReason.SHIPMENT_RECEIPT:
      return "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-400";
    case StockMovementReason.TRANSFER:
      return "bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-400";
    case StockMovementReason.SALE:
      return "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400";
    case StockMovementReason.DAMAGE:
    case StockMovementReason.SHIPMENT_RECEIPT_REVERSED:
      return "bg-red-50 text-red-700 dark:bg-red-950/60 dark:text-red-400";
    case StockMovementReason.RETURN:
      return "bg-purple-50 text-purple-700 dark:bg-purple-950/60 dark:text-purple-400";
    case StockMovementReason.DISPLAY_SET:
    case StockMovementReason.DISPLAY_REMOVED:
    case StockMovementReason.DISPLAY_SWAP:
      return "bg-violet-50 text-violet-700 dark:bg-violet-950/60 dark:text-violet-400";
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
  if (DISPLAY_REASONS.has(entry.reason)) {
    return entry.primaryFromLocationCode ?? entry.primaryToLocationCode ?? "NA";
  }
  return entry.primaryToLocationCode ?? entry.primaryFromLocationCode ?? "NA";
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
  return movement.toLocationCode ?? movement.fromLocationCode ?? "NA";
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

  const isDisplayOperation = DISPLAY_REASONS.has(detail.reason);

  return (
    <div className="px-4 md:px-12 pb-4 pt-1">
      {/* Show notes if present */}
      {detail.notes && (
        <p className="text-sm text-muted-foreground mb-3 italic">"{detail.notes}"</p>
      )}

      {/* Display operations - show movements with from/to and action indicators */}
      {isDisplayOperation && detail.movements.length > 0 ? (
        <div className="space-y-1">
          {/* Sub-header */}
          <div className="grid grid-cols-[1fr_10rem_4rem] gap-x-4 md:gap-x-8 px-3 pb-1 text-xs font-medium text-muted-foreground uppercase tracking-wide">
            <span>Product</span>
            <span className="text-center">Location</span>
            <span className="text-center">Action</span>
          </div>

          {detail.movements.map((movement) => {
            const isRemoved = movement.fromLocationCode && !movement.toLocationCode;
            const isAdded = !movement.fromLocationCode && movement.toLocationCode;
            const isTransfer = movement.fromLocationCode && movement.toLocationCode;

            return (
              <div
                key={movement.id}
                className="grid grid-cols-[1fr_10rem_4rem] gap-x-4 md:gap-x-8 px-3 py-2 rounded-md hover:bg-black/5 dark:hover:bg-white/5 transition-colors"
              >
                {/* Product */}
                <div className="flex items-center gap-2.5 min-w-0">
                  {movement.itemImageUrl ? (
                    <img
                      src={movement.itemImageUrl}
                      alt={movement.itemName}
                      className="h-8 w-8 rounded-md object-cover shrink-0 border border-border/50"
                    />
                  ) : (
                    <div className="h-8 w-8 rounded-md bg-muted shrink-0 flex items-center justify-center border border-border/50">
                      <span className="text-[10px] font-medium text-muted-foreground select-none">
                        {movement.itemName.charAt(0).toUpperCase()}
                      </span>
                    </div>
                  )}
                  <div className="min-w-0">
                    <p className="text-sm font-medium leading-tight truncate">{movement.itemName}</p>
                  </div>
                </div>

                {/* Location (from → to for transfers, single location for add/remove) */}
                <div className="flex items-center justify-center gap-1.5 text-sm text-muted-foreground">
                  {isRemoved && (
                    <span className="tabular-nums">{movement.fromLocationCode}</span>
                  )}
                  {isAdded && (
                    <span className="tabular-nums">{movement.toLocationCode}</span>
                  )}
                  {isTransfer && (
                    <>
                      <span className="tabular-nums">{movement.fromLocationCode}</span>
                      <ArrowRight className="h-3.5 w-3.5 shrink-0" />
                      <span className="tabular-nums">{movement.toLocationCode}</span>
                    </>
                  )}
                </div>

                {/* Action indicator */}
                <div className="flex items-center justify-center">
                  {isRemoved && (
                    <span className="inline-flex items-center gap-1 text-red-500 font-medium text-sm">
                      <Minus className="h-3.5 w-3.5" />
                    </span>
                  )}
                  {isAdded && (
                    <span className="inline-flex items-center gap-1 text-emerald-600 font-medium text-sm">
                      <Plus className="h-3.5 w-3.5" />
                    </span>
                  )}
                  {isTransfer && (
                    <span className="inline-flex items-center gap-1 text-blue-500 font-medium text-sm">
                      <ArrowRight className="h-3.5 w-3.5" />
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      ) : isDisplayOperation ? (
        /* Fallback for display operations without movements (legacy data) */
        <div className="flex items-center gap-2 px-3 py-2 text-sm text-muted-foreground">
          <span className="font-medium text-foreground">{detail.productSummary ?? "—"}</span>
          <span>·</span>
          <span>{REASON_LABELS[detail.reason]} on {detail.primaryFromLocationCode ?? detail.primaryToLocationCode ?? "NA"}</span>
        </div>
      ) : (
      /* Stock movements (transfers, adjustments, etc.) */
      <div className="space-y-1">
        {/* Sub-header */}
        <div className="grid grid-cols-[1fr_7rem_5rem] gap-x-4 md:gap-x-8 px-3 pb-1 text-xs font-medium text-muted-foreground uppercase tracking-wide">
          <span>Product</span>
          <span className="text-center">Location</span>
          <span className="text-center">Change</span>
        </div>

        {detail.movements.map((movement) => (
          <div
            key={movement.id}
            className="grid grid-cols-[1fr_7rem_5rem] gap-x-4 md:gap-x-8 px-3 py-2 rounded-md hover:bg-black/5 dark:hover:bg-white/5 transition-colors"
          >
            <div className="flex items-center gap-2.5 min-w-0">
              {movement.itemImageUrl ? (
                <img
                  src={movement.itemImageUrl}
                  alt={movement.itemName}
                  className="h-8 w-8 rounded-md object-cover shrink-0 border border-border/50"
                />
              ) : (
                <div className="h-8 w-8 rounded-md bg-muted shrink-0 flex items-center justify-center border border-border/50">
                  <span className="text-[10px] font-medium text-muted-foreground select-none">
                    {movement.itemName.charAt(0).toUpperCase()}
                  </span>
                </div>
              )}
              <div className="min-w-0">
                <p className="text-sm font-medium leading-tight truncate">{movement.itemName}</p>
              </div>
            </div>
            <span className="text-sm text-muted-foreground text-center self-center tabular-nums">
              {movementLocationLabel(
                movement,
                detail.reason,
                detail.primaryFromLocationCode,
                detail.primaryToLocationCode
              )}
            </span>
            <div className="text-center self-center">
              <QuantityChange change={movement.quantityChange} />
              <p className="text-xs text-muted-foreground tabular-nums">
                {movement.previousQuantity ?? 0} → {movement.currentQuantity ?? 0}
              </p>
            </div>
          </div>
        ))}
      </div>
      )}
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
      <div className="hidden md:grid grid-cols-[2rem_1fr_8rem_1fr_7rem_7rem_6rem] gap-x-6 items-center">
        <Skeleton className="h-4 w-4 rounded" />
        <div className="space-y-1.5">
          <Skeleton className="h-3.5 w-28" />
          <Skeleton className="h-3 w-20" />
        </div>
        <Skeleton className="h-6 w-20 rounded-full" />
        <Skeleton className="h-3.5 w-32" />
        <Skeleton className="h-3.5 w-16" />
        <Skeleton className="h-3.5 w-20" />
        <Skeleton className="h-3.5 w-20" />
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
      <div className="hidden md:grid grid-cols-[2rem_1fr_8rem_1fr_7rem_7rem_6rem] gap-x-6 items-center px-4 py-2.5 border-b text-xs font-medium text-muted-foreground uppercase tracking-wide select-none">
        <div />
        <div>User</div>
        <div>Action</div>
        <div>Product</div>
        <div>Location</div>
        <div>Date</div>
        <div>Time</div>
      </div>

      {isLoading ? (
        Array.from({ length: 10 }).map((_, i) => <RowSkeleton key={i} />)
      ) : entries.length === 0 ? (
        <div className="flex items-center justify-center h-40 text-sm text-muted-foreground">
          No audit log entries found
        </div>
      ) : (
        entries.map((entry) => {
          const isExpanded = expandedId === entry.id;
          const createdAt = new Date(entry.createdAt);
          return (
            // Outer wrapper owns the border-b and the shared background.
            // This makes the expanded detail feel like part of the same row —
            // no divider between the header and the detail, only at the very bottom.
            <div
              key={entry.id}
              className={cn(
                "border-b transition-colors",
                isExpanded ? "bg-black/[0.06] dark:bg-white/[0.06]" : "hover:bg-black/[0.03] dark:hover:bg-white/[0.03]"
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
                className="hidden md:grid grid-cols-[2rem_1fr_8rem_1fr_7rem_7rem_6rem] gap-x-6 items-center px-4 py-4 cursor-pointer select-none"
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
                  {!DISPLAY_REASONS.has(entry.reason) && (
                    <p className="text-xs text-muted-foreground">
                      {entry.itemCount > 1
                        ? `${entry.itemCount} items · ${entry.totalQuantityMoved} units`
                        : `${entry.totalQuantityMoved} units`}
                    </p>
                  )}
                </div>

                {/* Location */}
                <p className="text-sm text-muted-foreground truncate">
                  {formatLocation(entry)}
                </p>

                {/* Date */}
                <p className="text-sm text-muted-foreground whitespace-nowrap tabular-nums">
                  {format(createdAt, "MM-dd-yyyy")}
                </p>

                {/* Time */}
                <p className="text-sm text-muted-foreground whitespace-nowrap tabular-nums">
                  {format(createdAt, "h:mm a")}
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
