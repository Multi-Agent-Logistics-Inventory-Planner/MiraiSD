"use client";

import { AuditLogDetail } from "@/types/api";

export function ShipmentStatusOverriddenDetail({ detail }: { detail: AuditLogDetail }) {
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-[10rem_1fr_auto_1fr] items-center gap-x-3 md:gap-x-6 px-3 py-2 rounded-md hover:bg-black/5 dark:hover:bg-white/5">
        <span className="text-sm font-medium">Status</span>
        <span className="text-sm text-muted-foreground tabular-nums">
          {detail.previousStatus ?? "—"}
        </span>
        <span className="text-muted-foreground">→</span>
        <span className="text-sm font-medium tabular-nums">{detail.newStatus ?? "—"}</span>
      </div>

      {detail.overrideReason && (
        <div className="px-3 py-2 rounded-md bg-amber-50 dark:bg-amber-950/30 border border-amber-100 dark:border-amber-900/40">
          <p className="text-xs font-medium uppercase tracking-wide text-amber-700 dark:text-amber-400 mb-1">
            Reason
          </p>
          <p className="text-sm text-amber-900 dark:text-amber-200">{detail.overrideReason}</p>
        </div>
      )}
    </div>
  );
}
