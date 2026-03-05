"use client";

import { useMemo, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import {
  ChevronLeft,
  ChevronRight,
  X,
  ArrowRight,
  ImageOff,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import type { RiskBand, FilterableStockItem } from "@/types/dashboard";
import { RISK_BAND_LABELS, getRiskBand } from "@/types/dashboard";
import type { ForecastPrediction, Product } from "@/types/api";

const PAGE_SIZE = 10;

interface FilterableStockListProps {
  forecasts: ForecastPrediction[];
  products: Product[];
  selectedRiskBand: RiskBand | null;
  onClearFilter: () => void;
  isLoading?: boolean;
}

const RISK_BAND_COLORS: Record<RiskBand, string> = {
  critical: "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400",
  warning:
    "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400",
  healthy:
    "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400",
  safe: "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400",
  overstocked: "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400",
};

const PROGRESS_COLORS: Record<RiskBand, string> = {
  critical: "[&>[data-slot=progress-indicator]]:bg-red-500",
  warning: "[&>[data-slot=progress-indicator]]:bg-amber-500",
  healthy: "[&>[data-slot=progress-indicator]]:bg-green-500",
  safe: "[&>[data-slot=progress-indicator]]:bg-blue-500",
  overstocked: "[&>[data-slot=progress-indicator]]:bg-gray-400",
};

function formatDaysToStockout(days: number | null): string {
  if (days === null) return "-";
  if (days <= 0) return "OOS";
  if (days > 365) return ">1yr";
  return `${Math.round(days)}d`;
}

function buildStockItems(
  forecasts: ForecastPrediction[],
  products: Product[],
): FilterableStockItem[] {
  const forecastByItemId = new Map(forecasts.map((f) => [f.itemId, f]));

  return products.map((product) => {
    const forecast = forecastByItemId.get(product.id);
    const daysToStockout = forecast?.daysToStockout ?? null;
    const maxStock =
      product.targetStockLevel ??
      product.reorderPoint ??
      Math.max(product.quantity, 1);
    const currentStock = product.quantity;
    const stockPercentage = Math.min(
      100,
      (currentStock / Math.max(maxStock, 1)) * 100,
    );

    return {
      itemId: product.id,
      itemName: product.name,
      itemSku: product.sku,
      imageUrl: product.imageUrl ?? null,
      status: getRiskBand(daysToStockout),
      daysToStockout,
      currentStock,
      maxStock,
      stockPercentage,
      lastUpdated: product.updatedAt,
    };
  });
}

function sortByDaysToStockout(items: FilterableStockItem[]): FilterableStockItem[] {
  return [...items].sort((a, b) => {
    const da = a.daysToStockout ?? Infinity;
    const db = b.daysToStockout ?? Infinity;
    return da - db;
  });
}

export function FilterableStockList({
  forecasts,
  products,
  selectedRiskBand,
  onClearFilter,
  isLoading,
}: FilterableStockListProps) {
  const [page, setPage] = useState(0);

  // Track previous filter to reset page when it changes
  const [prevRiskBand, setPrevRiskBand] = useState(selectedRiskBand);
  if (selectedRiskBand !== prevRiskBand) {
    setPrevRiskBand(selectedRiskBand);
    setPage(0);
  }

  const allItems = useMemo(
    () => buildStockItems(forecasts, products),
    [forecasts, products],
  );

  const filteredItems = useMemo(() => {
    if (!selectedRiskBand) return allItems;
    return allItems.filter((item) => item.status === selectedRiskBand);
  }, [allItems, selectedRiskBand]);

  const sortedItems = useMemo(
    () => sortByDaysToStockout(filteredItems),
    [filteredItems],
  );

  const totalPages = Math.max(1, Math.ceil(sortedItems.length / PAGE_SIZE));
  const pagedItems = useMemo(
    () => sortedItems.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE),
    [sortedItems, page],
  );

  if (isLoading) {
    return (
      <Card className="h-full bg-transparent border-none shadow-none py-6">
        <CardHeader className="flex flex-row items-center justify-between px-6">
          <Skeleton className="h-6 w-32" />
          <Skeleton className="h-8 w-20" />
        </CardHeader>
        <CardContent>
          <div className="h-[280px] space-y-4">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-start gap-3">
                <Skeleton className="h-12 w-12 shrink-0 rounded-md" />
                <div className="flex-1 space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Skeleton className="h-4 w-40" />
                      <Skeleton className="h-5 w-16" />
                    </div>
                    <Skeleton className="h-4 w-24" />
                  </div>
                  <Skeleton className="h-2 w-full" />
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="h-full bg-transparent border-none shadow-none py-6">
      <CardHeader className="flex flex-row items-center justify-between px-6">
        <div className="flex items-center gap-2">
          <CardTitle className="text-base">Stock Levels</CardTitle>
          {selectedRiskBand && (
            <Badge
              variant="secondary"
              className={cn("gap-1", RISK_BAND_COLORS[selectedRiskBand])}
            >
              {RISK_BAND_LABELS[selectedRiskBand]}
              <button
                onClick={onClearFilter}
                className="ml-1 rounded-full hover:bg-black/10 dark:hover:bg-white/10"
                aria-label="Clear filter"
              >
                <X className="h-3 w-3" />
              </button>
            </Badge>
          )}
        </div>
        <Button asChild variant="ghost" size="sm">
          <Link href="/products" className="gap-1">
            View All
            <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </Button>
      </CardHeader>
      <CardContent>
        <div className="h-[280px] flex flex-col">
          {sortedItems.length === 0 ? (
            <p className="text-sm text-muted-foreground py-4 text-center flex-1 flex items-center justify-center">
              {selectedRiskBand
                ? `No items in ${RISK_BAND_LABELS[selectedRiskBand]} status.`
                : "No inventory data available"}
            </p>
          ) : (
            <>
              <div className="space-y-4 flex-1 overflow-y-auto pr-1">
                {pagedItems.map((item) => (
                  <Link
                    key={item.itemId}
                    href={`/products/${item.itemId}`}
                    className="flex items-start gap-3 rounded-md p-2 transition-colors hover:bg-accent"
                  >
                    <div className="relative h-12 w-12 shrink-0 overflow-hidden rounded-md bg-muted">
                      {item.imageUrl ? (
                        <Image
                          src={item.imageUrl}
                          alt={item.itemName}
                          fill
                          sizes="48px"
                          className="object-cover"
                        />
                      ) : (
                        <div className="flex h-full w-full items-center justify-center">
                          <ImageOff className="h-5 w-5 text-muted-foreground" />
                        </div>
                      )}
                    </div>
                    <div className="flex-1 min-w-0 space-y-2">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 min-w-0">
                          <span className="font-medium truncate text-sm">
                            {item.itemName}
                          </span>
                          <Badge
                            variant="outline"
                            className={cn(
                              "text-xs shrink-0",
                              RISK_BAND_COLORS[item.status],
                            )}
                          >
                            {RISK_BAND_LABELS[item.status]}
                          </Badge>
                          {item.daysToStockout !== null &&
                            item.status !== "overstocked" && (
                              <Badge
                                variant="secondary"
                                className="text-xs tabular-nums shrink-0"
                              >
                                {formatDaysToStockout(item.daysToStockout)}
                              </Badge>
                            )}
                        </div>
                        <span className="text-sm text-muted-foreground tabular-nums shrink-0">
                          {item.currentStock} / {item.maxStock}
                        </span>
                      </div>
                      <Progress
                        value={item.stockPercentage}
                        className={cn("h-2", PROGRESS_COLORS[item.status])}
                      />
                    </div>
                  </Link>
                ))}
              </div>

              {totalPages > 1 && (
                <div className="flex items-center justify-between border-t pt-3 mt-3">
                  <span className="text-xs text-muted-foreground">
                    {page * PAGE_SIZE + 1}–
                    {Math.min((page + 1) * PAGE_SIZE, sortedItems.length)} of{" "}
                    {sortedItems.length}
                  </span>
                  <div className="flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-7 w-7 p-0"
                      onClick={() => setPage((p) => p - 1)}
                      disabled={page === 0}
                      aria-label="Previous page"
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </Button>
                    <span className="text-xs text-muted-foreground tabular-nums">
                      {page + 1} / {totalPages}
                    </span>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-7 w-7 p-0"
                      onClick={() => setPage((p) => p + 1)}
                      disabled={page >= totalPages - 1}
                      aria-label="Next page"
                    >
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
