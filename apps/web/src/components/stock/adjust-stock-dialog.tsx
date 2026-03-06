"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2, Minus, Plus } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import {
  type Category,
  LocationType,
  StockMovementReason,
} from "@/types/api";
import { DEFAULT_REASON_BY_ACTION } from "./adjust/types";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useQueryClient } from "@tanstack/react-query";
import { useLocationInventory } from "@/hooks/queries/use-location-inventory";
import { useAdjustStockMutation } from "@/hooks/mutations/use-stock-mutations";
import {
  useCreateInventoryMutation,
  useUpdateInventoryMutation,
} from "@/hooks/mutations/use-location-mutations";
import { AddInventoryDialog } from "@/components/locations/add-inventory-dialog";
import type { InventoryRequest } from "@/types/api";
import { parseQuantityInput } from "@/lib/utils/validation";
import { LocationSelector } from "./location-selector";
import { InventoryPreviewTooltip } from "./inventory-preview-tooltip";
import { LOCATION_TYPE_CODES, type LocationSelection } from "@/types/transfer";
import {
  ProductList,
  SelectedProductCard,
  ProductFilterHeader,
  type AdjustAction,
  type NormalizedInventory,
  normalizeInventory,
} from "./adjust";

interface AdjustStockDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const EMPTY_LOCATION: LocationSelection = {
  locationType: null,
  locationId: null,
  locationCode: "",
};

