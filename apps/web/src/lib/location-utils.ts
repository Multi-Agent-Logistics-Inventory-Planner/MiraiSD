import type { LocationWithCounts, Location } from "@/types/api";

/**
 * Converts a LocationWithCounts to a Location for use in detail sheets/forms.
 * The unified Location type uses locationCode directly.
 */
export function toStorageLocation(row: LocationWithCounts): Location {
  return {
    id: row.id,
    locationCode: row.locationCode,
    storageLocationId: "",
    storageLocationType: row.locationType,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
  };
}
