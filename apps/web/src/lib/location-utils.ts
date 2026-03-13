import {
  LocationType,
  type LocationWithCounts,
  type StorageLocation,
} from "@/types/api";

/**
 * Map of location types to their corresponding code property names.
 */
const LOCATION_CODE_KEYS: Record<LocationType, string | null> = {
  [LocationType.BOX_BIN]: "boxBinCode",
  [LocationType.CABINET]: "cabinetCode",
  [LocationType.DOUBLE_CLAW_MACHINE]: "doubleClawMachineCode",
  [LocationType.FOUR_CORNER_MACHINE]: "fourCornerMachineCode",
  [LocationType.GACHAPON]: "gachaponCode",
  [LocationType.KEYCHAIN_MACHINE]: "keychainMachineCode",
  [LocationType.PUSHER_MACHINE]: "pusherMachineCode",
  [LocationType.RACK]: "rackCode",
  [LocationType.SINGLE_CLAW_MACHINE]: "singleClawMachineCode",
  [LocationType.WINDOW]: "windowCode",
  [LocationType.NOT_ASSIGNED]: null,
};

/**
 * Converts a LocationWithCounts object to a StorageLocation object.
 * Used when passing data from aggregate endpoints to detail sheets/forms.
 *
 * @param row The LocationWithCounts object from the aggregate endpoint
 * @returns A StorageLocation object with the appropriate code field set
 */
export function toStorageLocation(row: LocationWithCounts): StorageLocation {
  const base = {
    id: row.id,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
  };

  const codeKey = LOCATION_CODE_KEYS[row.locationType];
  if (codeKey) {
    return { ...base, [codeKey]: row.locationCode } as StorageLocation;
  }

  return base as StorageLocation;
}
