"use client";

import { memo, useCallback, useEffect, useMemo, useState } from "react";
import Image from "next/image";
import { ImageOff, Loader2 } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useVirtualizer } from "@tanstack/react-virtual";
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
import { ProductFilterHeader } from "@/components/stock/adjust/product-filter-header";
import { getProducts } from "@/lib/api/products";
import { cn } from "@/lib/utils";
import { getSafeImageUrl } from "@/lib/utils/validation";
import type { Product, Category } from "@/types/api";

interface SelectShipmentProductDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (product: Product) => void;
  excludeProductIds?: string[];
}

interface ShipmentProductCardProps {
  product: Product;
  onSelect: (product: Product) => void;
  disabled?: boolean;
}

const ShipmentProductCard = memo(function ShipmentProductCard({
  product,
  onSelect,
  disabled = false,
}: ShipmentProductCardProps) {
  const [imageError, setImageError] = useState(false);

  const safeImageUrl = getSafeImageUrl(product.imageUrl);
  const hasImage = safeImageUrl && !imageError;
  const categoryLabel = product.category.name;

  return (
    <button
      type="button"
      onClick={() => onSelect(product)}
      disabled={disabled}
      className={cn(
        "overflow-hidden min-w-0 w-full h-full flex items-center gap-2 sm:gap-4 px-3 border-b cursor-pointer",
        "transition-colors text-left",
        "hover:bg-muted/50",
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
});

export function SelectShipmentProductDialog({
  open,
  onOpenChange,
  onSelect,
  excludeProductIds = [],
}: SelectShipmentProductDialogProps) {
  const productsQuery = useQuery({
    queryKey: ["products", { rootOnly: true }],
    queryFn: () => getProducts(true),
  });

  const products = productsQuery.data ?? [];

  // Filter state
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilters, setCategoryFilters] = useState<string[]>([]);
  const [childCategoryFilters, setChildCategoryFilters] = useState<string[]>([]);

  // Products not already selected in shipment
  const availableProducts = useMemo(
    () => products.filter((p) => !excludeProductIds.includes(p.id)),
    [products, excludeProductIds]
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

  // Virtualization setup - use callback ref for proper timing
  const [scrollContainer, setScrollContainer] = useState<HTMLDivElement | null>(null);

  const virtualizer = useVirtualizer({
    count: filteredProducts.length,
    getScrollElement: () => scrollContainer,
    estimateSize: () => 88, // Row height: image (64px) + padding (24px)
    overscan: 5,
  });

  // Reset state when dialog closes
  useEffect(() => {
    if (!open) {
      setSearchQuery("");
      setCategoryFilters([]);
      setChildCategoryFilters([]);
    }
  }, [open]);

  // Scroll to top when filters change
  useEffect(() => {
    scrollContainer?.scrollTo({ top: 0 });
  }, [searchQuery, categoryFilters, childCategoryFilters, scrollContainer]);

  function handleClearFilters() {
    setCategoryFilters([]);
    setChildCategoryFilters([]);
  }

  const handleProductSelect = useCallback((product: Product) => {
    onSelect(product);
    onOpenChange(false);
  }, [onSelect, onOpenChange]);

  const isLoading = productsQuery.isLoading;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl h-[85dvh] max-h-[85dvh] flex flex-col overflow-hidden p-0">
        <DialogHeader className="shrink-0 p-6 pb-0">
          <DialogTitle>Select Product</DialogTitle>
          <DialogDescription>
            Select a product to add to this shipment.
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
                disabled={isLoading}
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
                <div
                  ref={setScrollContainer}
                  className="flex-1 min-h-0 w-full overflow-auto"
                >
                  <div
                    style={{
                      height: `${virtualizer.getTotalSize()}px`,
                      width: "100%",
                      position: "relative",
                    }}
                  >
                    {virtualizer.getVirtualItems().map((virtualRow) => {
                      const product = filteredProducts[virtualRow.index];
                      return (
                        <div
                          key={product.id}
                          style={{
                            position: "absolute",
                            top: 0,
                            left: 0,
                            width: "100%",
                            height: `${virtualRow.size}px`,
                            transform: `translateY(${virtualRow.start}px)`,
                          }}
                        >
                          <ShipmentProductCard
                            product={product}
                            onSelect={handleProductSelect}
                          />
                        </div>
                      );
                    })}
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
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
