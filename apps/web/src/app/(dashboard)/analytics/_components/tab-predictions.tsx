"use client";

import { useState, useMemo, useCallback } from "react";
import { AlertCircle, Package } from "lucide-react";
import { Card } from "@/components/ui/card";
import { usePredictions } from "@/hooks/queries/use-predictions";
import { useDismissedPredictions } from "@/hooks/use-dismissed-predictions";
import type { MultiSelectOption } from "@/components/ui/multi-select";
import type { ActionItem, ActionUrgency } from "@/types/analytics";
import {
  PAGE_SIZE,
  WELL_STOCKED_THRESHOLD,
  isForecastStale,
  PredictionItemCard,
  UrgencyTabs,
  MobileFilterControls,
  DesktopFilterControls,
  PredictionsPagination,
  PredictionsSkeleton,
  type UrgencyFilter,
  type SortField,
  type SortDirection,
  type SortOption,
} from "@/components/analytics/predictions";

function countByUrgency(items: readonly ActionItem[]): Record<ActionUrgency, number> {
  const counts: Record<ActionUrgency, number> = { CRITICAL: 0, URGENT: 0, ATTENTION: 0, HEALTHY: 0 };
  for (const item of items) {
    if (item.urgency in counts) {
      counts[item.urgency]++;
    }
  }
  return counts;
}

