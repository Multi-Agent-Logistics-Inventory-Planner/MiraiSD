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
import { cn } from "@/lib/utils";

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
      {/* Scroll fade edges */}
      <div className="pointer-events-none absolute left-0 top-0 bottom-0 w-6 bg-gradient-to-r from-background to-transparent z-10" />
      <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-6 bg-gradient-to-l from-background to-transparent z-10" />

      <div className="flex gap-1.5 overflow-x-auto scrollbar-none px-1 pb-1">
        {LOCATION_TABS.map(({ type, label, icon: Icon }) => {
          const active = value === type;
          return (
            <button
              key={type}
              type="button"
              onClick={() => onValueChange(type)}
              className={cn(
                "flex items-center gap-1.5 whitespace-nowrap rounded-full px-3.5 py-1.5 text-sm font-medium transition-all",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring shrink-0",
                active
                  ? "bg-primary text-primary-foreground shadow-sm"
                  : "bg-muted text-muted-foreground hover:bg-muted/80 hover:text-foreground"
              )}
            >
              <Icon className="h-3.5 w-3.5 shrink-0" />
              {label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
