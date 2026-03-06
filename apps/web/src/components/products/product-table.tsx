"use client";

import Image from "next/image";
import { ImageOff, MoreVertical } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  DataTableHeader,
  TableRow,
} from "@/components/ui/table";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";

interface ProductTableProps {
  items: ProductWithInventory[];
  isLoading: boolean;
  onSelect: (item: ProductWithInventory) => void;
}

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 10 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell className="py-2 rounded-l-lg">
            <div className="flex items-center gap-3">
              <Skeleton className="h-10 w-10 rounded-md shrink-0" />
              <Skeleton className="h-4 w-40" />
            </div>
          </TableCell>
          <TableCell className="hidden sm:table-cell">
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-16" />
          </TableCell>
          <TableCell className="hidden sm:table-cell rounded-r-lg">
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
  return (
    <Table className="border-none">
      <DataTableHeader>
        <TableHead className="text-left rounded-l-lg">Product</TableHead>
        <TableHead className="hidden sm:table-cell">Category</TableHead>
        <TableHead>Stock</TableHead>
        <TableHead className="hidden sm:table-cell rounded-r-lg w-12"></TableHead>
      </DataTableHeader>
      <TableBody>
        {isLoading ? (
          <TableSkeleton />
        ) : items.length === 0 ? (
          <TableRow>
            <TableCell
              colSpan={3}
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
              <TableCell className="py-2 rounded-l-lg">
                <div className="flex items-center gap-3">
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
                  <span className="font-medium">{row.product.name}</span>
                </div>
              </TableCell>
              <TableCell className="hidden sm:table-cell">
                {row.product.category.name}
              </TableCell>
              <TableCell>{row.totalQuantity}</TableCell>
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
  );
}
