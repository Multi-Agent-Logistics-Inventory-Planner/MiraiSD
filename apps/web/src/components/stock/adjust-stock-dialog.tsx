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
  type BatchAdjustLine,
} from "@/types/api";
import { DEFAULT_REASON_BY_ACTION } from "./adjust/types";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/hooks/use-auth";
import { useQueryClient } from "@tanstack/react-query";
import { useLocationInventory } from "@/hooks/queries/use-location-inventory";
import { useCategories } from "@/hooks/queries/use-categories";
import { useBatchAdjustStockMutation } from "@/hooks/mutations/use-stock-mutations";
import {
  useCreateInventoryMutation,
  useUpdateInventoryMutation,
} from "@/hooks/mutations/use-location-mutations";
import { AddInventoryDialog } from "@/components/locations/add-inventory-dialog";
import type {
  InventoryRequest,
  ProductInventoryEntry,
  Product,
} from "@/types/api";
import { cn } from "@/lib/utils";
import { parseQuantityInput } from "@/lib/utils/validation";
import { LocationSelector } from "./location-selector";
import { InventoryPreviewTooltip } from "./inventory-preview-tooltip";
import { LOCATION_TYPE_CODES, type LocationSelection } from "@/types/transfer";
import {
  ProductList,
  SelectedProductCard,
  ProductFilterHeader,
  AdjustmentConfirmDialog,
  CartProductList,
  CartSummaryStrip,
  type AdjustAction,
  type CartLine,
  type NormalizedInventory,
  normalizeInventory,
} from "./adjust";
import { ProductLocationSelector } from "./product-location-selector";

export interface PreselectedProductInfo {
  product: Pick<Product, "id" | "name" | "sku" | "imageUrl" | "category">;
  inventoryEntries: ProductInventoryEntry[];
}

interface AdjustStockDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialLocation?: LocationSelection | null;
  preselectedProduct?: PreselectedProductInfo | null;
}

const EMPTY_LOCATION: LocationSelection = {
  locationType: null,
  locationId: null,
  locationCode: "",
};

/**
 * Best-effort extraction of an inventory UUID from a backend error message so
 * the offending row can be highlighted in the cart on failure.
 */
function parseFailedInventoryId(message: string): string | null {
  const match = message.match(
    /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i
  );
  return match ? match[0] : null;
}

