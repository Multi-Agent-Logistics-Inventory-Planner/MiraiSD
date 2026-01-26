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
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";
import {
  PRODUCT_CATEGORY_LABELS,
  PRODUCT_SUBCATEGORY_LABELS,
} from "@/types/api";

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
            <div className="flex items-center gap-3">
              <Skeleton className="h-10 w-10 rounded-md shrink-0" />
              <Skeleton className="h-4 w-40" />
            </div>
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
          <TableCell>
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
    <Table>
      <TableHeader className="bg-muted">
        <TableRow>
          <TableHead className="text-left rounded-tl-xl">Product</TableHead>
          <TableHead>SKU</TableHead>
          <TableHead>Category</TableHead>
          <TableHead>Subcategory</TableHead>
          <TableHead className="">Stock</TableHead>
          <TableHead>Unit Price</TableHead>
          <TableHead className="rounded-tr-xl w-12"></TableHead>
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
              className="cursor-pointer"
              onClick={() => onSelect(row)}
            >
              <TableCell className="py-2">
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
              <TableCell className="font-mono text-xs text-muted-foreground">
                {row.product.sku}
              </TableCell>
              <TableCell>
                {PRODUCT_CATEGORY_LABELS[row.product.category]}
              </TableCell>
              <TableCell className="text-muted-foreground">
                {row.product.subcategory
                  ? PRODUCT_SUBCATEGORY_LABELS[row.product.subcategory]
                  : ""}
              </TableCell>
              <TableCell className="">{row.totalQuantity}</TableCell>
              <TableCell className="">
                {row.product.unitCost != null
                  ? `$${row.product.unitCost.toFixed(2)}`
                  : "-"}
              </TableCell>
              <TableCell>
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
