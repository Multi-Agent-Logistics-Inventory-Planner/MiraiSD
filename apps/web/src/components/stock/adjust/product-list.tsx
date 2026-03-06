"use client";

import { Loader2 } from "lucide-react";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ProductSelectCard } from "@/components/stock/product-select-card";
import type { Category } from "@/types/api";
import { type NormalizedInventory, getNoResultsMessage } from "./types";

interface ProductListProps {
  items: NormalizedInventory[];
  selectedId: string | null;
  onSelect: (id: string, quantity: number) => void;
  isLoading: boolean;
  disabled: boolean;
  emptyMessage: string;
  noResultsMessage: string;
  searchQuery: string;
  categoryFilters: string[];
  childCategoryFilters: string[];
  availableCategories: Category[];
  availableChildCategories: Category[];
}

export function ProductList({
  items,
  selectedId,
  onSelect,
  isLoading,
  disabled,
  emptyMessage,
  noResultsMessage,
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

  if (items.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
        {emptyMessage}
      </div>
    );
  }

  const filteredItems = items.filter((inv) => {
    const category = inv.item.category;
    // Check if product's category matches root category filter
    // A product matches if its category OR its category's parent is in the filter
    const matchesCategory =
      categoryFilters.length === 0 ||
      categoryFilters.includes(category.id) ||
      (category.parentId && categoryFilters.includes(category.parentId));
    // Check if product's category matches child category filter (for products in subcategories)
    const matchesChildCategory =
      childCategoryFilters.length === 0 ||
      (category.parentId && childCategoryFilters.includes(category.id));
    const query = searchQuery.toLowerCase().trim();
    const matchesSearch =
      !query || inv.item.name.toLowerCase().includes(query);
    return matchesCategory && matchesChildCategory && matchesSearch;
  });

  if (filteredItems.length === 0) {
    const message = getNoResultsMessage(
      searchQuery,
      categoryFilters,
      childCategoryFilters,
      availableCategories,
      availableChildCategories
    );
    return (
      <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
        {message || noResultsMessage}
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0 w-full overflow-hidden">
      <ScrollArea className="h-full w-full [&>[data-slot=scroll-area-viewport]]:!overflow-x-hidden">
        <div className="w-full overflow-hidden pb-2 pr-3">
          {filteredItems.map((inv) => (
            <ProductSelectCard
              key={inv.id}
              inventory={inv}
              selected={selectedId === inv.id}
              onSelect={() => onSelect(inv.id, inv.quantity)}
              disabled={disabled}
            />
          ))}
        </div>
      </ScrollArea>
    </div>
  );
}
