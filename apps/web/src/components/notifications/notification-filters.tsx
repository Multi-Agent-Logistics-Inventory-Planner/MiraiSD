"use client";

import { useState, useEffect } from "react";
import { Search, CalendarIcon, X } from "lucide-react";
import { format } from "date-fns";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
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
import { NotificationType } from "@/types/api";
import { cn } from "@/lib/utils";
import type { DateRange } from "react-day-picker";

const TYPE_OPTIONS = [
  { value: "ALL", label: "All Types" },
  { value: NotificationType.LOW_STOCK, label: "Low Stock" },
  { value: NotificationType.OUT_OF_STOCK, label: "Out of Stock" },
  { value: NotificationType.REORDER_SUGGESTION, label: "Reorder Suggestion" },
  { value: NotificationType.EXPIRY_WARNING, label: "Expiry Warning" },
  { value: NotificationType.SYSTEM_ALERT, label: "System Alert" },
  { value: NotificationType.UNASSIGNED_ITEM, label: "Unassigned Item" },
];

interface NotificationFiltersProps {
  search: string;
  onSearchChange: (value: string) => void;
  typeFilter: NotificationType | undefined;
  onTypeFilterChange: (type: NotificationType | undefined) => void;
  dateRange: { from?: Date; to?: Date };
  onDateRangeChange: (range: { from?: Date; to?: Date }) => void;
}

export function NotificationFilters({
  search,
  onSearchChange,
  typeFilter,
  onTypeFilterChange,
  dateRange,
  onDateRangeChange,
}: NotificationFiltersProps) {
  const [localSearch, setLocalSearch] = useState(search);

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      onSearchChange(localSearch);
    }, 300);
    return () => clearTimeout(timer);
  }, [localSearch, onSearchChange]);

  // Sync external search changes
  useEffect(() => {
    setLocalSearch(search);
  }, [search]);

  const hasFilters = typeFilter || dateRange.from || dateRange.to;

  const handleClearFilters = () => {
    onTypeFilterChange(undefined);
    onDateRangeChange({});
  };

  const handleDateSelect = (range: DateRange | undefined) => {
    onDateRangeChange({
      from: range?.from,
      to: range?.to,
    });
  };

  const formatDateRange = () => {
    if (dateRange.from && dateRange.to) {
      return `${format(dateRange.from, "MMM d")} - ${format(dateRange.to, "MMM d, yyyy")}`;
    }
    if (dateRange.from) {
      return `From ${format(dateRange.from, "MMM d, yyyy")}`;
    }
    return "Date range";
  };

  return (
    <div className="flex items-center gap-2 flex-wrap">
      <div className="relative flex-1 sm:flex-none sm:w-64">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Search notifications..."
          value={localSearch}
          onChange={(e) => setLocalSearch(e.target.value)}
          className="pl-9"
        />
      </div>

      <Select
        value={typeFilter ?? "ALL"}
        onValueChange={(value) =>
          onTypeFilterChange(value === "ALL" ? undefined : (value as NotificationType))
        }
      >
        <SelectTrigger className="w-[170px]">
          <SelectValue placeholder="All Types" />
        </SelectTrigger>
        <SelectContent>
          {TYPE_OPTIONS.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Popover>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            className={cn(
              "justify-start text-left font-normal",
              !dateRange.from && "text-muted-foreground"
            )}
          >
            <CalendarIcon className="mr-2 h-4 w-4" />
            {formatDateRange()}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0" align="start">
          <Calendar
            mode="range"
            selected={dateRange.from ? { from: dateRange.from, to: dateRange.to } : undefined}
            onSelect={handleDateSelect}
            numberOfMonths={2}
          />
        </PopoverContent>
      </Popover>

      {hasFilters && (
        <Button variant="ghost" size="sm" onClick={handleClearFilters}>
          <X className="mr-1 h-4 w-4" />
          Clear
        </Button>
      )}
    </div>
  );
}
