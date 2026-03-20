"use client";

import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Clock, ListFilter } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ProductThumbnail } from "@/components/products/product-thumbnail";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  DataTableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  LocationType,
  LOCATION_TYPE_LABELS,
  MACHINE_LOCATION_TYPES,
} from "@/types/api";
import {
  useLongestRunningDisplays,
  MachineDisplaySummary,
} from "@/hooks/queries/use-machine-displays";
import { useProducts } from "@/hooks/queries/use-products";
import { useLocation } from "@/hooks/queries/use-locations";
import { LocationDetailSheet } from "@/components/locations/location-detail-sheet";

const ROWS_PER_PAGE = 10;

function LoadingSkeleton() {
  return (
    <Card className="flex flex-col overflow-hidden h-[340px] dark:border-none">
      <CardHeader className="shrink-0 pb-0">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Skeleton className="h-4 w-4 rounded" />
            <Skeleton className="h-5 w-44" />
          </div>
          <Skeleton className="h-8 w-8 rounded-md" />
        </div>
      </CardHeader>
      <CardContent className="flex-1 min-h-0 pt-6 px-6 overflow-hidden">
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="flex items-center gap-3 py-2">
              <Skeleton className="h-10 w-10 rounded-md shrink-0" />
              <div className="min-w-0 space-y-1 flex-1">
                <Skeleton className="h-4 w-20" />
                <Skeleton className="h-3 w-14" />
              </div>
              <Skeleton className="h-4 w-6 hidden sm:block" />
              <Skeleton className="h-4 w-14" />
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

export function LongestRunningDisplaysCard() {
  const [page, setPage] = useState(0);
  const [machineTypeFilter, setMachineTypeFilter] = useState<string>("all");
  const [selectedMachine, setSelectedMachine] =
    useState<MachineDisplaySummary | null>(null);

  const locationType =
    machineTypeFilter === "all"
      ? undefined
      : MACHINE_LOCATION_TYPES.includes(machineTypeFilter as LocationType)
        ? (machineTypeFilter as LocationType)
        : undefined;

  const {
    data: summaries,
    isLoading,
    isError,
  } = useLongestRunningDisplays({
    locationType,
  });

  const { data: products = [], isError: isProductsError } = useProducts(true);

  // Fetch the full location data for the selected machine
  const { data: selectedLocation } = useLocation(
    selectedMachine?.locationType,
    selectedMachine?.machineId,
  );

  const productImageMap = useMemo(() => {
    const map = new Map<string, string | null>();
    for (const product of products) {
      map.set(product.id, product.imageUrl ?? null);
    }
    return map;
  }, [products]);

  const totalPages = Math.ceil((summaries?.length ?? 0) / ROWS_PER_PAGE);
  const paginatedData = useMemo(() => {
    if (!summaries) return [];
    const start = page * ROWS_PER_PAGE;
    return summaries.slice(start, start + ROWS_PER_PAGE);
  }, [summaries, page]);

  function handleRowClick(summary: MachineDisplaySummary) {
    setSelectedMachine(summary);
  }

  if (isLoading) {
    return <LoadingSkeleton />;
  }

  if (isError || isProductsError) {
    return (
      <Card className="flex flex-col overflow-hidden h-[340px]">
        <CardHeader className="shrink-0 pb-8">
          <CardTitle className="text-base font-semibold flex items-center gap-2">
            <Clock className="h-4 w-4 text-muted-foreground" />
            Longest Running Displays
          </CardTitle>
        </CardHeader>
        <CardContent className="flex-1 flex items-center justify-center">
          <p className="text-sm text-muted-foreground text-center">
            Failed to load display data
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <>
      <Card className="flex flex-col overflow-hidden h-[340px] dark:border-none">
        <CardHeader className="shrink-0 pb-0">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base font-semibold flex items-center gap-2">
              <Clock className="h-4 w-4 text-muted-foreground" />
              Longest Running Displays
            </CardTitle>
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 shrink-0 text-muted-foreground hover:text-muted-foreground"
                  aria-label="Filter by machine type"
                >
                  <ListFilter className="h-4 w-4" />
                </Button>
              </PopoverTrigger>
              <PopoverContent align="end" className="w-48 p-2">
                <div className="space-y-1">
                  <button
                    onClick={() => {
                      setMachineTypeFilter("all");
                      setPage(0);
                    }}
                    className={`w-full text-left px-3 py-2 text-sm rounded-md hover:bg-muted ${
                      machineTypeFilter === "all" ? "bg-muted font-medium" : ""
                    }`}
                  >
                    All Types
                  </button>
                  {MACHINE_LOCATION_TYPES.map((type) => (
                    <button
                      key={type}
                      onClick={() => {
                        setMachineTypeFilter(type);
                        setPage(0);
                      }}
                      className={`w-full text-left px-3 py-2 text-sm rounded-md hover:bg-muted ${
                        machineTypeFilter === type ? "bg-muted font-medium" : ""
                      }`}
                    >
                      {LOCATION_TYPE_LABELS[type]}
                    </button>
                  ))}
                </div>
              </PopoverContent>
            </Popover>
          </div>
        </CardHeader>

        <CardContent className="flex-1 min-h-0 pt-0 px-0 overflow-hidden -mt-4">
          {summaries?.length === 0 ? (
            <div className="flex items-center justify-center h-full px-6">
              <p className="text-sm text-muted-foreground">
                All displays are under 14 days old
              </p>
            </div>
          ) : (
            <div className="flex flex-col h-full">
              <div className="px-6 shrink-0">
                <Table className="table-fixed">
                  <DataTableHeader>
                    <TableHead className="rounded-l-lg w-[50%]">
                      Location
                    </TableHead>
                    <TableHead className="hidden sm:table-cell w-[25%] text-center">
                      Products
                    </TableHead>
                    <TableHead className="rounded-r-lg w-[25%]">
                      Duration
                    </TableHead>
                  </DataTableHeader>
                </Table>
              </div>
              <div className="flex-1 min-h-0 overflow-y-auto px-6">
                <Table className="table-fixed">
                  <TableBody>
                    {paginatedData.map((summary) => {
                      const firstProduct = summary.products[0];
                      return (
                        <TableRow
                          key={`${summary.locationType}:${summary.machineId}`}
                          className="cursor-pointer hover:bg-muted/50"
                          onClick={() => handleRowClick(summary)}
                        >
                          <TableCell className="w-[50%]">
                            <div className="flex items-center gap-3">
                              <ProductThumbnail
                                imageUrl={productImageMap.get(
                                  firstProduct?.productId ?? "",
                                )}
                                alt={firstProduct?.productName ?? "Unknown"}
                                size="md"
                                fallbackVariant="icon"
                                badge={summary.productCount}
                              />
                              <div className="min-w-0">
                                <p className="text-sm font-medium truncate">
                                  {summary.machineCode}
                                </p>
                                <p className="text-xs text-muted-foreground truncate">
                                  {LOCATION_TYPE_LABELS[summary.locationType]}
                                </p>
                              </div>
                            </div>
                          </TableCell>
                          <TableCell className="hidden sm:table-cell w-[25%] text-center">
                            {summary.productCount}
                          </TableCell>
                          <TableCell className="w-[25%]">
                            <span className="text-sm font-medium">
                              {summary.maxDaysActive} day
                              {summary.maxDaysActive !== 1 ? "s" : ""}
                            </span>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
              {totalPages > 1 && (
                <div className="flex items-center justify-between px-6 py-3 text-xs text-muted-foreground shrink-0">
                  <span>
                    Page {page + 1} of {totalPages}
                  </span>
                  <div className="flex gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-6 w-6"
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      disabled={page === 0}
                      aria-label="Previous page"
                    >
                      <ChevronLeft className="h-3 w-3" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-6 w-6"
                      onClick={() =>
                        setPage((p) => Math.min(totalPages - 1, p + 1))
                      }
                      disabled={page >= totalPages - 1}
                      aria-label="Next page"
                    >
                      <ChevronRight className="h-3 w-3" />
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {selectedMachine && (
        <LocationDetailSheet
          open={!!selectedMachine}
          onOpenChange={(open) => {
            if (!open) setSelectedMachine(null);
          }}
          locationType={selectedMachine.locationType}
          location={selectedLocation ?? null}
          onEdit={() => {}} // Read-only view from analytics - no edit action needed
          defaultTab="display"
        />
      )}
    </>
  );
}
