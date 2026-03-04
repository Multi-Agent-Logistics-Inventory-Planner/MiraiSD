"use client";

import { useState } from "react";
import { format } from "date-fns";
import { Loader2 } from "lucide-react";
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
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  AuditLog,
  AuditLogDetail,
  AuditLogMovement,
  StockMovementReason,
  PaginatedResponse,
} from "@/types/api";
import { useAuditLogDetail } from "@/hooks/queries/use-audit-log";

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
  reason: StockMovementReason
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
  data?: PaginatedResponse<AuditLog>;
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
            <Skeleton className="h-4 w-32" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-6 w-20" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-16" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-32" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-36" />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}

function formatQuantityChange(change: number): React.ReactNode {
  if (change > 0) {
    return <span className="text-emerald-700 font-medium">+{change}</span>;
  }
  if (change < 0) {
    return <span className="text-red-600 font-medium">{change}</span>;
  }
  return <span className="text-muted-foreground">0</span>;
}

function formatLocation(entry: AuditLog): string {
  if (
    entry.reason === StockMovementReason.TRANSFER &&
    entry.primaryToLocationCode
  ) {
    return `${entry.primaryFromLocationCode ?? "NA"} → ${entry.primaryToLocationCode}`;
  }
  if (
    entry.reason === StockMovementReason.TRANSFER &&
    entry.primaryFromLocationCode
  ) {
    return `${entry.primaryFromLocationCode} → NA`;
  }
  return (
    entry.primaryToLocationCode ?? entry.primaryFromLocationCode ?? "NA"
  );
}

function formatMovementLocation(movement: AuditLogMovement, reason: StockMovementReason): string {
  if (reason === StockMovementReason.TRANSFER && movement.toLocationCode) {
    return `${movement.fromLocationCode ?? "NA"} → ${movement.toLocationCode}`;
  }
  if (reason === StockMovementReason.TRANSFER && movement.fromLocationCode) {
    return `${movement.fromLocationCode} → NA`;
  }
  return movement.toLocationCode ?? movement.fromLocationCode ?? "NA";
}

interface AuditLogDetailModalProps {
  auditLogId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function AuditLogDetailModal({
  auditLogId,
  open,
  onOpenChange,
}: AuditLogDetailModalProps) {
  const { data: detail, isLoading } = useAuditLogDetail(open ? auditLogId : null);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Audit Log Details</DialogTitle>
        </DialogHeader>

        {isLoading ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : detail ? (
          <div className="space-y-4">
            {/* Header info */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-sm text-muted-foreground">Actor</p>
                <p className="font-medium">{detail.actorName ?? "-"}</p>
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Action</p>
                <Badge variant={getReasonBadgeVariant(detail.reason)}>
                  {REASON_LABELS[detail.reason]}
                </Badge>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-sm text-muted-foreground">Date & Time</p>
                <p className="font-medium">
                  {format(new Date(detail.createdAt), "MMM d, yyyy 'at' h:mm a")}
                </p>
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Total Items</p>
                <p className="font-medium">{detail.itemCount}</p>
              </div>
            </div>

            {detail.notes && (
              <div>
                <p className="text-sm text-muted-foreground">Notes</p>
                <p className="font-medium">{detail.notes}</p>
              </div>
            )}

            {/* Movements list */}
            <div>
              <p className="text-sm text-muted-foreground mb-2">
                Items ({detail.movements.length})
              </p>
              <div className="space-y-3 max-h-64 overflow-y-auto">
                {detail.movements.map((movement) => (
                  <div
                    key={movement.id}
                    className="border rounded-lg p-3 space-y-2"
                  >
                    <div className="flex justify-between items-start">
                      <div>
                        <p className="font-medium">{movement.itemName}</p>
                        <p className="text-xs text-muted-foreground">
                          {movement.itemSku}
                        </p>
                      </div>
                      <div className="text-right">
                        {formatQuantityChange(movement.quantityChange)}
                      </div>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-muted-foreground">
                        {formatMovementLocation(movement, detail.reason)}
                      </span>
                      <span className="text-muted-foreground">
                        {movement.previousQuantity ?? 0} → {movement.currentQuantity ?? 0}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <p className="text-center text-muted-foreground py-4">
            No details available
          </p>
        )}
      </DialogContent>
    </Dialog>
  );
}

export function AuditLogTable({ data, isLoading }: AuditLogTableProps) {
  const entries = data?.content ?? [];
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  const handleRowClick = (entry: AuditLog) => {
    setSelectedId(entry.id);
    setModalOpen(true);
  };

  return (
    <>
      <Table>
        <TableHeader className="bg-muted">
          <TableRow>
            <TableHead className="rounded-tl-xl">Name</TableHead>
            <TableHead>Action</TableHead>
            <TableHead>Location</TableHead>
            <TableHead>Product</TableHead>
            <TableHead className="rounded-tr-xl">When</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading ? (
            <TableSkeleton />
          ) : entries.length === 0 ? (
            <TableRow>
              <TableCell
                colSpan={5}
                className="h-24 text-center text-muted-foreground"
              >
                No audit log entries found.
              </TableCell>
            </TableRow>
          ) : (
            entries.map((entry) => (
              <TableRow
                key={entry.id}
                className="cursor-pointer hover:bg-muted/50"
                onClick={() => handleRowClick(entry)}
              >
                <TableCell>
                  <span className="font-medium">{entry.actorName ?? "-"}</span>
                </TableCell>
                <TableCell>
                  <Badge variant={getReasonBadgeVariant(entry.reason)}>
                    {REASON_LABELS[entry.reason]}
                  </Badge>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {formatLocation(entry)}
                </TableCell>
                <TableCell>
                  <span className="font-medium">{entry.productSummary}</span>
                </TableCell>
                <TableCell className="text-muted-foreground whitespace-nowrap">
                  {format(new Date(entry.createdAt), "MM-dd-yyyy h:mm a")}
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>

      <AuditLogDetailModal
        auditLogId={selectedId}
        open={modalOpen}
        onOpenChange={setModalOpen}
      />
    </>
  );
}
