"use client";

import { useState } from "react";
import { Filter, Search, X, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  MultiSelect,
  type MultiSelectOption,
} from "@/components/ui/multi-select";
import {
  ProductCategory,
  PRODUCT_CATEGORY_LABELS,
  PRODUCT_SUBCATEGORY_LABELS,
  type ProductSubcategory,
} from "@/types/api";
import { cn } from "@/lib/utils";

interface ProductFilterHeaderProps {
  title: string;
  itemCount: number;
  searchQuery: string;
  categoryFilters: ProductCategory[];
  subcategoryFilters: ProductSubcategory[];
  availableCategories: ProductCategory[];
  availableSubcategories: ProductSubcategory[];
  disabled: boolean;
  showFilters: boolean;
  onSearchChange: (value: string) => void;
  onCategoryChange: (categories: ProductCategory[]) => void;
  onSubcategoryChange: (subcategories: ProductSubcategory[]) => void;
  onClearFilters: () => void;
}

export function ProductFilterHeader({
  title,
  itemCount,
  searchQuery,
  categoryFilters,
  subcategoryFilters,
  availableCategories,
  availableSubcategories,
  disabled,
  showFilters,
  onSearchChange,
  onCategoryChange,
  onSubcategoryChange,
  onClearFilters,
}: ProductFilterHeaderProps) {
  const [isFilterOpen, setIsFilterOpen] = useState(false);

  const hasActiveFilters =
    categoryFilters.length > 0 || subcategoryFilters.length > 0;
  const totalActiveFilters = categoryFilters.length + subcategoryFilters.length;

  // Subcategory filter is only enabled when Blind Box category is selected
  const isSubcategoryEnabled = categoryFilters.includes(ProductCategory.BLIND_BOX);

  const categoryOptions: MultiSelectOption<ProductCategory>[] =
    availableCategories.map((category) => ({
      value: category,
      label: PRODUCT_CATEGORY_LABELS[category],
    }));

  const subcategoryOptions: MultiSelectOption<ProductSubcategory>[] =
    availableSubcategories.map((subcategory) => ({
      value: subcategory,
      label: PRODUCT_SUBCATEGORY_LABELS[subcategory],
    }));

  return (
    <div className="shrink-0 flex flex-col gap-2 mb-2">
      <Label className="text-xs sm:text-sm text-muted-foreground">
        {title} ({itemCount})
      </Label>

      {showFilters && (
        <div className="flex items-center gap-2">
          {/* Search input - always visible */}
          <div className="relative flex-1">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              type="text"
              placeholder="Search products..."
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
              className="h-9 pl-8 text-sm"
              disabled={disabled}
              aria-label="Search products"
            />
          </div>

          {/* Desktop: Show multi-selects inline */}
          <div className="hidden sm:flex items-center gap-2">
            <MultiSelect
              options={categoryOptions}
              selected={categoryFilters}
              onChange={onCategoryChange}
              placeholder="Category"
              label="Categories"
              disabled={disabled || availableCategories.length === 0}
              className="w-32"
            />
            <MultiSelect
              options={subcategoryOptions}
              selected={subcategoryFilters}
              onChange={onSubcategoryChange}
              placeholder="Subcategory"
              label="Subcategories"
              disabled={disabled || !isSubcategoryEnabled}
              className="w-36"
            />
          </div>

          {/* Mobile: Show filter button with popover */}
          <Popover open={isFilterOpen} onOpenChange={setIsFilterOpen}>
            <PopoverTrigger asChild>
              <Button
                type="button"
                variant="outline"
                size="icon"
                className={cn(
                  "h-9 w-9 shrink-0 sm:hidden relative",
                  hasActiveFilters && "border-primary text-primary"
                )}
                disabled={disabled}
                aria-label="Filter products"
              >
                <Filter className="h-4 w-4" />
                {hasActiveFilters && (
                  <span className="absolute -top-1 -right-1 h-4 w-4 rounded-full bg-primary text-[10px] text-primary-foreground flex items-center justify-center">
                    {totalActiveFilters}
                  </span>
                )}
              </Button>
            </PopoverTrigger>
            <PopoverContent align="end" className="w-56 p-0">
              <div className="divide-y">
                {/* Category filter section */}
                <div className="p-2">
                  <div className="flex items-center justify-between px-2 pb-2">
                    <span className="text-xs font-medium text-muted-foreground">
                      Category
                    </span>
                    {categoryFilters.length > 0 && (
                      <button
                        type="button"
                        onClick={() => onCategoryChange([])}
                        className="text-xs text-muted-foreground hover:text-foreground transition-colors"
                        aria-label="Clear category filters"
                      >
                        Clear
                      </button>
                    )}
                  </div>
                  <div className="space-y-0.5">
                    {availableCategories.length === 0 ? (
                      <div className="py-2 px-2 text-sm text-muted-foreground">
                        No categories available
                      </div>
                    ) : (
                      availableCategories.map((category) => {
                        const isSelected = categoryFilters.includes(category);
                        return (
                          <button
                            key={category}
                            type="button"
                            onClick={() => {
                              if (isSelected) {
                                onCategoryChange(
                                  categoryFilters.filter((c) => c !== category)
                                );
                              } else {
                                onCategoryChange([...categoryFilters, category]);
                              }
                            }}
                            className={cn(
                              "flex w-full items-center gap-2 rounded-md px-2 py-2 text-sm outline-none cursor-pointer",
                              "hover:bg-accent hover:text-accent-foreground",
                              "focus:bg-accent focus:text-accent-foreground",
                              "active:bg-accent/80 transition-colors"
                            )}
                          >
                            <div
                              className={cn(
                                "flex h-4 w-4 shrink-0 items-center justify-center rounded-sm border transition-colors",
                                isSelected
                                  ? "border-primary bg-primary text-primary-foreground"
                                  : "border-input"
                              )}
                            >
                              {isSelected && <Check className="h-3 w-3" />}
                            </div>
                            <span className="truncate">
                              {PRODUCT_CATEGORY_LABELS[category]}
                            </span>
                          </button>
                        );
                      })
                    )}
                  </div>
                </div>

                {/* Subcategory filter section - only shown when Blind Box is selected */}
                {isSubcategoryEnabled && (
                  <div className="p-2">
                    <div className="flex items-center justify-between px-2 pb-2">
                      <span className="text-xs font-medium text-muted-foreground">
                        Subcategory
                      </span>
                      {subcategoryFilters.length > 0 && (
                        <button
                          type="button"
                          onClick={() => onSubcategoryChange([])}
                          className="text-xs text-muted-foreground hover:text-foreground transition-colors"
                          aria-label="Clear subcategory filters"
                        >
                          Clear
                        </button>
                      )}
                    </div>
                    <div className="space-y-0.5">
                      {availableSubcategories.length === 0 ? (
                        <div className="py-2 px-2 text-sm text-muted-foreground">
                          No subcategories available
                        </div>
                      ) : (
                        availableSubcategories.map((subcategory) => {
                          const isSelected = subcategoryFilters.includes(subcategory);
                          return (
                            <button
                              key={subcategory}
                              type="button"
                              onClick={() => {
                                if (isSelected) {
                                  onSubcategoryChange(
                                    subcategoryFilters.filter((s) => s !== subcategory)
                                  );
                                } else {
                                  onSubcategoryChange([...subcategoryFilters, subcategory]);
                                }
                              }}
                              className={cn(
                                "flex w-full items-center gap-2 rounded-md px-2 py-2 text-sm outline-none cursor-pointer",
                                "hover:bg-accent hover:text-accent-foreground",
                                "focus:bg-accent focus:text-accent-foreground",
                                "active:bg-accent/80 transition-colors"
                              )}
                            >
                              <div
                                className={cn(
                                  "flex h-4 w-4 shrink-0 items-center justify-center rounded-sm border transition-colors",
                                  isSelected
                                    ? "border-primary bg-primary text-primary-foreground"
                                    : "border-input"
                                )}
                              >
                                {isSelected && <Check className="h-3 w-3" />}
                              </div>
                              <span className="truncate">
                                {PRODUCT_SUBCATEGORY_LABELS[subcategory]}
                              </span>
                            </button>
                          );
                        })
                      )}
                    </div>
                  </div>
                )}
              </div>
            </PopoverContent>
          </Popover>

          {/* Clear filters button - always visible but disabled when no filters */}
          <Button
            type="button"
            variant={hasActiveFilters ? "destructive" : "outline"}
            size="icon"
            className="h-9 w-9 shrink-0"
            onClick={onClearFilters}
            disabled={disabled || !hasActiveFilters}
            aria-label="Clear filters"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}
    </div>
  );
}
