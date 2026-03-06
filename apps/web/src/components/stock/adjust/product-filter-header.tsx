"use client";

import { useState } from "react";
import { Plus, Search, SlidersHorizontal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
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
import type { Category } from "@/types/api";

interface ProductFilterHeaderProps {
  title: string;
  itemCount: number;
  searchQuery: string;
  categoryFilters: string[];
  childCategoryFilters: string[];
  availableCategories: Category[];
  availableChildCategories: Category[];
  disabled: boolean;
  showFilters: boolean;
  onSearchChange: (value: string) => void;
  onCategoryChange: (categories: string[]) => void;
  onChildCategoryChange: (childCategories: string[]) => void;
  onClearFilters: () => void;
  onAddClick?: () => void;
}

export function ProductFilterHeader({
  title,
  itemCount,
  searchQuery,
  categoryFilters,
  childCategoryFilters,
  availableCategories,
  availableChildCategories,
  disabled,
  showFilters,
  onSearchChange,
  onCategoryChange,
  onChildCategoryChange,
  onClearFilters,
  onAddClick,
}: ProductFilterHeaderProps) {
  const [filterOpen, setFilterOpen] = useState(false);

  const selectedCategoryId = categoryFilters[0] ?? null;
  const selectedChildCategoryId = childCategoryFilters[0] ?? null;

  const hasActiveFilters =
    categoryFilters.length > 0 || childCategoryFilters.length > 0;
  const filterCount = [
    Boolean(selectedCategoryId),
    Boolean(selectedChildCategoryId),
  ].filter(Boolean).length;

  function handleCategorySelect(value: string) {
    if (value === "__all__") {
      onCategoryChange([]);
      onChildCategoryChange([]);
    } else {
      onCategoryChange([value]);
      onChildCategoryChange([]);
    }
  }

  function handleChildCategorySelect(value: string) {
    if (value === "__all__") {
      onChildCategoryChange([]);
    } else {
      onChildCategoryChange([value]);
    }
  }

  return (
    <div className="shrink-0 flex flex-col gap-2 mb-2">
      <Label className="text-xs sm:text-sm text-muted-foreground">
        {title} ({itemCount})
      </Label>

      {showFilters && (
        <div className="flex items-center gap-2">
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

          <Popover open={filterOpen} onOpenChange={setFilterOpen}>
            <PopoverTrigger asChild>
              <Button
                variant={hasActiveFilters ? "default" : "outline"}
                size="sm"
                className="shrink-0 h-9 border dark:bg-input dark:border-[#41413d] dark:text-[#a1a1a1]"
                disabled={disabled}
              >
                <SlidersHorizontal className="h-4 w-4 sm:mr-2" />
                <span className="hidden sm:inline">Filter</span>
                {filterCount > 0 && (
                  <span className="ml-1 flex h-5 w-5 items-center justify-center rounded-full bg-table-header text-[10px] font-medium text-table-header-foreground">
                    {filterCount}
                  </span>
                )}
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-64" align="end">
              <div className="grid grid-cols-1 gap-3">
                <div className="grid gap-1.5">
                  <Label className="text-xs text-muted-foreground">
                    Category
                  </Label>
                  <Select
                    value={selectedCategoryId ?? "__all__"}
                    onValueChange={handleCategorySelect}
                    disabled={disabled || availableCategories.length === 0}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="All categories" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__all__">All categories</SelectItem>
                      {availableCategories.map((c) => (
                        <SelectItem key={c.id} value={c.id}>
                          {c.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="grid gap-1.5">
                  <Label className="text-xs text-muted-foreground">
                    Subcategory
                  </Label>
                  <Select
                    value={selectedChildCategoryId ?? "__all__"}
                    onValueChange={handleChildCategorySelect}
                    disabled={
                      disabled ||
                      !selectedCategoryId ||
                      availableChildCategories.length === 0
                    }
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="All subcategories" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__all__">All subcategories</SelectItem>
                      {availableChildCategories.map((s) => (
                        <SelectItem key={s.id} value={s.id}>
                          {s.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {hasActiveFilters && (
                  <>
                    <Separator />
                    <Button
                      variant="destructive"
                      size="sm"
                      className="w-full"
                      onClick={() => {
                        onClearFilters();
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
            <Button
              variant="outline"
              size="sm"
              className="shrink-0 h-9 border dark:bg-input dark:border-[#41413d] dark:text-[#a1a1a1]"
              onClick={onAddClick}
              disabled={disabled}
            >
              <Plus className="h-4 w-4 sm:mr-2" />
              <span className="hidden sm:inline">Add New</span>
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
