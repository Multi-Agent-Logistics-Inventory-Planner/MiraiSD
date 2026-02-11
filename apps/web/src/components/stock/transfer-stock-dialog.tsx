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
import { ProductCategory, ProductSubcategory } from "@/types/api";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useLocationInventory } from "@/hooks/queries/use-location-inventory";
import {
  useBatchTransferMutation,
  type BatchTransferItem,
} from "@/hooks/mutations/use-stock-mutations";
import { LocationSelector } from "./location-selector";
import { ProductTransferCard } from "./product-transfer-card";
import { InventoryPreviewTooltip } from "./inventory-preview-tooltip";
import { ProductFilterHeader, getNoResultsMessage } from "./adjust";
import { LOCATION_TYPE_CODES, type LocationSelection } from "@/types/transfer";
import { cn } from "@/lib/utils";

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
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilters, setCategoryFilters] = useState<ProductCategory[]>([]);
  const [subcategoryFilters, setSubcategoryFilters] = useState<
    ProductSubcategory[]
  >([]);

  const batchTransferMutation = useBatchTransferMutation();

  const sourceInventoryQuery = useLocationInventory(
    sourceLocation.locationType ?? undefined,
    sourceLocation.locationId ?? undefined
  );

  const destinationInventoryQuery = useLocationInventory(
    destinationLocation.locationType ?? undefined,
    destinationLocation.locationId ?? undefined
  );

  useEffect(() => {
    if (!open) {
      setSourceLocation(EMPTY_LOCATION);
      setDestinationLocation(EMPTY_LOCATION);
      setTransferQuantities({});
      setSearchQuery("");
      setCategoryFilters([]);
      setSubcategoryFilters([]);
    }
  }, [open]);

  useEffect(() => {
    if (sourceLocation.locationId) {
      setTransferQuantities({});
    }
  }, [sourceLocation.locationId]);

  const sourceInventory = sourceInventoryQuery.data ?? [];
  const destinationInventory = destinationInventoryQuery.data ?? [];

  const filteredInventory = useMemo(() => {
    let result = sourceInventory;

    if (categoryFilters.length > 0) {
      result = result.filter((inv) =>
        categoryFilters.includes(inv.item.category)
      );
    }

    if (subcategoryFilters.length > 0) {
      result = result.filter(
        (inv) =>
          inv.item.subcategory &&
          subcategoryFilters.includes(inv.item.subcategory)
      );
    }

    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase().trim();
      result = result.filter((inv) =>
        inv.item.name.toLowerCase().includes(query)
      );
    }

    return result;
  }, [sourceInventory, searchQuery, categoryFilters, subcategoryFilters]);

  const availableCategories = useMemo(() => {
    const categories = new Set<ProductCategory>();
    sourceInventory.forEach((inv) => {
      if (inv.item.category) {
        categories.add(inv.item.category);
      }
    });
    return Array.from(categories).sort();
  }, [sourceInventory]);

  const availableSubcategories = useMemo(() => {
    const subcategories = new Set<ProductSubcategory>();
    sourceInventory.forEach((inv) => {
      if (inv.item.subcategory) {
        subcategories.add(inv.item.subcategory);
      }
    });
    return Array.from(subcategories).sort();
  }, [sourceInventory]);

  const hasActiveFilters =
    categoryFilters.length > 0 || subcategoryFilters.length > 0;

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

  function handleCategoryChange(categories: ProductCategory[]) {
    setCategoryFilters(categories);
    // Clear subcategory filters if Blind Box is deselected
    if (!categories.includes(ProductCategory.BLIND_BOX)) {
      setSubcategoryFilters([]);
    }
  }

  function handleClearFilters() {
    setCategoryFilters([]);
    setSubcategoryFilters([]);
    setSearchQuery("");
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

    // Extract validated values for type safety
    const srcLocationType = sourceLocation.locationType;
    const srcLocationId = sourceLocation.locationId;
    const destLocationType = destinationLocation.locationType;
    const destLocationId = destinationLocation.locationId;

    const transfers: BatchTransferItem[] = transferItems.map((inv) => {
      const existingDestInventory = destinationInventory.find(
        (destInv) => destInv.item.id === inv.item.id
      );

      return {
        payload: {
          sourceLocationType: srcLocationType,
          sourceInventoryId: inv.id,
          destinationLocationType: destLocationType,
          ...(existingDestInventory
            ? { destinationInventoryId: existingDestInventory.id }
            : { destinationLocationId: destLocationId }),
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
        sourceLocationId: srcLocationId,
        destinationLocationId: destLocationId,
        sourceLocationType: srcLocationType,
        destinationLocationType: destLocationType,
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

  const locationLabel = sourceLocation.locationType
    ? `${LOCATION_TYPE_CODES[sourceLocation.locationType]}${sourceLocation.locationCode}`
    : "";

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl h-[95dvh] max-h-[95dvh] flex flex-col overflow-hidden p-0">
        <DialogHeader className="shrink-0 p-6 pb-0">
          <DialogTitle>Transfer Stock</DialogTitle>
        </DialogHeader>

        <div className="flex-1 min-h-0 flex flex-col overflow-hidden px-6">
          {/* Location selectors */}
          <div className="shrink-0 grid grid-cols-2 gap-4 sm:gap-16 bg-muted py-4 px-4 rounded-xl mt-4">
            <LocationSelector
              label="From"
              value={sourceLocation}
              onChange={setSourceLocation}
              disabled={isTransferring}
            />
            <LocationSelector
              label="To"
              labelSuffix={
                hasValidDestination && destinationLocation.locationType && (
                  <InventoryPreviewTooltip
                    locationType={destinationLocation.locationType}
                    locationCode={destinationLocation.locationCode}
                    inventory={destinationInventory}
                    isLoading={destinationInventoryQuery.isLoading}
                  />
                )
              }
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
            <p className="shrink-0 text-sm text-destructive mt-2">
              Source and destination cannot be the same
            </p>
          ) : null}

          {/* Products section */}
          {hasValidSource ? (
            <div className="flex-1 min-h-0 flex flex-col mt-4">
              <ProductFilterHeader
                title={`Products at ${locationLabel}`}
                itemCount={sourceInventory.length}
                searchQuery={searchQuery}
                categoryFilters={categoryFilters}
                subcategoryFilters={subcategoryFilters}
                availableCategories={availableCategories}
                availableSubcategories={availableSubcategories}
                disabled={isTransferring}
                showFilters={sourceInventory.length > 0}
                onSearchChange={setSearchQuery}
                onCategoryChange={handleCategoryChange}
                onSubcategoryChange={setSubcategoryFilters}
                onClearFilters={handleClearFilters}
              />

              {sourceInventoryQuery.isLoading ? (
                <div className="flex-1 flex items-center justify-center py-4">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              ) : sourceInventory.length === 0 ? (
                <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                  No inventory at this location
                </div>
              ) : filteredInventory.length === 0 ? (
                <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                  {getNoResultsMessage(searchQuery, categoryFilters, subcategoryFilters)}
                </div>
              ) : (
                <div className="flex-1 min-h-0">
                  <ScrollArea className="h-full **:data-[slot=scroll-area-viewport]:overscroll-auto">
                    <div className="pb-2 pr-3">
                      {filteredInventory.map((inv) => (
                        <ProductTransferCard
                          key={inv.id}
                          inventory={inv}
                          transferQuantity={transferQuantities[inv.id] ?? 0}
                          onQuantityChange={(qty) =>
                            handleQuantityChange(inv.id, qty)
                          }
                          maxQuantity={inv.quantity}
                          disabled={isTransferring}
                        />
                      ))}
                    </div>
                  </ScrollArea>
                </div>
              )}
            </div>
          ) : (
            <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center mt-4">
              Select a source location to see available products
            </div>
          )}
        </div>

        {/* Fixed bottom section */}
        <div className="shrink-0 border-t bg-background">
          {/* Transfer summary */}
          <div
            className={cn(
              "overflow-hidden transition-all duration-200 ease-in-out",
              hasItemsToTransfer ? "max-h-32 opacity-100" : "max-h-0 opacity-0"
            )}
            aria-live="polite"
            aria-atomic="true"
          >
            <div className="px-6 pt-4 space-y-1">
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Items to transfer</span>
                <span className="font-medium">{totalItemsToTransfer}</span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Total quantity</span>
                <span className="font-medium">{totalQuantityToTransfer}</span>
              </div>
            </div>
          </div>

          {/* Progress indicator */}
          {isTransferring && progress.total > 0 ? (
            <div className="px-6 pt-4 space-y-2">
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

          {/* Footer buttons */}
          <DialogFooter className="px-6 py-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isTransferring}
              className="min-h-11 sm:min-h-9"
            >
              Cancel
            </Button>
            <Button
              type="button"
              onClick={handleSubmit}
              disabled={!canSubmit}
              className="min-h-11 sm:min-h-9"
            >
              {isTransferring ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              Transfer {hasItemsToTransfer ? `(${totalItemsToTransfer})` : ""}
            </Button>
          </DialogFooter>
        </div>
      </DialogContent>
    </Dialog>
  );
}
