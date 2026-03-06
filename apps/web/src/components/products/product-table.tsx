"use client";

import { useMemo } from "react";
import Image from "next/image";
import { ImageOff, MoreVertical } from "lucide-react";
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
import { useCategories } from "@/hooks/queries/use-categories";

interface ProductTableProps {
  items: ProductWithInventory[];
  isLoading: boolean;
  onSelect: (item: ProductWithInventory) => void;
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

export function ProductTable({
  items,
  isLoading,
  onSelect,
}: ProductTableProps) {
  const { data: categories } = useCategories();

  // Build a map from category ID to parent category name for quick lookups
  const parentNameMap = useMemo(() => {
    const map = new Map<string, string>();
    if (!categories) return map;

    for (const parent of categories) {
      // Root categories map to themselves
      map.set(parent.id, parent.name);
      // Children map to their parent's name
      for (const child of parent.children) {
        map.set(child.id, parent.name);
      }
    }
    return map;
  }, [categories]);

  return (
    <div className="overflow-hidden sm:overflow-visible w-full">
      <Table className="border-none table-fixed w-full">
        <DataTableHeader>
        <TableHead className="text-left rounded-l-lg">Product</TableHead>
        <TableHead className="hidden sm:table-cell w-24 text-center">Status</TableHead>
        <TableHead className="hidden sm:table-cell w-36 pl-4">Category</TableHead>
        <TableHead className="hidden sm:table-cell w-36 pl-4">Subcategory</TableHead>
        <TableHead className="w-20 text-right pr-4 rounded-r-lg sm:rounded-none">Stock</TableHead>
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
          items.map((row) => (
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
                  <span className="font-medium truncate min-w-0">
                    {row.product.name}
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
          ))
        )}
        </TableBody>
      </Table>
    </div>
  );
}
