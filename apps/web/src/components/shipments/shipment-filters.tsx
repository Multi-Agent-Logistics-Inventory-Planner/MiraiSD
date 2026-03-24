"use client";

import { Search, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Can, Permission } from "@/components/rbac";

export type SortOption = "newest" | "oldest";

interface ShipmentFiltersProps {
  search: string;
  onSearchChange: (value: string) => void;
  sortOption: SortOption;
  onSortChange: (value: SortOption) => void;
  onAddClick: () => void;
}

export function ShipmentFilters({
  search,
  onSearchChange,
  sortOption,
  onSortChange,
  onAddClick,
}: ShipmentFiltersProps) {
  return (
    <div className="flex items-center gap-2">
      <div className="relative flex-1 sm:flex-none sm:w-64">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search shipments..."
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          className="pl-9"
        />
      </div>
      <Select value={sortOption} onValueChange={(v) => onSortChange(v as SortOption)}>
        <SelectTrigger className="w-[130px]">
          <SelectValue placeholder="Sort by" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="newest">Newest First</SelectItem>
          <SelectItem value="oldest">Oldest First</SelectItem>
        </SelectContent>
      </Select>
      <Can permission={Permission.SHIPMENTS_CREATE}>
        <Button
          onClick={onAddClick}
          className="shrink-0 text-white bg-brand-primary hover:bg-brand-primary-hover"
        >
          <Plus className="h-4 w-4 sm:mr-2" />
          <span className="hidden sm:inline">New Shipment</span>
        </Button>
      </Can>
    </div>
  );
}
