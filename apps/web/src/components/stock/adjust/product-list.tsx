"use client";

import { Loader2 } from "lucide-react";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ProductSelectCard } from "@/components/stock/product-select-card";
import type { ProductCategory, ProductSubcategory } from "@/types/api";
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
  categoryFilters: ProductCategory[];
  subcategoryFilters: ProductSubcategory[];
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
  subcategoryFilters,
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
    const matchesCategory =
      categoryFilters.length === 0 ||
      categoryFilters.includes(inv.item.category);
    const matchesSubcategory =
      subcategoryFilters.length === 0 ||
      (inv.item.subcategory &&
        subcategoryFilters.includes(inv.item.subcategory));
    const query = searchQuery.toLowerCase().trim();
    const matchesSearch =
      !query || inv.item.name.toLowerCase().includes(query);
    return matchesCategory && matchesSubcategory && matchesSearch;
  });

  if (filteredItems.length === 0) {
    const message = getNoResultsMessage(
      searchQuery,
      categoryFilters,
      subcategoryFilters
    );
    return (
      <div className="flex-1 flex items-center justify-center rounded-md border border-dashed p-4 text-sm text-muted-foreground text-center">
        {message || noResultsMessage}
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0">
      <ScrollArea className="h-full **:data-[slot=scroll-area-viewport]:overscroll-auto">
        <div className="pb-2">
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

