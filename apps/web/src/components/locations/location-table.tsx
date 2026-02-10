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
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import type { LocationWithCounts } from "@/hooks/queries/use-locations";
import type {
  LocationType,
  StorageLocation,
  BoxBin,
  Rack,
  Cabinet,
  SingleClawMachine,
  DoubleClawMachine,
  KeychainMachine,
  FourCornerMachine,
  PusherMachine,
} from "@/types/api";

function getLocationCode(locationType: LocationType, loc: StorageLocation): string {
  switch (locationType) {
    case "BOX_BIN":
      return (loc as BoxBin).boxBinCode;
    case "RACK":
      return (loc as Rack).rackCode;
    case "CABINET":
      return (loc as Cabinet).cabinetCode;
    case "SINGLE_CLAW_MACHINE":
      return (loc as SingleClawMachine).singleClawMachineCode;
    case "DOUBLE_CLAW_MACHINE":
      return (loc as DoubleClawMachine).doubleClawMachineCode;
    case "KEYCHAIN_MACHINE":
      return (loc as KeychainMachine).keychainMachineCode;
    case "FOUR_CORNER_MACHINE":
      return (loc as FourCornerMachine).fourCornerMachineCode;
    case "PUSHER_MACHINE":
      return (loc as PusherMachine).pusherMachineCode;
    default:
      return loc.id;
  }
}

const LOCATION_TYPE_SHORT_LABELS: Record<LocationType, string> = {
  BOX_BIN: "Box Bin",
  RACK: "Rack",
  CABINET: "Cabinet",
  SINGLE_CLAW_MACHINE: "Single Claw",
  DOUBLE_CLAW_MACHINE: "Double Claw",
  KEYCHAIN_MACHINE: "Keychain",
  FOUR_CORNER_MACHINE: "Four Corner",
  PUSHER_MACHINE: "Pusher",
  NOT_ASSIGNED: "Not Assigned",
};

interface LocationTableProps {
  items: LocationWithCounts[];
  isLoading: boolean;
  onRowClick: (item: LocationWithCounts) => void;
}

export function LocationTable({ items, isLoading, onRowClick }: LocationTableProps) {
  if (isLoading) {
    return (
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Location Code</TableHead>
            <TableHead>Type</TableHead>
            <TableHead>Items</TableHead>
            <TableHead>Total Units</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {Array.from({ length: 5 }).map((_, i) => (
            <TableRow key={i}>
              <TableCell><Skeleton className="h-4 w-16" /></TableCell>
              <TableCell><Skeleton className="h-5 w-20" /></TableCell>
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
      <TableHeader>
        <TableRow>
          <TableHead>Location Code</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Items</TableHead>
          <TableHead>Total Units</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => {
          const code = getLocationCode(item.locationType, item.location);

          return (
            <TableRow
              key={item.location.id}
              className="cursor-pointer hover:bg-muted/50"
              onClick={() => onRowClick(item)}
            >
              <TableCell className="font-mono text-sm">
                {code}
              </TableCell>
              <TableCell>
                <Badge variant="outline" className="text-xs">
                  {LOCATION_TYPE_SHORT_LABELS[item.locationType]}
                </Badge>
              </TableCell>
              <TableCell>{item.inventoryRecords}</TableCell>
              <TableCell>{item.totalQuantity}</TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
