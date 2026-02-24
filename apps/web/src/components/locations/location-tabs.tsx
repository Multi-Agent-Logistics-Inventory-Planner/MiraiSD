"use client";

import { LocationType } from "@/types/api";
import { Button } from "@/components/ui/button";

const LOCATION_TABS: Array<{ type: LocationType; label: string }> = [
  { type: LocationType.BOX_BIN, label: "Box Bins" },
  { type: LocationType.RACK, label: "Racks" },
  { type: LocationType.CABINET, label: "Cabinets" },
  { type: LocationType.SINGLE_CLAW_MACHINE, label: "Single Claw" },
  { type: LocationType.DOUBLE_CLAW_MACHINE, label: "Double Claw" },
  { type: LocationType.KEYCHAIN_MACHINE, label: "Keychain" },
  { type: LocationType.FOUR_CORNER_MACHINE, label: "Four Corner" },
  { type: LocationType.PUSHER_MACHINE, label: "Pusher" },
  { type: LocationType.NOT_ASSIGNED, label: "Not Assigned" },
];

interface LocationTabsProps {
  value: LocationType;
  onValueChange: (value: LocationType) => void;
}

export function LocationTabs({ value, onValueChange }: LocationTabsProps) {
  return (
    <div className="flex flex-wrap gap-2">
      {LOCATION_TABS.map((t) => (
        <Button
          key={t.type}
          variant={value === t.type ? "default" : "outline"}
          size="sm"
          onClick={() => onValueChange(t.type)}
        >
          {t.label}
        </Button>
      ))}
    </div>
  );
}
