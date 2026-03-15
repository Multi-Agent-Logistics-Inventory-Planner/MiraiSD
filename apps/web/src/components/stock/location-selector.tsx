"use client";

import { useMemo, useState } from "react";
import { Check, ChevronsUpDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Label } from "@/components/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useLocations } from "@/hooks/queries/use-locations";
import { LocationType, type StorageLocation } from "@/types/api";
import { cn, naturalSortCompare } from "@/lib/utils";
import {
  CODE_TO_LOCATION_TYPE,
  LOCATION_TYPE_OPTIONS,
  type LocationSelection,
} from "@/types/transfer";

/** Location type codes that are display-only (no inventory) */
const DISPLAY_ONLY_CODES = ["G", "K"];

const DEFAULT_LOCATION_TYPE = LocationType.BOX_BIN;

/** Virtual ID used for "Not Assigned" selection */
const NOT_ASSIGNED_ID = "__not_assigned__";

/**
 * Custom filter for location code search.
 * Matches full code (e.g., "B14") or just the numeric part (e.g., "14").
 */
function filterLocationCode(value: string, search: string): number {
  const normalizedSearch = search.toLowerCase().trim();
  const normalizedValue = value.toLowerCase();

  if (normalizedValue.includes(normalizedSearch)) return 1;

  const numericPart = value.replace(/^[A-Z]+/i, "");
  if (numericPart.includes(normalizedSearch)) return 1;

  return 0;
}

interface LocationSelectorProps {
  label: string;
  labelSuffix?: React.ReactNode;
  value: LocationSelection;
  onChange: (value: LocationSelection) => void;
  disabled?: boolean;
  excludeLocation?: { locationType: LocationType; locationId: string };
  /** Content to render after the code input*/
  endContent?: React.ReactNode;
  /** Exclude display-only location types (Gachapon, Keychain) that don't support inventory */
  excludeDisplayOnly?: boolean;
}

function getLocationCode(
  locationType: LocationType,
  location: StorageLocation
): string {
  switch (locationType) {
    case "BOX_BIN":
      return "boxBinCode" in location ? location.boxBinCode : "";
    case "CABINET":
      return "cabinetCode" in location ? location.cabinetCode : "";
    case "DOUBLE_CLAW_MACHINE":
      return "doubleClawMachineCode" in location
        ? location.doubleClawMachineCode
        : "";
    case "FOUR_CORNER_MACHINE":
      return "fourCornerMachineCode" in location
        ? location.fourCornerMachineCode
        : "";
    case "GACHAPON":
      return "gachaponCode" in location ? location.gachaponCode : "";
    case "KEYCHAIN_MACHINE":
      return "keychainMachineCode" in location
        ? location.keychainMachineCode
        : "";
    case "PUSHER_MACHINE":
      return "pusherMachineCode" in location
        ? location.pusherMachineCode
        : "";
    case "RACK":
      return "rackCode" in location ? location.rackCode : "";
    case "SINGLE_CLAW_MACHINE":
      return "singleClawMachineCode" in location
        ? location.singleClawMachineCode
        : "";
    case "WINDOW":
      return "windowCode" in location ? location.windowCode : "";
    default:
      return "";
  }
}

