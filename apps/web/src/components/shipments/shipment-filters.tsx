"use client";

import { Search, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Can, Permission } from "@/components/rbac";

interface ShipmentFiltersProps {
  search: string;
  onSearchChange: (value: string) => void;
  onAddClick: () => void;
}

export function ShipmentFilters({
  search,
  onSearchChange,
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
      <Can permission={Permission.SHIPMENTS_CREATE}>
        <Button onClick={onAddClick} size="sm" className="shrink-0">
          <Plus className="h-4 w-4 sm:mr-2" />
          <span className="hidden sm:inline">New Shipment</span>
        </Button>
      </Can>
    </div>
  );
}
