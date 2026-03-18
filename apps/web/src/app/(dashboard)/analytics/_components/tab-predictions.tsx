"use client";

import { useState, useMemo } from "react";
import { AlertCircle, Package } from "lucide-react";
import { Card } from "@/components/ui/card";
import { usePredictions } from "@/hooks/queries/use-predictions";
import type { MultiSelectOption } from "@/components/ui/multi-select";
import {
  PAGE_SIZE,
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

  const urgencyTabs = data ? [
    { value: "ALL" as const, label: "All", count: data.items.length },
    { value: "CRITICAL" as const, label: "Critical", count: data.riskSummary.critical },
    { value: "URGENT" as const, label: "Urgent", count: data.riskSummary.urgent },
    { value: "ATTENTION" as const, label: "Attention", count: data.riskSummary.attention },
    { value: "HEALTHY" as const, label: "Safe", count: data.riskSummary.healthy },
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
