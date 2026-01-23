"use client";

import { useState } from "react";
import { format } from "date-fns";
import { Search, Filter, X, Calendar as CalendarIcon } from "lucide-react";
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
import { Calendar } from "@/components/ui/calendar";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { StockMovementReason, ProductCategory, User } from "@/types/api";
import { cn } from "@/lib/utils";

export type ReasonFilter = StockMovementReason | "all";
export type CategoryFilter = ProductCategory | "all";

export interface AuditLogFiltersState {
  search: string;
  category: CategoryFilter;
  actorId: string;
  reason: ReasonFilter;
  fromDate: string;
  toDate: string;
}

export const DEFAULT_AUDIT_LOG_FILTERS: AuditLogFiltersState = {
  search: "",
  category: "all",
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

const REASON_LABELS: Record<StockMovementReason, string> = {
  [StockMovementReason.INITIAL_STOCK]: "Initial Stock",
  [StockMovementReason.RESTOCK]: "Restock",
  [StockMovementReason.SALE]: "Sale",
  [StockMovementReason.DAMAGE]: "Damage",
  [StockMovementReason.ADJUSTMENT]: "Adjustment",
  [StockMovementReason.RETURN]: "Return",
  [StockMovementReason.TRANSFER]: "Transfer",
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

export function AuditLogFilters({
  state,
  onChange,
  users = [],
}: AuditLogFiltersProps) {
  const [filterOpen, setFilterOpen] = useState(false);

  const hasActiveFilters =
    state.category !== "all" ||
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
    state.category !== "all",
    state.reason !== "all",
    Boolean(state.actorId),
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
                  {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="action-select">Action</Label>
              <Select
                value={state.reason}
                onValueChange={(v) => updateField("reason", v as ReasonFilter)}
              >
                <SelectTrigger id="action-select">
                  <SelectValue placeholder="Select action" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All actions</SelectItem>
                  {Object.entries(REASON_LABELS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="employee-select">Performed by</Label>
              <Select
                value={state.actorId || "__all__"}
                onValueChange={(v) => updateField("actorId", v === "__all__" ? "" : v)}
              >
                <SelectTrigger id="employee-select">
                  <SelectValue placeholder="Select employee" />
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
              <Label htmlFor="from-date">Start date</Label>
              <Popover>
                <PopoverTrigger asChild>
                  <Button
                    id="from-date"
                    variant="outline"
                    className={cn(
                      "w-full justify-start text-left font-normal",
                      !state.fromDate && "text-muted-foreground"
                    )}
                  >
                    {state.fromDate ? (
                      format(new Date(state.fromDate), "MM/dd/yyyy")
                    ) : (
                      <span>mm/dd/yyyy</span>
                    )}
                    <CalendarIcon className="ml-auto h-4 w-4 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0" align="start">
                  <Calendar
                    mode="single"
                    selected={state.fromDate ? new Date(state.fromDate) : undefined}
                    onSelect={(date) => {
                      updateField("fromDate", date ? format(date, "yyyy-MM-dd") : "");
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
                      "w-full justify-start text-left font-normal",
                      !state.toDate && "text-muted-foreground"
                    )}
                  >
                    {state.toDate ? (
                      format(new Date(state.toDate), "MM/dd/yyyy")
                    ) : (
                      <span>mm/dd/yyyy</span>
                    )}
                    <CalendarIcon className="ml-auto h-4 w-4 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0" align="start">
                  <Calendar
                    mode="single"
                    selected={state.toDate ? new Date(state.toDate) : undefined}
                    onSelect={(date) => {
                      updateField("toDate", date ? format(date, "yyyy-MM-dd") : "");
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
