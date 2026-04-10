"use client";

import { useState, useMemo, useCallback } from "react";
import { AlertCircle, Package, TrendingUp } from "lucide-react";
import { computePriorityScore } from "@/lib/utils/format-forecast";
import { Card } from "@/components/ui/card";
import { usePredictions } from "@/hooks/queries/use-predictions";
import { useDismissedPredictions } from "@/hooks/use-dismissed-predictions";
import type { MultiSelectOption } from "@/components/ui/multi-select";
import type { ActionItem } from "@/types/analytics";
import { STOCKOUT_THRESHOLDS } from "@/components/analytics/predictions";
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

interface TabCounts {
  actionNeeded: number;
  watch: number;
  healthy: number;
}

function countByTab(items: readonly ActionItem[]): TabCounts {
  const counts: TabCounts = { actionNeeded: 0, watch: 0, healthy: 0 };
  for (const item of items) {
    const days = item.daysToStockout;
    if (days === null || days <= STOCKOUT_THRESHOLDS.URGENT) {
      counts.actionNeeded++;
    } else if (days <= STOCKOUT_THRESHOLDS.ATTENTION) {
      counts.watch++;
    } else {
      counts.healthy++;
    }
  }
  return counts;
}

export function TabPredictions() {
  const { data, isLoading, isError } = usePredictions();
  const { dismissedMap, dismissedIds, dismiss, restore } = useDismissedPredictions();

  const [urgencyFilter, setUrgencyFilter] = useState<UrgencyFilter>("ACTION_NEEDED");
  const [categoryFilter, setCategoryFilter] = useState<string[]>([]);
  const [sortOption, setSortOption] = useState<SortOption>("priority-desc");
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

  // Recalculate tab counts from active items
  const tabCounts = useMemo(() => countByTab(baseFilteredItems), [baseFilteredItems]);

  // Find top priority item from action-needed items
  const topPriorityItem = useMemo(() => {
    const actionItems = baseFilteredItems.filter(
      (item) => item.daysToStockout === null || item.daysToStockout <= STOCKOUT_THRESHOLDS.URGENT
    );
    if (actionItems.length === 0) return null;
    return actionItems.reduce((top, item) => {
      const topScore = computePriorityScore(top);
      const itemScore = computePriorityScore(item);
      return itemScore > topScore ? item : top;
    });
  }, [baseFilteredItems]);

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

    // Filter by tab (based on daysToStockout thresholds)
    let filtered: ActionItem[];
    if (urgencyFilter === "RESOLVED") {
      filtered = [...sourceItems];
    } else if (urgencyFilter === "ACTION_NEEDED") {
      filtered = sourceItems.filter((item) =>
        item.daysToStockout === null || item.daysToStockout <= STOCKOUT_THRESHOLDS.URGENT
      );
    } else if (urgencyFilter === "WATCH") {
      filtered = sourceItems.filter((item) =>
        item.daysToStockout !== null &&
        item.daysToStockout > STOCKOUT_THRESHOLDS.URGENT &&
        item.daysToStockout <= STOCKOUT_THRESHOLDS.ATTENTION
      );
    } else {
      // HEALTHY
      filtered = sourceItems.filter((item) =>
        item.daysToStockout !== null && item.daysToStockout > STOCKOUT_THRESHOLDS.ATTENTION
      );
    }

    // Filter by search query
    if (searchQuery.trim()) {
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
      if (sortField === "priority") {
        const aVal = computePriorityScore(a);
        const bVal = computePriorityScore(b);
        return multiplier * (aVal - bVal);
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
    { value: "ACTION_NEEDED" as const, label: "Action Needed", count: tabCounts.actionNeeded },
    { value: "WATCH" as const, label: "Watch", count: tabCounts.watch },
    { value: "HEALTHY" as const, label: "Healthy", count: tabCounts.healthy },
    { value: "RESOLVED" as const, label: "Resolved", count: resolvedItems.length },
  ] : [];

  // Format order date for summary
  const formatOrderDate = (dateStr: string | null): string => {
    if (!dateStr) return "";
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return "";
    return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
  };

  return (
    <div className="space-y-6">
      {!isLoading && data && (
        <>
          {/* Summary Header */}
          {tabCounts.actionNeeded > 0 && topPriorityItem && (
            <Card className="bg-gradient-to-r from-amber-50 to-orange-50 dark:from-amber-950/30 dark:to-orange-950/30 border border-amber-200 dark:border-amber-800 p-4">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-2">
                    <TrendingUp className="h-5 w-5 text-amber-600 dark:text-amber-400" />
                    <span className="font-semibold text-amber-900 dark:text-amber-100">
                      {tabCounts.actionNeeded} {tabCounts.actionNeeded === 1 ? "item needs" : "items need"} ordering this week
                    </span>
                  </div>
                </div>
              </div>
              <div className="mt-2 text-sm text-amber-800 dark:text-amber-200">
                <span className="font-medium">Top priority:</span>{" "}
                <span className="font-semibold">{topPriorityItem.name}</span>
                {topPriorityItem.suggestedReorderQty > 0 && (
                  <>
                    {" "}- order {topPriorityItem.suggestedReorderQty} units
                    {topPriorityItem.suggestedOrderDate && (
                      <> by {formatOrderDate(topPriorityItem.suggestedOrderDate)}</>
                    )}
                  </>
                )}
              </div>
            </Card>
          )}

          <Card className="bg-background border-0 rounded-none py-0 shadow-none">
            <UrgencyTabs
              tabs={urgencyTabs}
              activeTab={urgencyFilter}
              onTabChange={handleUrgencyChange}
            />

          <MobileFilterControls
            searchQuery={searchQuery}
            onSearchChange={handleSearchChange}
            categories={categories}
            categoryFilter={categoryFilter}
            onCategoryChange={handleCategoryChange}
            sortOption={sortOption}
            onSortChange={handleSortChange}
          />

          <DesktopFilterControls
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
                  : urgencyFilter === "ACTION_NEEDED"
                    ? "No action needed"
                    : urgencyFilter === "WATCH"
                      ? "Nothing to watch"
                      : "No healthy items"}
              </h3>
              <p className="text-muted-foreground">
                {categoryFilter.length > 0
                  ? "No items match your filters."
                  : isResolved
                    ? "Dismissed items appear here. They auto-archive after 30 days."
                    : urgencyFilter === "ACTION_NEEDED"
                      ? "Great! No items need immediate attention."
                      : urgencyFilter === "WATCH"
                        ? "No items to monitor right now."
                        : "No items with healthy stock levels."}
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
        </>
      )}

      {isLoading && <PredictionsSkeleton />}
    </div>
  );
}
