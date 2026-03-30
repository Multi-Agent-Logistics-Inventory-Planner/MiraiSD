"use client";

import { useMemo, useState } from "react";
import {
  Archive,
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
  Plus,
  Loader2,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { LocationType, STORAGE_LOCATION_CODES } from "@/types/api";
import { Button } from "@/components/ui/button";
import {
  useStorageLocations,
  useCreateStorageLocationMutation,
  type StorageLocationCategory,
} from "@/hooks/queries/use-storage-locations";
import { useToast } from "@/hooks/use-toast";
import { AddStorageLocationDialog } from "./add-storage-location-dialog";

// Configuration for each location type
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
  { type: LocationType.DOUBLE_CLAW_MACHINE, code: "DOUBLE_CLAW",  label: "Double Claw",  icon: Gamepad,        codePrefix: "DC", hasDisplay: true,  isDisplayOnly: false, displayOrder: 2 },
  { type: LocationType.FOUR_CORNER_MACHINE, code: "FOUR_CORNER",  label: "Four Corner",  icon: LayoutGrid,     codePrefix: "FC", hasDisplay: true,  isDisplayOnly: false, displayOrder: 3 },
  { type: LocationType.GACHAPON,            code: "GACHAPON",     label: "Gachapon",     icon: Disc3,          codePrefix: "G",  hasDisplay: true,  isDisplayOnly: true,  displayOrder: 4 },
  { type: LocationType.KEYCHAIN_MACHINE,    code: "KEYCHAIN",     label: "Keychain",     icon: Key,            codePrefix: "K",  hasDisplay: true,  isDisplayOnly: true,  displayOrder: 5 },
  { type: LocationType.PUSHER_MACHINE,      code: "PUSHER",       label: "Pusher",       icon: ChevronsRight,  codePrefix: "P",  hasDisplay: true,  isDisplayOnly: false, displayOrder: 6 },
  { type: LocationType.RACK,                code: "RACKS",        label: "Racks",        icon: Layers,         codePrefix: "R",  hasDisplay: false, isDisplayOnly: false, displayOrder: 7 },
  { type: LocationType.SINGLE_CLAW_MACHINE, code: "SINGLE_CLAW",  label: "Single Claw",  icon: Gamepad2,       codePrefix: "SC", hasDisplay: true,  isDisplayOnly: false, displayOrder: 8 },
  { type: LocationType.WINDOW,              code: "WINDOWS",      label: "Windows",      icon: PanelsTopLeft,  codePrefix: "W",  hasDisplay: false, isDisplayOnly: false, displayOrder: 9 },
  { type: LocationType.NOT_ASSIGNED,        code: "NOT_ASSIGNED", label: "Not Assigned", icon: CircleHelp,     codePrefix: "NA", hasDisplay: false, isDisplayOnly: false, displayOrder: 10 },
];

interface LocationTabsProps {
  value: LocationType | null;
  onValueChange: (value: LocationType) => void;
}

export function LocationTabs({ value, onValueChange }: LocationTabsProps) {
  const { toast } = useToast();
  const { data: storageLocations, isLoading } = useStorageLocations();
  const createMutation = useCreateStorageLocationMutation();
  const [dialogOpen, setDialogOpen] = useState(false);

  // Create a set of existing storage location codes
  const existingCodes = useMemo(() => {
    return new Set(storageLocations?.map((sl) => sl.code) ?? []);
  }, [storageLocations]);

  // Filter tabs to only show those with existing storage locations
  const availableTabs = useMemo(() => {
    return LOCATION_TAB_CONFIG.filter((config) => existingCodes.has(config.code));
  }, [existingCodes]);

  // Find tabs that are missing (can be created)
  const missingTabs = useMemo(() => {
    return LOCATION_TAB_CONFIG.filter((config) => !existingCodes.has(config.code));
  }, [existingCodes]);

  // Handle creating a missing storage location
  const handleCreateStorageLocation = async (config: LocationTabConfig) => {
    try {
      await createMutation.mutateAsync({
        code: config.code,
        name: config.label,
        codePrefix: config.codePrefix,
        hasDisplay: config.hasDisplay,
        isDisplayOnly: config.isDisplayOnly,
        displayOrder: config.displayOrder,
      });
      toast({ title: `Created ${config.label} storage location` });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to create storage location";
      toast({ title: "Error", description: message, variant: "destructive" });
    }
  };

  // If current value is not available, switch to first available
  const currentValueExists = availableTabs.some((tab) => tab.type === value);

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 py-2">
        <Loader2 className="h-4 w-4 animate-spin" />
        <span className="text-sm text-muted-foreground">Loading storage locations...</span>
      </div>
    );
  }

  // No storage locations exist - show setup UI
  if (availableTabs.length === 0) {
    return (
      <div className="rounded-lg border border-dashed p-4">
        <div className="text-sm text-muted-foreground mb-3">
          No storage locations configured. Create your first storage location to get started.
        </div>
        <div className="flex flex-wrap gap-2">
          {LOCATION_TAB_CONFIG.slice(0, 3).map((config) => (
            <Button
              key={config.code}
              variant="outline"
              size="sm"
              onClick={() => handleCreateStorageLocation(config)}
              disabled={createMutation.isPending}
            >
              <Plus className="h-3.5 w-3.5 mr-1" />
              {config.label}
            </Button>
          ))}
          <Button
            variant="ghost"
            size="sm"
            className="text-muted-foreground"
            onClick={() => {
              // Create all storage locations
              LOCATION_TAB_CONFIG.forEach((config) => {
                if (!existingCodes.has(config.code)) {
                  handleCreateStorageLocation(config);
                }
              });
            }}
            disabled={createMutation.isPending}
          >
            Create all
          </Button>
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

        {/* Add button for new storage locations */}
        <Button
          variant="ghost"
          size="sm"
          className="text-muted-foreground hover:text-foreground"
          onClick={() => setDialogOpen(true)}
        >
          <Plus className="h-3.5 w-3.5" />
        </Button>
      </div>

      <AddStorageLocationDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        missingPresets={missingTabs}
      />
    </div>
  );
}
