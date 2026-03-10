"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import { Check, ImageOff, Loader2, X } from "lucide-react";
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
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ProductFilterHeader } from "@/components/stock/adjust/product-filter-header";
import { getProducts } from "@/lib/api/products";
import { cn } from "@/lib/utils";
import { getSafeImageUrl } from "@/lib/utils/validation";
import type { Product, Category, MachineDisplay } from "@/types/api";

interface AddDisplayDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  activeDisplays: MachineDisplay[];
  isSaving?: boolean;
  onSubmit: (productIds: string[]) => Promise<void> | void;
}

interface DisplayProductCardProps {
  product: Product;
  selected: boolean;
  onToggle: () => void;
  disabled?: boolean;
}

function DisplayProductCard({
  product,
  selected,
  onToggle,
  disabled = false,
}: DisplayProductCardProps) {
  const [imageError, setImageError] = useState(false);

  const safeImageUrl = getSafeImageUrl(product.imageUrl);
  const hasImage = safeImageUrl && !imageError;
  const categoryLabel = product.category.name;

  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={disabled}
      className={cn(
        "overflow-hidden min-w-0 w-full flex items-center gap-2 sm:gap-4 py-3 sm:py-4 sm:px-3 border-b last:border-b-0 cursor-pointer",
        "transition-colors text-left",
        selected
          ? "bg-primary/5 border-l-2 border-l-primary pl-2"
          : "hover:bg-muted/50",
        disabled && "opacity-50 cursor-not-allowed"
      )}
    >
      <div className="relative h-12 w-12 sm:h-16 sm:w-16 shrink-0 rounded-lg overflow-hidden bg-muted">
        {hasImage ? (
          <Image
            src={safeImageUrl}
            alt={product.name}
            fill
            sizes="(max-width: 640px) 48px, 64px"
            className="object-cover"
            onError={() => setImageError(true)}
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center">
            <ImageOff className="h-5 w-5 sm:h-6 sm:w-6 text-muted-foreground" />
          </div>
        )}
        {selected && (
          <div className="absolute inset-0 bg-primary/20 flex items-center justify-center">
            <Check className="h-6 w-6 text-primary" />
          </div>
        )}
      </div>

      <div className="w-0 flex-1 overflow-hidden">
        <p className="font-medium text-xs sm:text-base truncate">{product.name}</p>
        <div className="flex flex-wrap gap-1 mt-1">
          <Badge variant="outline" className="text-[10px] sm:text-xs px-1.5 py-0">
            {categoryLabel}
          </Badge>
        </div>
      </div>

      <div className="flex flex-col items-end gap-0.5 shrink-0 pl-2">
        <Badge variant="secondary" className="text-[10px] sm:text-xs">
          New
        </Badge>
      </div>
    </button>
  );
}

