"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ProductFilterHeader } from "@/components/stock/adjust/product-filter-header";
import { ProductList, SelectedProduct } from "./add-inventory";
import { getProducts } from "@/lib/api/products";
import type { Product, LocationType, InventoryRequest, Inventory, Category } from "@/types/api";

interface AddInventoryDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  locationType: LocationType;
  locationId: string;
  existingInventory?: Inventory[];
  isSaving?: boolean;
  onSubmit: (payload: InventoryRequest, isUpdate: boolean, inventoryId?: string) => Promise<void> | void;
}

export function AddInventoryDialog({
  open,
  onOpenChange,
  existingInventory = [],
  isSaving,
  onSubmit,
}: AddInventoryDialogProps) {
  const productsQuery = useQuery({
    queryKey: ["products"],
    queryFn: getProducts,
  });

  const products = productsQuery.data ?? [];

  const [productId, setProductId] = useState<string>("");
  const [qty, setQty] = useState<string>("1");
  const [qtyError, setQtyError] = useState<string | null>(null);

  // Filter state
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilters, setCategoryFilters] = useState<string[]>([]);
  const [childCategoryFilters, setChildCategoryFilters] = useState<string[]>([]);

  const selected: Product | undefined = useMemo(
    () => products.find((p) => p.id === productId),
    [products, productId]
  );

  // Check if the selected product already exists in the location
  const existingItem = useMemo(
    () => existingInventory.find((inv) => inv.item.id === productId),
    [existingInventory, productId]
  );

  // Compute available categories from products
  const { availableCategories, availableChildCategories } = useMemo(() => {
    const categoryMap = new Map<string, Category>();
    const childCategoryMap = new Map<string, Category>();

    for (const product of products) {
      const category = product.category;
      if (category.parentId) {
        // This is a child category
        childCategoryMap.set(category.id, category);
      } else {
        // This is a root category
        categoryMap.set(category.id, category);
      }
    }

    // Also add parent categories of child categories
    for (const product of products) {
      const category = product.category;
      if (category.parentId) {
        // Find and add parent category if not already present
        const parentProduct = products.find(
          (p) => p.category.id === category.parentId && !p.category.parentId
        );
        if (parentProduct) {
          categoryMap.set(parentProduct.category.id, parentProduct.category);
        }
      }
    }

    // Filter child categories based on selected parent category
    const filteredChildCategories = categoryFilters.length > 0
      ? Array.from(childCategoryMap.values()).filter(
          (c) => c.parentId && categoryFilters.includes(c.parentId)
        )
      : [];

    return {
      availableCategories: Array.from(categoryMap.values()).sort((a, b) =>
        a.name.localeCompare(b.name)
      ),
      availableChildCategories: filteredChildCategories.sort((a, b) =>
        a.name.localeCompare(b.name)
      ),
    };
  }, [products, categoryFilters]);

  // Filtered product count for header
  const filteredCount = useMemo(() => {
    return products.filter((product) => {
      const category = product.category;
      const matchesCategory =
        categoryFilters.length === 0 ||
        categoryFilters.includes(category.id) ||
        (category.parentId && categoryFilters.includes(category.parentId));
      const matchesChildCategory =
        childCategoryFilters.length === 0 ||
        (category.parentId && childCategoryFilters.includes(category.id));
      const query = searchQuery.toLowerCase().trim();
      const matchesSearch = !query || product.name.toLowerCase().includes(query);
      return matchesCategory && matchesChildCategory && matchesSearch;
    }).length;
  }, [products, searchQuery, categoryFilters, childCategoryFilters]);

  // When selecting a product that exists, pre-fill with current quantity
  useEffect(() => {
    if (existingItem) {
      setQty(String(existingItem.quantity));
    } else {
      setQty("1");
    }
  }, [existingItem]);

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setProductId("");
      setQty("1");
      setQtyError(null);
      setSearchQuery("");
      setCategoryFilters([]);
      setChildCategoryFilters([]);
    }
  }, [open]);

  function handleClearFilters() {
    setCategoryFilters([]);
    setChildCategoryFilters([]);
  }

  function handleClearSelection() {
    setProductId("");
    setQty("1");
    setQtyError(null);
  }

  function handleQuantityChange(value: string) {
    setQty(value);
    setQtyError(null);
  }

  async function handleSubmit() {
    const quantity = Number(qty);
    if (!productId) return;
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setQtyError("Quantity must be greater than 0.");
      return;
    }

    const isUpdate = Boolean(existingItem);
    await onSubmit({ itemId: productId, quantity }, isUpdate, existingItem?.id);
    setProductId("");
    setQty("1");
    onOpenChange(false);
  }

  const isLoading = productsQuery.isLoading;
  const hasSelection = Boolean(selected);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl h-[85dvh] max-h-[85dvh] flex flex-col overflow-hidden p-0">
        <DialogHeader className="shrink-0 p-6 pb-0">
          <DialogTitle>
            {existingItem ? "Update Inventory" : "Add Inventory"}
          </DialogTitle>
          <DialogDescription>
            {existingItem
              ? "Update the quantity for this product in this location."
              : "Select a product and enter the quantity to add to this location."}
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 min-h-0 flex flex-col px-6 py-4">
          {productsQuery.isError ? (
            <div className="flex-1 flex items-center justify-center">
              <p className="text-sm text-destructive">Failed to load products.</p>
            </div>
          ) : !hasSelection ? (
            <>
              <ProductFilterHeader
                title="Select a product"
                itemCount={filteredCount}
                searchQuery={searchQuery}
                categoryFilters={categoryFilters}
                childCategoryFilters={childCategoryFilters}
                availableCategories={availableCategories}
                availableChildCategories={availableChildCategories}
                disabled={isLoading || Boolean(isSaving)}
                showFilters={products.length > 0}
                onSearchChange={setSearchQuery}
                onCategoryChange={setCategoryFilters}
                onChildCategoryChange={setChildCategoryFilters}
                onClearFilters={handleClearFilters}
              />
              <ProductList
                products={products}
                existingInventory={existingInventory}
                selectedId={productId}
                onSelect={setProductId}
                isLoading={isLoading}
                disabled={Boolean(isSaving)}
                searchQuery={searchQuery}
                categoryFilters={categoryFilters}
                childCategoryFilters={childCategoryFilters}
                availableCategories={availableCategories}
                availableChildCategories={availableChildCategories}
              />
            </>
          ) : selected ? (
            <SelectedProduct
              product={selected}
              existingInventory={existingItem}
              quantity={qty}
              quantityError={qtyError}
              onQuantityChange={handleQuantityChange}
              onClearSelection={handleClearSelection}
              disabled={Boolean(isSaving)}
            />
          ) : null}
        </div>

        <DialogFooter className="shrink-0 border-t p-6">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={Boolean(isSaving) || !productId}
          >
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            {existingItem ? "Update" : "Add"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
