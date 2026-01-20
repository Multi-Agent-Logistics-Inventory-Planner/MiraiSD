"use client";

import { Pencil, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";
import type { StockStatus } from "@/types/dashboard";

interface ProductTableProps {
  items: ProductWithInventory[];
  isLoading?: boolean;
  onSelect: (item: ProductWithInventory) => void;
  onEdit: (item: ProductWithInventory) => void;
  onDelete: (item: ProductWithInventory) => void;
}

function getStatusColor(status: StockStatus) {
  switch (status) {
    case "good":
      return "bg-green-100 text-green-700";
    case "low":
      return "bg-amber-100 text-amber-700";
    case "critical":
      return "bg-red-100 text-red-700";
    case "out-of-stock":
      return "bg-gray-100 text-gray-700";
    default:
      return "bg-gray-100 text-gray-700";
  }
}

function formatStatus(status: StockStatus) {
  return status
    .split("-")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

export function ProductTable({
  items,
  isLoading,
  onSelect,
  onEdit,
  onDelete,
}: ProductTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>SKU</TableHead>
          <TableHead>Item Name</TableHead>
          <TableHead>Category</TableHead>
          <TableHead>Stock</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Unit Cost</TableHead>
          <TableHead className="w-[110px]">Actions</TableHead>
        </TableRow>
      </TableHeader>

      <TableBody>
        {isLoading ? (
          Array.from({ length: 8 }).map((_, i) => (
            <TableRow key={i}>
              <TableCell>
                <Skeleton className="h-4 w-20" />
              </TableCell>
              <TableCell>
                <Skeleton className="h-4 w-48" />
              </TableCell>
              <TableCell>
                <Skeleton className="h-4 w-28" />
              </TableCell>
              <TableCell>
                <Skeleton className="h-4 w-20" />
              </TableCell>
              <TableCell>
                <Skeleton className="h-5 w-20" />
              </TableCell>
              <TableCell className="text-right">
                <Skeleton className="ml-auto h-4 w-20" />
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-1">
                  <Skeleton className="h-8 w-8" />
                  <Skeleton className="h-8 w-8" />
                </div>
              </TableCell>
            </TableRow>
          ))
        ) : items.length === 0 ? (
          <TableRow>
            <TableCell colSpan={7} className="py-8 text-center text-sm text-muted-foreground">
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
              <TableCell className="font-mono text-sm">{row.product.sku}</TableCell>
              <TableCell className="font-medium">{row.product.name}</TableCell>
              <TableCell>{row.product.category}</TableCell>
              <TableCell>
                {row.totalQuantity} / {row.product.targetStockLevel ?? "—"}
              </TableCell>
              <TableCell>
                <Badge
                  variant="outline"
                  className={cn("text-xs", getStatusColor(row.status))}
                >
                  {formatStatus(row.status)}
                </Badge>
              </TableCell>
              <TableCell className="text-right">
                {row.product.unitCost != null ? `$${row.product.unitCost.toFixed(2)}` : "—"}
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={(e) => {
                      e.stopPropagation();
                      onEdit(row);
                    }}
                    title="Edit"
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    className="text-destructive"
                    onClick={(e) => {
                      e.stopPropagation();
                      onDelete(row);
                    }}
                    title="Delete"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}

