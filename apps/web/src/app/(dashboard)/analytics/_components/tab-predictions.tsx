"use client";

import { useState, useMemo } from "react";
import {
  AlertCircle,
  Package,
  Warehouse,
  Activity,
  Timer,
  ChevronLeft,
  ChevronRight,
  Search,
  SlidersHorizontal,
} from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { MultiSelect, type MultiSelectOption } from "@/components/ui/multi-select";
import { cn } from "@/lib/utils";
import { usePredictions } from "@/hooks/queries/use-predictions";
import type { ActionItem, ActionUrgency } from "@/types/analytics";

type UrgencyFilter = ActionUrgency | "ALL";
type SortField = "daysToStockout" | "demandVelocity" | "suggestedReorderQty" | "name";
type SortDirection = "asc" | "desc";
type SortOption = `${SortField}-${SortDirection}`;

const SORT_OPTIONS: { value: SortOption; label: string }[] = [
  { value: "daysToStockout-asc", label: "Days to Stockout: Low to High" },
  { value: "daysToStockout-desc", label: "Days to Stockout: High to Low" },
  { value: "demandVelocity-desc", label: "Demand Velocity: High to Low" },
  { value: "demandVelocity-asc", label: "Demand Velocity: Low to High" },
  { value: "suggestedReorderQty-desc", label: "Reorder Qty: High to Low" },
  { value: "suggestedReorderQty-asc", label: "Reorder Qty: Low to High" },
  { value: "name-asc", label: "Name: A to Z" },
  { value: "name-desc", label: "Name: Z to A" },
];

const PAGE_SIZE = 25;

const STOCKOUT_THRESHOLDS = {
  CRITICAL: 3,
  URGENT: 7,
  ATTENTION: 14,
} as const;

function getDaysToStockoutColor(days: number | null): string {
  if (days == null) return "text-foreground";
  if (days <= STOCKOUT_THRESHOLDS.CRITICAL) return "text-red-600 dark:text-red-400";
  if (days <= STOCKOUT_THRESHOLDS.URGENT) return "text-orange-600 dark:text-orange-400";
  if (days <= STOCKOUT_THRESHOLDS.ATTENTION) return "text-amber-600 dark:text-amber-400";
  return "text-green-600 dark:text-green-400";
}

function isValidImageUrl(url: string | null | undefined): boolean {
  if (!url) return false;
  return url.startsWith('https://') || url.startsWith('/');
}

