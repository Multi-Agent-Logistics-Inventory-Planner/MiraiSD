"use client";

import { useState, useMemo, useCallback } from "react";
import { AlertCircle, Clock, Package } from "lucide-react";
import { computePriorityScore } from "@/lib/utils/format-forecast";
import { Card } from "@/components/ui/card";
import { usePredictions } from "@/hooks/queries/use-predictions";
import { useDismissedPredictions } from "@/hooks/use-dismissed-predictions";
import type { MultiSelectOption } from "@/components/ui/multi-select";
import type { ActionItem } from "@/types/analytics";
import {
  PAGE_SIZE,
  STOCKOUT_THRESHOLDS,
  STALENESS_BANNER_THRESHOLD_MS,
  WELL_STOCKED_THRESHOLD,
  forecastAgeMs,
  formatRelativeAge,
  TriageRow,
  SummaryHead,
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

  const [sortField, sortDirection] = sortOption.split("-") as [SortField, SortDirection];

  const isDismissed = useCallback(
    (item: ActionItem): boolean => {
      if (!dismissedIds.has(item.itemId)) return false;
      const entry = dismissedMap[item.itemId];
      if (!entry) return false;
      return entry.computedAt === item.computedAt;
    },
    [dismissedIds, dismissedMap],
  );

  const nonStaleItems = useMemo(() => {
    if (!data?.items) return [];
    return data.items.filter((item) => {
      if (item.daysToStockout != null && item.daysToStockout >= WELL_STOCKED_THRESHOLD) return false;
      return true;
    });
  }, [data?.items]);

  const newestForecastAgeMs = useMemo(() => {
    if (!data?.items?.length) return null;
    let youngest: number | null = null;
    for (const item of data.items) {
      const age = forecastAgeMs(item.computedAt);
      if (age === null) continue;
      if (youngest === null || age < youngest) youngest = age;
    }
    return youngest;
  }, [data?.items]);

  const showStalenessBanner =
    newestForecastAgeMs !== null && newestForecastAgeMs > STALENESS_BANNER_THRESHOLD_MS;

  const baseFilteredItems = useMemo(
    () => nonStaleItems.filter((item) => !isDismissed(item)),
    [nonStaleItems, isDismissed],
  );

  const resolvedItems = useMemo(
    () => nonStaleItems.filter((item) => isDismissed(item)),
    [nonStaleItems, isDismissed],
  );

  const tabCounts = useMemo(() => countByTab(baseFilteredItems), [baseFilteredItems]);

  const topPriorityItem = useMemo(() => {
    const actionItems = baseFilteredItems.filter(
      (item) => item.daysToStockout === null || item.daysToStockout <= STOCKOUT_THRESHOLDS.URGENT,
    );
    if (actionItems.length === 0) return null;
    return actionItems.reduce((top, item) => {
      const topScore = computePriorityScore(top);
      const itemScore = computePriorityScore(item);
      return itemScore > topScore ? item : top;
    });
  }, [baseFilteredItems]);

  const sourceItems = urgencyFilter === "RESOLVED" ? resolvedItems : baseFilteredItems;

  const categories = useMemo((): MultiSelectOption<string>[] => {
    const uniqueCategories = [...new Set(sourceItems.map((item) => item.categoryName))].sort();
    return uniqueCategories.map((cat) => ({ value: cat, label: cat }));
  }, [sourceItems]);

  // Single predicate for the user's search + category filters, shared by the
  // main list and the pinned restock section so they can never disagree.
  const matchesSearchAndCategory = useCallback(
    (item: ActionItem) => {
      const query = searchQuery.toLowerCase().trim();
      if (query && !item.name.toLowerCase().includes(query)) return false;
      if (categoryFilter.length > 0 && !categoryFilter.includes(item.categoryName)) return false;
      return true;
    },
    [searchQuery, categoryFilter],
  );

  // Drop-segment items at or past their reorder trigger. Pinned as a
  // "Restock candidates" section at the top of ACTION_NEEDED: their
  // days-to-stockout math is drop-shaped (sell out in days when stocked),
  // so they get order-per-drop framing instead of runway framing.
  const restockCandidates = useMemo(
    () =>
      baseFilteredItems
        .filter(
          (item) =>
            item.demandSegment === "drop" &&
            (item.currentStock <= 0 || item.currentStock < item.reorderPoint) &&
            matchesSearchAndCategory(item),
        )
        .sort((a, b) => (b.revenueAtRisk ?? 0) - (a.revenueAtRisk ?? 0)),
    [baseFilteredItems, matchesSearchAndCategory],
  );
  const restockCandidateIds = useMemo(
    () => new Set(restockCandidates.map((item) => item.itemId)),
    [restockCandidates],
  );

  const processedItems = useMemo(() => {
    if (sourceItems.length === 0) return { items: [], totalCount: 0 };

    let filtered: ActionItem[];
    if (urgencyFilter === "RESOLVED") {
      filtered = [...sourceItems];
    } else if (urgencyFilter === "ACTION_NEEDED") {
      filtered = sourceItems.filter(
        (item) => item.daysToStockout === null || item.daysToStockout <= STOCKOUT_THRESHOLDS.URGENT,
      );
    } else if (urgencyFilter === "WATCH") {
      filtered = sourceItems.filter(
        (item) =>
          item.daysToStockout !== null &&
          item.daysToStockout > STOCKOUT_THRESHOLDS.URGENT &&
          item.daysToStockout <= STOCKOUT_THRESHOLDS.ATTENTION,
      );
    } else {
      filtered = sourceItems.filter(
        (item) =>
          item.daysToStockout !== null && item.daysToStockout > STOCKOUT_THRESHOLDS.ATTENTION,
      );
    }

    // Restock candidates render in their own pinned section on ACTION_NEEDED
    if (urgencyFilter === "ACTION_NEEDED") {
      filtered = filtered.filter((item) => !restockCandidateIds.has(item.itemId));
    }

    filtered = filtered.filter(matchesSearchAndCategory);

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

    const start = page * PAGE_SIZE;
    return {
      items: sortedItems.slice(start, start + PAGE_SIZE),
      totalCount: filtered.length,
    };
  }, [sourceItems, urgencyFilter, restockCandidateIds, matchesSearchAndCategory, sortField, sortDirection, page]);

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

  const urgencyTabs = data
    ? [
        { value: "ACTION_NEEDED" as const, label: "Action Needed", count: tabCounts.actionNeeded },
        { value: "WATCH" as const, label: "Watch", count: tabCounts.watch },
        { value: "HEALTHY" as const, label: "Healthy", count: tabCounts.healthy },
        { value: "RESOLVED" as const, label: "Resolved", count: resolvedItems.length },
      ]
    : [];

  return (
    <div className="space-y-4">
      {!isLoading && data && (
        <>
          {showStalenessBanner && (
            <Card className="bg-amber-50 dark:bg-amber-950/30 border border-amber-300 dark:border-amber-700/60 p-3 rounded-xl">
              <div className="flex items-center gap-2 text-sm text-amber-900 dark:text-amber-100">
                <Clock className="h-4 w-4 shrink-0" />
                <span className="font-semibold">Forecasts may be stale.</span>
                <span className="text-amber-800 dark:text-amber-200">
                  Newest forecast is{" "}
                  <span className="font-mono">{formatRelativeAge(newestForecastAgeMs)}</span> —
                  the forecasting worker may be lagging.
                </span>
              </div>
            </Card>
          )}

          <SummaryHead actionCount={tabCounts.actionNeeded} topPriority={topPriorityItem} />

          <Card className="bg-background border-0 rounded-none py-0 shadow-none space-y-3">
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

            {urgencyFilter === "ACTION_NEEDED" && restockCandidates.length > 0 && (
              <Card className="overflow-hidden rounded-xl border border-purple-300 dark:border-purple-700/60 p-0">
                <div className="flex items-center gap-2 px-3 py-2 bg-purple-50 dark:bg-purple-950/30 border-b border-purple-200 dark:border-purple-800/60">
                  <Package className="h-4 w-4 text-purple-700 dark:text-purple-300" />
                  <span className="text-sm font-semibold text-purple-900 dark:text-purple-100">
                    Restock candidates
                  </span>
                  <span className="text-xs text-purple-800 dark:text-purple-300">
                    Drop products at or below their reorder trigger — order per
                    drop, not by daily runway.
                  </span>
                </div>
                {restockCandidates.map((item) => (
                  <TriageRow
                    key={item.itemId}
                    item={item}
                    onDismiss={() => handleDismiss(item)}
                  />
                ))}
              </Card>
            )}

            {processedItems.items.length === 0 ? (
              <Card className="bg-card border p-6 text-center rounded-xl">
                <Package className="h-12 w-12 mx-auto text-emerald-500 mb-4" />
                <h3 className="text-lg font-semibold text-emerald-700 dark:text-emerald-300">
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
              <Card className="overflow-hidden rounded-xl border p-0">
                {processedItems.items.map((item) => (
                  <TriageRow
                    key={item.itemId}
                    item={item}
                    onDismiss={isResolved ? undefined : () => handleDismiss(item)}
                    onRestore={isResolved ? () => handleRestore(item) : undefined}
                  />
                ))}
              </Card>
            )}

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