export function AdjustStockDialog({
  open,
  onOpenChange,
}: AdjustStockDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const [location, setLocation] = useState<LocationSelection>(EMPTY_LOCATION);
  const [selectedInventoryId, setSelectedInventoryId] = useState<string | null>(
    null,
  );
  const [addInventoryDialogOpen, setAddInventoryDialogOpen] = useState(false);
  const [action, setAction] = useState<AdjustAction>("subtract");
  const [quantity, setQuantity] = useState(1);
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilters, setCategoryFilters] = useState<string[]>([]);
  const [childCategoryFilters, setChildCategoryFilters] = useState<string[]>([]);
  const [quantityWarning, setQuantityWarning] = useState<string | null>(null);
  const [reason, setReason] = useState<StockMovementReason>(DEFAULT_REASON_BY_ACTION["subtract"]);

  const adjustMutation = useAdjustStockMutation();

  const inventoryQuery = useLocationInventory(
    location.locationType ?? undefined,
    location.locationId ?? undefined,
  );

  const createInventoryMutation = useCreateInventoryMutation(
    location.locationType ?? LocationType.BOX_BIN,
    location.locationId ?? ""
  );
  const updateInventoryMutation = useUpdateInventoryMutation(
    location.locationType ?? LocationType.BOX_BIN,
    location.locationId ?? ""
  );

  useEffect(() => {
    if (!open) {
      setLocation(EMPTY_LOCATION);
      setSelectedInventoryId(null);
      setAddInventoryDialogOpen(false);
      setAction("subtract");
      setQuantity(1);
      setSearchQuery("");
      setCategoryFilters([]);
      setChildCategoryFilters([]);
      setQuantityWarning(null);
      setReason(DEFAULT_REASON_BY_ACTION["subtract"]);
    }
  }, [open]);

  useEffect(() => {
    if (location.locationId) {
      setSelectedInventoryId(null);
      setAddInventoryDialogOpen(false);
      setQuantity(1);
      setQuantityWarning(null);
    }
  }, [location.locationId]);

  useEffect(() => {
    setSelectedInventoryId(null);
    setAddInventoryDialogOpen(false);
    setQuantity(1);
    setQuantityWarning(null);
    setSearchQuery("");
    setCategoryFilters([]);
    setChildCategoryFilters([]);
    setReason(DEFAULT_REASON_BY_ACTION[action]);
  }, [action]);

  const inventory = inventoryQuery.data ?? [];

  const normalizedInventory: NormalizedInventory[] = useMemo(() => {
    return inventory.map(normalizeInventory);
  }, [inventory]);

  const availableCategories = useMemo(() => {
    const categoryMap = new Map<string, Category>();
    inventory.forEach((item) => {
      if (item.item.category) {
        categoryMap.set(item.item.category.id, item.item.category);
      }
    });
    return Array.from(categoryMap.values()).sort((a, b) =>
      a.name.localeCompare(b.name)
    );
  }, [inventory]);

  const availableChildCategories = useMemo(() => {
    const childCategoryMap = new Map<string, Category>();
    inventory.forEach((item) => {
      if (item.item.category?.parentId) {
        childCategoryMap.set(item.item.category.id, item.item.category);
      }
    });
    return Array.from(childCategoryMap.values()).sort((a, b) =>
      a.name.localeCompare(b.name)
    );
  }, [inventory]);

  const selectedInventory = useMemo(() => {
    return inventory.find((inv) => inv.id === selectedInventoryId) ?? null;
  }, [inventory, selectedInventoryId]);

  const hasValidLocation = Boolean(location.locationId);
  const isAdjusting = adjustMutation.isPending;
  const isSavingInventory =
    createInventoryMutation.isPending || updateInventoryMutation.isPending;

  const hasSelectedProduct = Boolean(selectedInventory);
  const currentQtyAtLocation = selectedInventory?.quantity ?? 0;
  const sourceListCount = inventory.length;

  const newQty =
    action === "subtract"
      ? currentQtyAtLocation - quantity
      : currentQtyAtLocation + quantity;

  const canSubmit =
    hasValidLocation &&
    hasSelectedProduct &&
    quantity >= 1 &&
    newQty >= 0 &&
    !isAdjusting;

  const selectedNormalizedItem: NormalizedInventory | null = useMemo(() => {
    return selectedInventory ? normalizeInventory(selectedInventory) : null;
  }, [selectedInventory]);

  function handleQuantityChange(value: string) {
    const parsed = parseQuantityInput(value);

    if (parsed === null) {
      setQuantity(0);
      setQuantityWarning(null);
      return;
    }

    if (action === "subtract" && parsed > currentQtyAtLocation) {
      setQuantity(currentQtyAtLocation);
      setQuantityWarning(
        `Clamped to available stock (${currentQtyAtLocation})`,
      );
    } else {
      setQuantity(Math.max(1, parsed));
      setQuantityWarning(null);
    }
  }

  function handleIncrement() {
    if (action === "subtract") {
      if (quantity < currentQtyAtLocation) {
        setQuantity(quantity + 1);
        setQuantityWarning(null);
      }
    } else {
      setQuantity(quantity + 1);
      setQuantityWarning(null);
    }
  }

  function handleDecrement() {
    if (quantity > 1) {
      setQuantity(quantity - 1);
      setQuantityWarning(null);
    }
  }

  function handleActionChange(value: string) {
    if (value === "add" || value === "subtract") {
      setAction(value);
    }
  }

  function handleClearSelection() {
    setSelectedInventoryId(null);
    setQuantity(1);
    setQuantityWarning(null);
  }

  function handleCategoryChange(categories: string[]) {
    setCategoryFilters(categories);
    setChildCategoryFilters([]);
  }

  function handleClearFilters() {
    setCategoryFilters([]);
    setChildCategoryFilters([]);
    setSearchQuery("");
  }

  function handleProductSelect(id: string, itemQuantity: number) {
    setSelectedInventoryId(id);
    if (action === "subtract" && quantity > itemQuantity) {
      setQuantity(Math.max(1, itemQuantity));
    }
    setQuantityWarning(null);
  }

  async function handleSubmit() {
    const actorId = user?.personId || user?.id;
    if (!actorId) {
      toast({ title: "Missing user", description: "Please sign in again." });
      return;
    }

    if (!location.locationType || !location.locationId || !selectedInventory) {
      toast({
        title: "Missing selection",
        description: "Select a location and product.",
      });
      return;
    }

    const quantityChange = action === "subtract" ? -quantity : quantity;

    if (action === "subtract" && quantity > currentQtyAtLocation) {
      toast({
        title: "Invalid quantity",
        description: "Cannot subtract more than available stock.",
        variant: "destructive",
      });
      return;
    }

    try {
      await adjustMutation.mutateAsync({
        locationType: location.locationType,
        inventoryId: selectedInventory.id,
        payload: { quantityChange, reason, actorId },
        productId: selectedInventory.item.id,
      });

      await queryClient.invalidateQueries({
        queryKey: [
          "locationInventory",
          location.locationType,
          location.locationId,
        ],
      });

      toast({ title: "Stock adjusted" });
      onOpenChange(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Adjustment failed";
      toast({ title: "Adjustment failed", description: message });
    }
  }

  async function handleAddNewInventory(
    payload: InventoryRequest,
    isUpdate: boolean,
    inventoryId?: string
  ) {
    try {
      if (isUpdate && inventoryId) {
        await updateInventoryMutation.mutateAsync({ inventoryId, payload });
      } else {
        await createInventoryMutation.mutateAsync(payload);
      }
      toast({ title: "Inventory added successfully" });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to save inventory";
      toast({ title: "Error", description: message, variant: "destructive" });
    }
  }

  const locationLabel = location.locationType
    ? `${LOCATION_TYPE_CODES[location.locationType]}${location.locationCode}`
    : "";

  const listTitle =
    action === "subtract"
      ? `Products at ${locationLabel}`
      : `Select product at ${locationLabel} to add stock`;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl h-[95dvh] max-h-[95dvh] flex flex-col overflow-hidden p-0">
        <DialogHeader className="shrink-0 p-6 pb-0">
          <DialogTitle>Adjust Stock</DialogTitle>
        </DialogHeader>

        <div className="flex-1 min-h-0 flex flex-col overflow-hidden px-6">
          {/* Location selector and action toggle */}
          <div className="shrink-0 bg-[#f0eee6] dark:bg-[#1f1e1d] py-4 px-5 rounded-xl mt-4 flex gap-4 sm:gap-16">
            {/* Location section */}
            <div className="flex-1 min-w-0 space-y-2">
              <div className="flex items-center gap-1.5">
                <Label>Location</Label>
                {action === "add" &&
                  hasValidLocation &&
                  location.locationType && (
                    <InventoryPreviewTooltip
                      locationType={location.locationType}
                      locationCode={location.locationCode}
                      inventory={inventory}
                      isLoading={inventoryQuery.isLoading}
                    />
                  )}
              </div>
              <LocationSelector
                label=""
                value={location}
                onChange={setLocation}
                disabled={isAdjusting}
              />
            </div>
            {/* Action section */}
            <div className="shrink-0 space-y-2">
              <Label>Action</Label>
              <ToggleGroup
                type="single"
                value={action}
                onValueChange={handleActionChange}
                variant="outline"
                disabled={isAdjusting}
                className="border border-input rounded-md"
              >
                <ToggleGroupItem
                  value="subtract"
                  className="px-4 border-0 data-[state=on]:bg-rose-600 data-[state=on]:text-white data-[state=off]:bg-rose-500/20 data-[state=off]:text-muted-foreground data-[state=off]:hover:bg-rose-500/30 dark:data-[state=on]:bg-amber-700 dark:data-[state=on]:text-white dark:data-[state=off]:bg-amber-700/20 dark:data-[state=off]:text-muted-foreground"
                  aria-label="Subtract stock"
                >
                  <Minus className="h-4 w-4" />
                  <span className="hidden sm:inline">Subtract</span>
                </ToggleGroupItem>
                <ToggleGroupItem
                  value="add"
                  className="px-4 border-0 data-[state=on]:bg-emerald-600 data-[state=on]:text-white data-[state=off]:bg-emerald-400/30 data-[state=off]:text-muted-foreground data-[state=off]:hover:bg-emerald-500/30 dark:data-[state=on]:bg-emerald-700 dark:data-[state=on]:text-white dark:data-[state=off]:bg-emerald-800/20 dark:data-[state=off]:text-muted-foreground"
                  aria-label="Add stock"
                >
                  <Plus className="h-4 w-4" />
                  <span className="hidden sm:inline">Add</span>
                </ToggleGroupItem>
              </ToggleGroup>
            </div>
          </div>

          {/* Products section */}
          {hasValidLocation && !hasSelectedProduct ? (
            <div className="flex-1 min-h-0 flex flex-col mt-4">
              <ProductFilterHeader
                title={listTitle}
                itemCount={sourceListCount}
                searchQuery={searchQuery}
                categoryFilters={categoryFilters}
                childCategoryFilters={childCategoryFilters}
                availableCategories={availableCategories}
                availableChildCategories={availableChildCategories}
                disabled={isAdjusting}
                showFilters={sourceListCount > 0 || action === "add"}
                onSearchChange={setSearchQuery}
                onCategoryChange={handleCategoryChange}
                onChildCategoryChange={setChildCategoryFilters}
                onClearFilters={handleClearFilters}
                onAddClick={action === "add" ? () => setAddInventoryDialogOpen(true) : undefined}
              />

              <ProductList
                items={normalizedInventory}
                selectedId={selectedInventoryId}
                onSelect={handleProductSelect}
                isLoading={inventoryQuery.isLoading}
                disabled={isAdjusting}
                emptyMessage={
                  action === "subtract"
                    ? "No inventory at this location"
                    : "No inventory at this location. Click 'Add New' above to add one."
                }
                noResultsMessage="No products found"
                searchQuery={searchQuery}
                categoryFilters={categoryFilters}
                childCategoryFilters={childCategoryFilters}
                availableCategories={availableCategories}
                availableChildCategories={availableChildCategories}
              />
            </div>
          ) : hasValidLocation &&
            hasSelectedProduct &&
            selectedNormalizedItem ? (
            <SelectedProductCard
              inventory={selectedNormalizedItem}
              existingQuantityAtLocation={currentQtyAtLocation}
              action={action}
              quantity={quantity}
              reason={reason}
              quantityWarning={quantityWarning}
              locationLabel={locationLabel}
              disabled={isAdjusting}
              onClearSelection={handleClearSelection}
              onQuantityChange={handleQuantityChange}
              onReasonChange={setReason}
              onIncrement={handleIncrement}
              onDecrement={handleDecrement}
            />
          ) : (
            <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center mt-4">
              Select a location to see available products
            </div>
          )}
        </div>

        {/* Footer buttons */}
        <div className="shrink-0 border-t bg-background">
          <DialogFooter className="px-6 py-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isAdjusting}
              className="min-h-11 sm:min-h-9"
            >
              Cancel
            </Button>
            <Button
              type="button"
              onClick={handleSubmit}
              disabled={!canSubmit}
              className={`min-h-11 sm:min-h-9 border ${
                action === "subtract"
                  ? "bg-rose-600 text-white hover:bg-rose-700 dark:bg-amber-700 dark:hover:bg-amber-800"
                  : "bg-emerald-600 text-white hover:bg-emerald-700 dark:bg-emerald-700 dark:hover:bg-emerald-800"
              }`}
            >
              {isAdjusting ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              Adjust Stock
            </Button>
          </DialogFooter>
        </div>

        {location.locationType && location.locationId && (
          <AddInventoryDialog
            open={addInventoryDialogOpen}
            onOpenChange={setAddInventoryDialogOpen}
            locationType={location.locationType}
            locationId={location.locationId}
            existingInventory={inventory}
            isSaving={isSavingInventory}
            onSubmit={handleAddNewInventory}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}