export function LocationSelector({
  label,
  labelSuffix,
  value,
  onChange,
  disabled = false,
  excludeLocation,
  endContent,
  excludeDisplayOnly = false,
}: LocationSelectorProps) {
  const [codePopoverOpen, setCodePopoverOpen] = useState(false);
  const isNotAssigned = value.locationType === LocationType.NOT_ASSIGNED;

  // Filter out display-only types if requested
  const filteredLocationTypeOptions = useMemo(() => {
    if (!excludeDisplayOnly) return LOCATION_TYPE_OPTIONS;
    return LOCATION_TYPE_OPTIONS.filter(
      (opt) => !DISPLAY_ONLY_CODES.includes(opt.code)
    );
  }, [excludeDisplayOnly]);

  const selectedTypeCode =
    value.locationType
      ? LOCATION_TYPE_OPTIONS.find(
          (opt) => CODE_TO_LOCATION_TYPE[opt.code] === value.locationType
        )?.code ?? ""
      : "";

  // Only fetch locations if a non-NOT_ASSIGNED type is selected
  const locationsQuery = useLocations(
    isNotAssigned ? DEFAULT_LOCATION_TYPE : (value.locationType ?? DEFAULT_LOCATION_TYPE)
  );

  // Build list of available locations for the dropdown
  const availableLocations = useMemo(() => {
    if (isNotAssigned || !value.locationType || !locationsQuery.data) return [];

    return (locationsQuery.data as StorageLocation[])
      .map((loc) => {
        const code = getLocationCode(value.locationType!, loc);
        return {
          id: loc.id,
          code,
          // Extract numeric part from the code
          numericCode: code.replace(/^[A-Z]+/, ""),
        };
      })
      .filter((loc) => {
        // Exclude the specified location if any
        if (
          excludeLocation &&
          excludeLocation.locationType === value.locationType &&
          excludeLocation.locationId === loc.id
        ) {
          return false;
        }
        return true;
      })
      .sort((a, b) => naturalSortCompare(a.code, b.code));
  }, [locationsQuery.data, value.locationType, excludeLocation, isNotAssigned]);

  function handleTypeChange(typeCode: string) {
    const locationType = CODE_TO_LOCATION_TYPE[typeCode];

    // If NOT_ASSIGNED is selected, immediately set the locationId
    if (locationType === LocationType.NOT_ASSIGNED) {
      onChange({
        locationType,
        locationId: NOT_ASSIGNED_ID,
        locationCode: "",
      });
    } else {
      onChange({
        locationType: locationType ?? null,
        locationId: null,
        locationCode: "",
      });
    }
  }

  function handleLocationChange(locationId: string) {
    if (!value.locationType) return;

    const selected = availableLocations.find((loc) => loc.id === locationId);
    if (selected) {
      onChange({
        locationType: value.locationType,
        locationId: selected.id,
        locationCode: selected.numericCode,
      });
    }
  }

  const hasLabel = Boolean(label || labelSuffix);

  return (
    <div className={hasLabel ? "space-y-2" : ""}>
      {hasLabel && (
        <div className="flex items-center gap-1.5">
          <Label>{label}</Label>
          {labelSuffix}
        </div>
      )}
      <div className="flex items-center gap-2 flex-1 min-w-0" role="group" aria-label={label || "Location selector"}>
        <Select
          value={selectedTypeCode}
          onValueChange={handleTypeChange}
          disabled={disabled}
        >
          <SelectTrigger
            className="flex-1 min-w-0 bg-background px-2 sm:px-3"
            aria-label={`${label || "Location"} type`}
          >
            <SelectValue>
              {selectedTypeCode ? (
                <>
                  {/* Mobile: show only code */}
                  <span className="sm:hidden">{selectedTypeCode}</span>
                  {/* Desktop: show code and label */}
                  <span className="hidden sm:inline">
                    {selectedTypeCode} -{" "}
                    {filteredLocationTypeOptions.find((opt) => opt.code === selectedTypeCode)?.label}
                  </span>
                </>
              ) : (
                <>
                  {/* Mobile: no placeholder text */}
                  <span className="sm:hidden text-muted-foreground">-</span>
                  {/* Desktop: show Location placeholder */}
                  <span className="hidden sm:inline text-muted-foreground">Location</span>
                </>
              )}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            {filteredLocationTypeOptions.map((opt) => (
              <SelectItem key={opt.code} value={opt.code}>
                {opt.code} - {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {/* Show location combobox for regular types, hide for NOT_ASSIGNED */}
        {!isNotAssigned && (
          <Popover open={codePopoverOpen} onOpenChange={setCodePopoverOpen}>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                role="combobox"
                aria-expanded={codePopoverOpen}
                aria-label={`${label || "Location"} code`}
                disabled={disabled || !value.locationType || locationsQuery.isLoading}
                className="flex-1 min-w-0 sm:flex-none sm:w-24 sm:shrink-0 justify-between font-normal overflow-hidden dark:bg-input dark:border-[#41413d]"
              >
                <span className="truncate">
                  {locationsQuery.isLoading
                    ? "..."
                    : value.locationId
                      ? (availableLocations.find((loc) => loc.id === value.locationId)?.numericCode ?? "...")
                      : ""}
                </span>
                <ChevronsUpDown className="ml-1 h-4 w-4 shrink-0 opacity-50" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-[120px] p-0" align="start" container={null}>
              <Command filter={filterLocationCode}>
                <CommandInput placeholder="Search..." inputMode="numeric" pattern="[0-9]*" />
                <CommandList>
                  <CommandEmpty>No locations</CommandEmpty>
                  <CommandGroup>
                    {availableLocations.map((loc) => (
                      <CommandItem
                        key={loc.id}
                        value={loc.numericCode}
                        onSelect={() => {
                          handleLocationChange(loc.id);
                          setCodePopoverOpen(false);
                        }}
                      >
                        <Check
                          className={cn(
                            "mr-2 h-4 w-4",
                            value.locationId === loc.id ? "opacity-100" : "opacity-0"
                          )}
                        />
                        {loc.numericCode}
                      </CommandItem>
                    ))}
                  </CommandGroup>
                </CommandList>
              </Command>
            </PopoverContent>
          </Popover>
        )}
        {endContent}
      </div>
    </div>
  );
}
