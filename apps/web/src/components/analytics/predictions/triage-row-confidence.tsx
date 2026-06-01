"use client";

import { cn } from "@/lib/utils";
import { confidenceRamp } from "./severity-tokens";

interface TriageRowConfidenceProps {
  confidence: number;
}

export function TriageRowConfidence({ confidence }: TriageRowConfidenceProps) {
  const clamped = Math.max(0, Math.min(1, confidence));
  const pct = Math.round(clamped * 100);
  const ramp = confidenceRamp(confidence);

  return (
    <div className="flex flex-col items-end gap-1 w-28">
      <span className="text-[9.5px] font-mono uppercase tracking-[0.13em] text-muted-foreground">
        Confidence
      </span>
      <div className="flex items-center gap-2 w-full justify-end">
        <div className="h-[5px] w-14 rounded-full bg-muted overflow-hidden">
          <div
            className={cn("h-full rounded-full transition-all", ramp.fill)}
            style={{ width: `${pct}%` }}
          />
        </div>
        <span className={cn("font-mono text-[11px] tabular-nums", ramp.text)}>
          {pct}%
        </span>
      </div>
    </div>
  );
}
