"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Progress } from "@/components/ui/progress";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useLocationInventory } from "@/hooks/queries/use-location-inventory";
import {
  useBatchTransferMutation,
  type BatchTransferItem,
} from "@/hooks/mutations/use-stock-mutations";
import { LocationSelector } from "./location-selector";
import { ProductTransferCard } from "./product-transfer-card";
import { LOCATION_TYPE_CODES, type LocationSelection } from "@/types/transfer";

interface TransferStockDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const EMPTY_LOCATION: LocationSelection = {
  locationType: null,
  locationId: null,
  locationCode: "",
};

export function TransferStockDialog({
  open,
  onOpenChange,
}: TransferStockDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();

  const [sourceLocation, setSourceLocation] =
    useState<LocationSelection>(EMPTY_LOCATION);
  const [destinationLocation, setDestinationLocation] =
    useState<LocationSelection>(EMPTY_LOCATION);
  const [transferQuantities, setTransferQuantities] = useState<
    Record<string, number>
  >({});

  const batchTransferMutation = useBatchTransferMutation();

  const sourceInventoryQuery = useLocationInventory(
    sourceLocation.locationType ?? undefined,
    sourceLocation.locationId ?? undefined,
  );

  const destinationInventoryQuery = useLocationInventory(
    destinationLocation.locationType ?? undefined,
    destinationLocation.locationId ?? undefined,
  );

  useEffect(() => {
    if (!open) {
      setSourceLocation(EMPTY_LOCATION);
      setDestinationLocation(EMPTY_LOCATION);
      setTransferQuantities({});
    }
  }, [open]);

  useEffect(() => {
    if (sourceLocation.locationId) {
      setTransferQuantities({});
    }
  }, [sourceLocation.locationId]);

  const sourceInventory = sourceInventoryQuery.data ?? [];
  const destinationInventory = destinationInventoryQuery.data ?? [];

  const transferItems = useMemo(() => {
    return sourceInventory.filter((inv) => {
      const qty = transferQuantities[inv.id] ?? 0;
      return qty > 0;
    });
  }, [sourceInventory, transferQuantities]);

  const totalItemsToTransfer = transferItems.length;
  const totalQuantityToTransfer = useMemo(() => {
    return transferItems.reduce((sum, inv) => {
      return sum + (transferQuantities[inv.id] ?? 0);
    }, 0);
  }, [transferItems, transferQuantities]);

  const hasValidSource = Boolean(sourceLocation.locationId);
  const hasValidDestination = Boolean(destinationLocation.locationId);
  const isSameLocation =
    hasValidSource &&
    hasValidDestination &&
    sourceLocation.locationType === destinationLocation.locationType &&
    sourceLocation.locationId === destinationLocation.locationId;
  const hasItemsToTransfer = totalItemsToTransfer > 0;

  const canSubmit =
    hasValidSource &&
    hasValidDestination &&
    !isSameLocation &&
    hasItemsToTransfer &&
    !batchTransferMutation.isPending;

  function handleQuantityChange(inventoryId: string, quantity: number) {
    setTransferQuantities((prev) => ({
      ...prev,
      [inventoryId]: quantity,
    }));
  }

  async function handleSubmit() {
    const actorId = user?.personId || user?.id;
    if (!actorId) {
      toast({ title: "Missing user", description: "Please sign in again." });
      return;
    }

    if (!sourceLocation.locationType || !sourceLocation.locationId) {
      toast({
        title: "Missing source",
        description: "Select a valid source location.",
      });
      return;
    }

    if (!destinationLocation.locationType || !destinationLocation.locationId) {
      toast({
        title: "Missing destination",
        description: "Select a valid destination location.",
      });
      return;
    }

    const transfers: BatchTransferItem[] = transferItems.map((inv) => {
      const existingDestInventory = destinationInventory.find(
        (destInv) => destInv.item.id === inv.item.id,
      );

      return {
        payload: {
          sourceLocationType: sourceLocation.locationType!,
          sourceInventoryId: inv.id,
          destinationLocationType: destinationLocation.locationType!,
          ...(existingDestInventory
            ? { destinationInventoryId: existingDestInventory.id }
            : { destinationLocationId: destinationLocation.locationId! }),
          quantity: transferQuantities[inv.id] ?? 0,
          actorId,
        },
        productId: inv.item.id,
        productName: inv.item.name,
      };
    });

    try {
      const result = await batchTransferMutation.mutateAsync({
        transfers,
        sourceLocationId: sourceLocation.locationId!,
        destinationLocationId: destinationLocation.locationId!,
      });

      if (result.failed.length === 0) {
        toast({
          title: "Transfer complete",
          description: `Successfully transferred ${result.successful.length} item(s).`,
        });
        onOpenChange(false);
      } else if (result.successful.length > 0) {
        toast({
          title: "Partial transfer",
          description: `${result.successful.length} succeeded, ${result.failed.length} failed.`,
          variant: "destructive",
        });
      } else {
        toast({
          title: "Transfer failed",
          description: "All transfers failed. Please try again.",
          variant: "destructive",
        });
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Transfer failed";
      toast({ title: "Transfer failed", description: message });
    }
  }

  const progress = batchTransferMutation.progress;
  const isTransferring = batchTransferMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl max-h-[90vh] flex flex-col">
        <DialogHeader>
          <DialogTitle>Transfer Stock</DialogTitle>
        </DialogHeader>

        <div className="flex-1 overflow-hidden flex flex-col gap-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 sm:gap-16 bg-gray-200 p-4 rounded-xl">
            <LocationSelector
              label="From"
              value={sourceLocation}
              onChange={setSourceLocation}
              disabled={isTransferring}
            />
            <LocationSelector
              label="To"
              value={destinationLocation}
              onChange={setDestinationLocation}
              disabled={isTransferring}
              excludeLocation={
                sourceLocation.locationType && sourceLocation.locationId
                  ? {
                      locationType: sourceLocation.locationType,
                      locationId: sourceLocation.locationId,
                    }
                  : undefined
              }
            />
          </div>

          {isSameLocation ? (
            <p className="text-sm text-destructive">
              Source and destination cannot be the same
            </p>
          ) : null}

          {hasValidSource ? (
            <div className="space-y-2">
              <Label className="text-xs sm:text-sm text-muted-foreground">
                Products at {sourceLocation.locationType ? LOCATION_TYPE_CODES[sourceLocation.locationType] : ""}{sourceLocation.locationCode} ({sourceInventory.length})
              </Label>
              {sourceInventoryQuery.isLoading ? (
                <div className="flex items-center justify-center py-4">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              ) : sourceInventory.length === 0 ? (
                <div className="rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                  No inventory at this location
                </div>
              ) : (
                <ScrollArea className="max-h-72 sm:max-h-80">
                  <div className="pr-4">
                    {sourceInventory.map((inv) => (
                      <ProductTransferCard
                        key={inv.id}
                        inventory={inv}
                        transferQuantity={transferQuantities[inv.id] ?? 0}
                        onQuantityChange={(qty) =>
                          handleQuantityChange(inv.id, qty)
                        }
                        maxQuantity={inv.quantity}
                      />
                    ))}
                  </div>
                </ScrollArea>
              )}
            </div>
          ) : (
            <div className="rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
              Select a source location to see available products
            </div>
          )}

          {hasItemsToTransfer ? (
            <div className="rounded-md border p-3 space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Items to transfer</span>
                <span className="font-medium">{totalItemsToTransfer}</span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Total quantity</span>
                <span className="font-medium">{totalQuantityToTransfer}</span>
              </div>
            </div>
          ) : null}

          {isTransferring && progress.total > 0 ? (
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">
                  Transferring: {progress.currentItem}
                </span>
                <span>
                  {progress.completed}/{progress.total}
                </span>
              </div>
              <Progress
                value={(progress.completed / progress.total) * 100}
                className="h-2"
              />
            </div>
          ) : null}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isTransferring}
          >
            Cancel
          </Button>
          <Button type="button" onClick={handleSubmit} disabled={!canSubmit}>
            {isTransferring ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : null}
            Transfer {hasItemsToTransfer ? `(${totalItemsToTransfer})` : ""}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
