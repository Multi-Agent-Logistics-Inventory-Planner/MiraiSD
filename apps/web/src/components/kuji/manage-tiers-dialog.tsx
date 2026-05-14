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
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import {
  useDeleteKujiPrizeMutation,
  useMoveKujiSlipsMutation,
} from "@/hooks/mutations/use-kuji-box-mutations";
import { KujiBoxStatus, type KujiBox, type KujiBoxTier } from "@/types/api";
import { TierTable } from "./tier-table";
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
  const [addTierOpen, setAddTierOpen] = useState(false);
  const [tierPendingDelete, setTierPendingDelete] = useState<KujiBoxTier | null>(null);
  const showAddTier = canEditStructural && box.status === KujiBoxStatus.OPEN;
  const { toast } = useToast();
  const { user } = useAuth();
  const moveMutation = useMoveKujiSlipsMutation();
  const deleteMutation = useDeleteKujiPrizeMutation();

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

  function handleDeletePrize(tier: KujiBoxTier) {
    setTierPendingDelete(tier);
  }

  function confirmDelete() {
    const actorId = user?.personId ?? user?.id;
    const tier = tierPendingDelete;
    if (!actorId || !tier) {
      setTierPendingDelete(null);
      return;
    }
    deleteMutation.mutate(
      {
        boxId: box.id,
        tierId: tier.id,
        productId: box.productId,
        payload: { actorId },
      },
      {
        onSuccess: () => {
          setTierPendingDelete(null);
          toast({
            title: "Prize deleted",
            description: `Tier "${tier.label}" was removed from this box.`,
            variant: "success",
          });
        },
        onError: (err) => {
          toast({
            title: "Failed to delete prize",
            description: err instanceof Error ? err.message : String(err),
            variant: "destructive",
          });
        },
      },
    );
  }

  const pendingTotal = tierPendingDelete
    ? tierPendingDelete.activeCount + tierPendingDelete.inactiveCount
    : 0;

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="w-[calc(100%-2rem)] sm:max-w-3xl h-[85vh] sm:h-[700px] flex flex-col gap-0 p-0 overflow-hidden">
          <DialogHeader className="p-6 pb-2 flex flex-row items-start justify-between gap-3">
            <div>
              <DialogTitle>Manage Tiers</DialogTitle>
              <DialogDescription>
                Edit tier properties or transfer inventory and slips in.
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
              onTransferIn={onTransferIn}
              onMoveSlip={handleMoveSlip}
              onDeletePrize={handleDeletePrize}
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

      <AlertDialog
        open={tierPendingDelete !== null}
        onOpenChange={(o) => {
          if (!o && !deleteMutation.isPending) setTierPendingDelete(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete prize</AlertDialogTitle>
            <AlertDialogDescription>
              {tierPendingDelete ? (
                <>
                  Delete the entire{" "}
                  <span className="font-semibold">
                    &quot;{tierPendingDelete.label}&quot;
                  </span>{" "}
                  prize from this box?
                  {pendingTotal > 0 ? (
                    <>
                      {" "}
                      This will remove {tierPendingDelete.activeCount} active and{" "}
                      {tierPendingDelete.inactiveCount} inactive slip
                      {pendingTotal === 1 ? "" : "s"} ({pendingTotal} total) and delete
                      the tier row.
                    </>
                  ) : (
                    <> The tier has no slips; this removes the empty row.</>
                  )}
                  {tierPendingDelete.linkedProductId ? (
                    <>
                      {" "}
                      Linked-product inventory is <strong>not</strong> returned to
                      regular stock — use Edit Tier &rarr; Clear linked product first
                      if you need that.
                    </>
                  ) : null}
                </>
              ) : null}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteMutation.isPending}>
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => {
                e.preventDefault();
                confirmDelete();
              }}
              disabled={deleteMutation.isPending}
              className="bg-red-600 text-white hover:bg-red-700"
            >
              {deleteMutation.isPending ? "Deleting..." : "Delete prize"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
