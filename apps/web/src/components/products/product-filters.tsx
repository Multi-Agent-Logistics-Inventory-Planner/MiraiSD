"use client";

import { Search, Plus, SlidersHorizontal, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Can, Permission } from "@/components/rbac";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { Category } from "@/types/api";

export interface ProductFiltersState {
  search: string;
  selectedCategoryId: string | null;
  selectedSubcategoryId: string | null;
}

export const DEFAULT_PRODUCT_FILTERS: ProductFiltersState = {
  search: "",
  selectedCategoryId: null,
  selectedSubcategoryId: null,
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
  const rootCategories = categories.filter((c) => !c.parentId);
  const selectedCategory = rootCategories.find((c) => c.id === state.selectedCategoryId) ?? null;
  const subcategories = selectedCategory?.children ?? [];

  const handleCategorySelect = (categoryId: string) => {
    if (state.selectedCategoryId === categoryId) {
      onChange({ ...state, selectedCategoryId: null, selectedSubcategoryId: null });
    } else {
      onChange({ ...state, selectedCategoryId: categoryId, selectedSubcategoryId: null });
    }
  };

  const clearFilter = (e: React.MouseEvent) => {
    e.stopPropagation();
    onChange({ ...state, selectedCategoryId: null, selectedSubcategoryId: null });
  };

  const isFiltered = state.selectedCategoryId !== null;

  return (
    <div className="flex flex-col gap-3">
      {/* Subcategory pills — appear above the search row when a category with children is selected */}
      {subcategories.length > 0 && (
        <div className="flex flex-wrap gap-2">
          <Button
            variant={state.selectedSubcategoryId === null ? "default" : "outline"}
            size="sm"
            onClick={() => onChange({ ...state, selectedSubcategoryId: null })}
          >
            All {selectedCategory?.name}
          </Button>
          {subcategories.map((sub) => (
            <Button
              key={sub.id}
              variant={state.selectedSubcategoryId === sub.id ? "default" : "outline"}
              size="sm"
              onClick={() =>
                onChange({
                  ...state,
                  selectedSubcategoryId:
                    state.selectedSubcategoryId === sub.id ? null : sub.id,
                })
              }
            >
              {sub.name}
            </Button>
          ))}
        </div>
      )}

      {/* Search + Filter + Add Product */}
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

        <div className="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant={isFiltered ? "default" : "outline"}
                size="sm"
                className="shrink-0"
              >
                <SlidersHorizontal className="h-4 w-4 sm:mr-2" />
                <span className="hidden sm:inline">
                  {selectedCategory ? selectedCategory.name : "Filter"}
                </span>
                {isFiltered && (
                  <X
                    className="ml-1 h-3 w-3 opacity-70 hover:opacity-100"
                    onClick={clearFilter}
                  />
                )}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-48">
              {rootCategories.length === 0 ? (
                <DropdownMenuItem disabled>No categories</DropdownMenuItem>
              ) : (
                rootCategories.map((category) => (
                  <DropdownMenuItem
                    key={category.id}
                    onSelect={() => handleCategorySelect(category.id)}
                    className={
                      state.selectedCategoryId === category.id ? "bg-accent font-medium" : ""
                    }
                  >
                    {category.name}
                  </DropdownMenuItem>
                ))
              )}
            </DropdownMenuContent>
          </DropdownMenu>

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
      </div>
    </div>
  );
}
