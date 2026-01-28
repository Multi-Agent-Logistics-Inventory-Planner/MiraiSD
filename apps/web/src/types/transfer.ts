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
  KEYCHAIN_MACHINE: "K",
  CABINET: "C",
  RACK: "R",
  FOUR_CORNER_MACHINE: "M",
  PUSHER_MACHINE: "P",
  NOT_ASSIGNED: "",
};

export const CODE_TO_LOCATION_TYPE: Record<string, LocationType> = {
  B: "BOX_BIN" as LocationType,
  S: "SINGLE_CLAW_MACHINE" as LocationType,
  D: "DOUBLE_CLAW_MACHINE" as LocationType,
  K: "KEYCHAIN_MACHINE" as LocationType,
  C: "CABINET" as LocationType,
  R: "RACK" as LocationType,
  M: "FOUR_CORNER_MACHINE" as LocationType,
  P: "PUSHER_MACHINE" as LocationType,
};

export const LOCATION_TYPE_OPTIONS = [
  { code: "B", label: "Box Bin" },
  { code: "S", label: "Single Claw" },
  { code: "D", label: "Double Claw" },
  { code: "K", label: "Keychain Machine" },
  { code: "C", label: "Cabinet" },
  { code: "R", label: "Rack" },
  { code: "M", label: "Four Corner" },
  { code: "P", label: "Pusher" },
] as const;
