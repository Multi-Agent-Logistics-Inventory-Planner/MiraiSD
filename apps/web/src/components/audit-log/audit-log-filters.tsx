"use client";

import { useState } from "react";
import { format } from "date-fns";
import { Search, SlidersHorizontal, X, Calendar as CalendarIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { StockMovementReason, type User } from "@/types/api";
import { cn } from "@/lib/utils";

export type ReasonFilter = StockMovementReason | "all";

export const REASON_LABELS: Record<StockMovementReason, string> = {
  [StockMovementReason.INITIAL_STOCK]: "Initial Stock",
  [StockMovementReason.RESTOCK]: "Restock",
  [StockMovementReason.SHIPMENT_RECEIPT]: "Shipment",
  [StockMovementReason.SHIPMENT_RECEIPT_REVERSED]: "Shipment Reversal",
  [StockMovementReason.SALE]: "Sale",
  [StockMovementReason.DAMAGE]: "Damage",
  [StockMovementReason.ADJUSTMENT]: "Adjustment",
  [StockMovementReason.RETURN]: "Return",
  [StockMovementReason.TRANSFER]: "Transfer",
  [StockMovementReason.DISPLAY_SET]: "Display Set",
  [StockMovementReason.DISPLAY_REMOVED]: "Display Removed",
  [StockMovementReason.DISPLAY_SWAP]: "Display Swap",
};

export interface AuditLogFiltersState {
  search: string;
  actorId: string;
  reason: ReasonFilter;
  fromDate: string;
  toDate: string;
}

export const DEFAULT_AUDIT_LOG_FILTERS: AuditLogFiltersState = {
  search: "",
  actorId: "",
  reason: "all",
  fromDate: "",
  toDate: "",
};

interface AuditLogFiltersProps {
  state: AuditLogFiltersState;
  onChange: (next: AuditLogFiltersState) => void;
  users?: User[];
}


export function AuditLogFilters({
  state,
  onChange,
  users = [],
}: AuditLogFiltersProps) {
  const [filterOpen, setFilterOpen] = useState(false);

  const hasActiveFilters =
    Boolean(state.actorId) ||
    state.reason !== "all" ||
    Boolean(state.fromDate) ||
    Boolean(state.toDate);

  const updateField = <K extends keyof AuditLogFiltersState>(
    key: K,
    value: AuditLogFiltersState[K],
  ) => {
    onChange({ ...state, [key]: value });
  };

  const today = new Date();
  today.setHours(23, 59, 59, 999);

  const filterCount = [
    Boolean(state.actorId),
    state.reason !== "all",
    Boolean(state.fromDate),
    Boolean(state.toDate),
  ].filter(Boolean).length;

  return (
    <div className="flex items-center gap-3">
      <div className="relative flex-1 max-w-sm">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search"
          value={state.search}
          onChange={(e) => updateField("search", e.target.value)}
          className="pl-9"
        />
      </div>

      <Popover open={filterOpen} onOpenChange={setFilterOpen}>
        <PopoverTrigger asChild>
          <Button variant="outline" className="gap-2 dark:bg-input dark:border-[#41413d] dark:text-muted-foreground">
            <SlidersHorizontal className="h-4 w-4" />
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
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="employee-select">Performed by</Label>
                <Select
                  value={state.actorId || "__all__"}
                  onValueChange={(v) =>
                    updateField("actorId", v === "__all__" ? "" : v)
                  }
                >
                  <SelectTrigger id="employee-select" className="bg-background text-muted-foreground">
                    <SelectValue placeholder="All employees" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__all__">All employees</SelectItem>
                    {users.map((user) => (
                      <SelectItem key={user.id} value={user.id}>
                        {user.fullName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="view-select">View</Label>
                <Select
                  value={state.reason}
                  onValueChange={(v) => updateField("reason", v as ReasonFilter)}
                >
                  <SelectTrigger id="view-select" className="w-full bg-background text-muted-foreground">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All</SelectItem>
                    {Object.entries(REASON_LABELS).map(([value, label]) => (
                      <SelectItem key={value} value={value}>
                        {label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="from-date">Start date</Label>
              <Popover>
                <PopoverTrigger asChild>
                  <Button
                    id="from-date"
                    variant="outline"
                    className={cn(
                      "w-full justify-start text-left font-normal dark:bg-input dark:border-[#41413d]",
                      !state.fromDate && "text-muted-foreground",
                    )}
                  >
                    {state.fromDate ? (
                      format(new Date(state.fromDate + "T00:00:00"), "MM/dd/yyyy")
                    ) : (
                      <span>mm/dd/yyyy</span>
                    )}
                    <CalendarIcon className="ml-auto h-4 w-4 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0" align="start">
                  <Calendar
                    mode="single"
                    selected={
                      state.fromDate ? new Date(state.fromDate + "T00:00:00") : undefined
                    }
                    onSelect={(date) => {
                      if (!date) { updateField("fromDate", ""); return; }
                      const y = date.getUTCFullYear();
                      const m = String(date.getUTCMonth() + 1).padStart(2, "0");
                      const d = String(date.getUTCDate()).padStart(2, "0");
                      updateField("fromDate", `${y}-${m}-${d}`);
                    }}
                    disabled={(date) => date > today}
                  />
                </PopoverContent>
              </Popover>
            </div>

            <div className="space-y-2">
              <Label htmlFor="to-date">End date</Label>
              <Popover>
                <PopoverTrigger asChild>
                  <Button
                    id="to-date"
                    variant="outline"
                    className={cn(
                      "w-full justify-start text-left font-normal dark:bg-input dark:border-[#41413d]",
                      !state.toDate && "text-muted-foreground",
                    )}
                  >
                    {state.toDate ? (
                      format(new Date(state.toDate + "T00:00:00"), "MM/dd/yyyy")
                    ) : (
                      <span>mm/dd/yyyy</span>
                    )}
                    <CalendarIcon className="ml-auto h-4 w-4 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0" align="start">
                  <Calendar
                    mode="single"
                    selected={state.toDate ? new Date(state.toDate + "T00:00:00") : undefined}
                    onSelect={(date) => {
                      if (!date) { updateField("toDate", ""); return; }
                      const y = date.getUTCFullYear();
                      const m = String(date.getUTCMonth() + 1).padStart(2, "0");
                      const d = String(date.getUTCDate()).padStart(2, "0");
                      updateField("toDate", `${y}-${m}-${d}`);
                    }}
                    disabled={(date) => date > today}
                  />
                </PopoverContent>
              </Popover>
            </div>

            {hasActiveFilters && (
              <>
                <Separator />
                <Button
                  type="button"
                  variant="ghost"
                  className="w-full"
                  onClick={() => {
                    onChange(DEFAULT_AUDIT_LOG_FILTERS);
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