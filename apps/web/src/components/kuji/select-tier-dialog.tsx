"use client";

import { useEffect, useMemo, useState } from "react";
import { Search } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import type { KujiBoxTier } from "@/types/api";
import { compareTiers } from "./tier-palette";
import { TierName } from "./tier-name";

interface SelectTierDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly tiers: readonly KujiBoxTier[];
  readonly excludeTierIds?: readonly string[];
  readonly onSelect: (tier: KujiBoxTier) => void;
}

export function SelectTierDialog({
  open,
  onOpenChange,
  tiers,
  excludeTierIds = [],
  onSelect,
}: SelectTierDialogProps) {
  const [query, setQuery] = useState("");

  useEffect(() => {
    if (!open) setQuery("");
  }, [open]);

  const sortedTiers = useMemo(() => {
    const excluded = new Set(excludeTierIds);
    return [...tiers]
      .filter((t) => t.activeCount > 0 && !excluded.has(t.id))
      .sort(compareTiers);
  }, [tiers, excludeTierIds]);

  const filtered = useMemo(() => {
    const q = query.toLowerCase().trim();
    if (!q) return sortedTiers;
    return sortedTiers.filter((t) => {
      const label = t.label?.toLowerCase() ?? "";
      const linked = t.linkedProductName?.toLowerCase() ?? "";
      const letter = t.letter?.toLowerCase() ?? "";
      return (
        label.includes(q) || linked.includes(q) || letter.includes(q)
      );
    });
  }, [sortedTiers, query]);

  function handleSelect(tier: KujiBoxTier) {
    onSelect(tier);
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg h-[70dvh] max-h-[70dvh] flex flex-col overflow-hidden p-0">
        <DialogHeader className="shrink-0 p-6 pb-2">
          <DialogTitle>Select prize</DialogTitle>
          <DialogDescription>
            Pick a tier with remaining slips.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 min-h-0 flex flex-col px-6 pb-4 gap-3">
          <div className="relative shrink-0">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search prizes..."
              className="pl-9"
            />
          </div>

          <div className="flex-1 min-h-0 overflow-y-auto rounded-md border">
            {filtered.length === 0 ? (
              <div className="flex h-full items-center justify-center p-6 text-sm text-muted-foreground">
                {sortedTiers.length === 0
                  ? "No tiers with remaining slips."
                  : "No prizes match your search."}
              </div>
            ) : (
              <ul className="divide-y">
                {filtered.map((tier) => (
                  <li key={tier.id}>
                    <button
                      type="button"
                      onClick={() => handleSelect(tier)}
                      className={cn(
                        "w-full flex items-center justify-between gap-3 px-3 py-2.5 text-left",
                        "hover:bg-muted/50 transition-colors",
                      )}
                    >
                      <TierName tier={tier} className="min-w-0 flex-1 truncate text-sm" />
                      <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
                        {tier.activeCount} left
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <DialogFooter className="shrink-0 border-t p-4">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onOpenChange(false)}
          >
            Cancel
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
