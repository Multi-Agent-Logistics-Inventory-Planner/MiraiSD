"use client";

import { ArrowDown, ArrowUp, Minus } from "lucide-react";
import { cn } from "@/lib/utils";
import { Card } from "@/components/ui/card";

interface KpiCardProps {
  label: string;
  value: string;
  valueClassName?: string;
  delta?: {
    value: number;
    label: string;
    betterWhen: "lower" | "higher";
  } | null;
  sub?: string;
}

export function KpiCard({ label, value, valueClassName, delta, sub }: KpiCardProps) {
  let deltaBadge: React.ReactNode = null;
  if (delta) {
    if (Math.abs(delta.value) < 0.001) {
      deltaBadge = (
        <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
          <Minus className="h-3 w-3" /> flat
        </span>
      );
    } else {
      const improving =
        (delta.betterWhen === "lower" && delta.value < 0) ||
        (delta.betterWhen === "higher" && delta.value > 0);
      const Icon = delta.value < 0 ? ArrowDown : ArrowUp;
      deltaBadge = (
        <span
          className={cn(
            "inline-flex items-center gap-1 text-xs",
            improving
              ? "text-emerald-600 dark:text-emerald-400"
              : "text-red-600 dark:text-red-400",
          )}
        >
          <Icon className="h-3 w-3" />
          {delta.label}
        </span>
      );
    }
  }

  return (
    <Card className="flex flex-col gap-2 rounded-xl border p-[18px] min-w-[180px] flex-1">
      <span className="text-[9.5px] font-mono uppercase tracking-[0.13em] text-muted-foreground">
        {label}
      </span>
      <div className="flex items-baseline gap-2">
        <span
          className={cn(
            "text-[38px] font-bold tracking-[-0.02em] tabular-nums leading-none",
            valueClassName,
          )}
        >
          {value}
        </span>
        {deltaBadge}
      </div>
      {sub && <span className="text-xs text-muted-foreground">{sub}</span>}
    </Card>
  );
}
