"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import type { LocationWithCounts } from "@/hooks/queries/use-locations";
import type { LocationType, StorageLocation } from "@/types/api";

function getLocationCode(locationType: LocationType, loc: StorageLocation): string {
  switch (locationType) {
    case "BOX_BIN":
      return (loc as any).boxBinCode;
    case "RACK":
      return (loc as any).rackCode;
    case "CABINET":
      return (loc as any).cabinetCode;
    case "SINGLE_CLAW_MACHINE":
      return (loc as any).singleClawMachineCode;
    case "DOUBLE_CLAW_MACHINE":
      return (loc as any).doubleClawMachineCode;
    case "KEYCHAIN_MACHINE":
      return (loc as any).keychainMachineCode;
    default:
      return loc.id;
  }
}

interface LocationCardProps {
  data: LocationWithCounts;
  onClick: () => void;
}

export function LocationCard({ data, onClick }: LocationCardProps) {
  const code = getLocationCode(data.locationType, data.location);

  return (
    <Card className="cursor-pointer hover:bg-muted/30" onClick={onClick}>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">{code}</CardTitle>
      </CardHeader>
      <CardContent className="flex items-center justify-between">
        <div className="text-sm text-muted-foreground">
          {data.totalQuantity} units • {data.inventoryRecords} items
        </div>
        <Badge variant="secondary">View</Badge>
      </CardContent>
    </Card>
  );
}

