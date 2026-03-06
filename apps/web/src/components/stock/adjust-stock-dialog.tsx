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
  StockMovementReason,
} from "@/types/api";
import { DEFAULT_REASON_BY_ACTION } from "./adjust/types";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useQueryClient } from "@tanstack/react-query";
import { useLocationInventory } from "@/hooks/queries/use-location-inventory";
import { useProductInventory } from "@/hooks/queries/use-product-inventory";
import { useAdjustStockMutation } from "@/hooks/mutations/use-stock-mutations";
import { createInventory } from "@/lib/api/inventory";
import { parseQuantityInput, clampQuantity } from "@/lib/utils/validation";
import { LocationSelector } from "./location-selector";
import { InventoryPreviewTooltip } from "./inventory-preview-tooltip";
import { LOCATION_TYPE_CODES, type LocationSelection } from "@/types/transfer";
import {
  ProductList,
  SelectedProductCard,
  ProductFilterHeader,
  type AdjustAction,
  type NormalizedInventory,
  createNormalizedInventory,
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
  const [selectedProductId, setSelectedProductId] = useState<string | null>(
    null,
  );
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

  const productInventoryQuery = useProductInventory();

  useEffect(() => {
    if (!open) {
      setLocation(EMPTY_LOCATION);
      setSelectedInventoryId(null);
      setSelectedProductId(null);
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
      setSelectedProductId(null);
      setQuantity(1);
      setQuantityWarning(null);
    }
  }, [location.locationId]);

  useEffect(() => {
    setSelectedInventoryId(null);
    setSelectedProductId(null);
    setQuantity(1);
    setQuantityWarning(null);
    setSearchQuery("");
    setCategoryFilters([]);
    setChildCategoryFilters([]);
    setReason(DEFAULT_REASON_BY_ACTION[action]);
  }, [action]);

  const inventory = inventoryQuery.data ?? [];
  const allProductsWithInventory = productInventoryQuery.data ?? [];

  const productsWithStock = useMemo(() => {
    return allProductsWithInventory.filter((p) => p.totalQuantity > 0);
  }, [allProductsWithInventory]);

  const normalizedInventory: NormalizedInventory[] = useMemo(() => {
    return inventory.map(normalizeInventory);
  }, [inventory]);

  const normalizedProducts: NormalizedInventory[] = useMemo(() => {
    return productsWithStock.map(createNormalizedInventory);
  }, [productsWithStock]);

  const availableCategories = useMemo(() => {
    const categoryMap = new Map<string, Category>();
    const items = action === "subtract" ? inventory : productsWithStock;
    items.forEach((item) => {
      const category =
        "item" in item ? item.item.category : item.product.category;
      if (category) {
        categoryMap.set(category.id, category);
      }
    });
    return Array.from(categoryMap.values()).sort((a, b) =>
      a.name.localeCompare(b.name)
    );
  }, [action, inventory, productsWithStock]);

  // Get child categories (categories with a parentId) from inventory or products
  const availableChildCategories = useMemo(() => {
    const childCategoryMap = new Map<string, Category>();
    const items = action === "subtract" ? inventory : productsWithStock;
    items.forEach((item) => {
      const category =
        "item" in item ? item.item.category : item.product.category;
      if (category?.parentId) {
        childCategoryMap.set(category.id, category);
      }
    });
    return Array.from(childCategoryMap.values()).sort((a, b) =>
      a.name.localeCompare(b.name)
    );
  }, [action, inventory, productsWithStock]);

  const selectedInventory = useMemo(() => {
    return inventory.find((inv) => inv.id === selectedInventoryId) ?? null;
  }, [inventory, selectedInventoryId]);

  const selectedProduct = useMemo(() => {
    return (
      productsWithStock.find((p) => p.product.id === selectedProductId) ?? null
    );
  }, [productsWithStock, selectedProductId]);

  const existingInventoryForProduct = useMemo(() => {
    if (!selectedProductId || action !== "add") return null;
    return inventory.find((inv) => inv.item.id === selectedProductId) ?? null;
  }, [inventory, selectedProductId, action]);

  const hasValidLocation = Boolean(location.locationId);
  const isAdjusting = adjustMutation.isPending;

  const hasSelectedProduct =
    action === "subtract"
      ? Boolean(selectedInventory)
      : Boolean(selectedProduct);

  const currentQtyAtLocation =
    action === "subtract"
      ? (selectedInventory?.quantity ?? 0)
      : (existingInventoryForProduct?.quantity ?? 0);

  const selectedProductName =
    action === "subtract"
      ? selectedInventory?.item.name
      : selectedProduct?.product.name;

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

  const sourceListCount =
    action === "subtract" ? inventory.length : productsWithStock.length;

  const selectedNormalizedItem: NormalizedInventory | null = useMemo(() => {
    if (action === "subtract" && selectedInventory) {
      return normalizeInventory(selectedInventory);
    }
    if (action === "add" && selectedProduct) {
      return createNormalizedInventory(selectedProduct);
    }
    return null;
  }, [action, selectedInventory, selectedProduct]);

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
    setSelectedProductId(null);
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
    if (action === "subtract") {
      setSelectedInventoryId(id);
      if (quantity > itemQuantity) {
        setQuantity(Math.max(1, itemQuantity));
      }
    } else {
      setSelectedProductId(id);
      setQuantity(1);
    }
    setQuantityWarning(null);
  }

  async function handleSubmit() {
    const actorId = user?.personId || user?.id;
    if (!actorId) {
      toast({ title: "Missing user", description: "Please sign in again." });
      return;
    }

    if (!location.locationType || !location.locationId) {
      toast({
        title: "Missing selection",
        description: "Select a location and product.",
      });
      return;
    }

    try {
      if (action === "subtract") {
        if (!selectedInventory) {
          toast({
            title: "Missing selection",
            description: "Select a product.",
          });
          return;
        }

        if (quantity > currentQtyAtLocation) {
          toast({
            title: "Invalid quantity",
            description: "Cannot subtract more than available stock.",
            variant: "destructive",
          });
          return;
        }

        await adjustMutation.mutateAsync({
          locationType: location.locationType,
          inventoryId: selectedInventory.id,
          payload: {
            quantityChange: -quantity,
            reason,
            actorId,
          },
          productId: selectedInventory.item.id,
        });
      } else {
        if (!selectedProduct) {
          toast({
            title: "Missing selection",
            description: "Select a product.",
          });
          return;
        }

        let inventoryId: string;

        if (existingInventoryForProduct) {
          inventoryId = existingInventoryForProduct.id;
        } else {
          const newInventory = await createInventory(
            location.locationType,
            location.locationId,
            { itemId: selectedProduct.product.id, quantity: 0 },
          );
          inventoryId = newInventory.id;
        }

        await adjustMutation.mutateAsync({
          locationType: location.locationType,
          inventoryId,
          payload: {
            quantityChange: quantity,
            reason,
            actorId,
          },
          productId: selectedProduct.product.id,
        });
      }

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

  const locationLabel = location.locationType
    ? `${LOCATION_TYPE_CODES[location.locationType]}${location.locationCode}`
    : "";

  const listTitle =
    action === "subtract"
      ? `Products at ${locationLabel}`
      : `Select product to add to ${locationLabel}`;

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
                showFilters={sourceListCount > 0}
                onSearchChange={setSearchQuery}
                onCategoryChange={handleCategoryChange}
                onChildCategoryChange={setChildCategoryFilters}
                onClearFilters={handleClearFilters}
              />

              <ProductList
                items={
                  action === "subtract"
                    ? normalizedInventory
                    : normalizedProducts
                }
                selectedId={
                  action === "subtract"
                    ? selectedInventoryId
                    : selectedProductId
                }
                onSelect={handleProductSelect}
                isLoading={
                  action === "subtract"
                    ? inventoryQuery.isLoading
                    : productInventoryQuery.isLoading
                }
                disabled={isAdjusting}
                emptyMessage={
                  action === "subtract"
                    ? "No inventory at this location"
                    : "No products with stock available"
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
      </DialogContent>
    </Dialog>
  );
}