export function AddDisplayDialog({
  open,
  onOpenChange,
  activeDisplays,
  isSaving,
  onSubmit,
}: AddDisplayDialogProps) {
  const productsQuery = useQuery({
    queryKey: ["products", { rootOnly: true }],
    queryFn: () => getProducts(true),
  });

  const products = productsQuery.data ?? [];

  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  // Filter state
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilters, setCategoryFilters] = useState<string[]>([]);
  const [childCategoryFilters, setChildCategoryFilters] = useState<string[]>([]);

  // Products not already on display
  const activeProductIds = useMemo(
    () => activeDisplays.map((d) => d.productId),
    [activeDisplays]
  );

  const availableProducts = useMemo(
    () => products.filter((p) => !activeProductIds.includes(p.id)),
    [products, activeProductIds]
  );

  const selectedProducts = useMemo(
    () => products.filter((p) => selectedIds.includes(p.id)),
    [products, selectedIds]
  );

  // Compute available categories from products
  const { availableCategories, availableChildCategories } = useMemo(() => {
    const categoryMap = new Map<string, Category>();
    const childCategoryMap = new Map<string, Category>();

    for (const product of availableProducts) {
      const category = product.category;
      if (category.parentId) {
        childCategoryMap.set(category.id, category);
      } else {
        categoryMap.set(category.id, category);
      }
    }

    // Also add parent categories of child categories
    for (const product of availableProducts) {
      const category = product.category;
      if (category.parentId) {
        const parentProduct = availableProducts.find(
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
  }, [availableProducts, categoryFilters]);

  // Filtered products
  const filteredProducts = useMemo(() => {
    return availableProducts.filter((product) => {
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
    });
  }, [availableProducts, searchQuery, categoryFilters, childCategoryFilters]);

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setSelectedIds([]);
      setSearchQuery("");
      setCategoryFilters([]);
      setChildCategoryFilters([]);
    }
  }, [open]);

  function handleClearFilters() {
    setCategoryFilters([]);
    setChildCategoryFilters([]);
  }

  function handleProductToggle(productId: string) {
    setSelectedIds((prev) =>
      prev.includes(productId)
        ? prev.filter((id) => id !== productId)
        : [...prev, productId]
    );
  }

  function handleRemoveSelected(productId: string) {
    setSelectedIds((prev) => prev.filter((id) => id !== productId));
  }

  async function handleSubmit() {
    if (selectedIds.length === 0) return;
    await onSubmit(selectedIds);
    setSelectedIds([]);
    onOpenChange(false);
  }

  const isLoading = productsQuery.isLoading;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl h-[85dvh] max-h-[85dvh] flex flex-col overflow-hidden p-0">
        <DialogHeader className="shrink-0 p-6 pb-0">
          <DialogTitle>Add Display</DialogTitle>
          <DialogDescription>
            Select products to add to this machine&apos;s display.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 min-h-0 flex flex-col px-6 py-4">
          {productsQuery.isError ? (
            <div className="flex-1 flex items-center justify-center">
              <p className="text-sm text-destructive">Failed to load products.</p>
            </div>
          ) : (
            <>
              <ProductFilterHeader
                title="Select products"
                itemCount={filteredProducts.length}
                searchQuery={searchQuery}
                categoryFilters={categoryFilters}
                childCategoryFilters={childCategoryFilters}
                availableCategories={availableCategories}
                availableChildCategories={availableChildCategories}
                disabled={isLoading || Boolean(isSaving)}
                showFilters={availableProducts.length > 0}
                onSearchChange={setSearchQuery}
                onCategoryChange={setCategoryFilters}
                onChildCategoryChange={setChildCategoryFilters}
                onClearFilters={handleClearFilters}
              />

              {isLoading ? (
                <div className="flex-1 flex items-center justify-center py-4">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              ) : availableProducts.length === 0 ? (
                <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                  No products available to add
                </div>
              ) : filteredProducts.length === 0 ? (
                <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
                  No products match your search
                </div>
              ) : (
                <div className="flex-1 min-h-0 w-full overflow-hidden">
                  <ScrollArea className="h-full w-full [&>[data-slot=scroll-area-viewport]]:!overflow-x-hidden">
                    <div className="w-full overflow-hidden pb-2 pr-3">
                      {filteredProducts.map((product) => (
                        <DisplayProductCard
                          key={product.id}
                          product={product}
                          selected={selectedIds.includes(product.id)}
                          onToggle={() => handleProductToggle(product.id)}
                          disabled={Boolean(isSaving)}
                        />
                      ))}
                    </div>
                  </ScrollArea>
                </div>
              )}

              {/* Selected products badges */}
              {selectedProducts.length > 0 && (
                <div className="shrink-0 pt-3 border-t mt-3">
                  <p className="text-xs text-muted-foreground mb-2">
                    Selected ({selectedProducts.length})
                  </p>
                  <div className="flex flex-wrap gap-1">
                    {selectedProducts.map((p) => (
                      <Badge key={p.id} variant="secondary" className="text-xs pr-1">
                        {p.name}
                        <button
                          onClick={() => handleRemoveSelected(p.id)}
                          className="ml-1 hover:text-destructive"
                          disabled={Boolean(isSaving)}
                        >
                          <X className="h-3 w-3" />
                        </button>
                      </Badge>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
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
            disabled={Boolean(isSaving) || selectedIds.length === 0}
          >
            {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Add {selectedIds.length > 0 ? `${selectedIds.length} Product(s)` : ""}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