function PredictionItemCard({ item, showUrgencyColor }: { item: ActionItem; showUrgencyColor: boolean }) {
  const formatDate = (dateStr: string) => {
    if (!dateStr) return "N/A";
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return "N/A";
    return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
  };

  return (
    <Card className="bg-card hover:bg-card/50 transition-colors border border-border dark:border-0 shadow-none">
      <CardContent className="px-4 py-0">
        {/* Desktop layout */}
        <div className="hidden sm:flex items-center gap-4">
          {/* Thumbnail */}
          <div className="shrink-0">
            {isValidImageUrl(item.imageUrl) ? (
              <img
                src={item.imageUrl!}
                alt={item.name}
                className="h-16 w-16 rounded-lg object-cover"
              />
            ) : (
              <div className="h-16 w-16 rounded-lg bg-muted flex items-center justify-center">
                <Package className="h-8 w-8 text-muted-foreground" />
              </div>
            )}
          </div>

          {/* Product Info */}
          <div className="flex-1 min-w-0">
            <h3 className="text-base font-semibold truncate">{item.name}</h3>
            <div className="flex flex-wrap gap-8 mt-1 text-sm text-muted-foreground">
              <span className="flex items-center gap-1 font-light">
                <Warehouse className="h-4 w-4" />
                <span className="text-foreground font-medium font-mono">
                  {item.currentStock}
                </span>
                units
              </span>
              <span className="flex items-center gap-1 font-light">
                <Activity className="h-4 w-4" />
                <span className="text-foreground font-medium font-mono">
                  {item.demandVelocity?.toFixed(2) ?? "N/A"}
                </span>
                units/day
              </span>
              <span className="flex items-center gap-1 font-light">
                <Timer className="h-4 w-4" />
                <span className={cn(
                  "font-medium font-mono",
                  showUrgencyColor ? getDaysToStockoutColor(item.daysToStockout) : "text-foreground"
                )}>
                  {item.daysToStockout?.toFixed(2) ?? "N/A"}
                </span>
                days
              </span>
            </div>
          </div>

          {/* Action Info */}
          <div className="shrink-0 flex items-center gap-1.5 text-base font-semibold">
            <span className="text-muted-foreground font-normal">Suggestion:</span>
            {item.suggestedReorderQty} units <span className="font-light text-muted-foreground">by</span> {formatDate(item.suggestedOrderDate)}
          </div>
        </div>

        {/* Mobile layout */}
        <div className="flex flex-col gap-2 sm:hidden">
          {/* Row 1: Image + Name */}
          <div className="flex items-center gap-3">
            <div className="shrink-0">
              {isValidImageUrl(item.imageUrl) ? (
                <img
                  src={item.imageUrl!}
                  alt={item.name}
                  className="h-10 w-10 rounded-lg object-cover"
                />
              ) : (
                <div className="h-10 w-10 rounded-lg bg-muted flex items-center justify-center">
                  <Package className="h-5 w-5 text-muted-foreground" />
                </div>
              )}
            </div>
            <h3 className="text-sm font-semibold truncate flex-1">{item.name}</h3>
          </div>

          {/* Row 2: Metrics with justify-between */}
          <div className="flex justify-between text-xs text-muted-foreground">
            <span className="flex items-center gap-1 font-light">
              <Warehouse className="h-4 w-4" />
              <span className="text-foreground font-medium font-mono">
                {item.currentStock}
              </span>
              units
            </span>
            <span className="flex items-center gap-1 font-light">
              <Activity className="h-4 w-4" />
              <span className="text-foreground font-medium font-mono">
                {item.demandVelocity?.toFixed(2) ?? "N/A"}
              </span>
              units/day
            </span>
            <span className="flex items-center gap-1 font-light">
              <Timer className="h-4 w-4" />
              <span className={cn(
                "font-medium font-mono",
                showUrgencyColor ? getDaysToStockoutColor(item.daysToStockout) : "text-foreground"
              )}>
                {item.daysToStockout?.toFixed(2) ?? "N/A"}
              </span>
              days
            </span>
          </div>

          {/* Row 3: Action Info */}
          <div className="flex justify-center items-center gap-1.5 text-sm font-semibold">
            <span className="text-muted-foreground font-normal">Suggestion:</span>
            {item.suggestedReorderQty} units <span className="font-light text-muted-foreground">by</span> {formatDate(item.suggestedOrderDate)}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

export function TabPredictions() {
  const { data, isLoading, isError } = usePredictions();
  const [urgencyFilter, setUrgencyFilter] = useState<UrgencyFilter>("ALL");
  const [categoryFilter, setCategoryFilter] = useState<string[]>([]);
  const [sortOption, setSortOption] = useState<SortOption>("daysToStockout-asc");
  const [page, setPage] = useState(0);
  const [searchQuery, setSearchQuery] = useState("");

  // Parse sort option into field and direction
  const [sortField, sortDirection] = sortOption.split("-") as [SortField, SortDirection];

  // Extract unique categories from data
  const categories = useMemo((): MultiSelectOption<string>[] => {
    if (!data?.items) return [];
    const uniqueCategories = [...new Set(data.items.map((item) => item.categoryName))].sort();
    return uniqueCategories.map((cat) => ({ value: cat, label: cat }));
  }, [data?.items]);

  // Filter, sort, and paginate items
  const processedItems = useMemo(() => {
    if (!data?.items) return { items: [], totalCount: 0 };

    // Filter by urgency
    let filtered = urgencyFilter === "ALL"
      ? [...data.items]
      : data.items.filter((item) => item.urgency === urgencyFilter);

    // Filter by search query (only in ALL tab)
    if (urgencyFilter === "ALL" && searchQuery.trim()) {
      const query = searchQuery.toLowerCase().trim();
      filtered = filtered.filter((item) =>
        item.name.toLowerCase().includes(query)
      );
    }

    // Filter by category
    if (categoryFilter.length > 0) {
      filtered = filtered.filter((item) => categoryFilter.includes(item.categoryName));
    }

    // Sort
    const sortedItems = [...filtered].sort((a, b) => {
      const multiplier = sortDirection === "asc" ? 1 : -1;
      if (sortField === "name") {
        return multiplier * a.name.localeCompare(b.name);
      }
      const aVal = a[sortField] ?? 0;
      const bVal = b[sortField] ?? 0;
      return multiplier * (aVal - bVal);
    });

    // Paginate
    const start = page * PAGE_SIZE;
    return {
      items: sortedItems.slice(start, start + PAGE_SIZE),
      totalCount: filtered.length,
    };
  }, [data?.items, urgencyFilter, categoryFilter, searchQuery, sortField, sortDirection, page]);

  // Reset page when filters change
  const handleUrgencyChange = (newUrgency: UrgencyFilter) => {
    setUrgencyFilter(newUrgency);
    setSearchQuery(""); // Clear search when changing tabs
    setPage(0);
  };

  const handleSearchChange = (newQuery: string) => {
    setSearchQuery(newQuery);
    setPage(0);
  };

  const handleCategoryChange = (newCategories: string[]) => {
    setCategoryFilter(newCategories);
    setPage(0);
  };

  const handleSortChange = (newSortOption: SortOption) => {
    setSortOption(newSortOption);
    setPage(0);
  };

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <AlertCircle className="h-12 w-12 text-destructive mb-4" />
        <h3 className="text-lg font-semibold">Failed to load predictions</h3>
        <p className="text-muted-foreground">Please try again later.</p>
      </div>
    );
  }

  const totalPages = Math.ceil(processedItems.totalCount / PAGE_SIZE);
  const startItem = page * PAGE_SIZE + 1;
  const endItem = Math.min(startItem + PAGE_SIZE - 1, processedItems.totalCount);

  return (
    <div className="space-y-6">
      {!isLoading && data && (
        <Card className="bg-background border-0 rounded-none py-0 shadow-none">
          {/* Urgency Tabs - Horizontally Scrollable */}
          <div className="relative">
            <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-6 bg-gradient-to-l from-background to-transparent z-10" />
            <div className="flex gap-6 overflow-x-auto scrollbar-none border-b dark:border-b-[0.5px] pr-6">
              {[
                {
                  value: "ALL",
                  label: "All",
                  count: data.items.length,
                },
                {
                  value: "CRITICAL",
                  label: "Critical",
                  count: data.riskSummary.critical,
                },
                {
                  value: "URGENT",
                  label: "Urgent",
                  count: data.riskSummary.urgent,
                },
                {
                  value: "ATTENTION",
                  label: "Attention",
                  count: data.riskSummary.attention,
                },
                {
                  value: "HEALTHY",
                  label: "Safe",
                  count: data.riskSummary.healthy,
                },
              ].map((tab) => (
                <button
                  key={tab.value}
                  onClick={() => handleUrgencyChange(tab.value as UrgencyFilter)}
                  className={cn(
                    "shrink-0 whitespace-nowrap pb-2 text-xs sm:text-sm font-medium transition-colors relative cursor-pointer",
                    urgencyFilter === tab.value
                      ? "text-[#0b66c2] dark:text-[#7c3aed]"
                      : "text-muted-foreground hover:text-foreground"
                  )}
                >
                  {tab.label} ({tab.count})
                  {urgencyFilter === tab.value && (
                    <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-[#0b66c2] dark:bg-[#7c3aed]" />
                  )}
                </button>
              ))}
            </div>
          </div>

          {/* Filter Controls */}
          {/* Mobile: "All" tab shows search + filter popover; other tabs show category + sort directly */}
          <div className="flex sm:hidden items-center gap-2">
            {urgencyFilter === "ALL" ? (
              <>
                <div className="relative flex-1">
                  <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    placeholder="Search items..."
                    value={searchQuery}
                    onChange={(e) => handleSearchChange(e.target.value)}
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
                          onChange={handleCategoryChange}
                          placeholder="All Categories"
                          className="w-full"
                        />
                      </div>
                      <div className="grid gap-1.5">
                        <Label className="text-xs text-muted-foreground">Sort By</Label>
                        <Select
                          value={sortOption}
                          onValueChange={(val) => handleSortChange(val as SortOption)}
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
              </>
            ) : (
              <>
                <MultiSelect
                  options={categories}
                  selected={categoryFilter}
                  onChange={handleCategoryChange}
                  placeholder="All Categories"
                  className="flex-1 w-0 min-w-0 shrink overflow-hidden"
                />
                <Select
                  value={sortOption}
                  onValueChange={(val) => handleSortChange(val as SortOption)}
                >
                  <SelectTrigger className="flex-1 w-0 min-w-0 shrink overflow-hidden [&>span:first-child]:truncate">
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
              </>
            )}
          </div>

          {/* Desktop: Search bar + inline category/sort dropdowns */}
          <div className="hidden sm:flex items-center gap-2">
            {urgencyFilter === "ALL" && (
              <div className="relative w-64">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  placeholder="Search items..."
                  value={searchQuery}
                  onChange={(e) => handleSearchChange(e.target.value)}
                  className="pl-9"
                />
              </div>
            )}
            <MultiSelect
              options={categories}
              selected={categoryFilter}
              onChange={handleCategoryChange}
              placeholder="All Categories"
              className="w-40"
            />
            <Select
              value={sortOption}
              onValueChange={(val) => handleSortChange(val as SortOption)}
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

          {/* Items Grid */}
          {processedItems.items.length === 0 ? (
            <Card className="bg-card border-0 p-6 text-center">
              <Package className="h-12 w-12 mx-auto text-green-500 mb-4" />
              <h3 className="text-lg font-semibold text-green-700 dark:text-green-300">
                {urgencyFilter === "ALL" ? "No items" : `No ${urgencyFilter.toLowerCase()} items`}
              </h3>
              <p className="text-muted-foreground">
                {categoryFilter.length > 0
                  ? "No items match your filters."
                  : urgencyFilter === "ALL"
                    ? "No prediction items available."
                    : `No items with ${urgencyFilter.toLowerCase()} urgency level.`}
              </p>
            </Card>
          ) : (
            <div className="grid gap-2 grid-cols-1">
              {processedItems.items.map((item) => (
                <PredictionItemCard key={item.itemId} item={item} showUrgencyColor />
              ))}
            </div>
          )}

          {/* Pagination */}
          {processedItems.totalCount > 0 && (
            <div className="flex items-center justify-between px-2 pt-4 gap-2">
              <p className="text-xs sm:text-sm text-muted-foreground whitespace-nowrap">
                Showing {startItem}-{endItem} of {processedItems.totalCount}
              </p>
              <div className="flex items-center gap-1 sm:gap-2">
                <Button
                  variant="outline"
                  size="icon"
                  onClick={() => setPage(page - 1)}
                  disabled={page === 0}
                  className="h-8 w-8 sm:h-9 sm:w-auto sm:px-3"
                >
                  <ChevronLeft className="h-4 w-4" />
                  <span className="hidden sm:inline sm:ml-1">Previous</span>
                </Button>
                <span className="text-xs sm:text-sm text-muted-foreground whitespace-nowrap px-1 sm:px-2">
                  Page {page + 1} of {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="icon"
                  onClick={() => setPage(page + 1)}
                  disabled={page >= totalPages - 1}
                  className="h-8 w-8 sm:h-9 sm:w-auto sm:px-3"
                >
                  <span className="hidden sm:inline sm:mr-1">Next</span>
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          )}
        </Card>
      )}

      {isLoading && (
        <Card className="bg-background border-0 rounded-none py-0 shadow-none">
          {/* Urgency Tabs Skeleton */}
          <div className="relative">
            <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-6 bg-gradient-to-l from-background to-transparent z-10" />
            <div className="flex gap-6 overflow-x-auto scrollbar-none border-b dark:border-b-[0.5px] pr-6">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-4 w-16 shrink-0 mb-2" />
              ))}
            </div>
          </div>

          {/* Filter Controls Skeleton */}
          {/* Mobile */}
          <div className="flex sm:hidden items-center gap-2">
            <Skeleton className="h-9 flex-1" />
            <Skeleton className="h-8 w-10" />
          </div>
          {/* Desktop */}
          <div className="hidden sm:flex items-center gap-2">
            <Skeleton className="h-9 w-64" />
            <Skeleton className="h-9 w-40" />
            <Skeleton className="h-9 w-56" />
          </div>

          {/* Items Grid Skeleton */}
          <div className="grid gap-2 grid-cols-1">
            {Array.from({ length: 6 }).map((_, i) => (
              <Card key={i} className="bg-card border border-border dark:border-0 shadow-none">
                <CardContent className="px-4 py-0">
                  {/* Desktop layout skeleton */}
                  <div className="hidden sm:flex items-center gap-4">
                    <Skeleton className="h-16 w-16 rounded-lg shrink-0" />
                    <div className="flex-1 min-w-0 space-y-2">
                      <Skeleton className="h-5 w-48" />
                      <div className="flex gap-8">
                        <Skeleton className="h-4 w-20" />
                        <Skeleton className="h-4 w-24" />
                        <Skeleton className="h-4 w-16" />
                      </div>
                    </div>
                    <Skeleton className="h-5 w-44 shrink-0" />
                  </div>
                  {/* Mobile layout skeleton */}
                  <div className="flex flex-col gap-2 sm:hidden">
                    <div className="flex items-center gap-3">
                      <Skeleton className="h-10 w-10 rounded-lg shrink-0" />
                      <Skeleton className="h-4 w-32 flex-1" />
                    </div>
                    <div className="flex justify-between">
                      <Skeleton className="h-3 w-16" />
                      <Skeleton className="h-3 w-20" />
                      <Skeleton className="h-3 w-14" />
                    </div>
                    <Skeleton className="h-4 w-36 mx-auto" />
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}
