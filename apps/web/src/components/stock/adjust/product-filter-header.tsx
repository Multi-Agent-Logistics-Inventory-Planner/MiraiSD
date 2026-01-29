"use client";

import { useState } from "react";
import { Filter, Search, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { PRODUCT_CATEGORY_LABELS, type ProductCategory } from "@/types/api";
import { cn } from "@/lib/utils";

interface ProductFilterHeaderProps {
  title: string;
  itemCount: number;
  searchQuery: string;
  categoryFilter: ProductCategory | null;
  availableCategories: ProductCategory[];
  disabled: boolean;
  showFilters: boolean;
  onSearchChange: (value: string) => void;
  onCategoryChange: (category: ProductCategory | null) => void;
  onClearFilters: () => void;
}

export function ProductFilterHeader({
  title,
  itemCount,
  searchQuery,
  categoryFilter,
  availableCategories,
  disabled,
  showFilters,
  onSearchChange,
  onCategoryChange,
  onClearFilters,
}: ProductFilterHeaderProps) {
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const hasActiveFilters = categoryFilter !== null;

  return (
    <div className="shrink-0 flex flex-col gap-2 mb-2">
      <div className="flex items-center justify-between">
        <Label className="text-xs sm:text-sm text-muted-foreground">
          {title} ({itemCount})
        </Label>
        {hasActiveFilters && (
          <button
            type="button"
            onClick={onClearFilters}
            className="text-xs text-muted-foreground hover:text-foreground flex items-center gap-1"
            disabled={disabled}
          >
            <X className="h-3 w-3" />
            Clear filters
          </button>
        )}
      </div>

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
          <Popover open={isFilterOpen} onOpenChange={setIsFilterOpen}>
            <PopoverTrigger asChild>
              <Button
                type="button"
                variant="outline"
                size="icon"
                className={cn(
                  "h-9 w-9 shrink-0",
                  hasActiveFilters && "border-primary text-primary"
                )}
                disabled={disabled}
                aria-label="Filter by category"
              >
                <Filter className="h-4 w-4" />
                {hasActiveFilters && (
                  <span className="absolute -top-1 -right-1 h-4 w-4 rounded-full bg-primary text-[10px] text-primary-foreground flex items-center justify-center">
                    1
                  </span>
                )}
              </Button>
            </PopoverTrigger>
            <PopoverContent align="end" className="w-56 p-3">
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">Filter by category</span>
                  {categoryFilter && (
                    <button
                      type="button"
                      onClick={() => onCategoryChange(null)}
                      className="text-xs text-muted-foreground hover:text-foreground"
                    >
                      Clear
                    </button>
                  )}
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {availableCategories.map((category) => (
                    <Badge
                      key={category}
                      variant={categoryFilter === category ? "default" : "outline"}
                      className={cn(
                        "cursor-pointer text-xs",
                        categoryFilter === category
                          ? "bg-primary hover:bg-primary/90"
                          : "hover:bg-muted"
                      )}
                      onClick={() => {
                        onCategoryChange(categoryFilter === category ? null : category);
                      }}
                    >
                      {PRODUCT_CATEGORY_LABELS[category]}
                    </Badge>
                  ))}
                  {availableCategories.length === 0 && (
                    <span className="text-xs text-muted-foreground">
                      No categories available
                    </span>
                  )}
                </div>
              </div>
            </PopoverContent>
          </Popover>
        </div>
      )}
    </div>
  );
}
