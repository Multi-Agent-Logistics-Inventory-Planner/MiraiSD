"use client";

import { AuditLogDetail, AuditLogFieldChange } from "@/types/api";

const FIELD_LABELS: Record<string, string> = {
  supplier: "Supplier",
  shipmentNumber: "Shipment #",
  status: "Status",
  orderDate: "Order date",
  expectedDeliveryDate: "Expected date",
  actualDeliveryDate: "Actual date",
  totalCost: "Cost",
  trackingId: "Tracking",
  notes: "Notes",
  items_added: "Items added",
  items_removed: "Items removed",
};

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return JSON.stringify(value);
}

function ItemList({ items }: { items: unknown }) {
  if (!Array.isArray(items)) return <span className="text-muted-foreground">—</span>;
  return (
    <ul className="space-y-0.5">
      {items.map((it, idx) => {
        const entry = it as { name?: string; qty?: number };
        return (
          <li key={idx} className="text-sm">
            <span className="font-medium">{entry.name ?? "(unknown)"}</span>
            {entry.qty != null && (
              <span className="text-muted-foreground"> ({entry.qty})</span>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function FieldRow({ change }: { change: AuditLogFieldChange }) {
  const label = FIELD_LABELS[change.field] ?? change.field;

  if (change.field === "notes") {
    return (
      <div className="grid grid-cols-[10rem_1fr] gap-x-6 px-3 py-2 rounded-md hover:bg-black/5 dark:hover:bg-white/5">
        <span className="text-sm font-medium">{label}</span>
        <span className="text-sm text-muted-foreground italic">Notes updated</span>
      </div>
    );
  }

  if (change.field === "items_added" || change.field === "items_removed") {
    const items = change.field === "items_added" ? change.to : change.from;
    return (
      <div className="grid grid-cols-[10rem_1fr] gap-x-6 px-3 py-2 rounded-md hover:bg-black/5 dark:hover:bg-white/5">
        <span className="text-sm font-medium">{label}</span>
        <ItemList items={items} />
      </div>
    );
  }

  return (
    <div className="grid grid-cols-[10rem_1fr_auto_1fr] items-center gap-x-3 md:gap-x-6 px-3 py-2 rounded-md hover:bg-black/5 dark:hover:bg-white/5">
      <span className="text-sm font-medium">{label}</span>
      <span className="text-sm text-muted-foreground tabular-nums truncate">
        {formatValue(change.from)}
      </span>
      <span className="text-muted-foreground">→</span>
      <span className="text-sm font-medium tabular-nums truncate">
        {formatValue(change.to)}
      </span>
    </div>
  );
}

export function ShipmentEditedDetail({ detail }: { detail: AuditLogDetail }) {
  const changes = detail.fieldChanges ?? [];

  if (changes.length === 0) {
    return (
      <div className="px-3 py-4 text-sm text-muted-foreground italic">
        No field changes recorded.
      </div>
    );
  }

  return (
    <div className="space-y-1">
      <div className="grid grid-cols-[10rem_1fr] gap-x-6 px-3 pb-1 text-xs font-medium text-muted-foreground uppercase tracking-wide">
        <span>Field</span>
        <span>Change</span>
      </div>
      {changes.map((change, idx) => (
        <FieldRow key={`${change.field}-${idx}`} change={change} />
      ))}
    </div>
  );
}
