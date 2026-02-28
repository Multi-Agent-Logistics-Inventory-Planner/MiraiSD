"use client";

import { MapPin } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import type { LocationWithCounts } from "@/types/api";

interface LocationTableProps {
  items: LocationWithCounts[];
  isLoading: boolean;
  onRowClick: (item: LocationWithCounts) => void;
  pageSize?: number;
}

export function LocationTable({
  items,
  isLoading,
  onRowClick,
  pageSize = 10,
}: LocationTableProps) {
  if (isLoading) {
    return (
      <Table>
        <TableHeader className="bg-muted">
          <TableRow>
            <TableHead className="rounded-tl-lg">Location Code</TableHead>
            <TableHead>Items</TableHead>
            <TableHead className="rounded-tr-lg">Total Units</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {Array.from({ length: pageSize }).map((_, i) => (
            <TableRow key={i}>
              <TableCell><Skeleton className="h-4 w-16" /></TableCell>
              <TableCell><Skeleton className="h-4 w-8" /></TableCell>
              <TableCell><Skeleton className="h-4 w-12" /></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <MapPin className="h-12 w-12 text-muted-foreground/50 mb-4" />
        <p className="text-muted-foreground">No locations found</p>
      </div>
    );
  }

  return (
    <Table>
      <TableHeader className="bg-muted">
        <TableRow>
          <TableHead className="rounded-tl-lg">Location Code</TableHead>
          <TableHead>Items</TableHead>
          <TableHead className="rounded-tr-lg">Total Units</TableHead>
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
              {item.locationCode}
            </TableCell>
            <TableCell>{item.inventoryRecords}</TableCell>
            <TableCell>{item.totalQuantity}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
