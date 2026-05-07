"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { KujiBoxStatus, type KujiBox, type KujiBoxTier } from "@/types/api";
import { TierTable } from "./tier-table";
import { AddTierDialog } from "./add-tier-dialog";

interface ManageTiersDialogProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly box: KujiBox;
  readonly canEditStructural: boolean;
  readonly onEditTier: (tier: KujiBoxTier) => void;
  readonly onAddSlip: (tier: KujiBoxTier) => void;
  readonly onTransferIn: (tier: KujiBoxTier) => void;
  readonly onTransferInventoryOnly: (tier: KujiBoxTier) => void;
}

export function ManageTiersDialog({
  open,
  onOpenChange,
  box,
  canEditStructural,
  onEditTier,
  onAddSlip,
  onTransferIn,
  onTransferInventoryOnly,
}: ManageTiersDialogProps) {
  const [addTierOpen, setAddTierOpen] = useState(false);
  const showAddTier = canEditStructural && box.status === KujiBoxStatus.OPEN;

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="w-[calc(100%-2rem)] sm:max-w-3xl max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
          <DialogHeader className="p-6 pb-2 flex flex-row items-start justify-between gap-3">
            <div>
              <DialogTitle>Manage Tiers</DialogTitle>
              <DialogDescription>
                Edit tier properties, add slips, or transfer inventory in.
              </DialogDescription>
            </div>
            {showAddTier ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setAddTierOpen(true)}
              >
                <Plus className="h-4 w-4 mr-1" />
                Add tier
              </Button>
            ) : null}
          </DialogHeader>

          <div className="flex-1 overflow-y-auto px-6 pb-6">
            <TierTable
              box={box}
              canEditStructural={canEditStructural}
              onEditTier={onEditTier}
              onAddSlip={onAddSlip}
              onTransferIn={onTransferIn}
              onTransferInventoryOnly={onTransferInventoryOnly}
            />
          </div>
        </DialogContent>
      </Dialog>

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
