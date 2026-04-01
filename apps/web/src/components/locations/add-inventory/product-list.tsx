"use client";

import { Loader2 } from "lucide-react";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ProductCard } from "./product-card";
import type { Product, Category, LocationInventory } from "@/types/api";

interface ProductListProps {
  products: Product[];
  existingInventory: LocationInventory[];
  selectedId: string | null;
  onSelect: (productId: string) => void;
  isLoading: boolean;
  disabled: boolean;
  searchQuery: string;
  categoryFilters: string[];
  childCategoryFilters: string[];
  availableCategories: Category[];
  availableChildCategories: Category[];
}

function getNoResultsMessage(
  searchQuery: string,
  categoryFilters: string[],
  childCategoryFilters: string[],
  availableCategories: Category[] = [],
  availableChildCategories: Category[] = []
): string {
  const parts: string[] = [];

  if (searchQuery) {
    parts.push(`matching "${searchQuery}"`);
  }

  if (categoryFilters.length > 0) {
    const categoryNames = categoryFilters
      .map((id) => availableCategories.find((c) => c.id === id)?.name ?? id)
      .join(", ");
    parts.push(`in ${categoryNames}`);
  }

  if (childCategoryFilters.length > 0) {
    const childCategoryNames = childCategoryFilters
      .map((id) => availableChildCategories.find((s) => s.id === id)?.name ?? id)
      .join(", ");
    parts.push(`(${childCategoryNames})`);
  }

  if (parts.length === 0) {
    return "No products found";
  }

  return `No products ${parts.join(" ")}`;
}

export function ProductList({
  products,
  existingInventory,
  selectedId,
  onSelect,
  isLoading,
  disabled,
  searchQuery,
  categoryFilters,
  childCategoryFilters,
  availableCategories,
  availableChildCategories,
}: ProductListProps) {
  if (isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center py-4">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (products.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
        No products available
      </div>
    );
  }

  const filteredProducts = products.filter((product) => {
    const category = product.category;
    // Check if product's category matches root category filter
    const matchesCategory =
      categoryFilters.length === 0 ||
      categoryFilters.includes(category.id) ||
      (category.parentId && categoryFilters.includes(category.parentId));
    // Check if product's category matches child category filter
    const matchesChildCategory =
      childCategoryFilters.length === 0 ||
      (category.parentId && childCategoryFilters.includes(category.id));
    const query = searchQuery.toLowerCase().trim();
    const matchesSearch =
      !query || product.name.toLowerCase().includes(query);
    return matchesCategory && matchesChildCategory && matchesSearch;
  });

  if (filteredProducts.length === 0) {
    const message = getNoResultsMessage(
      searchQuery,
      categoryFilters,
      childCategoryFilters,
      availableCategories,
      availableChildCategories
    );
    return (
      <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
        {message}
      </div>
    );
  }

  // Create a map for quick lookup of existing inventory
  const existingMap = new Map(
    existingInventory.map((inv) => [inv.item.id, inv.quantity])
  );

  return (
    <div className="flex-1 min-h-0 w-full overflow-hidden">
      <ScrollArea className="h-full w-full [&>[data-slot=scroll-area-viewport]]:!overflow-x-hidden">
        <div className="w-full overflow-hidden pb-2 pr-3">
          {filteredProducts.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              existingQuantity={existingMap.get(product.id)}
              selected={selectedId === product.id}
              onSelect={() => onSelect(product.id)}
              disabled={disabled}
            />
          ))}
        </div>
      </ScrollArea>
    </div>
  );
}
