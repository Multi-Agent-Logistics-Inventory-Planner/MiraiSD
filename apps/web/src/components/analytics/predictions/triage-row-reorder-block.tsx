"use client";

import { cn } from "@/lib/utils";
import type { ActionItem } from "@/types/analytics";
import { SEVERITY_TOKENS } from "./severity-tokens";

function formatOrderDate(value: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

function coverWeeks(qty: number, velocity: number | null): number | null {
  if (!velocity || velocity <= 0 || qty <= 0) return null;
  const weeks = qty / velocity / 7;
  if (!Number.isFinite(weeks)) return null;
  return Math.max(1, Math.round(weeks));
}

interface TriageRowReorderBlockProps {
  item: ActionItem;
}

export function TriageRowReorderBlock({ item }: TriageRowReorderBlockProps) {
  const weeks = coverWeeks(item.suggestedReorderQty, item.demandVelocity);
  const severity = SEVERITY_TOKENS[item.urgency];
  const date = formatOrderDate(item.suggestedOrderDate);

  if (item.suggestedReorderQty <= 0) {
    return (
      <div className="flex flex-col items-end gap-1 w-52 text-right">
        <span className="text-[9.5px] font-mono uppercase tracking-[0.13em] text-muted-foreground">
          Recommended Reorder
        </span>
        <span className="text-sm text-muted-foreground">No order needed</span>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1 w-52 text-right">
      <span className="text-[9.5px] font-mono uppercase tracking-[0.13em] text-muted-foreground">
        Recommended Reorder
      </span>
      <div className="flex items-baseline gap-1.5">
        <span className="text-[22px] font-semibold tabular-nums leading-none">
          {item.suggestedReorderQty}
        </span>
        <span className="text-xs text-muted-foreground">units</span>
      </div>
      <span className="text-xs text-muted-foreground">
        {weeks ? `~${weeks}-week supply · ` : ""}by{" "}
        <span className={cn("font-semibold", severity.text)}>{date}</span>
      </span>
    </div>
  );
}