export function TabPredictions() {
  const { data, isLoading, isError } = usePredictions();
  const { dismissedMap, dismissedIds, dismiss, restore } = useDismissedPredictions();

  const [urgencyFilter, setUrgencyFilter] = useState<UrgencyFilter>("ALL");
  const [categoryFilter, setCategoryFilter] = useState<string[]>([]);
  const [sortOption, setSortOption] = useState<SortOption>("daysToStockout-asc");
  const [page, setPage] = useState(0);
  const [searchQuery, setSearchQuery] = useState("");

  // Parse sort option into field and direction
  const [sortField, sortDirection] = sortOption.split("-") as [SortField, SortDirection];

  // Check if a dismissed item should be auto-undismissed (forecast was re-run)
  const isDismissed = useCallback(
    (item: ActionItem): boolean => {
      if (!dismissedIds.has(item.itemId)) return false;
      const entry = dismissedMap[item.itemId];
      if (!entry) return false;
      return entry.computedAt === item.computedAt;
    },
    [dismissedIds, dismissedMap],
  );

  // Items that pass stale + stockout filters (before dismiss filter)
  const nonStaleItems = useMemo(() => {
    if (!data?.items) return [];
    return data.items.filter((item) => {
      if (isForecastStale(item.computedAt)) return false;
      if (item.daysToStockout != null && item.daysToStockout >= WELL_STOCKED_THRESHOLD) return false;
      return true;
    });
  }, [data?.items]);

  // Active items (not dismissed)
  const baseFilteredItems = useMemo(
    () => nonStaleItems.filter((item) => !isDismissed(item)),
    [nonStaleItems, isDismissed],
  );

  // Resolved items (dismissed, not yet archived -- auto-archived after 30 days by hook)
  const resolvedItems = useMemo(
    () => nonStaleItems.filter((item) => isDismissed(item)),
    [nonStaleItems, isDismissed],
  );

  // Recalculate urgency counts from active items
  const filteredCounts = useMemo(() => countByUrgency(baseFilteredItems), [baseFilteredItems]);

  // Pick the right source list based on active tab
  const sourceItems = urgencyFilter === "RESOLVED" ? resolvedItems : baseFilteredItems;

  // Extract unique categories from current source
  const categories = useMemo((): MultiSelectOption<string>[] => {
    const uniqueCategories = [...new Set(sourceItems.map((item) => item.categoryName))].sort();
    return uniqueCategories.map((cat) => ({ value: cat, label: cat }));
  }, [sourceItems]);

  // Filter, sort, and paginate items
  const processedItems = useMemo(() => {
    if (sourceItems.length === 0) return { items: [], totalCount: 0 };

    // Filter by urgency (skip for ALL and RESOLVED -- already scoped)
    let filtered = (urgencyFilter === "ALL" || urgencyFilter === "RESOLVED")
      ? [...sourceItems]
      : sourceItems.filter((item) => item.urgency === urgencyFilter);

    // Filter by search query (only in ALL/RESOLVED tabs)
    if ((urgencyFilter === "ALL" || urgencyFilter === "RESOLVED") && searchQuery.trim()) {
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
      const nullFallback = sortDirection === "asc" ? Infinity : -Infinity;
      const aVal = a[sortField] ?? nullFallback;
      const bVal = b[sortField] ?? nullFallback;
      return multiplier * (aVal - bVal);
    });

    // Paginate
    const start = page * PAGE_SIZE;
    return {
      items: sortedItems.slice(start, start + PAGE_SIZE),
      totalCount: filtered.length,
    };
  }, [sourceItems, urgencyFilter, categoryFilter, searchQuery, sortField, sortDirection, page]);

  // Reset page when filters change
  const handleUrgencyChange = (newUrgency: UrgencyFilter) => {
    setUrgencyFilter(newUrgency);
    setSearchQuery("");
    setCategoryFilter([]);
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

  const handleDismiss = useCallback(
    (item: ActionItem) => {
      dismiss(item.itemId, item.computedAt);
    },
    [dismiss],
  );

  const handleRestore = useCallback(
    (item: ActionItem) => {
      restore(item.itemId);
    },
    [restore],
  );

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
  const isResolved = urgencyFilter === "RESOLVED";

  const urgencyTabs = data ? [
    { value: "ALL" as const, label: "All", count: baseFilteredItems.length },
    { value: "CRITICAL" as const, label: "Critical", count: filteredCounts.CRITICAL },
    { value: "URGENT" as const, label: "Urgent", count: filteredCounts.URGENT },
    { value: "ATTENTION" as const, label: "Attention", count: filteredCounts.ATTENTION },
    { value: "HEALTHY" as const, label: "Safe", count: filteredCounts.HEALTHY },
    { value: "RESOLVED" as const, label: "Resolved", count: resolvedItems.length },
  ] : [];

  return (
    <div className="space-y-6">
      {!isLoading && data && (
        <Card className="bg-background border-0 rounded-none py-0 shadow-none">
          <UrgencyTabs
            tabs={urgencyTabs}
            activeTab={urgencyFilter}
            onTabChange={handleUrgencyChange}
          />

          <MobileFilterControls
            urgencyFilter={urgencyFilter}
            searchQuery={searchQuery}
            onSearchChange={handleSearchChange}
            categories={categories}
            categoryFilter={categoryFilter}
            onCategoryChange={handleCategoryChange}
            sortOption={sortOption}
            onSortChange={handleSortChange}
          />

          <DesktopFilterControls
            urgencyFilter={urgencyFilter}
            searchQuery={searchQuery}
            onSearchChange={handleSearchChange}
            categories={categories}
            categoryFilter={categoryFilter}
            onCategoryChange={handleCategoryChange}
            sortOption={sortOption}
            onSortChange={handleSortChange}
          />

          {/* Items Grid */}
          {processedItems.items.length === 0 ? (
            <Card className="bg-card border-0 p-6 text-center">
              <Package className="h-12 w-12 mx-auto text-green-500 mb-4" />
              <h3 className="text-lg font-semibold text-green-700 dark:text-green-300">
                {isResolved
                  ? "No resolved items"
                  : urgencyFilter === "ALL"
                    ? "No items"
                    : `No ${urgencyFilter.toLowerCase()} items`}
              </h3>
              <p className="text-muted-foreground">
                {categoryFilter.length > 0
                  ? "No items match your filters."
                  : isResolved
                    ? "Dismissed items appear here. They auto-archive after 30 days."
                    : urgencyFilter === "ALL"
                      ? "No prediction items available."
                      : `No items with ${urgencyFilter.toLowerCase()} urgency level.`}
              </p>
            </Card>
          ) : (
            <div className="grid gap-2 grid-cols-1">
              {processedItems.items.map((item) => (
                <PredictionItemCard
                  key={item.itemId}
                  item={item}
                  showUrgencyColor
                  onDismiss={isResolved ? undefined : () => handleDismiss(item)}
                  onRestore={isResolved ? () => handleRestore(item) : undefined}
                />
              ))}
            </div>
          )}

          {/* Pagination */}
          {processedItems.totalCount > 0 && (
            <PredictionsPagination
              page={page}
              totalPages={totalPages}
              startItem={startItem}
              endItem={endItem}
              totalCount={processedItems.totalCount}
              onPageChange={setPage}
            />
          )}
        </Card>
      )}

      {isLoading && <PredictionsSkeleton />}
    </div>
  );
}
