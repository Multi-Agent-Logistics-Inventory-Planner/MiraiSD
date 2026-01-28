import type { LocationType } from "./api";

export interface LocationSelection {
  locationType: LocationType | null;
  locationId: string | null;
  locationCode: string;
}

export const LOCATION_TYPE_CODES: Record<LocationType, string> = {
  BOX_BIN: "B",
  SINGLE_CLAW_MACHINE: "S",
  DOUBLE_CLAW_MACHINE: "D",
  KEYCHAIN_MACHINE: "M",
  CABINET: "C",
  RACK: "R",
};

export const CODE_TO_LOCATION_TYPE: Record<string, LocationType> = {
  B: "BOX_BIN" as LocationType,
  S: "SINGLE_CLAW_MACHINE" as LocationType,
  D: "DOUBLE_CLAW_MACHINE" as LocationType,
  M: "KEYCHAIN_MACHINE" as LocationType,
  C: "CABINET" as LocationType,
  R: "RACK" as LocationType,
};

export const LOCATION_TYPE_OPTIONS = [
  { code: "B", label: "Box Bin" },
  { code: "S", label: "Single Claw" },
  { code: "D", label: "Double Claw" },
  { code: "M", label: "Keychain Machine" },
  { code: "C", label: "Cabinet" },
  { code: "R", label: "Rack" },
] as const;
