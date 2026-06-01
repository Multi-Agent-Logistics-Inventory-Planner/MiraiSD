"use client";

import { cn } from "@/lib/utils";
import { wapeRamp } from "@/components/analytics/predictions";

interface WapeBarProps {
  value: number | null;
}

export function WapeBar({ value }: WapeBarProps) {
  const clamped = value === null || Number.isNaN(value) ? 0 : Math.max(0, Math.min(1, value));
  const ramp = wapeRamp(value);
  return (
    <div className="flex items-center gap-2">
      <div className={cn("h-1.5 w-[88px] rounded-full overflow-hidden", ramp.track)}>
        <div
          className={cn("h-full rounded-full", ramp.fill)}
          style={{ width: `${clamped * 100}%` }}
        />
      </div>
    </div>
  );
}
