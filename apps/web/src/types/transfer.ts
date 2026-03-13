import type { LocationType } from "./api";

export interface LocationSelection {
  locationType: LocationType | null;
  locationId: string | null;
  locationCode: string;
}

export const LOCATION_TYPE_CODES: Record<LocationType, string> = {
  BOX_BIN: "B",
  CABINET: "C",
  DOUBLE_CLAW_MACHINE: "D",
  FOUR_CORNER_MACHINE: "M",
  GACHAPON: "G",
  KEYCHAIN_MACHINE: "K",
  PUSHER_MACHINE: "P",
  RACK: "R",
  SINGLE_CLAW_MACHINE: "S",
  WINDOW: "W",
  NOT_ASSIGNED: "",
};

export const CODE_TO_LOCATION_TYPE: Record<string, LocationType> = {
  B: "BOX_BIN" as LocationType,
  C: "CABINET" as LocationType,
  D: "DOUBLE_CLAW_MACHINE" as LocationType,
  G: "GACHAPON" as LocationType,
  K: "KEYCHAIN_MACHINE" as LocationType,
  M: "FOUR_CORNER_MACHINE" as LocationType,
  NA: "NOT_ASSIGNED" as LocationType,
  P: "PUSHER_MACHINE" as LocationType,
  R: "RACK" as LocationType,
  S: "SINGLE_CLAW_MACHINE" as LocationType,
  W: "WINDOW" as LocationType,
};

export const LOCATION_TYPE_OPTIONS = [
  { code: "B", label: "Box Bin" },
  { code: "C", label: "Cabinet" },
  { code: "D", label: "Double Claw" },
  { code: "G", label: "Gachapon" },
  { code: "K", label: "Keychain Machine" },
  { code: "M", label: "Four Corner" },
  { code: "P", label: "Pusher" },
  { code: "R", label: "Rack" },
  { code: "S", label: "Single Claw" },
  { code: "W", label: "Window" },
  { code: "NA", label: "Not Assigned" },
] as const;
