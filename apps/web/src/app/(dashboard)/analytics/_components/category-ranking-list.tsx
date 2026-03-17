"use client";

import { useEffect, useRef } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  DataTableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { formatNumber } from "@/lib/utils/format";
import { getCategoryColor } from "@/lib/utils/category-colors";
import type { CategoryRanking } from "@/types/analytics";

interface CategoryRankingListProps {
  rankings?: CategoryRanking[];
  isLoading: boolean;
  selectedCategory: string | null;
}

export function CategoryRankingList({
  rankings,
  isLoading,
  selectedCategory,
}: CategoryRankingListProps) {
  const rowRefs = useRef<Map<string, HTMLTableRowElement>>(new Map());

  useEffect(() => {
    if (selectedCategory && rowRefs.current.has(selectedCategory)) {
      const element = rowRefs.current.get(selectedCategory);
      element?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }
  }, [selectedCategory]);

  if (isLoading) {
    return (
      <Card className="h-full bg-transparent border-none shadow-none py-6">
        <CardContent className="px-6">
          <div className="h-[320px] space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-center gap-4 py-2">
                <Skeleton className="h-4 w-4 rounded-full shrink-0" />
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-4 w-12 ml-auto" />
                <Skeleton className="h-4 w-16" />
                <Skeleton className="h-4 w-20" />
                <Skeleton className="h-4 w-12" />
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!rankings || rankings.length === 0) {
    return (
      <Card className="h-full bg-transparent border-none shadow-none py-6">
        <CardContent className="px-6">
          <div className="h-[320px] flex items-center justify-center">
            <p className="text-sm text-muted-foreground">
              No category data available
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="h-full bg-transparent border-none shadow-none py-6">
      <CardContent className="px-0">
        <div className="flex flex-col h-[320px]">
          <div className="px-6 shrink-0">
            <Table className="table-fixed">
              <DataTableHeader>
                <TableHead className="rounded-l-lg w-[40%]">Category</TableHead>
                <TableHead className="w-[15%] text-center">
                  <Tooltip>
                    <TooltipTrigger className="cursor-help">
                      Items
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>Number of products in this category</p>
                    </TooltipContent>
                  </Tooltip>
                </TableHead>
                <TableHead className="w-[22%] text-right">
                  <Tooltip>
                    <TooltipTrigger className="cursor-help">
                      Units
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>Total units sold in the selected period</p>
                    </TooltipContent>
                  </Tooltip>
                </TableHead>
                <TableHead className="rounded-r-lg w-[23%] text-right">
                  <Tooltip>
                    <TooltipTrigger className="cursor-help">
                      Velocity
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>Average units sold per day</p>
                    </TooltipContent>
                  </Tooltip>
                </TableHead>
              </DataTableHeader>
            </Table>
          </div>
          <ScrollArea className="flex-1 min-h-0">
            <div className="px-6">
              <Table className="table-fixed">
                <TableBody>
                  {rankings.map((category, index) => {
                    const color = getCategoryColor(index);
                    const isHighlighted = selectedCategory === category.categoryId;

                    return (
                      <TableRow
                        key={category.categoryId}
                        ref={(el) => {
                          if (el) {
                            rowRefs.current.set(category.categoryId, el);
                          } else {
                            rowRefs.current.delete(category.categoryId);
                          }
                        }}
                        className={cn(
                          "hover:bg-muted/50",
                          isHighlighted && "bg-accent",
                        )}
                      >
                        <TableCell className="w-[40%] max-w-0">
                          <div className="flex items-center gap-2 md:gap-3">
                            <span
                              className="h-2.5 w-2.5 md:h-3 md:w-3 rounded-full shrink-0"
                              style={{ backgroundColor: color }}
                            />
                            <span className="text-xs md:text-sm font-medium truncate">
                              {category.categoryName}
                            </span>
                          </div>
                        </TableCell>
                        <TableCell className="w-[15%] text-center text-muted-foreground text-xs md:text-sm">
                          {category.totalItems}
                        </TableCell>
                        <TableCell className="w-[22%] text-right tabular-nums text-xs md:text-sm">
                          {category.periodDemand.toLocaleString()}
                        </TableCell>
                        <TableCell className="w-[23%] text-right text-muted-foreground tabular-nums text-xs md:text-sm">
                          {formatNumber(category.totalDemandVelocity, 2)}/d
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          </ScrollArea>
        </div>
      </CardContent>
    </Card>
  );
}
