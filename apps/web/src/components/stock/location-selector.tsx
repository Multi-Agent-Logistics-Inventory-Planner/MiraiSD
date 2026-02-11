"use client";

import { useMemo } from "react";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useLocations } from "@/hooks/queries/use-locations";
import { LocationType, type StorageLocation } from "@/types/api";
import {
  CODE_TO_LOCATION_TYPE,
  LOCATION_TYPE_OPTIONS,
  type LocationSelection,
} from "@/types/transfer";

const DEFAULT_LOCATION_TYPE = LocationType.BOX_BIN;

/** Virtual ID used for "Not Assigned" selection */
const NOT_ASSIGNED_ID = "__not_assigned__";

interface LocationSelectorProps {
  label: string;
  labelSuffix?: React.ReactNode;
  value: LocationSelection;
  onChange: (value: LocationSelection) => void;
  disabled?: boolean;
  excludeLocation?: { locationType: LocationType; locationId: string };
  /** Content to render after the code input*/
  endContent?: React.ReactNode;
}

function getLocationCode(
  locationType: LocationType,
  location: StorageLocation
): string {
  switch (locationType) {
    case "BOX_BIN":
      return "boxBinCode" in location ? location.boxBinCode : "";
    case "RACK":
      return "rackCode" in location ? location.rackCode : "";
    case "CABINET":
      return "cabinetCode" in location ? location.cabinetCode : "";
    case "SINGLE_CLAW_MACHINE":
      return "singleClawMachineCode" in location
        ? location.singleClawMachineCode
        : "";
    case "DOUBLE_CLAW_MACHINE":
      return "doubleClawMachineCode" in location
        ? location.doubleClawMachineCode
        : "";
    case "KEYCHAIN_MACHINE":
      return "keychainMachineCode" in location
        ? location.keychainMachineCode
        : "";
    case "FOUR_CORNER_MACHINE":
      return "fourCornerMachineCode" in location
        ? location.fourCornerMachineCode
        : "";
    case "PUSHER_MACHINE":
      return "pusherMachineCode" in location
        ? location.pusherMachineCode
        : "";
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
}: LocationSelectorProps) {
  const isNotAssigned = value.locationType === LocationType.NOT_ASSIGNED;

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
      .sort((a, b) => a.code.localeCompare(b.code));
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
                    {LOCATION_TYPE_OPTIONS.find((opt) => opt.code === selectedTypeCode)?.label}
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
            {LOCATION_TYPE_OPTIONS.map((opt) => (
              <SelectItem key={opt.code} value={opt.code}>
                {opt.code} - {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {/* Show location dropdown for regular types, hide for NOT_ASSIGNED */}
        {!isNotAssigned && (
          <Select
            value={value.locationId ?? ""}
            onValueChange={handleLocationChange}
            disabled={disabled || !value.locationType || locationsQuery.isLoading}
          >
            <SelectTrigger
              className="w-24 shrink-0 bg-background"
              aria-label={`${label || "Location"} code`}
            >
              <SelectValue placeholder={locationsQuery.isLoading ? "..." : "Code"} />
            </SelectTrigger>
            <SelectContent>
              {availableLocations.length === 0 ? (
                <SelectItem value="__none__" disabled>
                  No locations
                </SelectItem>
              ) : (
                availableLocations.map((loc) => (
                  <SelectItem key={loc.id} value={loc.id}>
                    {loc.code}
                  </SelectItem>
                ))
              )}
            </SelectContent>
          </Select>
        )}
        {endContent}
      </div>
    </div>
  );
}