export function AdjustStockDialog({
  open,
  onOpenChange,
  initialLocation: initialLocationProp,
  preselectedProduct,
}: AdjustStockDialogProps) {
  const { toast } = useToast();
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const isProductFilteredMode = Boolean(preselectedProduct);

  const [location, setLocation] = useState<LocationSelection>(EMPTY_LOCATION);
  const [addInventoryDialogOpen, setAddInventoryDialogOpen] = useState(false);
  const [action, setAction] = useState<AdjustAction>("subtract");
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilters, setCategoryFilters] = useState<string[]>([]);
  const [childCategoryFilters, setChildCategoryFilters] = useState<string[]>([]);
  const [reason, setReason] = useState<StockMovementReason>(
    DEFAULT_REASON_BY_ACTION["subtract"]
  );
  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false);
  const [failedInventoryId, setFailedInventoryId] = useState<string | null>(null);

  // Cart-mode state (multi-product batch).
  const [cart, setCart] = useState<Map<string, CartLine>>(new Map());

  // Single-mode state (preselectedProduct path). Mirrors prior single-product UX.
  const [selectedInventoryId, setSelectedInventoryId] = useState<string | null>(
    null
  );
  const [quantity, setQuantity] = useState(1);
  const [quantityWarning, setQuantityWarning] = useState<string | null>(null);
  const [intakeUnit, setIntakeUnit] = useState<"pack" | "box">("pack");
  const [intakeQty, setIntakeQty] = useState<number>(1);

  const batchAdjustMutation = useBatchAdjustStockMutation();

  const inventoryQuery = useLocationInventory(
    location.locationType ?? undefined,
    location.locationId ?? undefined
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
      setAddInventoryDialogOpen(false);
      setAction("subtract");
      setSearchQuery("");
      setCategoryFilters([]);
      setChildCategoryFilters([]);
      setReason(DEFAULT_REASON_BY_ACTION["subtract"]);
      setConfirmDialogOpen(false);
      setFailedInventoryId(null);
      setCart(new Map());
      setSelectedInventoryId(null);
      setQuantity(1);
      setQuantityWarning(null);
      setIntakeUnit("pack");
      setIntakeQty(1);
    } else if (initialLocationProp && initialLocationProp.locationType != null) {
      setLocation({
        locationType: initialLocationProp.locationType,
        locationId: initialLocationProp.locationId ?? null,
        locationCode: initialLocationProp.locationCode ?? "",
      });
    }
  }, [open, initialLocationProp]);

  // Reset per-line state when the location changes.
  useEffect(() => {
    if (location.locationId) {
      setCart(new Map());
      setSelectedInventoryId(null);
      setAddInventoryDialogOpen(false);
      setQuantity(1);
      setQuantityWarning(null);
      setFailedInventoryId(null);
    }
  }, [location.locationId]);

  // Action toggle clears the cart and single-mode selection.
  useEffect(() => {
    setCart(new Map());
    setSelectedInventoryId(null);
    setAddInventoryDialogOpen(false);
    setQuantity(1);
    setQuantityWarning(null);
    setSearchQuery("");
    setCategoryFilters([]);
    setChildCategoryFilters([]);
    setReason(DEFAULT_REASON_BY_ACTION[action]);
    setFailedInventoryId(null);
  }, [action]);

  const inventory = inventoryQuery.data ?? [];

  const normalizedInventory: NormalizedInventory[] = useMemo(
    () => inventory.map(normalizeInventory),
    [inventory]
  );

  const { data: allCategories } = useCategories();

  const { presentRootIds, presentChildIds } = useMemo(() => {
    const roots = new Set<string>();
    const children = new Set<string>();
    inventory.forEach((item) => {
      const category = item.item.category;
      if (!category) return;
      if (category.parentId) {
        roots.add(category.parentId);
        children.add(category.id);
      } else {
        roots.add(category.id);
      }
    });
    return { presentRootIds: roots, presentChildIds: children };
  }, [inventory]);

  const availableCategories = useMemo<Category[]>(
    () =>
      (allCategories ?? []).filter(
        (c) => !c.parentId && presentRootIds.has(c.id)
      ),
    [allCategories, presentRootIds]
  );

  const availableChildCategories = useMemo<Category[]>(() => {
    const selectedCategoryId = categoryFilters[0];
    if (!selectedCategoryId) return [];
    const selected = availableCategories.find((c) => c.id === selectedCategoryId);
    return (selected?.children ?? []).filter((c) => presentChildIds.has(c.id));
  }, [availableCategories, categoryFilters, presentChildIds]);

  // ----- Single-mode (preselectedProduct) helpers -----

  const selectedInventory = useMemo(
    () => inventory.find((inv) => inv.id === selectedInventoryId) ?? null,
    [inventory, selectedInventoryId]
  );

  const currentQtyAtLocation = selectedInventory?.quantity ?? 0;

  const availableForSubtract = currentQtyAtLocation;

  // Auto-select the preselected product when location inventory loads (single mode).
  useEffect(() => {
    if (
      isProductFilteredMode &&
      preselectedProduct &&
      location.locationId &&
      inventory.length > 0 &&
      !selectedInventoryId
    ) {
      const matchingInventory = inventory.find(
        (inv) => inv.item.id === preselectedProduct.product.id
      );
      if (matchingInventory) {
        setSelectedInventoryId(matchingInventory.id);
      }
    }
  }, [
    isProductFilteredMode,
    preselectedProduct,
    location.locationId,
    inventory,
    selectedInventoryId,
  ]);

  // Clear single-mode selection if the inventory disappeared (e.g. drained to 0).
  useEffect(() => {
    if (selectedInventoryId && inventory.length > 0) {
      const stillExists = inventory.some((inv) => inv.id === selectedInventoryId);
      if (!stillExists) {
        setSelectedInventoryId(null);
        setQuantity(1);
        setQuantityWarning(null);
      }
    }
  }, [inventory, selectedInventoryId]);

  // ----- Submission shared logic -----

  const hasValidLocation = Boolean(location.locationId);
  const isAdjusting = batchAdjustMutation.isPending;
  const isSavingInventory =
    createInventoryMutation.isPending || updateInventoryMutation.isPending;

  const locationLabel = location.locationType
    ? `${LOCATION_TYPE_CODES[location.locationType]}${location.locationCode}`
    : "";

  /** Build batch payload from cart entries (cart mode). */
  const cartLines = useMemo<BatchAdjustLine[]>(() => {
    const out: BatchAdjustLine[] = [];
    for (const line of cart.values()) {
      const signed = action === "subtract" ? -line.quantity : line.quantity;
      out.push({
        inventoryId: line.inventoryId,
        quantityChange: signed,
        intakeUnit: line.intakeUnit === "box" ? "box" : undefined,
        intakeQty: line.intakeUnit === "box" ? line.intakeQty : undefined,
      });
    }
    return out;
  }, [cart, action]);

  const cartTotalQuantity = useMemo(
    () => [...cart.values()].reduce((sum, l) => sum + l.quantity, 0),
    [cart]
  );

  async function submitBatch(
    adjustments: BatchAdjustLine[],
    productIds: string[]
  ) {
    const actorId = user?.personId || user?.id;
    if (!actorId) {
      toast({ title: "Missing user", description: "Please sign in again." });
      return false;
    }
    if (!location.locationType || !location.locationId) {
      toast({
        title: "Missing selection",
        description: "Select a location.",
      });
      return false;
    }
    if (adjustments.length === 0) {
      toast({ title: "Nothing to adjust", description: "Stage at least one product." });
      return false;
    }

    try {
      await batchAdjustMutation.mutateAsync({
        payload: {
          locationType: location.locationType,
          locationId: location.locationId,
          adjustments,
          reason,
          actorId,
        },
        productIds,
      });

      await queryClient.invalidateQueries({
        queryKey: [
          "locationInventory",
          location.locationType,
          location.locationId,
        ],
      });

      toast({ title: "Stock adjusted", variant: "success" });
      setFailedInventoryId(null);
      return true;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Adjustment failed";
      const culprit = parseFailedInventoryId(message);
      setFailedInventoryId(culprit);
      toast({
        title: "Adjustment failed",
        description: message,
        variant: "destructive",
      });
      return false;
    }
  }

  function requireConfirmThenSubmit(submit: () => Promise<void>) {
    if (action === "subtract" && reason === StockMovementReason.ADJUSTMENT) {
      setConfirmDialogOpen(true);
      // The confirm handlers will retry the submission.
      pendingSubmitRef.current = submit;
      return;
    }
    void submit();
  }

  const pendingSubmitRef = useMemo(
    () => ({ current: null as null | (() => Promise<void>) }),
    []
  );

  function handleConfirmAdjustment() {
    setConfirmDialogOpen(false);
    const fn = pendingSubmitRef.current;
    pendingSubmitRef.current = null;
    if (fn) void fn();
  }

  function handleCancelAdjustment() {
    setConfirmDialogOpen(false);
    pendingSubmitRef.current = null;
  }

  function handleActionChange(value: string) {
    if (value === "add" || value === "subtract") {
      setAction(value);
    }
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

  // ----- Cart-mode handlers -----

  function clampLineQuantity(value: number, currentStock: number): number {
    if (action === "subtract") {
      return Math.max(1, Math.min(value, currentStock));
    }
    return Math.max(1, value);
  }

  function handleStage(id: string, currentStock: number) {
    setFailedInventoryId(null);
    setCart((prev) => {
      const next = new Map(prev);
      if (!next.has(id)) {
        next.set(id, {
          inventoryId: id,
          quantity: clampLineQuantity(1, currentStock),
          intakeUnit: "pack",
          intakeQty: 1,
        });
      }
      return next;
    });
  }

  function handleUnstage(id: string) {
    setCart((prev) => {
      const next = new Map(prev);
      next.delete(id);
      return next;
    });
  }

  function handleLineQuantityChange(id: string, value: string) {
    const parsed = parseQuantityInput(value);
    const inv = inventory.find((i) => i.id === id);
    const stock = inv?.quantity ?? 0;

    setCart((prev) => {
      const line = prev.get(id);
      if (!line) return prev;
      const newQty =
        parsed === null ? 1 : clampLineQuantity(parsed, stock);
      const next = new Map(prev);
      next.set(id, { ...line, quantity: newQty });
      return next;
    });
  }

  function handleLineIncrement(id: string) {
    const inv = inventory.find((i) => i.id === id);
    const stock = inv?.quantity ?? 0;
    setCart((prev) => {
      const line = prev.get(id);
      if (!line) return prev;
      const newQty = clampLineQuantity(line.quantity + 1, stock);
      const next = new Map(prev);
      next.set(id, { ...line, quantity: newQty });
      return next;
    });
  }

  function handleLineDecrement(id: string) {
    setCart((prev) => {
      const line = prev.get(id);
      if (!line || line.quantity <= 1) return prev;
      const next = new Map(prev);
      next.set(id, { ...line, quantity: line.quantity - 1 });
      return next;
    });
  }

  function handleLineIntakeChange(
    id: string,
    meta: { unit: "pack" | "box"; rawQty: number }
  ) {
    setCart((prev) => {
      const line = prev.get(id);
      if (!line) return prev;
      const next = new Map(prev);
      next.set(id, { ...line, intakeUnit: meta.unit, intakeQty: meta.rawQty });
      return next;
    });
  }

  function handleCartReasonChange(newReason: StockMovementReason) {
    setReason(newReason);
  }

  async function handleCartSubmit() {
    if (cartLines.length === 0) return;
    const productIds = [...cart.keys()]
      .map((id) => inventory.find((inv) => inv.id === id)?.item.id)
      .filter((id): id is string => Boolean(id));

    const doSubmit = async () => {
      const ok = await submitBatch(cartLines, productIds);
      if (ok) {
        setCart(new Map());
      }
    };

    requireConfirmThenSubmit(doSubmit);
  }

  // ----- Single-mode handlers (preselectedProduct) -----

  function handleSingleQuantityChange(value: string) {
    const parsed = parseQuantityInput(value);
    if (parsed === null) {
      setQuantity(0);
      setQuantityWarning(null);
      return;
    }
    if (action === "subtract" && parsed > availableForSubtract) {
      setQuantity(availableForSubtract);
      setQuantityWarning(`Clamped to available stock (${availableForSubtract})`);
    } else {
      setQuantity(Math.max(1, parsed));
      setQuantityWarning(null);
    }
  }

  function handleSingleIncrement() {
    if (action === "subtract") {
      if (quantity < availableForSubtract) {
        setQuantity(quantity + 1);
        setQuantityWarning(null);
      }
    } else {
      setQuantity(quantity + 1);
      setQuantityWarning(null);
    }
  }

  function handleSingleDecrement() {
    if (quantity > 1) {
      setQuantity(quantity - 1);
      setQuantityWarning(null);
    }
  }

  function handleClearSingleSelection() {
    setSelectedInventoryId(null);
    setQuantity(1);
    setQuantityWarning(null);
    setIntakeUnit("pack");
    setIntakeQty(1);
  }

  function handleSingleProductSelect(id: string, itemQuantity: number) {
    setSelectedInventoryId(id);
    if (action === "subtract" && quantity > itemQuantity) {
      setQuantity(Math.max(1, itemQuantity));
    }
    setQuantityWarning(null);
  }

  function handleSingleReasonChange(newReason: StockMovementReason) {
    setReason(newReason);
  }

  async function handleSingleSubmit() {
    if (!selectedInventory) return;
    if (action === "subtract" && quantity > availableForSubtract) {
      toast({
        title: "Invalid quantity",
        description: "Cannot subtract more than available stock.",
        variant: "destructive",
      });
      return;
    }
    const adjustments: BatchAdjustLine[] = [
      {
        inventoryId: selectedInventory.id,
        quantityChange: action === "subtract" ? -quantity : quantity,
        intakeUnit: intakeUnit === "box" ? "box" : undefined,
        intakeQty: intakeUnit === "box" ? intakeQty : undefined,
      },
    ];
    const productIds = [selectedInventory.item.id];

    const doSubmit = async () => {
      const ok = await submitBatch(adjustments, productIds);
      if (ok) {
        // Match prior UX: stay on dialog, reset selection so user can adjust another product.
        setSelectedInventoryId(null);
        setQuantity(1);
        setQuantityWarning(null);
      }
    };

    requireConfirmThenSubmit(doSubmit);
  }

  // ----- Add-new-inventory (preselectedProduct, location empty) -----

  async function handleAddNewInventory(
    payload: InventoryRequest,
    isUpdate: boolean,
    inventoryId?: string
  ) {
    const actorId = user?.personId || user?.id;
    const enrichedPayload: InventoryRequest = {
      ...payload,
      actorId,
      reason: StockMovementReason.RESTOCK,
    };

    try {
      if (isUpdate && inventoryId) {
        await updateInventoryMutation.mutateAsync({
          inventoryId,
          payload: enrichedPayload,
        });
      } else {
        await createInventoryMutation.mutateAsync(enrichedPayload);
      }
      toast({ title: "Inventory added successfully", variant: "success" });
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to save inventory";
      toast({
        title: "Error",
        description: message,
        variant: "destructive",
      });
    }
  }

  async function handleAddPreselectedProduct() {
    if (!preselectedProduct || !location.locationType || !location.locationId) {
      return;
    }
    const actorId = user?.personId || user?.id;
    if (!actorId) {
      toast({ title: "Missing user", description: "Please sign in again." });
      return;
    }
    if (quantity < 1) {
      toast({
        title: "Invalid quantity",
        description: "Quantity must be at least 1.",
      });
      return;
    }
    const payload: InventoryRequest = {
      itemId: preselectedProduct.product.id,
      quantity,
      actorId,
      reason: StockMovementReason.RESTOCK,
    };

    try {
      await createInventoryMutation.mutateAsync(payload);
      await queryClient.invalidateQueries({
        queryKey: [
          "locationInventory",
          location.locationType,
          location.locationId,
        ],
      });
      await queryClient.invalidateQueries({
        queryKey: ["productInventoryEntries", preselectedProduct.product.id],
      });
      toast({ title: "Product added to location", variant: "success" });
      setQuantity(1);
      onOpenChange(false);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to add product";
      toast({
        title: "Error",
        description: message,
        variant: "destructive",
      });
    }
  }

  // ----- Render -----

  const cartItemCount = cart.size;
  const cartCanSubmit =
    hasValidLocation && cartItemCount > 0 && !isAdjusting;
  const singleCanSubmit =
    hasValidLocation &&
    Boolean(selectedInventory) &&
    quantity >= 1 &&
    (action === "add" || quantity <= availableForSubtract) &&
    !isAdjusting;

  const listTitle =
    action === "subtract"
      ? `Products at ${locationLabel}`
      : `Select product at ${locationLabel} to add stock`;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl h-[95dvh] max-h-[95dvh] flex flex-col overflow-hidden p-0">
        <DialogHeader className="shrink-0 p-6 pb-0">
          <DialogTitle>
            {isProductFilteredMode && preselectedProduct
              ? `Adjust Stock: ${preselectedProduct.product.name}`
              : "Adjust Stock"}
          </DialogTitle>
        </DialogHeader>

        <div className="flex-1 min-h-0 flex flex-col overflow-hidden px-6">
          {/* Location + action header */}
          <div className="shrink-0 bg-[#f0eee6] dark:bg-[#1f1e1d] py-4 px-5 rounded-xl mt-4 flex gap-4 sm:gap-16">
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
              {isProductFilteredMode &&
              preselectedProduct &&
              preselectedProduct.inventoryEntries.length > 0 ? (
                <ProductLocationSelector
                  inventoryEntries={preselectedProduct.inventoryEntries}
                  value={location}
                  onChange={setLocation}
                  disabled={isAdjusting}
                />
              ) : (
                <LocationSelector
                  label=""
                  value={location}
                  onChange={setLocation}
                  disabled={isAdjusting}
                  excludeDisplayOnly
                />
              )}
            </div>
            <div className="shrink-0 space-y-2">
              <Label>Action</Label>
              <ToggleGroup
                type="single"
                value={action}
                onValueChange={handleActionChange}
                variant="outline"
                disabled={isAdjusting}
                className="border rounded-md dark:border-[#41413d]"
              >
                <ToggleGroupItem
                  value="subtract"
                  className="px-4 border-none data-[state=on]:bg-rose-600 data-[state=on]:text-white data-[state=off]:bg-rose-500/20 data-[state=off]:text-muted-foreground data-[state=off]:hover:bg-rose-500/30 dark:data-[state=on]:bg-amber-700 dark:data-[state=on]:text-white dark:data-[state=off]:bg-amber-700/20 dark:data-[state=off]:text-muted-foreground"
                  aria-label="Subtract stock"
                >
                  <Minus className="h-4 w-4" />
                  <span className="hidden sm:inline">Subtract</span>
                </ToggleGroupItem>
                <ToggleGroupItem
                  value="add"
                  className="px-4 border-none data-[state=on]:bg-emerald-600 data-[state=on]:text-white data-[state=off]:bg-emerald-400/30 data-[state=off]:text-muted-foreground data-[state=off]:hover:bg-emerald-500/30 dark:data-[state=on]:bg-emerald-700 dark:data-[state=on]:text-white dark:data-[state=off]:bg-emerald-800/20 dark:data-[state=off]:text-muted-foreground"
                  aria-label="Add stock"
                >
                  <Plus className="h-4 w-4" />
                  <span className="hidden sm:inline">Add</span>
                </ToggleGroupItem>
              </ToggleGroup>
            </div>
          </div>

          {/* Body */}
          {hasValidLocation &&
          isProductFilteredMode &&
          preselectedProduct &&
          preselectedProduct.inventoryEntries.length === 0 &&
          !inventoryQuery.isLoading ? (
            // Preselected product not yet at this location → add form.
            <div className="flex-1 flex flex-col items-center justify-center rounded-md border border-dashed p-6 text-center mt-4 gap-4">
              <div className="text-sm text-muted-foreground">
                <p className="font-medium text-foreground mb-1">
                  {preselectedProduct.product.name}
                </p>
                <p>This product doesn&apos;t exist at this location yet.</p>
              </div>
              {action === "add" ? (
                <div className="flex flex-col items-center gap-4">
                  <div className="flex items-center gap-2">
                    <Label className="text-sm text-muted-foreground">
                      Quantity:
                    </Label>
                    <div className="flex items-center gap-1">
                      <Button
                        type="button"
                        variant="outline"
                        size="icon"
                        className="h-8 w-8"
                        onClick={handleSingleDecrement}
                        disabled={quantity <= 1 || isSavingInventory}
                      >
                        <Minus className="h-4 w-4" />
                      </Button>
                      <input
                        type="text"
                        inputMode="numeric"
                        value={quantity}
                        onChange={(e) => handleSingleQuantityChange(e.target.value)}
                        className="h-8 w-16 text-center border rounded-md text-sm"
                        disabled={isSavingInventory}
                      />
                      <Button
                        type="button"
                        variant="outline"
                        size="icon"
                        className="h-8 w-8"
                        onClick={handleSingleIncrement}
                        disabled={isSavingInventory}
                      >
                        <Plus className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                  <Button
                    onClick={handleAddPreselectedProduct}
                    disabled={isSavingInventory || quantity < 1}
                    className="bg-emerald-600 text-white hover:bg-emerald-700 dark:bg-emerald-700 dark:hover:bg-emerald-800"
                  >
                    {isSavingInventory ? (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    ) : (
                      <Plus className="h-4 w-4 mr-2" />
                    )}
                    Add to Location
                  </Button>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">
                  Switch to &quot;Add&quot; mode to add this product to the location.
                </p>
              )}
            </div>
          ) : hasValidLocation && isProductFilteredMode ? (
            // Single-product UX: pick the one product, single quantity, reason.
            selectedInventory ? (
              <div className="flex-1 min-h-0 overflow-y-auto mt-4">
                <SelectedProductCard
                  inventory={normalizeInventory(selectedInventory)}
                  existingQuantityAtLocation={currentQtyAtLocation}
                  action={action}
                  quantity={quantity}
                  reason={reason}
                  quantityWarning={quantityWarning}
                  locationLabel={locationLabel}
                  disabled={isAdjusting}
                  onClearSelection={handleClearSingleSelection}
                  onQuantityChange={handleSingleQuantityChange}
                  onReasonChange={handleSingleReasonChange}
                  onIncrement={handleSingleIncrement}
                  onDecrement={handleSingleDecrement}
                  onIntakeMetaChange={(meta) => {
                    setIntakeUnit(meta.unit);
                    setIntakeQty(meta.rawQty);
                  }}
                />
              </div>
            ) : inventoryQuery.isLoading ? (
              <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center mt-4">
                <Loader2 className="h-5 w-5 animate-spin" />
              </div>
            ) : (
              <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center mt-4">
                {/* Inventory loaded but auto-select hasn't fired yet — fall back to a list. */}
                <ProductList
                  items={normalizedInventory}
                  selectedId={selectedInventoryId}
                  onSelect={handleSingleProductSelect}
                  isLoading={false}
                  disabled={isAdjusting}
                  emptyMessage="No inventory at this location"
                  noResultsMessage="No products found"
                  searchQuery={searchQuery}
                  categoryFilters={categoryFilters}
                  childCategoryFilters={childCategoryFilters}
                  availableCategories={availableCategories}
                  availableChildCategories={availableChildCategories}
                />
              </div>
            )
          ) : hasValidLocation ? (
            // Cart-mode: multi-product staging.
            <div className="flex-1 min-h-0 flex flex-col mt-4">
              <ProductFilterHeader
                title={listTitle}
                itemCount={inventory.length}
                searchQuery={searchQuery}
                categoryFilters={categoryFilters}
                childCategoryFilters={childCategoryFilters}
                availableCategories={availableCategories}
                availableChildCategories={availableChildCategories}
                disabled={isAdjusting}
                showFilters={inventory.length > 0 || action === "add"}
                onSearchChange={setSearchQuery}
                onCategoryChange={handleCategoryChange}
                onChildCategoryChange={setChildCategoryFilters}
                onClearFilters={handleClearFilters}
                onAddClick={
                  action === "add" ? () => setAddInventoryDialogOpen(true) : undefined
                }
              />

              <CartProductList
                items={normalizedInventory}
                cart={cart}
                failedInventoryId={failedInventoryId}
                action={action}
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
                onStage={handleStage}
                onUnstage={handleUnstage}
                onLineQuantityChange={handleLineQuantityChange}
                onLineIncrement={handleLineIncrement}
                onLineDecrement={handleLineDecrement}
                onLineIntakeChange={handleLineIntakeChange}
              />
            </div>
          ) : (
            <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center mt-4">
              {isProductFilteredMode
                ? "Select a location to adjust stock"
                : "Select a location to see available products"}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="shrink-0 border-t bg-background">
          {/* Cart summary strip (cart-mode only). */}
          {!isProductFilteredMode && hasValidLocation && (
            <CartSummaryStrip
              itemCount={cartItemCount}
              totalQuantity={cartTotalQuantity}
              action={action}
              reason={reason}
              disabled={isAdjusting}
              onReasonChange={handleCartReasonChange}
            />
          )}
          <DialogFooter className="px-6 py-3">
            <Button
              type="button"
              onClick={isProductFilteredMode ? handleSingleSubmit : handleCartSubmit}
              disabled={
                isProductFilteredMode ? !singleCanSubmit : !cartCanSubmit
              }
              className={cn(
                "min-h-11 sm:min-h-9 border w-full sm:w-auto",
                action === "subtract"
                  ? "bg-rose-600 text-white hover:bg-rose-700 dark:bg-amber-700 dark:hover:bg-amber-800"
                  : "bg-emerald-600 text-white hover:bg-emerald-700 dark:bg-emerald-700 dark:hover:bg-emerald-800"
              )}
            >
              {isAdjusting ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              {isProductFilteredMode
                ? "Adjust Stock"
                : `Adjust Stock${cartItemCount > 0 ? ` (${cartItemCount})` : ""}`}
            </Button>
          </DialogFooter>
        </div>

        <AdjustmentConfirmDialog
          open={confirmDialogOpen}
          onConfirm={handleConfirmAdjustment}
          onCancel={handleCancelAdjustment}
        />

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
