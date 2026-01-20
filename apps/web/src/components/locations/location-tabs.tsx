"use client";

import type { LocationType } from "@/types/api";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";

const LOCATION_TABS: Array<{ type: LocationType; label: string }> = [
  { type: "BOX_BIN", label: "Box Bins" },
  { type: "RACK", label: "Racks" },
  { type: "CABINET", label: "Cabinets" },
  { type: "SINGLE_CLAW_MACHINE", label: "Single Claw" },
  { type: "DOUBLE_CLAW_MACHINE", label: "Double Claw" },
  { type: "KEYCHAIN_MACHINE", label: "Keychain" },
];

interface LocationTabsProps {
  value: LocationType;
  onValueChange: (value: LocationType) => void;
}

export function LocationTabs({ value, onValueChange }: LocationTabsProps) {
  return (
    <Tabs value={value} onValueChange={(v) => onValueChange(v as LocationType)}>
      <TabsList className="flex flex-wrap">
        {LOCATION_TABS.map((t) => (
          <TabsTrigger key={t.type} value={t.type}>
            {t.label}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );
}

