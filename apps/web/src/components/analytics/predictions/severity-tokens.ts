import type { ActionUrgency } from "@/types/analytics";

export interface SeverityToken {
  label: string;
  text: string;
  bg: string;
  border: string;
  ring: string;
  dot: string;
}

export const SEVERITY_TOKENS: Record<ActionUrgency, SeverityToken> = {
  CRITICAL: {
    label: "Order today",
    text: "text-red-700 dark:text-red-400",
    bg: "bg-red-50 dark:bg-red-950/30",
    border: "border-red-300 dark:border-red-800/60",
    ring: "ring-red-400/40",
    dot: "bg-red-500 dark:bg-red-400",
  },
  URGENT: {
    label: "This week",
    text: "text-amber-700 dark:text-amber-400",
    bg: "bg-amber-50 dark:bg-amber-950/30",
    border: "border-amber-300 dark:border-amber-800/60",
    ring: "ring-amber-400/40",
    dot: "bg-amber-500 dark:bg-amber-400",
  },
  ATTENTION: {
    label: "Watch",
    text: "text-sky-700 dark:text-sky-400",
    bg: "bg-sky-50 dark:bg-sky-950/30",
    border: "border-sky-300 dark:border-sky-800/60",
    ring: "ring-sky-400/40",
    dot: "bg-sky-500 dark:bg-sky-400",
  },
  HEALTHY: {
    label: "Healthy",
    text: "text-emerald-700 dark:text-emerald-400",
    bg: "bg-emerald-50 dark:bg-emerald-950/30",
    border: "border-emerald-300 dark:border-emerald-800/60",
    ring: "ring-emerald-400/40",
    dot: "bg-emerald-500 dark:bg-emerald-400",
  },
};

export interface WapeRamp {
  text: string;
  fill: string;
  track: string;
}

export function wapeRamp(value: number | null): WapeRamp {
  if (value === null || Number.isNaN(value)) {
    return {
      text: "text-muted-foreground",
      fill: "bg-muted-foreground/40",
      track: "bg-muted/40",
    };
  }
  if (value < 0.6) {
    return {
      text: "text-emerald-700 dark:text-emerald-400",
      fill: "bg-emerald-500 dark:bg-emerald-400",
      track: "bg-emerald-500/15 dark:bg-emerald-400/15",
    };
  }
  if (value < 0.75) {
    return {
      text: "text-amber-700 dark:text-amber-400",
      fill: "bg-amber-500 dark:bg-amber-400",
      track: "bg-amber-500/15 dark:bg-amber-400/15",
    };
  }
  return {
    text: "text-red-700 dark:text-red-400",
    fill: "bg-red-500 dark:bg-red-400",
    track: "bg-red-500/15 dark:bg-red-400/15",
  };
}

export function biasRamp(value: number | null): string {
  if (value === null || Number.isNaN(value)) return "text-muted-foreground";
  const magnitude = Math.abs(value);
  if (magnitude < 1.5) return "text-emerald-700 dark:text-emerald-400";
  if (magnitude < 3.5) return "text-amber-700 dark:text-amber-400";
  return "text-red-700 dark:text-red-400";
}

export function confidenceRamp(value: number | null): { fill: string; text: string } {
  if (value === null || Number.isNaN(value) || value < 0.6) {
    return {
      fill: "bg-amber-500 dark:bg-amber-400",
      text: "text-amber-700 dark:text-amber-400",
    };
  }
  return {
    fill: "bg-emerald-500 dark:bg-emerald-400",
    text: "text-emerald-700 dark:text-emerald-400",
  };
}
