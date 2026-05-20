import { hexWithAlpha } from "@/components/kuji/tier-palette";

export { hexWithAlpha };

const FALLBACK_TIER_COLOR = "#7c3aed";

export function resolveTierColor(color: string | null | undefined): string {
  if (!color) return FALLBACK_TIER_COLOR;
  return color.startsWith("#") ? color : FALLBACK_TIER_COLOR;
}

export function fmtPct(probabilityPct: number): string {
  if (probabilityPct >= 1) return `${probabilityPct.toFixed(1)}%`;
  if (probabilityPct >= 0.1) return `${probabilityPct.toFixed(2)}%`;
  return `${probabilityPct.toFixed(3)}%`;
}

export function formatTimeAgo(iso: string, now: Date = new Date()): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const secs = Math.max(0, Math.floor((now.getTime() - then) / 1000));
  if (secs < 45) return "just now";
  if (secs < 90) return "1m";
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d`;
  return `${Math.floor(days / 7)}w`;
}
