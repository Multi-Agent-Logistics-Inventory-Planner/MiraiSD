"use client";

import {
  Box,
  Layers,
  Archive,
  Gamepad2,
  Gamepad,
  Key,
  LayoutGrid,
  ChevronsRight,
  CircleHelp,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { LocationType } from "@/types/api";
import { Button } from "@/components/ui/button";

const LOCATION_TABS: Array<{ type: LocationType; label: string; icon: LucideIcon }> = [
  { type: LocationType.BOX_BIN,            label: "Box Bins",     icon: Box },
  { type: LocationType.RACK,               label: "Racks",        icon: Layers },
  { type: LocationType.CABINET,            label: "Cabinets",     icon: Archive },
  { type: LocationType.SINGLE_CLAW_MACHINE,label: "Single Claw",  icon: Gamepad2 },
  { type: LocationType.DOUBLE_CLAW_MACHINE,label: "Double Claw",  icon: Gamepad },
  { type: LocationType.KEYCHAIN_MACHINE,   label: "Keychain",     icon: Key },
  { type: LocationType.FOUR_CORNER_MACHINE,label: "Four Corner",  icon: LayoutGrid },
  { type: LocationType.PUSHER_MACHINE,     label: "Pusher",       icon: ChevronsRight },
  { type: LocationType.NOT_ASSIGNED,       label: "Not Assigned", icon: CircleHelp },
];

interface LocationTabsProps {
  value: LocationType;
  onValueChange: (value: LocationType) => void;
}

export function LocationTabs({ value, onValueChange }: LocationTabsProps) {
  return (
    <div className="relative">
      {/* Scroll fade edge (right only) */}
      <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-6 bg-gradient-to-l from-background to-transparent z-10" />

      <div className="flex gap-1.5 overflow-x-auto scrollbar-none pb-1 pr-6">
        {LOCATION_TABS.map(({ type, label, icon: Icon }) => {
          const active = value === type;
          return (
            <Button
              key={type}
              variant={active ? "default" : "outline"}
              size="sm"
              onClick={() => onValueChange(type)}
              className={active ? "" : "bg-[#f2f2f2] dark:bg-[#30302e] border-none dark:text-[#9b9b9a]"}
            >
              <Icon className="h-3.5 w-3.5" />
              {label}
            </Button>
          );
        })}
      </div>
    </div>
  );
}
