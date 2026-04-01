"use client";

import { useMemo } from "react";
import {
  Archive,
  BookOpen,
  Box,
  ChevronsRight,
  CircleHelp,
  Disc3,
  Gamepad,
  Gamepad2,
  Key,
  Layers,
  LayoutGrid,
  PanelsTopLeft,
  Loader2,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { LocationType } from "@/types/api";
import { Button } from "@/components/ui/button";
import { useStorageLocations } from "@/hooks/queries/use-storage-locations";

// Configuration for each location type (source of truth for UI)
export interface LocationTabConfig {
  type: LocationType;
  code: string;
  label: string;
  icon: LucideIcon;
  codePrefix: string;
  hasDisplay: boolean;
  isDisplayOnly: boolean;
  displayOrder: number;
}

export const LOCATION_TAB_CONFIG: LocationTabConfig[] = [
  { type: LocationType.BOX_BIN,             code: "BOX_BINS",     label: "Box Bins",     icon: Box,            codePrefix: "B",  hasDisplay: false, isDisplayOnly: false, displayOrder: 0 },
  { type: LocationType.CABINET,             code: "CABINETS",     label: "Cabinets",     icon: Archive,        codePrefix: "C",  hasDisplay: false, isDisplayOnly: false, displayOrder: 1 },
  { type: LocationType.RACK,                code: "RACKS",        label: "Racks",        icon: Layers,         codePrefix: "R",  hasDisplay: false, isDisplayOnly: false, displayOrder: 2 },
  { type: LocationType.SHELF,               code: "SHELVES",      label: "Shelves",      icon: BookOpen,       codePrefix: "SH", hasDisplay: false, isDisplayOnly: false, displayOrder: 3 },
  { type: LocationType.WINDOW,              code: "WINDOWS",      label: "Windows",      icon: PanelsTopLeft,  codePrefix: "W",  hasDisplay: false, isDisplayOnly: false, displayOrder: 4 },
  { type: LocationType.SINGLE_CLAW_MACHINE, code: "SINGLE_CLAW",  label: "Single Claw",  icon: Gamepad2,       codePrefix: "SC", hasDisplay: true,  isDisplayOnly: false, displayOrder: 5 },
  { type: LocationType.DOUBLE_CLAW_MACHINE, code: "DOUBLE_CLAW",  label: "Double Claw",  icon: Gamepad,        codePrefix: "DC", hasDisplay: true,  isDisplayOnly: false, displayOrder: 6 },
  { type: LocationType.FOUR_CORNER_MACHINE, code: "FOUR_CORNER",  label: "Four Corner",  icon: LayoutGrid,     codePrefix: "FC", hasDisplay: true,  isDisplayOnly: false, displayOrder: 7 },
  { type: LocationType.PUSHER_MACHINE,      code: "PUSHER",       label: "Pusher",       icon: ChevronsRight,  codePrefix: "P",  hasDisplay: true,  isDisplayOnly: false, displayOrder: 8 },
  { type: LocationType.GACHAPON,            code: "GACHAPON",     label: "Gachapon",     icon: Disc3,          codePrefix: "G",  hasDisplay: true,  isDisplayOnly: true,  displayOrder: 9 },
  { type: LocationType.KEYCHAIN_MACHINE,    code: "KEYCHAIN",     label: "Keychain",     icon: Key,            codePrefix: "K",  hasDisplay: true,  isDisplayOnly: true,  displayOrder: 10 },
  { type: LocationType.NOT_ASSIGNED,        code: "NOT_ASSIGNED", label: "Not Assigned", icon: CircleHelp,     codePrefix: "NA", hasDisplay: false, isDisplayOnly: false, displayOrder: 99 },
];

interface LocationTabsProps {
  value: LocationType | null;
  onValueChange: (value: LocationType) => void;
}

export function LocationTabs({ value, onValueChange }: LocationTabsProps) {
  const { data: storageLocations, isLoading } = useStorageLocations();

  // Create a set of existing storage location codes from the API
  const existingCodes = useMemo(() => {
    return new Set(storageLocations?.map((sl) => sl.code) ?? []);
  }, [storageLocations]);

  // Filter tabs to only show those with existing storage locations
  const availableTabs = useMemo(() => {
    return LOCATION_TAB_CONFIG.filter((config) => existingCodes.has(config.code));
  }, [existingCodes]);

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 py-2">
        <Loader2 className="h-4 w-4 animate-spin" />
        <span className="text-sm text-muted-foreground">Loading storage locations...</span>
      </div>
    );
  }

  // No storage locations exist - show message
  if (availableTabs.length === 0) {
    return (
      <div className="rounded-lg border border-dashed p-4">
        <div className="text-sm text-muted-foreground">
          No storage locations found. Please run the database seeder to create standard storage locations.
        </div>
      </div>
    );
  }

  return (
    <div className="relative">
      {/* Scroll fade edge (right only) */}
      <div className="pointer-events-none absolute right-0 top-0 bottom-0 w-6 bg-gradient-to-l from-background to-transparent z-10" />

      <div className="flex gap-1.5 overflow-x-auto scrollbar-none pb-1 pr-6">
        {availableTabs.map(({ type, label, icon: Icon }) => {
          const active = value === type;
          return (
            <Button
              key={type}
              variant={active ? "default" : "outline"}
              size="sm"
              onClick={() => onValueChange(type)}
              className={active ? "bg-brand-primary text-white hover:bg-brand-primary-hover" : "bg-[#f2f2f2] dark:bg-[#30302e] border-none dark:text-[#9b9b9a]"}
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
