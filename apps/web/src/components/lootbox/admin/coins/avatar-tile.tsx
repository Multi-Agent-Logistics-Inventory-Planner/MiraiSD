"use client";

/**
 * Deterministic hue from a stable id (userId) so a given player gets the same
 * avatar tint across renders and sessions. Lifted from the design handoff:
 *   bg  = hsl(hue 50% 30%)
 *   fg  = hsl(hue 70% 88%)
 */
function hueFromId(id: string): number {
  let hash = 0;
  for (let i = 0; i < id.length; i++) {
    hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  }
  return hash % 360;
}

function initialsFromName(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

interface AvatarTileProps {
  readonly userId: string;
  readonly fullName: string;
}

export function AvatarTile({ userId, fullName }: AvatarTileProps) {
  const hue = hueFromId(userId);
  return (
    <div
      className="flex h-7 w-7 flex-none items-center justify-center rounded-lg font-mono text-[10.5px] font-semibold"
      style={{
        background: `hsl(${hue} 50% 30%)`,
        color: `hsl(${hue} 70% 88%)`,
      }}
    >
      {initialsFromName(fullName)}
    </div>
  );
}
