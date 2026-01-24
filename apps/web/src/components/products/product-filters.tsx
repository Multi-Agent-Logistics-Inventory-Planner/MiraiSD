"use client";

import { useState } from "react";
import { Search, Filter, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { ProductCategory } from "@/types/api";

export type CategoryFilter = ProductCategory | "all";
export type StatusFilter = "all" | "good" | "low" | "critical" | "out-of-stock";

export interface ProductFiltersState {
  search: string;
  category: CategoryFilter;
  status: StatusFilter;
}

export const DEFAULT_PRODUCT_FILTERS: ProductFiltersState = {
  search: "",
  category: "all",
  status: "all",
};

interface ProductFiltersProps {
  state: ProductFiltersState;
  onChange: (next: ProductFiltersState) => void;
  categories: ProductCategory[];
}

const STATUS_LABELS: Record<StatusFilter, string> = {
  all: "All Status",
  good: "Good",
  low: "Low",
  critical: "Critical",
  "out-of-stock": "Out of Stock",
};

const CATEGORY_LABELS: Record<ProductCategory, string> = {
  [ProductCategory.PLUSHIE]: "Plushie",
  [ProductCategory.KEYCHAIN]: "Keychain",
  [ProductCategory.FIGURINE]: "Figurine",
  [ProductCategory.GACHAPON]: "Gachapon",
  [ProductCategory.BLIND_BOX]: "Blind Box",
  [ProductCategory.BUILD_KIT]: "Build Kit",
  [ProductCategory.GUNDAM]: "Gundam",
  [ProductCategory.KUJI]: "Kuji",
  [ProductCategory.MISCELLANEOUS]: "Miscellaneous",
};

export function ProductFilters({
  state,
  onChange,
  categories,
}: ProductFiltersProps) {
  const [filterOpen, setFilterOpen] = useState(false);

  const hasActiveFilters =
    state.category !== "all" || state.status !== "all";

  const updateField = <K extends keyof ProductFiltersState>(
    key: K,
    value: ProductFiltersState[K],
  ) => {
    onChange({ ...state, [key]: value });
  };

  const filterCount = [
    state.category !== "all",
    state.status !== "all",
  ].filter(Boolean).length;

  return (
    <div className="flex items-center gap-3">
      <div className="relative flex-1 max-w-sm">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search by name or SKU"
          value={state.search}
          onChange={(e) => updateField("search", e.target.value)}
          className="pl-9"
        />
      </div>

      <Popover open={filterOpen} onOpenChange={setFilterOpen}>
        <PopoverTrigger asChild>
          <Button variant="outline" className="gap-2">
            <Filter className="h-4 w-4" />
            Filters
            {filterCount > 0 && (
              <span className="ml-1 rounded-full bg-primary px-2 py-0.5 text-xs text-primary-foreground">
                {filterCount}
              </span>
            )}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-80" align="end">
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="category-select">Category</Label>
              <Select
                value={state.category}
                onValueChange={(v) => updateField("category", v as CategoryFilter)}
              >
                <SelectTrigger id="category-select">
                  <SelectValue placeholder="Select category" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All categories</SelectItem>
                  {categories.map((cat) => (
                    <SelectItem key={cat} value={cat}>
                      {CATEGORY_LABELS[cat] ?? cat}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="status-select">Stock Status</Label>
              <Select
                value={state.status}
                onValueChange={(v) => updateField("status", v as StatusFilter)}
              >
                <SelectTrigger id="status-select">
                  <SelectValue placeholder="Select status" />
                </SelectTrigger>
                <SelectContent>
                  {Object.entries(STATUS_LABELS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {hasActiveFilters && (
              <>
                <Separator />
                <Button
                  type="button"
                  variant="ghost"
                  className="w-full"
                  onClick={() => {
                    onChange(DEFAULT_PRODUCT_FILTERS);
                    setFilterOpen(false);
                  }}
                >
                  <X className="h-4 w-4 mr-2" />
                  Remove filters
                </Button>
              </>
            )}
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
}
