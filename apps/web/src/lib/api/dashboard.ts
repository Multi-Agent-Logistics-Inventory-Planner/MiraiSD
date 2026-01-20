import { LocationType, InventoryItem } from "@/types/api";
import { getLocationsByType } from "@/lib/api/locations";
import { getInventoryByLocation } from "@/lib/api/inventory";

export interface InventoryTotals {
  byItemId: Record<
    string,
    {
      item: InventoryItem;
      quantity: number;
      lastUpdatedAt: string;
    }
  >;
}

const ALL_LOCATION_TYPES: LocationType[] = [
  LocationType.BOX_BIN,
  LocationType.RACK,
  LocationType.CABINET,
  LocationType.SINGLE_CLAW_MACHINE,
  LocationType.DOUBLE_CLAW_MACHINE,
  LocationType.KEYCHAIN_MACHINE,
];

function maxIso(a: string, b: string): string {
  // ISO strings sort lexicographically if they are full ISO timestamps
  return a >= b ? a : b;
}

/**
 * Fetch inventory across ALL location types + locations, then aggregate quantities by itemId.
 * Note: this is intentionally simple (many small requests). It’s acceptable for Phase 3 / small data.
 */
export async function getInventoryTotalsByItemId(): Promise<InventoryTotals> {
  const byItemId: InventoryTotals["byItemId"] = {};

  // 1) Fetch all locations for each type
  const locationsByType = await Promise.all(
    ALL_LOCATION_TYPES.map(async (locationType) => {
      const locations = await getLocationsByType(locationType);
      const ids = (locations as Array<{ id: string }>).map((l) => l.id);
      return { locationType, ids };
    })
  );

  // 2) Fetch inventories for each location and aggregate
  await Promise.all(
    locationsByType.flatMap(({ locationType, ids }) =>
      ids.map(async (locationId) => {
        const inventories = await getInventoryByLocation(locationType, locationId);
        for (const inv of inventories) {
          const itemId = inv.item.id;
          const updatedAt = inv.updatedAt ?? inv.createdAt;

          if (!byItemId[itemId]) {
            byItemId[itemId] = {
              item: inv.item,
              quantity: inv.quantity ?? 0,
              lastUpdatedAt: updatedAt,
            };
          } else {
            byItemId[itemId].quantity += inv.quantity ?? 0;
            byItemId[itemId].lastUpdatedAt = maxIso(
              byItemId[itemId].lastUpdatedAt,
              updatedAt
            );
          }
        }
      })
    )
  );

  return { byItemId };
}

