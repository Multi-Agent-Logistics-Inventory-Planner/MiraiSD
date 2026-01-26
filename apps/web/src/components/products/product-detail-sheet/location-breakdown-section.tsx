"use client";

import { MapPin } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useInventoryByItemId } from "@/hooks/queries/use-inventory-by-item";
import type { LocationType } from "@/types/api";

interface LocationBreakdownSectionProps {
  productId: string;
}

const LOCATION_TYPE_LABELS: Record<LocationType, string> = {
  BOX_BIN: "Box Bin",
  SINGLE_CLAW_MACHINE: "Single Claw",
  DOUBLE_CLAW_MACHINE: "Double Claw",
  KEYCHAIN_MACHINE: "Keychain Machine",
  CABINET: "Cabinet",
  RACK: "Rack",
};

function TableSkeleton() {
  return (
    <>
      {Array.from({ length: 3 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell>
            <Skeleton className="h-4 w-16" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-24" />
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-12" />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}

export function LocationBreakdownSection({
  productId,
}: LocationBreakdownSectionProps) {
  const { data: entries, isLoading, error } = useInventoryByItemId(productId);

  if (error) {
    return (
      <div className="rounded-lg border bg-card p-4 text-center text-sm text-muted-foreground">
        Failed to load location data
      </div>
    );
  }

  return (
    <div className="rounded-lg border bg-card overflow-hidden">
      <Table>
        <TableHeader className="bg-muted">
          <TableRow>
            <TableHead>Code</TableHead>
            <TableHead>Type</TableHead>
            <TableHead className="text-right">Quantity</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading ? (
            <TableSkeleton />
          ) : !entries || entries.length === 0 ? (
            <TableRow>
              <TableCell
                colSpan={3}
                className="h-24 text-center text-muted-foreground"
              >
                <div className="flex flex-col items-center gap-2">
                  <MapPin className="h-8 w-8 text-muted-foreground/50" />
                  <span>No inventory at any location</span>
                </div>
              </TableCell>
            </TableRow>
          ) : (
            entries.map((entry) => (
              <TableRow key={entry.inventoryId}>
                <TableCell className="font-mono text-sm">
                  {entry.locationCode}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {LOCATION_TYPE_LABELS[entry.locationType]}
                </TableCell>
                <TableCell className="text-right font-medium">
                  {entry.quantity}
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  );
}
