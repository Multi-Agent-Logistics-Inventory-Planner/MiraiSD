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
import { Switch } from "@/components/ui/switch";
import { LocationType, LOCATION_TYPE_LABELS } from "@/types/api";

// Machine location types only (exclude storage types)
const MACHINE_LOCATION_TYPES = [
  LocationType.SINGLE_CLAW_MACHINE,
  LocationType.DOUBLE_CLAW_MACHINE,
  LocationType.KEYCHAIN_MACHINE,
  LocationType.FOUR_CORNER_MACHINE,
  LocationType.PUSHER_MACHINE,
] as const;

export interface MachineDisplayFiltersState {
  search: string;
  locationType: LocationType | "all";
  staleOnly: boolean;
}

export const DEFAULT_MACHINE_DISPLAY_FILTERS: MachineDisplayFiltersState = {
  search: "",
  locationType: "all",
  staleOnly: false,
};

interface MachineDisplayFiltersProps {
  state: MachineDisplayFiltersState;
  onChange: (next: MachineDisplayFiltersState) => void;
}

export function MachineDisplayFilters({
  state,
  onChange,
}: MachineDisplayFiltersProps) {
  const [filterOpen, setFilterOpen] = useState(false);

  const hasActiveFilters =
    state.locationType !== "all" || state.staleOnly;

  const updateField = <K extends keyof MachineDisplayFiltersState>(
    key: K,
    value: MachineDisplayFiltersState[K]
  ) => {
    onChange({ ...state, [key]: value });
  };

  const filterCount = [
    state.locationType !== "all",
    state.staleOnly,
  ].filter(Boolean).length;

  return (
    <div className="flex items-center gap-3">
      <div className="relative flex-1 max-w-sm">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search by product or machine..."
          value={state.search}
          onChange={(e) => updateField("search", e.target.value)}
          className="pl-9"
        />
      </div>

      <Popover open={filterOpen} onOpenChange={setFilterOpen}>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            className="gap-2 dark:bg-input dark:border-[#41413d]"
          >
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
              <Label htmlFor="machine-type-select">Machine Type</Label>
              <Select
                value={state.locationType}
                onValueChange={(v) =>
                  updateField("locationType", v as LocationType | "all")
                }
              >
                <SelectTrigger id="machine-type-select">
                  <SelectValue placeholder="Select type" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All machines</SelectItem>
                  {MACHINE_LOCATION_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {LOCATION_TYPE_LABELS[type]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex items-center justify-between">
              <Label htmlFor="stale-only" className="cursor-pointer">
                Show stale only
              </Label>
              <Switch
                id="stale-only"
                checked={state.staleOnly}
                onCheckedChange={(checked) => updateField("staleOnly", checked)}
              />
            </div>

            {hasActiveFilters && (
              <>
                <Separator />
                <Button
                  type="button"
                  variant="ghost"
                  className="w-full"
                  onClick={() => {
                    onChange(DEFAULT_MACHINE_DISPLAY_FILTERS);
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
