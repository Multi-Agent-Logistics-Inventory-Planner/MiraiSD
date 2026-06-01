"use client";

import { Activity } from "lucide-react";

export function AccuracyHeader() {
  return (
    <div className="flex items-center gap-2">
      <Activity className="h-5 w-5 text-muted-foreground" />
      <h2 className="text-xl font-semibold">Forecast Accuracy</h2>
    </div>
  );
}
