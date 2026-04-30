"use client";

import { useState, useMemo, useEffect } from "react";
import { Loader2 } from "lucide-react";
import { ProductThumbnail } from "@/components/products/product-thumbnail";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
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
import { Checkbox } from "@/components/ui/checkbox";
import { Badge } from "@/components/ui/badge";
import { cn, prizeLetterDisplay, sortPrizes } from "@/lib/utils";
import { useToast } from "@/hooks/use-toast";
import { useUndoReceiveShipmentItemMutation } from "@/hooks/mutations/use-shipment-mutations";
import type { Shipment, ShipmentItem } from "@/types/api";

interface ShipmentUndoItemsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  shipment: Shipment | null;
  onSuccess?: (updatedShipment: Shipment) => void;
}

interface DetailBlock {
  parentItem: ShipmentItem;
  prizeItems: ShipmentItem[];
}

function hasReceivedContent(item: ShipmentItem): boolean {
  return (
    item.receivedQuantity > 0 ||
    (item.damagedQuantity ?? 0) > 0 ||
    (item.displayQuantity ?? 0) > 0 ||
    (item.shopQuantity ?? 0) > 0
  );
}

export function ShipmentUndoItemsDialog({
  open,
  onOpenChange,
  shipment,
  onSuccess,
}: ShipmentUndoItemsDialogProps) {
  const { toast } = useToast();
  const undoItemMutation = useUndoReceiveShipmentItemMutation();
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set());
  const [isProcessing, setIsProcessing] = useState(false);
  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false);

  // Reset selection when dialog opens
  useEffect(() => {
    if (open) {
      setSelectedItems(new Set());
      setConfirmDialogOpen(false);
    }
  }, [open]);

  // Group items into blocks
  const detailBlocks = useMemo((): DetailBlock[] => {
    if (!shipment?.items) return [];
    const roots = shipment.items.filter((i) => !i.item.parentId);
    const prizesByParentId: Record<string, ShipmentItem[]> = {};
    shipment.items.forEach((i) => {
      const pid = i.item.parentId;
      if (pid) {
        if (!prizesByParentId[pid]) prizesByParentId[pid] = [];
        prizesByParentId[pid].push(i);
      }
    });
    return roots.map((parentItem) => {
      const unsortedPrizes = prizesByParentId[parentItem.item.id] ?? [];
      const sortedPrizes = sortPrizes(
        unsortedPrizes.map((p) => ({ ...p, letter: p.item.letter }))
      ).map((p) => {
        const { letter: _, ...rest } = p;
        return rest as ShipmentItem;
      });
      return {
        parentItem,
        prizeItems: sortedPrizes,
      };
    });
  }, [shipment?.items]);

  // Get all items with received content
  const receivedItems = useMemo(() => {
    if (!shipment?.items) return [];
    return shipment.items.filter(hasReceivedContent);
  }, [shipment?.items]);

  const toggleItem = (itemId: string) => {
    setSelectedItems((prev) => {
      const next = new Set(prev);
      if (next.has(itemId)) {
        next.delete(itemId);
      } else {
        next.add(itemId);
      }
      return next;
    });
  };

  const allSelected = selectedItems.size === receivedItems.length && receivedItems.length > 0;

  const toggleSelectAll = () => {
    if (allSelected) {
      setSelectedItems(new Set());
    } else {
      setSelectedItems(new Set(receivedItems.map((item) => item.id)));
    }
  };

  async function handleConfirm() {
    if (!shipment || selectedItems.size === 0) return;

    setIsProcessing(true);
    let lastUpdatedShipment: Shipment | null = null;
    let failedCount = 0;
    const itemsToUndo = Array.from(selectedItems);

    for (const itemId of itemsToUndo) {
      try {
        lastUpdatedShipment = await undoItemMutation.mutateAsync({
          shipmentId: shipment.id,
          itemId,
        });
      } catch (err: unknown) {
        failedCount++;
        const message =
          err instanceof Error ? err.message : "Failed to undo item";
        toast({
          title: "Error undoing item",
          description: message,
          variant: "destructive",
        });
      }
    }

    setIsProcessing(false);

    const successCount = itemsToUndo.length - failedCount;
    if (successCount > 0) {
      toast({
        title: `${successCount} item${successCount > 1 ? "s" : ""} reversed`,
        description: "Inventory has been restored for the selected items.",
      });
      if (lastUpdatedShipment && onSuccess) {
        onSuccess(lastUpdatedShipment);
      }
      onOpenChange(false);
    }
  }

  if (!shipment) return null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Undo Items</DialogTitle>
          <DialogDescription>
            Select items to undo from shipment {shipment.shipmentNumber}.
            This will reverse inventory additions for the selected items only.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {/* Select all toggle */}
          <div className="flex items-center gap-2 text-sm">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={toggleSelectAll}
              disabled={receivedItems.length === 0}
            >
              {allSelected ? "Deselect All" : "Select All"}
            </Button>
            <span className="text-muted-foreground ml-auto">
              {selectedItems.size} of {receivedItems.length} selected
            </span>
          </div>

          {/* Warning */}
          <div className="text-sm text-amber-600 bg-amber-50 dark:bg-amber-950/30 p-3 rounded-lg">
            Warning: If inventory for any selected item has been sold or transferred,
            the undo will fail for that item.
          </div>

          {/* Items list */}
          <div className="space-y-2 border rounded-lg">
            {detailBlocks.map((block) => {
              const { parentItem, prizeItems } = block;
              const parentHasReceived = hasReceivedContent(parentItem);
              const imageUrl = parentItem.item?.imageUrl;
              const hasPrizes = prizeItems.length > 0;

              // For Kuji blocks, check if any prizes have received content
              const receivedPrizes = prizeItems.filter(hasReceivedContent);
              const hasAnyReceived = parentHasReceived || receivedPrizes.length > 0;

              if (!hasAnyReceived) return null;

              return (
                <div key={parentItem.id} className="border-b last:border-b-0 p-3">
                  {/* Parent item */}
                  {parentHasReceived && (
                    <div className="flex items-center gap-3">
                      <Checkbox
                        id={`item-${parentItem.id}`}
                        checked={selectedItems.has(parentItem.id)}
                        onCheckedChange={() => toggleItem(parentItem.id)}
                      />
                      <ProductThumbnail
                        imageUrl={imageUrl}
                        alt={parentItem.item.name}
                        size="md"
                        fallbackVariant="package"
                      />
                      <label
                        htmlFor={`item-${parentItem.id}`}
                        className="flex-1 cursor-pointer"
                      >
                        <p className="font-medium text-sm">{parentItem.item.name}</p>
                        <p className="text-xs text-muted-foreground">
                          {parentItem.receivedQuantity} received
                          {(parentItem.damagedQuantity ?? 0) > 0 && (
                            <>, {parentItem.damagedQuantity} damaged</>
                          )}
                          {(parentItem.displayQuantity ?? 0) > 0 && (
                            <>, {parentItem.displayQuantity} display</>
                          )}
                          {(parentItem.shopQuantity ?? 0) > 0 && (
                            <>, {parentItem.shopQuantity} shop</>
                          )}
                        </p>
                      </label>
                    </div>
                  )}

                  {/* Prize items */}
                  {hasPrizes && receivedPrizes.length > 0 && (
                    <div className={cn("space-y-2", parentHasReceived && "mt-3 pl-6 border-l-2 border-muted")}>
                      {!parentHasReceived && (
                        <p className="text-xs font-medium text-muted-foreground mb-2">
                          {parentItem.item.name} - Prizes
                        </p>
                      )}
                      {receivedPrizes.map((prize) => {
                        const letter = (prize.item as { letter?: string | null }).letter;
                        const prizeLabel = letter ? prizeLetterDisplay(letter) : prize.item.name;
                        return (
                          <div key={prize.id} className="flex items-center gap-3">
                            <Checkbox
                              id={`item-${prize.id}`}
                              checked={selectedItems.has(prize.id)}
                              onCheckedChange={() => toggleItem(prize.id)}
                            />
                            <label
                              htmlFor={`item-${prize.id}`}
                              className="flex items-center gap-2 cursor-pointer flex-1"
                            >
                              <Badge variant="outline" className="font-mono font-bold">
                                {prizeLabel}
                              </Badge>
                              <span className="text-xs text-muted-foreground">
                                {prize.receivedQuantity} received
                                {(prize.damagedQuantity ?? 0) > 0 && (
                                  <>, {prize.damagedQuantity} damaged</>
                                )}
                              </span>
                            </label>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isProcessing}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={() => setConfirmDialogOpen(true)}
            disabled={isProcessing || selectedItems.size === 0}
            className="bg-amber-600 text-white hover:bg-amber-700"
          >
            {isProcessing ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Reversing...
              </>
            ) : (
              `Undo ${selectedItems.size} Item${selectedItems.size !== 1 ? "s" : ""}`
            )}
          </Button>
        </DialogFooter>
      </DialogContent>

      {/* Confirmation Dialog */}
      <AlertDialog open={confirmDialogOpen} onOpenChange={setConfirmDialogOpen}>
        <AlertDialogContent className="sm:max-w-md">
          <AlertDialogHeader className="flex flex-col items-center gap-4">
            <AlertDialogTitle className="text-center">Undo shipment receipt?</AlertDialogTitle>
            <AlertDialogDescription asChild>
              <div className="space-y-2 text-center">
                <p>
                  This will reverse inventory additions from shipment{" "}
                  <span className="font-medium font-mono">
                    {shipment.shipmentNumber}
                  </span>
                  . The shipment will return to PENDING status and can be received again or deleted.
                </p>
                <p className="text-amber-600 font-medium">
                  Warning: If items from this shipment have been sold or transferred, the undo will fail.
                </p>
              </div>
            </AlertDialogDescription>
            {/* <div className="relative w-full max-w-[280px] sm:max-w-[320px] aspect-square overflow-hidden rounded-lg border border-border">
              <Image
                src="/dont-mess-up.jpg"
                alt="Warning"
                fill
                className="object-contain"
                sizes="(max-width: 640px) 280px, 320px"
                priority
              />
            </div> */}
          </AlertDialogHeader>
          <AlertDialogFooter className="sm:justify-center gap-2 mt-4">
            <AlertDialogCancel disabled={isProcessing}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-amber-600 text-white hover:bg-amber-700"
              onClick={handleConfirm}
              disabled={isProcessing}
            >
              {isProcessing ? "Reversing..." : "Undo Receipt"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Dialog>
  );
}
