"use client";

import { format } from "date-fns";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  AuditLogEntry,
  StockMovementReason,
  PaginatedResponse,
} from "@/types/api";

const REASON_LABELS: Record<StockMovementReason, string> = {
  [StockMovementReason.INITIAL_STOCK]: "Initial Stock",
  [StockMovementReason.RESTOCK]: "Restock",
  [StockMovementReason.SALE]: "Sale",
  [StockMovementReason.DAMAGE]: "Damage",
  [StockMovementReason.ADJUSTMENT]: "Adjustment",
  [StockMovementReason.RETURN]: "Return",
  [StockMovementReason.TRANSFER]: "Transfer",
};

function getReasonBadgeVariant(
  reason: StockMovementReason,
): "default" | "secondary" | "destructive" | "outline" {
  switch (reason) {
    case StockMovementReason.RESTOCK:
    case StockMovementReason.INITIAL_STOCK:
    case StockMovementReason.RETURN:
      return "default";
    case StockMovementReason.SALE:
    case StockMovementReason.TRANSFER:
      return "secondary";
    case StockMovementReason.DAMAGE:
      return "destructive";
    case StockMovementReason.ADJUSTMENT:
    default:
      return "outline";
  }
}

interface AuditLogTableProps {
  data?: PaginatedResponse<AuditLogEntry>;
  isLoading: boolean;
  page: number;
  onPageChange: (page: number) => void;
}

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 10 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell>
            <Skeleton className="h-4 w-36" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-6 w-20" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-16" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-12" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-12" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-12" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-32" />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}

function formatQuantityChange(change: number): React.ReactNode {
  if (change > 0) {
    return <span className="text-green-600 font-medium">+{change}</span>;
  }
  if (change < 0) {
    return <span className="text-red-600 font-medium">{change}</span>;
  }
  return <span className="text-muted-foreground">0</span>;
}

export function AuditLogTable({
  data,
  isLoading,
  page,
  onPageChange,
}: AuditLogTableProps) {
  const entries = data?.content ?? [];

  return (
    <Table>
      <TableHeader className="bg-muted">
        <TableRow className="">
          <TableHead className="w-40 rounded-tl-xl">Time</TableHead>
          <TableHead>Name</TableHead>
          <TableHead className="text-center">Action</TableHead>
          <TableHead className="text-center">Location</TableHead>
          <TableHead className="text-center">Previous Qty</TableHead>
          <TableHead className="text-center">Current Qty</TableHead>
          <TableHead className="text-center">Change</TableHead>
          <TableHead className="rounded-tr-xl pr-0">Product</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {isLoading ? (
          <TableSkeleton />
        ) : entries.length === 0 ? (
          <TableRow>
            <TableCell
              colSpan={8}
              className="h-24 text-center text-muted-foreground"
            >
              No audit log entries found.
            </TableCell>
          </TableRow>
        ) : (
          entries.map((entry) => (
            <TableRow key={entry.id}>
              <TableCell className="text-muted-foreground whitespace-nowrap">
                {format(new Date(entry.at), "yyyy-MM-dd h:mma").toLowerCase()}
              </TableCell>
              <TableCell>{entry.actorName ?? "-"}</TableCell>
              <TableCell className="text-center">
                <Badge variant={getReasonBadgeVariant(entry.reason)}>
                  {REASON_LABELS[entry.reason]}
                </Badge>
              </TableCell>
              <TableCell className="text-center">
                {entry.reason === StockMovementReason.TRANSFER &&
                entry.fromLocationCode &&
                entry.toLocationCode ? (
                  <span className="text-sm">
                    {entry.fromLocationCode} → {entry.toLocationCode}
                  </span>
                ) : (
                  <span className="text-sm">
                    {entry.toLocationCode ?? "-"}
                  </span>
                )}
              </TableCell>
              <TableCell className="text-center text-muted-foreground">
                {entry.previousQuantity ?? "-"}
              </TableCell>
              <TableCell className="text-center font-medium">
                {entry.currentQuantity ?? "-"}
              </TableCell>
              <TableCell className="text-center">
                {formatQuantityChange(entry.quantityChange)}
              </TableCell>
              <TableCell className="pr-0">
                <div className="flex flex-col">
                  <span className="font-medium">{entry.itemName}</span>
                  <span className="text-xs text-muted-foreground">
                    {entry.itemSku}
                  </span>
                </div>
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}
