"use client";

import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { LocationType, type StorageLocation } from "@/types/api";
import {
  getLocationsByType,
  getBoxBinById,
  getCabinetById,
  getDoubleClawMachineById,
  getFourCornerMachineById,
  getGachaponById,
  getKeychainMachineById,
  getPusherMachineById,
  getRackById,
  getSingleClawMachineById,
  getWindowById,
} from "@/lib/api/locations";

async function getLocationById(
  locationType: LocationType,
  id: string
): Promise<StorageLocation> {
  switch (locationType) {
    case LocationType.BOX_BIN:
      return getBoxBinById(id);
    case LocationType.CABINET:
      return getCabinetById(id);
    case LocationType.DOUBLE_CLAW_MACHINE:
      return getDoubleClawMachineById(id);
    case LocationType.FOUR_CORNER_MACHINE:
      return getFourCornerMachineById(id);
    case LocationType.GACHAPON:
      return getGachaponById(id);
    case LocationType.KEYCHAIN_MACHINE:
      return getKeychainMachineById(id);
    case LocationType.PUSHER_MACHINE:
      return getPusherMachineById(id);
    case LocationType.RACK:
      return getRackById(id);
    case LocationType.SINGLE_CLAW_MACHINE:
      return getSingleClawMachineById(id);
    case LocationType.WINDOW:
      return getWindowById(id);
    default:
      throw new Error(`Unknown location type: ${locationType}`);
  }
}

export function useLocations(locationType: LocationType) {
  return useQuery({
    queryKey: ["locations", locationType],
    queryFn: () => getLocationsByType(locationType),
    enabled: locationType !== LocationType.NOT_ASSIGNED,
  });
}

/**
 * Fetch locations without inventory counts (single API call).
 * Use this for getting the full list for filtering/pagination.
 */
export function useLocationsOnly(locationType: LocationType) {
  return useQuery({
    queryKey: ["locationsOnly", locationType],
    queryFn: async (): Promise<StorageLocation[]> => {
      return (await getLocationsByType(locationType)) as StorageLocation[];
    },
    enabled: locationType !== LocationType.NOT_ASSIGNED,
  });
}

/**
 * Fetch a single location by type and ID.
 */
export function useLocation(
  locationType: LocationType | undefined,
  locationId: string | undefined
): UseQueryResult<StorageLocation> {
  return useQuery({
    queryKey: ["location", locationType, locationId],
    queryFn: () => getLocationById(locationType!, locationId!),
    enabled:
      !!locationType &&
      locationType !== LocationType.NOT_ASSIGNED &&
      !!locationId,
  });
}
