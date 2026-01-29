"use client";

import { Loader2 } from "lucide-react";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ProductSelectCard } from "@/components/stock/product-select-card";
import { PRODUCT_CATEGORY_LABELS, type ProductCategory } from "@/types/api";
import type { NormalizedInventory } from "./types";

interface ProductListProps {
  items: NormalizedInventory[];
  selectedId: string | null;
  onSelect: (id: string, quantity: number) => void;
  isLoading: boolean;
  disabled: boolean;
  emptyMessage: string;
  noResultsMessage: string;
  searchQuery: string;
  categoryFilter: ProductCategory | null;
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
  categoryFilter,
}: ProductListProps) {
  if (isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center py-4">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  // If no items at all (before filtering)
  if (items.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
        {emptyMessage}
      </div>
    );
  }

  // Filter items
  const filteredItems = items.filter((inv) => {
    const matchesCategory = !categoryFilter || inv.item.category === categoryFilter;
    const query = searchQuery.toLowerCase().trim();
    const matchesSearch = !query || inv.item.name.toLowerCase().includes(query);
    return matchesCategory && matchesSearch;
  });

  // If items exist but none match filters
  if (filteredItems.length === 0) {
    const message = getNoResultsMessage(searchQuery, categoryFilter);
    return (
      <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
        {message || noResultsMessage}
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0">
      <ScrollArea className="h-full **:data-[slot=scroll-area-viewport]:overscroll-auto">
        <div className="pr-4 pb-2">
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

function getNoResultsMessage(
  searchQuery: string,
  categoryFilter: ProductCategory | null
): string {
  if (searchQuery && categoryFilter) {
    return `No products match "${searchQuery}" in ${PRODUCT_CATEGORY_LABELS[categoryFilter]}`;
  }
  if (searchQuery) {
    return `No products match "${searchQuery}"`;
  }
  if (categoryFilter) {
    return `No ${PRODUCT_CATEGORY_LABELS[categoryFilter]} products`;
  }
  return "No products found";
}
