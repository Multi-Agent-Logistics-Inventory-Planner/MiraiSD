"use client";

import { Search, SlidersHorizontal } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
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
import { MultiSelect, type MultiSelectOption } from "@/components/ui/multi-select";
import { SORT_OPTIONS } from "./constants";
import type { SortOption } from "./types";

interface FilterControlsProps {
  searchQuery: string;
  onSearchChange: (query: string) => void;
  categories: MultiSelectOption<string>[];
  categoryFilter: string[];
  onCategoryChange: (categories: string[]) => void;
  sortOption: SortOption;
  onSortChange: (option: SortOption) => void;
}

export function MobileFilterControls({
  searchQuery,
  onSearchChange,
  categories,
  categoryFilter,
  onCategoryChange,
  sortOption,
  onSortChange,
}: FilterControlsProps) {
  return (
    <div className="flex sm:hidden items-center gap-2">
      <div className="relative flex-1">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search items..."
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          className="pl-9 text-sm"
        />
      </div>
      <Popover>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            size="sm"
            className="shrink-0 dark:bg-input dark:border-[#41413d]"
          >
            <SlidersHorizontal className="h-4 w-4" />
            {categoryFilter.length > 0 && (
              <span className="ml-1 flex h-5 w-5 items-center justify-center rounded-full bg-table-header text-[10px] font-medium text-table-header-foreground">
                {categoryFilter.length}
              </span>
            )}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-64" align="end">
          <div className="grid grid-cols-1 gap-3">
            <div className="grid gap-1.5">
              <Label className="text-xs text-muted-foreground">Category</Label>
              <MultiSelect
                options={categories}
                selected={categoryFilter}
                onChange={onCategoryChange}
                placeholder="All Categories"
                className="w-full"
              />
            </div>
            <div className="grid gap-1.5">
              <Label className="text-xs text-muted-foreground">Sort By</Label>
              <Select
                value={sortOption}
                onValueChange={(val) => onSortChange(val as SortOption)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Sort by" />
                </SelectTrigger>
                <SelectContent>
                  {SORT_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
}

export function DesktopFilterControls({
  searchQuery,
  onSearchChange,
  categories,
  categoryFilter,
  onCategoryChange,
  sortOption,
  onSortChange,
}: FilterControlsProps) {
  return (
    <div className="hidden sm:flex items-center gap-2">
      <div className="relative w-64">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search items..."
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          className="pl-9"
        />
      </div>
      <MultiSelect
        options={categories}
        selected={categoryFilter}
        onChange={onCategoryChange}
        placeholder="All Categories"
        className="w-40"
      />
      <Select
        value={sortOption}
        onValueChange={(val) => onSortChange(val as SortOption)}
      >
        <SelectTrigger className="w-56">
          <SelectValue placeholder="Sort by" />
        </SelectTrigger>
        <SelectContent>
          {SORT_OPTIONS.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
