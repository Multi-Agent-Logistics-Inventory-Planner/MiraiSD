"use client";

import { Search, X } from "lucide-react";
import type { KujiBoxTier } from "@/types/api";
import { TierClassChips } from "./tier-class-chips";

interface PrizeSearchFilterProps {
  readonly tiers: readonly KujiBoxTier[];
  readonly totalActive: number;
  readonly query: string;
  readonly onQueryChange: (next: string) => void;
  readonly filter: string;
  readonly onFilterChange: (next: string) => void;
  readonly autoFocus?: boolean;
}

export function PrizeSearchFilter({
  tiers,
  totalActive,
  query,
  onQueryChange,
  filter,
  onFilterChange,
  autoFocus,
}: PrizeSearchFilterProps) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center gap-2">
      <div className="flex items-center gap-2 rounded-[10px] sm:rounded-[9px] border border-border bg-background px-3 w-full sm:max-w-[320px] sm:flex-1">
        <Search className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
        <input
          autoFocus={autoFocus}
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="Search prizes..."
          className="flex-1 bg-transparent border-none outline-none text-[13px] py-2.5 sm:py-2 text-foreground placeholder:text-muted-foreground"
        />
        {query ? (
          <button
            type="button"
            onClick={() => onQueryChange("")}
            className="text-muted-foreground hover:text-foreground p-0.5"
            aria-label="Clear search"
          >
            <X className="h-3 w-3" />
          </button>
        ) : null}
      </div>
      <div className="w-full sm:flex-1 sm:min-w-0">
        <TierClassChips
          tiers={tiers}
          totalActive={totalActive}
          value={filter}
          onChange={onFilterChange}
          variant="pills"
        />
      </div>
    </div>
  );
}
