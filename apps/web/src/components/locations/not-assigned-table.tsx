"use client";

import Image from "next/image";
import { ImageOff, Package } from "lucide-react";
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
  pageSize?: number;
}

export function NotAssignedTable({
  items,
  isLoading,
  pageSize = 10,
}: NotAssignedTableProps) {
  if (isLoading) {
    return (
      <Table>
        <TableHeader className="bg-muted">
          <TableRow>
            <TableHead className="rounded-tl-lg">SKU</TableHead>
            <TableHead>Product</TableHead>
            <TableHead>Category</TableHead>
            <TableHead className="rounded-tr-lg">Quantity</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {Array.from({ length: pageSize }).map((_, i) => (
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
      <TableHeader className="bg-muted">
        <TableRow>
          <TableHead className="rounded-tl-lg">SKU</TableHead>
          <TableHead>Product</TableHead>
          <TableHead>Category</TableHead>
          <TableHead className="rounded-tr-lg">Quantity</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.id}>
            <TableCell className="py-2">
              <div className="flex items-center gap-3">
                {item.item.imageUrl ? (
                  <div className="relative h-8 w-8 shrink-0 overflow-hidden rounded-md bg-muted">
                    <Image
                      src={item.item.imageUrl}
                      alt={item.item.name}
                      fill
                      sizes="32px"
                      className="object-cover"
                    />
                  </div>
                ) : (
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-muted">
                    <ImageOff className="h-3.5 w-3.5 text-muted-foreground" />
                  </div>
                )}
                <span className="font-mono text-sm">{item.item.sku}</span>
              </div>
            </TableCell>
            <TableCell>{item.item.name}</TableCell>
            <TableCell>
              <Badge variant="outline" className="text-xs">
                {item.item.category.name}
              </Badge>
            </TableCell>
            <TableCell>{item.quantity}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
