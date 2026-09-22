"use client";

import { LocationType } from "@/types/api";
import { useLocationInventory } from "@/hooks/queries/use-location-inventory";

/**
 * Site-scoped NOT_ASSIGNED inventory - a thin wrapper over useLocationInventory, which resolves
 * the site's real NOT_ASSIGNED location and excludes kuji-child/CUSTOM-kuji-parent rows (T-6d-9).
 */
export function useNotAssignedInventory() {
  return useLocationInventory(LocationType.NOT_ASSIGNED, undefined);
}
