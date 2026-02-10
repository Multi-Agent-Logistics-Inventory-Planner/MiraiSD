"use client";

import { Package } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import type { NotAssignedInventory } from "@/types/api";

interface NotAssignedTableProps {
  items: NotAssignedInventory[];
  isLoading: boolean;
  onRowClick: (item: NotAssignedInventory) => void;
}

export function NotAssignedTable({ items, isLoading, onRowClick }: NotAssignedTableProps) {
  if (isLoading) {
    return (
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>SKU</TableHead>
            <TableHead>Product</TableHead>
            <TableHead>Category</TableHead>
            <TableHead>Quantity</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {Array.from({ length: 5 }).map((_, i) => (
            <TableRow key={i}>
              <TableCell><Skeleton className="h-4 w-20" /></TableCell>
              <TableCell><Skeleton className="h-4 w-48" /></TableCell>
              <TableCell><Skeleton className="h-5 w-20" /></TableCell>
              <TableCell><Skeleton className="h-4 w-10" /></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <Package className="h-12 w-12 text-muted-foreground/50 mb-4" />
        <p className="text-muted-foreground">No unassigned inventory items</p>
      </div>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>SKU</TableHead>
          <TableHead>Product</TableHead>
          <TableHead>Category</TableHead>
          <TableHead>Quantity</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => (
          <TableRow
            key={item.id}
            className="cursor-pointer hover:bg-muted/50"
            onClick={() => onRowClick(item)}
          >
            <TableCell className="font-mono text-sm">
              {item.item.sku}
            </TableCell>
            <TableCell>{item.item.name}</TableCell>
            <TableCell>
              <Badge variant="outline" className="text-xs">
                {item.item.category}
              </Badge>
            </TableCell>
            <TableCell>{item.quantity}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
