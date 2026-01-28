"use client";

import { useMemo } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useLocations } from "@/hooks/queries/use-locations";
import type { LocationType, StorageLocation } from "@/types/api";
import {
  CODE_TO_LOCATION_TYPE,
  LOCATION_TYPE_OPTIONS,
  type LocationSelection,
} from "@/types/transfer";

interface LocationSelectorProps {
  label: string;
  value: LocationSelection;
  onChange: (value: LocationSelection) => void;
  disabled?: boolean;
  excludeLocation?: { locationType: LocationType; locationId: string };
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
  value,
  onChange,
  disabled = false,
  excludeLocation,
}: LocationSelectorProps) {
  const selectedTypeCode =
    value.locationType
      ? LOCATION_TYPE_OPTIONS.find(
          (opt) => CODE_TO_LOCATION_TYPE[opt.code] === value.locationType
        )?.code ?? ""
      : "";

  const locationsQuery = useLocations(
    value.locationType ?? ("BOX_BIN" as LocationType)
  );

  const matchedLocation = useMemo(() => {
    if (!value.locationType || !value.locationCode) return null;
    if (!locationsQuery.data) return null;

    const typePrefix = selectedTypeCode;
    const fullCode = typePrefix + value.locationCode;

    const match = (locationsQuery.data as StorageLocation[]).find((loc) => {
      const locCode = getLocationCode(value.locationType!, loc);
      return locCode === fullCode;
    });

    if (
      match &&
      excludeLocation &&
      excludeLocation.locationType === value.locationType &&
      excludeLocation.locationId === match.id
    ) {
      return null;
    }

    return match;
  }, [
    locationsQuery.data,
    value.locationType,
    value.locationCode,
    selectedTypeCode,
    excludeLocation,
  ]);

  const isValidLocation = Boolean(matchedLocation);
  const locationLabel = matchedLocation
    ? LOCATION_TYPE_OPTIONS.find((opt) => opt.code === selectedTypeCode)?.label +
      " " +
      selectedTypeCode +
      value.locationCode
    : null;

  function handleTypeChange(typeCode: string) {
    const locationType = CODE_TO_LOCATION_TYPE[typeCode];
    onChange({
      locationType: locationType ?? null,
      locationId: null,
      locationCode: "",
    });
  }

  function handleCodeChange(numericCode: string) {
    const sanitized = numericCode.replace(/\D/g, "");
    const locationType = value.locationType;

    if (!locationType || !sanitized) {
      onChange({
        ...value,
        locationCode: sanitized,
        locationId: null,
      });
      return;
    }

    const typePrefix =
      LOCATION_TYPE_OPTIONS.find(
        (opt) => CODE_TO_LOCATION_TYPE[opt.code] === locationType
      )?.code ?? "";
    const fullCode = typePrefix + sanitized;

    const match = (locationsQuery.data as StorageLocation[] | undefined)?.find(
      (loc) => getLocationCode(locationType, loc) === fullCode
    );

    const shouldExclude =
      match &&
      excludeLocation &&
      excludeLocation.locationType === locationType &&
      excludeLocation.locationId === match.id;

    onChange({
      locationType,
      locationCode: sanitized,
      locationId: match && !shouldExclude ? match.id : null,
    });
  }

  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <div className="flex gap-2">
        <Select
          value={selectedTypeCode}
          onValueChange={handleTypeChange}
          disabled={disabled}
        >
          <SelectTrigger className="w-full bg-background">
            <SelectValue placeholder="Location" />
          </SelectTrigger>
          <SelectContent>
            {LOCATION_TYPE_OPTIONS.map((opt) => (
              <SelectItem key={opt.code} value={opt.code}>
                {opt.code} - {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Input
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          placeholder="Code"
          value={value.locationCode}
          onChange={(e) => handleCodeChange(e.target.value)}
          disabled={disabled || !value.locationType}
          className="w-[80px] bg-background"
        />
      </div>
    </div>
  );
}
