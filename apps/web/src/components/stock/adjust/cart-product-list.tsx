"use client";

import { Loader2 } from "lucide-react";
import { ScrollArea } from "@/components/ui/scroll-area";
import type { Category } from "@/types/api";
import { CartProductRow } from "./cart-product-row";
import {
  type AdjustAction,
  type CartLine,
  type NormalizedInventory,
  getNoResultsMessage,
} from "./types";
import type { QuantityIntakeMeta } from "@/components/ui/quantity-input";

interface CartProductListProps {
  items: NormalizedInventory[];
  cart: Map<string, CartLine>;
  failedInventoryId: string | null;
  action: AdjustAction;
  isLoading: boolean;
  disabled: boolean;
  emptyMessage: string;
  noResultsMessage: string;
  searchQuery: string;
  categoryFilters: string[];
  childCategoryFilters: string[];
  availableCategories: Category[];
  availableChildCategories: Category[];
  onStage: (id: string, currentStock: number) => void;
  onUnstage: (id: string) => void;
  onLineQuantityChange: (id: string, value: string) => void;
  onLineIncrement: (id: string) => void;
  onLineDecrement: (id: string) => void;
  onLineIntakeChange: (id: string, meta: QuantityIntakeMeta) => void;
}

export function CartProductList({
  items,
  cart,
  failedInventoryId,
  action,
  isLoading,
  disabled,
  emptyMessage,
  noResultsMessage,
  searchQuery,
  categoryFilters,
  childCategoryFilters,
  availableCategories,
  availableChildCategories,
  onStage,
  onUnstage,
  onLineQuantityChange,
  onLineIncrement,
  onLineDecrement,
  onLineIntakeChange,
}: CartProductListProps) {
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
    const matchesCategory =
      categoryFilters.length === 0 ||
      categoryFilters.includes(category.id) ||
      (category.parentId && categoryFilters.includes(category.parentId));
    const matchesChildCategory =
      childCategoryFilters.length === 0 ||
      (category.parentId && childCategoryFilters.includes(category.id));
    const query = searchQuery.toLowerCase().trim();
    const matchesSearch = !query || inv.item.name.toLowerCase().includes(query);
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
          {filteredItems.map((inv) => {
            const staged = cart.get(inv.id) ?? null;
            return (
              <CartProductRow
                key={inv.id}
                inventory={inv}
                action={action}
                staged={staged}
                failed={failedInventoryId === inv.id}
                disabled={disabled}
                onStage={() => onStage(inv.id, inv.quantity)}
                onUnstage={() => onUnstage(inv.id)}
                onQuantityChange={(value) => onLineQuantityChange(inv.id, value)}
                onIncrement={() => onLineIncrement(inv.id)}
                onDecrement={() => onLineDecrement(inv.id)}
                onIntakeMetaChange={(meta) => onLineIntakeChange(inv.id, meta)}
              />
            );
          })}
        </div>
      </ScrollArea>
    </div>
  );
}
