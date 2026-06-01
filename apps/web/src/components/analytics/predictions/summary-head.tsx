"use client";

import { cn } from "@/lib/utils";
import type { ActionItem } from "@/types/analytics";
import { SEVERITY_TOKENS } from "./severity-tokens";

interface SummaryHeadProps {
  actionCount: number;
  topPriority: ActionItem | null;
}

function formatDate(value: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

export function SummaryHead({ actionCount, topPriority }: SummaryHeadProps) {
  if (actionCount === 0 || !topPriority) {
    return (
      <div>
        <div className="flex items-baseline gap-2.5">
          <span className="text-[30px] font-bold tracking-[-0.02em] tabular-nums leading-none">
            0
          </span>
          <span className="text-lg font-semibold">items need ordering this week</span>
        </div>
        <p className="mt-2 text-[13px] text-muted-foreground">
          No items in the action band — everything has comfortable runway.
        </p>
      </div>
    );
  }

  const severity = SEVERITY_TOKENS[topPriority.urgency];
  const orderBy = formatDate(topPriority.suggestedOrderDate);

  return (
    <div>
      <div className="flex items-baseline gap-2.5">
        <span className="text-[30px] font-bold tracking-[-0.02em] tabular-nums leading-none">
          {actionCount}
        </span>
        <span className="text-lg font-semibold">
          {actionCount === 1 ? "item needs" : "items need"} ordering this week
        </span>
      </div>
      <p className="mt-2 text-[13px] text-muted-foreground">
        Top priority <span className="font-semibold text-foreground">{topPriority.name}</span>
        {topPriority.suggestedReorderQty > 0 && (
          <> — reorder {topPriority.suggestedReorderQty} units</>
        )}
        {orderBy && (
          <>
            {" "}
            by <span className={cn("font-semibold", severity.text)}>{orderBy}</span>
          </>
        )}
      </p>
    </div>
  );
}
