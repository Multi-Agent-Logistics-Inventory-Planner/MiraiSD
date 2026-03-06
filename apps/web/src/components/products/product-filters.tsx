"use client";

import { useState } from "react";
import { Search, Plus, SlidersHorizontal, Pencil } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Can, Permission } from "@/components/rbac";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ManageCategoriesDialog } from "./manage-categories-dialog";
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
  const [filterOpen, setFilterOpen] = useState(false);
  const [manageCategoriesOpen, setManageCategoriesOpen] = useState(false);

  const rootCategories = categories.filter((c) => !c.parentId);
  const selectedCategory = rootCategories.find((c) => c.id === state.selectedCategoryId) ?? null;
  const subcategories = selectedCategory?.children ?? [];

  const hasActiveFilters = state.selectedCategoryId !== null;
  const filterCount = [
    Boolean(state.selectedCategoryId),
    Boolean(state.selectedSubcategoryId),
  ].filter(Boolean).length;

  return (
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
        <Popover open={filterOpen} onOpenChange={setFilterOpen}>
          <PopoverTrigger asChild>
            <Button
              variant={hasActiveFilters ? "default" : "outline"}
              size="sm"
              className="shrink-0 dark:bg-input dark:border-[#41413d] dark:text-[#a1a1a1]"
            >
              <SlidersHorizontal className="h-4 w-4 sm:mr-2" />
              <span className="hidden sm:inline">Filter</span>
              {filterCount > 0 && (
                <span className="ml-1 flex h-4 w-4 items-center justify-center rounded-full bg-background text-[10px] font-medium text-foreground">
                  {filterCount}
                </span>
              )}
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-64" align="end">
            <div className="grid grid-cols-1 gap-3">
              <div className="grid gap-1.5">
                <Label className="text-xs text-muted-foreground">Category</Label>
                <div className="flex items-center gap-1.5">
                <Select
                  value={state.selectedCategoryId ?? "__all__"}
                  onValueChange={(v) => {
                    if (v === "__all__") {
                      onChange({ ...state, selectedCategoryId: null, selectedSubcategoryId: null });
                    } else {
                      onChange({ ...state, selectedCategoryId: v, selectedSubcategoryId: null });
                    }
                  }}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="All categories" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">All categories</SelectItem>
                    {rootCategories.map((c) => (
                      <SelectItem key={c.id} value={c.id}>
                        {c.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  className="h-9 w-9 shrink-0"
                  onClick={() => setManageCategoriesOpen(true)}
                  title="Edit categories"
                >
                  <Pencil className="h-3.5 w-3.5" />
                </Button>
                </div>
              </div>

              <div className="grid gap-1.5">
                <Label className="text-xs text-muted-foreground">Subcategory</Label>
                <div className="flex items-center gap-1.5">
                <Select
                  value={state.selectedSubcategoryId ?? "__all__"}
                  onValueChange={(v) => {
                    onChange({
                      ...state,
                      selectedSubcategoryId: v === "__all__" ? null : v,
                    });
                  }}
                  disabled={!state.selectedCategoryId || subcategories.length === 0}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="All subcategories" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">All subcategories</SelectItem>
                    {subcategories.map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  className="h-9 w-9 shrink-0"
                  onClick={() => setManageCategoriesOpen(true)}
                  title="Edit subcategories"
                >
                  <Pencil className="h-3.5 w-3.5" />
                </Button>
                </div>
              </div>

              {hasActiveFilters && (
                <>
                  <Separator />
                  <Button
                    variant="destructive"
                    size="sm"
                    className="w-full"
                    onClick={() => {
                      onChange({ ...state, selectedCategoryId: null, selectedSubcategoryId: null });
                      setFilterOpen(false);
                    }}
                  >
                    Remove filters
                  </Button>
                </>
              )}
            </div>
          </PopoverContent>
        </Popover>

        {onAddClick && (
          <Can permission={Permission.PRODUCTS_CREATE}>
            <Button
              onClick={onAddClick}
              size="sm"
              className="shrink-0 text-white bg-[#0b66c2] hover:bg-[#0a5eb3] dark:bg-[#7c3aed] dark:hover:bg-[#6d28d9] dark:text-foreground"
            >
              <Plus className="h-4 w-4 sm:mr-2" />
              <span className="hidden sm:inline">Add Product</span>
            </Button>
          </Can>
        )}
      </div>

      <ManageCategoriesDialog
        open={manageCategoriesOpen}
        onOpenChange={setManageCategoriesOpen}
      />
    </div>
  );
}
