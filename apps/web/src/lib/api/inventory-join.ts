import type { LocationInventory, LocationType, ProductListItem } from "@/types/api";
import type { SiteLocationInventoryEntry } from "./site-inventory";

/**
 * Joins slim v1 `SiteLocationInventoryEntry` rows (inventoryId, productId, quantity, updatedAt
 * only - AC-5, no catalog metadata) against the existing catalog/products query by product ID,
 * producing the `LocationInventory[]` shape every current consumer (location-detail-sheet,
 * not-assigned inventory view) already expects.
 *
 * `locationId`/`locationCode`/`storageLocationType` come from the caller, since the v1 route is
 * already scoped to one location and does not repeat that context per row. Entries whose
 * `productId` has no match in `products` are dropped rather than rendered with placeholder
 * catalog data - the id is stale (e.g. the product was deleted) and the caller only ever
 * intended a real, joined row.
 *
 * Shared by every T-6d caller that reads site-scoped location inventory (T-6d-6, T-6d-9), per
 * the checkpoint's explicit "one helper, not three copies" direction.
 */
export function joinSiteLocationEntriesWithCatalog(
  entries: SiteLocationInventoryEntry[],
  products: ProductListItem[],
  location: { locationId: string; locationCode: string; storageLocationType: LocationType | string }
): LocationInventory[] {
  const productsById = new Map(products.map((p) => [p.id, p]));

  const joined: LocationInventory[] = [];
  for (const entry of entries) {
    const product = productsById.get(entry.productId);
    if (!product) {
      continue;
    }
    joined.push({
      id: entry.inventoryId,
      locationId: location.locationId,
      locationCode: location.locationCode,
      storageLocationType: location.storageLocationType as string,
      item: {
        id: product.id,
        sku: product.sku,
        name: product.name,
        category: product.category,
        imageUrl: product.imageUrl,
        parentId: product.parentId,
        letter: product.letter,
        templateQuantity: product.templateQuantity,
        packsPerBox: product.packsPerBox,
      },
      quantity: entry.quantity,
      createdAt: entry.updatedAt ?? "",
      updatedAt: entry.updatedAt ?? "",
    });
  }
  return joined;
}
