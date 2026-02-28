"use client";

import { Search, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Can, Permission } from "@/components/rbac";
import { Toggle } from "@/components/ui/toggle";
import type { Category } from "@/types/api";

export interface ProductFiltersState {
  search: string;
  categoryIds: string[];
}

export const DEFAULT_PRODUCT_FILTERS: ProductFiltersState = {
  search: "",
  categoryIds: [],
};

interface ProductFiltersProps {
  state: ProductFiltersState;
  onChange: (next: ProductFiltersState) => void;
  categories: Category[];
  onAddClick?: () => void;
}

export function ProductFilters({
  state,
  onChange,
  categories,
  onAddClick,
}: ProductFiltersProps) {
  const toggleCategory = (categoryId: string) => {
    const isSelected = state.categoryIds.includes(categoryId);
    const newCategoryIds = isSelected
      ? state.categoryIds.filter((id) => id !== categoryId)
      : [...state.categoryIds, categoryId];
    onChange({ ...state, categoryIds: newCategoryIds });
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2 sm:gap-3">
        <div className="relative flex-1 sm:flex-none sm:w-64">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search by name or SKU"
            value={state.search}
            onChange={(e) => onChange({ ...state, search: e.target.value })}
            className="pl-9"
          />
        </div>

        {onAddClick && (
          <Can permission={Permission.PRODUCTS_CREATE}>
            <Button
              onClick={onAddClick}
              size="sm"
              className="shrink-0 text-white bg-[#0b66c2] dark:bg-[#7c3aed] dark:text-foreground"
            >
              <Plus className="h-4 w-4 sm:mr-2" />
              <span className="hidden sm:inline">Add Product</span>
            </Button>
          </Can>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {categories.map((category) => (
          <Toggle
            key={category.id}
            pressed={state.categoryIds.includes(category.id)}
            onPressedChange={() => toggleCategory(category.id)}
            variant="outline"
            size="sm"
            className="data-[state=on]:bg-primary data-[state=on]:text-primary-foreground"
          >
            {category.name}
          </Toggle>
        ))}
      </div>
    </div>
  );
}
