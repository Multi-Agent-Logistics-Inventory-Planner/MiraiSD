"use client";

import { Sparkles } from "lucide-react";

export function DefinitionStrip() {
  return (
    <div className="flex items-start gap-2.5 rounded-lg border bg-muted/40 px-4 py-2.5">
      <Sparkles className="h-3.5 w-3.5 shrink-0 mt-0.5 text-muted-foreground" />
      <p className="text-xs text-muted-foreground leading-[1.5]">
        <span className="font-semibold text-foreground">lt-WAPE</span> = total units missed
        over the 14-day lead-time window, divided by total units sold — lower is better.{" "}
        <span className="font-semibold text-foreground">Bias</span> = average per-day error;
        negative means the forecast under-shoots actual demand.
      </p>
    </div>
  );
}
