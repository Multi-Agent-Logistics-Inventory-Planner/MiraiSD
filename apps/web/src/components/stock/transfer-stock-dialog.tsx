"use client";

import { useEffect, useMemo, useState } from "react";
import { Filter, Loader2, Search, X } from "lucide-react";
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
import { Input } from "@/components/ui/input";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Badge } from "@/components/ui/badge";
import {
  ProductCategory,
  PRODUCT_CATEGORY_LABELS,
} from "@/types/api";
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
  const [categoryFilter, setCategoryFilter] = useState<ProductCategory | null>(null);
  const [isFilterOpen, setIsFilterOpen] = useState(false);

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
      setSearchQuery("");
      setCategoryFilter(null);
      setIsFilterOpen(false);
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

    if (categoryFilter) {
      result = result.filter((inv) => inv.item.category === categoryFilter);
    }

    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase().trim();
      result = result.filter((inv) =>
        inv.item.name.toLowerCase().includes(query),
      );
    }

    return result;
  }, [sourceInventory, searchQuery, categoryFilter]);

  const availableCategories = useMemo(() => {
    const categories = new Set<ProductCategory>();
    sourceInventory.forEach((inv) => {
      if (inv.item.category) {
        categories.add(inv.item.category);
      }
    });
    return Array.from(categories).sort();
  }, [sourceInventory]);

  const hasActiveFilters = categoryFilter !== null;

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
      <DialogContent className="sm:max-w-3xl h-[95dvh] max-h-[95dvh] flex flex-col overflow-hidden p-0">
        <DialogHeader className="shrink-0 p-6 pb-0">
          <DialogTitle>Transfer Stock</DialogTitle>
        </DialogHeader>

        <div className="flex-1 min-h-0 flex flex-col overflow-hidden px-6">
          {/* Location selectors - fixed height, never shrinks */}
          <div className="shrink-0 grid grid-cols-1 sm:grid-cols-2 gap-4 sm:gap-16 bg-muted py-4 px-5 rounded-xl mt-4">
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

          {/* Products section - this is the only section that grows/shrinks */}
          {hasValidSource ? (
            <div className="flex-1 min-h-0 flex flex-col mt-4">
              {/* Header with search and filter */}
              <div className="shrink-0 flex flex-col gap-2 mb-2">
                <div className="flex items-center justify-between">
                  <Label className="text-xs sm:text-sm text-muted-foreground">
                    Products at{" "}
                    {sourceLocation.locationType
                      ? LOCATION_TYPE_CODES[sourceLocation.locationType]
                      : ""}
                    {sourceLocation.locationCode} ({sourceInventory.length})
                  </Label>
                  {hasActiveFilters && (
                    <button
                      type="button"
                      onClick={() => {
                        setCategoryFilter(null);
                        setSearchQuery("");
                      }}
                      className="text-xs text-muted-foreground hover:text-foreground flex items-center gap-1"
                      disabled={isTransferring}
                    >
                      <X className="h-3 w-3" />
                      Clear filters
                    </button>
                  )}
                </div>

                {sourceInventory.length > 0 && (
                  <div className="flex items-center gap-2">
                    <div className="relative flex-1">
                      <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                      <Input
                        type="text"
                        placeholder="Search products..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="h-9 pl-8 text-sm"
                        disabled={isTransferring}
                      />
                    </div>
                    <Popover open={isFilterOpen} onOpenChange={setIsFilterOpen}>
                      <PopoverTrigger asChild>
                        <Button
                          type="button"
                          variant="outline"
                          size="icon"
                          className={cn(
                            "h-9 w-9 shrink-0",
                            hasActiveFilters && "border-primary text-primary"
                          )}
                          disabled={isTransferring}
                        >
                          <Filter className="h-4 w-4" />
                          {hasActiveFilters && (
                            <span className="absolute -top-1 -right-1 h-4 w-4 rounded-full bg-primary text-[10px] text-primary-foreground flex items-center justify-center">
                              1
                            </span>
                          )}
                        </Button>
                      </PopoverTrigger>
                      <PopoverContent align="end" className="w-56 p-3">
                        <div className="space-y-3">
                          <div className="flex items-center justify-between">
                            <span className="text-sm font-medium">Filter by category</span>
                            {categoryFilter && (
                              <button
                                type="button"
                                onClick={() => setCategoryFilter(null)}
                                className="text-xs text-muted-foreground hover:text-foreground"
                              >
                                Clear
                              </button>
                            )}
                          </div>
                          <div className="flex flex-wrap gap-1.5">
                            {availableCategories.map((category) => (
                              <Badge
                                key={category}
                                variant={categoryFilter === category ? "default" : "outline"}
                                className={cn(
                                  "cursor-pointer text-xs",
                                  categoryFilter === category
                                    ? "bg-primary hover:bg-primary/90"
                                    : "hover:bg-muted"
                                )}
                                onClick={() => {
                                  setCategoryFilter(
                                    categoryFilter === category ? null : category
                                  );
                                }}
                              >
                                {PRODUCT_CATEGORY_LABELS[category]}
                              </Badge>
                            ))}
                            {availableCategories.length === 0 && (
                              <span className="text-xs text-muted-foreground">
                                No categories available
                              </span>
                            )}
                          </div>
                        </div>
                      </PopoverContent>
                    </Popover>
                  </div>
                )}
              </div>

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
                  {searchQuery && categoryFilter
                    ? `No products match "${searchQuery}" in ${PRODUCT_CATEGORY_LABELS[categoryFilter]}`
                    : searchQuery
                      ? `No products match "${searchQuery}"`
                      : categoryFilter
                        ? `No ${PRODUCT_CATEGORY_LABELS[categoryFilter]} products`
                        : "No products found"}
                </div>
              ) : (
                <div className="flex-1 min-h-0">
                  <ScrollArea className="h-full **:data-[slot=scroll-area-viewport]:overscroll-auto">
                    <div className="pr-4 pb-2">
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

        {/* Fixed bottom section - summary, progress, and footer */}
        <div className="shrink-0 border-t bg-background">
          {/* Transfer summary */}
          <div
            className={cn(
              "overflow-hidden transition-all duration-200 ease-in-out",
              hasItemsToTransfer ? "max-h-32 opacity-100" : "max-h-0 opacity-0",
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
