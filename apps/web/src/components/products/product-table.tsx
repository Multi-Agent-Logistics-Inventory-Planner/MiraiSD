"use client";

import Image from "next/image";
import { ImageOff, MoreVertical, FolderOpen, ArrowUp, ArrowDown } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  DataTableHeader,
  TableRow,
} from "@/components/ui/table";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";
import type { SortColumn, ProductSort } from "./product-sort-utils";

interface ProductTableProps {
  readonly items: ProductWithInventory[];
  readonly isLoading: boolean;
  readonly onSelect: (item: ProductWithInventory) => void;
  readonly parentNameMap: Map<string, string>;
  readonly kujiCategoryIds: ReadonlySet<string>;
  readonly sort: ProductSort;
  readonly onSortChange: (sort: ProductSort) => void;
}

function getProductStatusColor(isActive: boolean) {
  return isActive
    ? "bg-[#20d760] text-black"
    : "bg-[#e50815] text-white";
}

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 10 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell className="py-2 rounded-l-lg max-w-0 overflow-hidden">
            <div className="flex items-center gap-3 min-w-0 w-full">
              <Skeleton className="h-10 w-10 rounded-md shrink-0" />
              <Skeleton className="h-4 w-32 sm:w-40" />
            </div>
          </TableCell>
          <TableCell className="hidden sm:table-cell text-center">
            <Skeleton className="h-6 w-16 mx-auto" />
          </TableCell>
          <TableCell className="hidden sm:table-cell pl-4 max-w-0 overflow-hidden">
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell className="hidden sm:table-cell pl-4 max-w-0 overflow-hidden">
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell className="text-right pr-4 rounded-r-lg sm:rounded-none tabular-nums">
            <Skeleton className="h-4 w-8 ml-auto" />
          </TableCell>
          <TableCell className="hidden sm:table-cell sm:rounded-r-lg">
            <Skeleton className="h-8 w-8" />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}

function SortableHeader({
  column,
  label,
  sort,
  onSortChange,
  className,
}: {
  readonly column: SortColumn;
  readonly label: string;
  readonly sort: ProductSort;
  readonly onSortChange: (sort: ProductSort) => void;
  readonly className?: string;
}) {
  const isActive = sort.column === column;
  const ariaSortValue = isActive
    ? (sort.direction === "asc" ? "ascending" : "descending")
    : "none";

  const handleClick = () => {
    if (isActive) {
      onSortChange({ column, direction: sort.direction === "asc" ? "desc" : "asc" });
    } else {
      onSortChange({ column, direction: "asc" });
    }
  };

  return (
    <TableHead className={className} aria-sort={ariaSortValue}>
      <button
        type="button"
        className="inline-flex items-center gap-1 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-sm transition-colors cursor-pointer"
        onClick={handleClick}
      >
        {label}
        {isActive && (
          sort.direction === "asc"
            ? <ArrowUp className="h-3.5 w-3.5" />
            : <ArrowDown className="h-3.5 w-3.5" />
        )}
      </button>
    </TableHead>
  );
}

export function ProductTable({
  items,
  isLoading,
  onSelect,
  parentNameMap,
  kujiCategoryIds,
  sort,
  onSortChange,
}: ProductTableProps) {
  return (
    <div className="overflow-hidden sm:overflow-visible w-full">
      <Table className="border-none table-fixed w-full">
        <DataTableHeader>
          <SortableHeader column="product" label="Product" sort={sort} onSortChange={onSortChange} className="text-left rounded-l-lg" />
          <SortableHeader column="status" label="Status" sort={sort} onSortChange={onSortChange} className="hidden sm:table-cell w-24 text-center" />
          <SortableHeader column="category" label="Category" sort={sort} onSortChange={onSortChange} className="hidden sm:table-cell w-36 pl-4" />
          <SortableHeader column="subcategory" label="Subcategory" sort={sort} onSortChange={onSortChange} className="hidden sm:table-cell w-36 pl-4" />
          <SortableHeader column="stock" label="Stock" sort={sort} onSortChange={onSortChange} className="w-20 text-right pr-4 rounded-r-lg sm:rounded-none" />
          <TableHead className="hidden sm:table-cell rounded-r-lg w-14"></TableHead>
        </DataTableHeader>
        <TableBody>
          {isLoading ? (
            <TableSkeleton />
          ) : items.length === 0 ? (
            <TableRow>
              <TableCell
                colSpan={2}
                className="h-24 text-center text-muted-foreground sm:hidden"
              >
                No products found
              </TableCell>
              <TableCell
                colSpan={6}
                className="h-24 text-center text-muted-foreground hidden sm:table-cell"
              >
                No products found
              </TableCell>
            </TableRow>
          ) : (
            items.map((row) => {
              const showKujiIcon = row.product.hasChildren || kujiCategoryIds.has(row.product.category.id);
              return (
                <TableRow
                  key={row.product.id}
                  className="cursor-pointer"
                  onClick={() => onSelect(row)}
                >
                  <TableCell className="py-2 rounded-l-lg max-w-0 overflow-hidden">
                    <div className="flex items-center gap-3 min-w-0 w-full">
                      {row.product.imageUrl ? (
                        <div className="relative h-10 w-10 shrink-0 overflow-hidden rounded-md bg-muted">
                          <Image
                            src={row.product.imageUrl}
                            alt={row.product.name}
                            fill
                            sizes="40px"
                            className="object-cover"
                          />
                        </div>
                      ) : (
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-muted">
                          <ImageOff className="h-4 w-4 text-muted-foreground" />
                        </div>
                      )}
                      <span className="font-medium truncate min-w-0 flex items-center gap-1.5">
                        {row.product.name}
                        {showKujiIcon && (
                          <span title="Click to manage prizes">
                            <FolderOpen className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                          </span>
                        )}
                      </span>
                    </div>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell text-center">
                    <Badge
                      className={cn("text-xs", getProductStatusColor(row.product.isActive))}
                    >
                      {row.product.isActive ? "Active" : "Inactive"}
                    </Badge>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell pl-4 max-w-0 overflow-hidden">
                    <span className="truncate block">
                      {parentNameMap.get(row.product.category.id) ?? row.product.category.name}
                    </span>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell pl-4 max-w-0 overflow-hidden">
                    <span className="truncate block">
                      {parentNameMap.get(row.product.category.id) !== row.product.category.name
                        ? row.product.category.name
                        : "-"}
                    </span>
                  </TableCell>
                  <TableCell className="text-right pr-4 rounded-r-lg sm:rounded-none tabular-nums">{row.totalQuantity}</TableCell>
                  <TableCell className="hidden sm:table-cell rounded-r-lg">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8"
                      aria-label="View product details"
                      onClick={(e) => {
                        e.stopPropagation();
                        onSelect(row);
                      }}
                    >
                      <MoreVertical className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })
          )}
        </TableBody>
      </Table>
    </div>
  );
}
