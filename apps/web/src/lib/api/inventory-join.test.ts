import { describe, it, expect } from "vitest";
import { joinSiteLocationEntriesWithCatalog } from "./inventory-join";
import { LocationType, type ProductListItem } from "@/types/api";
import type { SiteLocationInventoryEntry } from "./site-inventory";

const CATEGORY = {
  id: "cat-1",
  name: "Toys",
  slug: "toys",
  parentId: null,
  displayOrder: 0,
  isActive: true,
  usesPacks: false,
  children: [],
  createdAt: "",
  updatedAt: "",
};

function product(overrides: Partial<ProductListItem> = {}): ProductListItem {
  return {
    id: "p-1",
    sku: "SKU-1",
    name: "Widget",
    imageUrl: "https://example.com/w.png",
    isActive: true,
    quantity: 0,
    category: CATEGORY,
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function entry(overrides: Partial<SiteLocationInventoryEntry> = {}): SiteLocationInventoryEntry {
  return {
    inventoryId: "inv-1",
    productId: "p-1",
    quantity: 5,
    updatedAt: "2026-01-02T00:00:00Z",
    ...overrides,
  };
}

describe("joinSiteLocationEntriesWithCatalog", () => {
  it("joins a slim entry against the catalog by product id, carrying the caller's location context", () => {
    const result = joinSiteLocationEntriesWithCatalog([entry()], [product()], {
      locationId: "loc-1",
      locationCode: "R01",
      storageLocationType: LocationType.RACK,
    });

    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      id: "inv-1",
      locationId: "loc-1",
      locationCode: "R01",
      storageLocationType: LocationType.RACK,
      quantity: 5,
      item: expect.objectContaining({ id: "p-1", name: "Widget", sku: "SKU-1" }),
    });
  });

  it("drops entries whose productId has no match in the catalog, rather than rendering placeholder data", () => {
    const result = joinSiteLocationEntriesWithCatalog(
      [entry({ productId: "stale-product" })],
      [product()],
      { locationId: "loc-1", locationCode: "R01", storageLocationType: LocationType.RACK }
    );

    expect(result).toHaveLength(0);
  });

  it("joins multiple entries independently, preserving each one's own quantity", () => {
    const result = joinSiteLocationEntriesWithCatalog(
      [entry({ inventoryId: "inv-1", productId: "p-1", quantity: 3 }), entry({ inventoryId: "inv-2", productId: "p-2", quantity: 9 })],
      [product({ id: "p-1" }), product({ id: "p-2", name: "Gadget" })],
      { locationId: "loc-1", locationCode: "R01", storageLocationType: LocationType.RACK }
    );

    expect(result).toHaveLength(2);
    expect(result.find((r) => r.id === "inv-2")).toMatchObject({
      quantity: 9,
      item: expect.objectContaining({ name: "Gadget" }),
    });
  });
});
