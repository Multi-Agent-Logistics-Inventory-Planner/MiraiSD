"use client";

import { useEffect, useState } from "react";
import { Coins } from "lucide-react";
import { cn } from "@/lib/utils";
import type { Lootbox } from "@/types/lootbox";

interface CrateSelectorProps {
  readonly crates: readonly Lootbox[];
  readonly selectedId: string | null;
  readonly onSelect: (id: string) => void;
}

/**
 * Horizontal selector strip for choosing which open crate to play. Each card shows
 * the crate name, cost, and (when applicable) a live countdown until ends_at.
 */
export function CrateSelector({ crates, selectedId, onSelect }: CrateSelectorProps) {
  if (crates.length === 0) return null;

  return (
    <div className="flex gap-3 overflow-x-auto pb-1">
      {crates.map((crate) => (
        <CrateCard
          key={crate.id}
          crate={crate}
          selected={crate.id === selectedId}
          onSelect={() => onSelect(crate.id)}
        />
      ))}
    </div>
  );
}

function CrateCard({
  crate,
  selected,
  onSelect,
}: {
  readonly crate: Lootbox;
  readonly selected: boolean;
  readonly onSelect: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        "min-w-[180px] flex-1 rounded-xl border px-4 py-3 text-left transition-colors",
        selected
          ? "border-brand-primary bg-card shadow-sm"
          : "border-border bg-card/50 hover:bg-card"
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <span className="truncate text-sm font-semibold text-foreground">{crate.name}</span>
        <span className="inline-flex shrink-0 items-center gap-1 font-mono text-[11px] text-muted-foreground">
          <Coins className="h-3 w-3 text-amber-500" />
          <span className="tabular-nums">{crate.cost}</span>
        </span>
      </div>
      {crate.description ? (
        <div className="mt-0.5 truncate font-mono text-[11px] text-muted-foreground">
          {crate.description}
        </div>
      ) : null}
      <CrateCountdown endsAt={crate.endsAt} />
    </button>
  );
}

function CrateCountdown({ endsAt }: { readonly endsAt: string | null }) {
  // Re-render every minute to keep the relative label fresh without storing the
  // derived string in state (which would trigger the react-hooks/set-state-in-effect lint).
  const [, force] = useState(0);
  useEffect(() => {
    if (!endsAt) return;
    const id = setInterval(() => force((n) => n + 1), 60_000);
    return () => clearInterval(id);
  }, [endsAt]);

  const label = formatCountdown(endsAt);
  if (!label) return null;
  return (
    <div className="mt-1 font-mono text-[10.5px] uppercase tracking-[0.12em] text-muted-foreground">
      {label}
    </div>
  );
}

function formatCountdown(endsAt: string | null): string | null {
  if (!endsAt) return null;
  const end = new Date(endsAt).getTime();
  const diff = end - Date.now();
  if (diff <= 0) return "Closed";
  const days = Math.floor(diff / (24 * 60 * 60 * 1000));
  const hours = Math.floor((diff / (60 * 60 * 1000)) % 24);
  if (days > 7) return null;
  if (days > 0) return `Ends in ${days}d ${hours}h`;
  const minutes = Math.floor((diff / (60 * 1000)) % 60);
  return `Ends in ${hours}h ${minutes}m`;
}
