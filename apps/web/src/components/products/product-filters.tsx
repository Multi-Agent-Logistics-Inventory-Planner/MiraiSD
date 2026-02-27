"use client";

import { Search, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Can, Permission } from "@/components/rbac";
import { Toggle } from "@/components/ui/toggle";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  ProductCategory,
  ProductSubcategory,
  PRODUCT_CATEGORY_LABELS,
  PRODUCT_SUBCATEGORY_LABELS,
} from "@/types/api";

const CATEGORY_SUBCATEGORIES: Partial<
  Record<ProductCategory, ProductSubcategory[]>
> = {
  [ProductCategory.BLIND_BOX]: Object.values(ProductSubcategory),
};

const ALL_VALUE = "ALL";

export interface ProductFiltersState {
  search: string;
  category: ProductCategory | "";
  subcategories: ProductSubcategory[];
}

export const DEFAULT_PRODUCT_FILTERS: ProductFiltersState = {
  search: "",
  category: "",
  subcategories: [],
};

interface ProductFiltersProps {
  state: ProductFiltersState;
  onChange: (next: ProductFiltersState) => void;
  categories: ProductCategory[];
  onAddClick?: () => void;
}

export function ProductFilters({
  state,
  onChange,
  categories,
  onAddClick,
}: ProductFiltersProps) {
  const subcategoryOptions = state.category
    ? CATEGORY_SUBCATEGORIES[state.category]
    : undefined;

  const handleCategoryChange = (value: string) => {
    onChange({
      ...state,
      category: value === ALL_VALUE ? "" : (value as ProductCategory),
      subcategories: [],
    });
  };

  const toggleSubcategory = (sub: ProductSubcategory) => {
    const isSelected = state.subcategories.includes(sub);
    const newSubs = isSelected
      ? state.subcategories.filter((s) => s !== sub)
      : [...state.subcategories, sub];
    onChange({ ...state, subcategories: newSubs });
  };

  return (
    <div className="flex flex-col gap-3">
      {subcategoryOptions && (
        <div className="flex flex-wrap items-center gap-2">
          {subcategoryOptions.map((sub) => (
            <Toggle
              key={sub}
              pressed={state.subcategories.includes(sub)}
              onPressedChange={() => toggleSubcategory(sub)}
              variant="outline"
              size="sm"
              className="data-[state=on]:bg-primary data-[state=on]:text-primary-foreground"
            >
              {PRODUCT_SUBCATEGORY_LABELS[sub] ?? sub}
            </Toggle>
          ))}
        </div>
      )}

      <div className="flex items-center gap-2 sm:gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search by name or SKU"
            value={state.search}
            onChange={(e) => onChange({ ...state, search: e.target.value })}
            className="pl-9"
          />
        </div>

        <div className="flex items-center gap-2 ml-auto">
          <Select
            value={state.category || ALL_VALUE}
            onValueChange={handleCategoryChange}
          >
            <SelectTrigger className="w-[180px] shrink-0">
              <SelectValue placeholder="All Categories" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_VALUE}>All Categories</SelectItem>
              {categories.map((category) => (
                <SelectItem key={category} value={category}>
                  {PRODUCT_CATEGORY_LABELS[category] ?? category}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {onAddClick && (
            <Can permission={Permission.PRODUCTS_CREATE}>
              <Button
                onClick={onAddClick}
                size="sm"
                className="shrink-0 dark:bg-[#7c3aed] dark:text-foreground"
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
