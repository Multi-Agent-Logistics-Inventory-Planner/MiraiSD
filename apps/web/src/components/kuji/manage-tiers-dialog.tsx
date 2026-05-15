"use client";

import { useEffect, useMemo, useState } from "react";
import { Plus } from "lucide-react";
import {
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useMoveKujiSlipsMutation } from "@/hooks/mutations/use-kuji-box-mutations";
import {
  KujiBoxStatus,
  type KujiBox,
  type KujiBoxTier,
} from "@/types/api";
import { compareTiers } from "./tier-palette";
import { DialogChrome, DialogCloseButton } from "./dialog-chrome";
import { ALL_FILTER, matchesClassFilter } from "./tier-class-chips";
import { PrizeSearchFilter } from "./prize-search-filter";
import { TierClassColorProvider } from "./tier-class-color-context";
import { TierTile } from "./tier-tile";
import { TierRow } from "./tier-row";
import { AddTierDialog } from "./add-tier-dialog";

interface ManageTiersDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly box: KujiBox;
  readonly canEditStructural: boolean;
  readonly onEditTier: (tier: KujiBoxTier) => void;
  readonly onTransferIn: (tier: KujiBoxTier) => void;
}

export function ManageTiersDialog({
  open,
  onOpenChange,
  box,
  canEditStructural,
  onEditTier,
  onTransferIn,
}: ManageTiersDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const moveMutation = useMoveKujiSlipsMutation();

  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<string>(ALL_FILTER);
  const [addTierOpen, setAddTierOpen] = useState(false);

  useEffect(() => {
    if (!open) {
      setQuery("");
      setFilter(ALL_FILTER);
    }
  }, [open]);

  const showAddTier = canEditStructural && box.status === KujiBoxStatus.OPEN;

  const sortedTiers = useMemo(
    () => [...box.tiers].sort(compareTiers),
    [box.tiers],
  );

  const totalActive = useMemo(
    () => box.tiers.reduce((s, t) => s + t.activeCount, 0),
    [box.tiers],
  );

  const totalInactive = useMemo(
    () => box.tiers.reduce((s, t) => s + t.inactiveCount, 0),
    [box.tiers],
  );

  const visibleTiers = useMemo(() => {
    const q = query.trim().toLowerCase();
    return sortedTiers.filter((t) => {
      if (!matchesClassFilter(t, filter)) return false;
      if (!q) return true;
      const label = (t.label ?? "").toLowerCase();
      const product = (t.linkedProductName ?? "").toLowerCase();
      return label.includes(q) || product.includes(q);
    });
  }, [sortedTiers, query, filter]);

  function handleMoveSlip(
    tier: KujiBoxTier,
    direction: "ACTIVATE" | "DEACTIVATE",
  ) {
    const actorId = user?.personId ?? user?.id;
    if (!actorId) return;
    moveMutation.mutate(
      {
        boxId: box.id,
        tierId: tier.id,
        productId: box.productId,
        payload: { actorId, quantity: 1, direction },
      },
      {
        onError: (err) => {
          toast({
            title: "Failed to move slip",
            description: err instanceof Error ? err.message : String(err),
            variant: "destructive",
          });
        },
      },
    );
  }


  const matchingCount = visibleTiers.length;
  const showingMatchSuffix =
    query.trim().length > 0 || filter !== ALL_FILTER ? "matching" : "in box";

  return (
    <>
      <DialogChrome
        open={open}
        onOpenChange={onOpenChange}
        variant="managePrizes"
      >
       <TierClassColorProvider tiers={box.tiers}>
        <DialogHeader className="px-4 sm:px-6 pt-3.5 sm:pt-5 pb-3 sm:pb-3.5 flex flex-row items-start gap-2 sm:gap-3 space-y-0">
          <div className="flex-1 min-w-0">
            <DialogTitle className="text-base sm:text-lg font-medium text-white">
              Manage prizes
            </DialogTitle>
            <DialogDescription className="hidden sm:block text-[12.5px] text-muted-foreground mt-0.5">
              Edit prize properties or move slips between active / inactive
              pools.
            </DialogDescription>
            <div className="sm:hidden text-[11.5px] text-muted-foreground mt-0.5 tabular-nums">
              {box.tiers.length} prizes · {totalActive} active
              {totalInactive ? ` · ${totalInactive} held` : ""}
            </div>
          </div>
          {showAddTier ? (
            <button
              type="button"
              onClick={() => setAddTierOpen(true)}
              className="inline-flex items-center gap-1.5 rounded-lg px-2.5 sm:px-3 py-1.5 text-[12.5px] sm:text-[13px] font-medium text-white bg-brand-primary hover:bg-brand-primary/90 shadow-[0_4px_12px_rgba(124,58,237,0.35)] transition"
            >
              <Plus className="h-3 w-3" />
              <span className="sm:hidden">Add</span>
              <span className="hidden sm:inline">Add prize</span>
            </button>
          ) : null}
          <DialogCloseButton onClose={() => onOpenChange(false)} />
        </DialogHeader>

        <div className="px-4 sm:px-6 pt-2.5 sm:pt-0 pb-2 sm:pb-3">
          <PrizeSearchFilter
            tiers={box.tiers}
            totalActive={totalActive}
            query={query}
            onQueryChange={setQuery}
            filter={filter}
            onFilterChange={setFilter}
          />
        </div>

        <div className="px-4 sm:px-6 pb-2 sm:pb-2.5 flex items-center justify-between text-[11px] text-muted-foreground">
          <span>
            {matchingCount} {matchingCount === 1 ? "prize" : "prizes"}{" "}
            {showingMatchSuffix}
          </span>
          {totalInactive > 0 ? (
            <span className="text-amber-500">
              <span className="hidden sm:inline">
                {totalInactive} inactive slip{totalInactive === 1 ? "" : "s"}{" "}
                held back
              </span>
              <span className="sm:hidden">{totalInactive} held back</span>
            </span>
          ) : null}
        </div>

        <div className="flex-1 overflow-y-auto px-4 sm:px-6 pt-1 pb-5">
          {visibleTiers.length === 0 ? (
            <div className="py-10 text-center text-[13px] text-muted-foreground">
              No prizes match &ldquo;
              <span className="text-foreground/80">{query}</span>&rdquo;.
            </div>
          ) : (
            <>
              <div className="hidden sm:grid grid-cols-2 sm:grid-cols-3 gap-2.5">
                {visibleTiers.map((tier) => (
                  <TierTile
                    key={tier.id}
                    mode="managePrizes"
                    tier={tier}
                    totalActive={totalActive}
                    onStash={() => handleMoveSlip(tier, "DEACTIVATE")}
                    onPromote={() => handleMoveSlip(tier, "ACTIVATE")}
                    onAdd={() => onTransferIn(tier)}
                    onMore={() => onEditTier(tier)}
                    disableStash={tier.activeCount === 0}
                    disablePromote={tier.inactiveCount === 0}
                  />
                ))}
              </div>
              <ul className="grid sm:hidden grid-cols-1 gap-1.5 list-none p-0 m-0">
                {visibleTiers.map((tier) => (
                  <li key={tier.id}>
                    <TierRow
                      mode="managePrizes"
                      tier={tier}
                      totalActive={totalActive}
                      onStash={() => handleMoveSlip(tier, "DEACTIVATE")}
                      onPromote={() => handleMoveSlip(tier, "ACTIVATE")}
                      onAdd={() => onTransferIn(tier)}
                      onMore={() => onEditTier(tier)}
                      disableStash={tier.activeCount === 0}
                      disablePromote={tier.inactiveCount === 0}
                    />
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
       </TierClassColorProvider>
      </DialogChrome>

      {showAddTier ? (
        <AddTierDialog
          open={addTierOpen}
          onOpenChange={setAddTierOpen}
          box={box}
        />
      ) : null}
    </>
  );
}
