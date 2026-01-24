"use client";

import Image from "next/image";
import { ImageOff } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
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
          <TableCell>
            <Skeleton className="h-10 w-10 rounded-md" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-40" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-16" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-20" />
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
    <Table>
      <TableHeader className="bg-muted">
        <TableRow>
          <TableHead className="w-14 rounded-tl-xl"></TableHead>
          <TableHead className="text-left">Name</TableHead>
          <TableHead>SKU</TableHead>
          <TableHead>Category</TableHead>
          <TableHead>Subcategory</TableHead>
          <TableHead className="text-center">Quantity</TableHead>
          <TableHead className="text-right rounded-tr-xl">Unit Price</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {isLoading ? (
          <TableSkeleton />
        ) : items.length === 0 ? (
          <TableRow>
            <TableCell
              colSpan={7}
              className="h-24 text-center text-muted-foreground"
            >
              No products found.
            </TableCell>
          </TableRow>
        ) : (
          items.map((row) => (
            <TableRow
              key={row.product.id}
              className="cursor-pointer hover:bg-muted/50"
              onClick={() => onSelect(row)}
            >
              <TableCell className="py-2">
                {row.product.imageUrl ? (
                  <div className="relative h-10 w-10 overflow-hidden rounded-md bg-muted">
                    <Image
                      src={row.product.imageUrl}
                      alt={row.product.name}
                      fill
                      sizes="40px"
                      className="object-cover"
                    />
                  </div>
                ) : (
                  <div className="flex h-10 w-10 items-center justify-center rounded-md bg-muted">
                    <ImageOff className="h-4 w-4 text-muted-foreground" />
                  </div>
                )}
              </TableCell>
              <TableCell className="font-medium">{row.product.name}</TableCell>
              <TableCell className="font-mono text-xs text-muted-foreground">
                {row.product.sku}
              </TableCell>
              <TableCell>{row.product.category}</TableCell>
              <TableCell className="text-muted-foreground">
                {row.product.subcategory ?? "-"}
              </TableCell>
              <TableCell className="text-center">{row.totalQuantity}</TableCell>
              <TableCell className="text-right">
                {row.product.unitCost != null
                  ? `$${row.product.unitCost.toFixed(2)}`
                  : "-"}
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}
