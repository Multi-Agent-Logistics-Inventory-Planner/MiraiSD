/**
 * Short relative-time string, e.g. "2h ago", "5d ago". Used by the admin Coins
 * tab so the player table and activity feed read like the design handoff. Kept
 * local — there's no existing helper in lib/, and a one-off doesn't justify a
 * date library.
 */
export function formatRelativeShort(isoOrDate: string | Date): string {
  const then = typeof isoOrDate === "string" ? new Date(isoOrDate) : isoOrDate;
  if (Number.isNaN(then.getTime())) return "";
  const seconds = Math.max(0, Math.floor((Date.now() - then.getTime()) / 1000));
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months}mo ago`;
  return `${Math.floor(months / 12)}y ago`;
}

/** Short clock time, e.g. "8:24 PM". For the recent-activity right column. */
export function formatClockTime(isoOrDate: string | Date): string {
  const d = typeof isoOrDate === "string" ? new Date(isoOrDate) : isoOrDate;
  if (Number.isNaN(d.getTime())) return "";
  return d.toLocaleTimeString(undefined, {
    hour: "numeric",
    minute: "2-digit",
  });
}
