"use client";

import { Plus } from "lucide-react";
import { cn } from "@/lib/utils";
import { Skeleton } from "@/components/ui/skeleton";
import type { LootboxAdmin } from "@/types/lootbox";

interface CrateListRailProps {
  readonly crates: readonly LootboxAdmin[];
  readonly activeCrateId: string | null;
  readonly onSelect: (id: string) => void;
  readonly onNew: () => void;
  readonly isLoading: boolean;
  readonly isCreating: boolean;
}

export function CrateListRail({
  crates,
  activeCrateId,
  onSelect,
  onNew,
  isLoading,
  isCreating,
}: CrateListRailProps) {
  return (
    <div className="flex max-h-[40vh] min-h-0 flex-col gap-2 border-b border-border p-3 md:max-h-none md:h-full md:border-b-0 md:border-r">
      <div className="flex items-center justify-between px-1 pb-1">
        <span className="font-mono text-[10.5px] uppercase tracking-[0.18em] text-muted-foreground">
          Crates · <span className="tabular-nums">{crates.length}</span>
        </span>
        <button
          type="button"
          onClick={onNew}
          disabled={isCreating}
          className="inline-flex items-center gap-1 font-mono text-[10.5px] uppercase tracking-[0.12em] text-brand-primary transition-opacity hover:opacity-80 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Plus className="h-3 w-3" />
          New
        </button>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
        {isLoading ? (
          <>
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </>
        ) : crates.length === 0 ? (
          <p className="px-2 py-4 text-[12px] text-muted-foreground">
            No crates yet. Create one to get started.
          </p>
        ) : (
          crates.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => onSelect(c.id)}
              className={cn(
                "rounded-lg border px-3 py-2.5 text-left transition-colors",
                c.id === activeCrateId
                  ? "border-brand-primary/30 bg-brand-primary/10"
                  : "border-transparent bg-transparent hover:bg-card/60",
                !c.active ? "opacity-60" : ""
              )}
            >
              <div className="truncate text-[13px] font-medium text-foreground">
                {c.name}
              </div>
              <div className="font-mono text-[10.5px] text-muted-foreground">
                <span className="tabular-nums">{c.prizeCount}</span> prize
                {c.prizeCount === 1 ? "" : "s"} ·{" "}
                <span className="tabular-nums">{c.cost}</span> coin
                {c.cost === 1 ? "" : "s"}
              </div>
            </button>
          ))
        )}
      </div>
    </div>
  );
}
