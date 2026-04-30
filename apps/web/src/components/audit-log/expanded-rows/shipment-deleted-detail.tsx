"use client";

import { AuditLogDetail } from "@/types/api";

interface DeletedItem {
  name?: string;
  ordered?: number;
  received?: number;
}

export function ShipmentDeletedDetail({ detail }: { detail: AuditLogDetail }) {
  const changes = detail.fieldChanges ?? [];
  const supplierEntry = changes.find((c) => c.field === "supplier");
  const supplier =
    supplierEntry && typeof supplierEntry.value === "string" ? supplierEntry.value : null;

  const deletedItemsEntry = changes.find((c) => c.field === "deleted_items");
  const items: DeletedItem[] = Array.isArray(deletedItemsEntry?.to)
    ? (deletedItemsEntry?.to as DeletedItem[])
    : [];

  return (
    <div className="space-y-3">
      {supplier && (
        <div className="px-3 py-1 text-sm">
          <span className="text-muted-foreground">Supplier: </span>
          <span className="font-medium">{supplier}</span>
        </div>
      )}

      {items.length === 0 ? (
        <div className="px-3 py-2 text-sm text-muted-foreground italic">
          No items captured at deletion time.
        </div>
      ) : (
        <div className="space-y-1">
          <div className="grid grid-cols-[1fr_5rem_5rem] gap-x-4 md:gap-x-8 px-3 pb-1 text-xs font-medium text-muted-foreground uppercase tracking-wide">
            <span>Product</span>
            <span className="text-center">Ordered</span>
            <span className="text-center">Received</span>
          </div>
          {items.map((item, idx) => (
            <div
              key={idx}
              className="grid grid-cols-[1fr_5rem_5rem] gap-x-4 md:gap-x-8 px-3 py-2 rounded-md hover:bg-black/5 dark:hover:bg-white/5"
            >
              <span className="text-sm font-medium truncate">{item.name ?? "(unknown)"}</span>
              <span className="text-sm text-muted-foreground text-center tabular-nums">
                {item.ordered ?? 0}
              </span>
              <span className="text-sm text-muted-foreground text-center tabular-nums">
                {item.received ?? 0}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
