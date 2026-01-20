"use client";

import { Search, Filter, Grid, List } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { ProductCategory } from "@/types/api";

export interface ProductFiltersState {
  searchQuery: string;
  category: "all" | ProductCategory;
  status: "all" | "good" | "low" | "critical" | "out-of-stock";
  viewMode: "table" | "grid";
}

interface ProductFiltersProps {
  state: ProductFiltersState;
  categories: ProductCategory[];
  onChange: (next: ProductFiltersState) => void;
  onAddClick: () => void;
}

function formatStatus(status: ProductFiltersState["status"]) {
  if (status === "all") return "All Status";
  return status
    .split("-")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

export function ProductFilters({
  state,
  categories,
  onChange,
  onAddClick,
}: ProductFiltersProps) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex flex-1 items-center gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by SKU or name..."
            className="pl-8"
            value={state.searchQuery}
            onChange={(e) => onChange({ ...state, searchQuery: e.target.value })}
          />
        </div>

        <Select
          value={state.category}
          onValueChange={(v) => onChange({ ...state, category: v as ProductFiltersState["category"] })}
        >
          <SelectTrigger className="w-[170px]">
            <Filter className="mr-2 h-4 w-4" />
            <SelectValue placeholder="Category" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Categories</SelectItem>
            {categories.map((c) => (
              <SelectItem key={c} value={c}>
                {c}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={state.status}
          onValueChange={(v) => onChange({ ...state, status: v as ProductFiltersState["status"] })}
        >
          <SelectTrigger className="w-[150px]">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Status</SelectItem>
            {(["good", "low", "critical", "out-of-stock"] as const).map((s) => (
              <SelectItem key={s} value={s}>
                {formatStatus(s)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="flex items-center gap-2">
        <div className="flex rounded-md border">
          <Button
            variant={state.viewMode === "table" ? "secondary" : "ghost"}
            size="icon-sm"
            onClick={() => onChange({ ...state, viewMode: "table" })}
          >
            <List className="h-4 w-4" />
          </Button>
          <Button
            variant={state.viewMode === "grid" ? "secondary" : "ghost"}
            size="icon-sm"
            onClick={() => onChange({ ...state, viewMode: "grid" })}
          >
            <Grid className="h-4 w-4" />
          </Button>
        </div>

        <Button onClick={onAddClick}>Add Item</Button>
      </div>
    </div>
  );
}

