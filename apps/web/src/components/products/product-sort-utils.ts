import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";
import type { Category } from "@/types/api";

export type SortColumn = "product" | "status" | "category" | "subcategory" | "stock";
export type SortDirection = "asc" | "desc";
export interface ProductSort {
  readonly column: SortColumn;
  readonly direction: SortDirection;
}

export const DEFAULT_PRODUCT_SORT: ProductSort = { column: "product", direction: "asc" };

/**
 * Build a map from category ID to parent (root) category name.
 * Root categories map to themselves; children map to their parent's name.
 */
export function buildParentNameMap(categories: readonly Category[]): Map<string, string> {
  const map = new Map<string, string>();
  for (const parent of categories) {
    map.set(parent.id, parent.name);
    for (const child of parent.children) {
      map.set(child.id, parent.name);
    }
  }
  return map;
}

/**
 * Build a set of category IDs that belong to the "Kuji" family (root + children).
 */
export function buildKujiCategoryIds(categories: readonly Category[]): ReadonlySet<string> {
  const ids = new Set<string>();
  const kujiCat = categories.find(
    (c) => c.name.toLowerCase() === "kuji" || c.slug?.toLowerCase() === "kuji"
  );
  if (kujiCat) {
    ids.add(kujiCat.id);
    for (const child of kujiCat.children) {
      ids.add(child.id);
    }
  }
  return ids;
}

/**
 * Check whether a product row should be treated as a Kuji product
 * (has children or belongs to the Kuji category tree).
 */
export function isKujiProduct(
  row: ProductWithInventory,
  kujiCategoryIds: ReadonlySet<string>,
): boolean {
  return row.product.hasChildren || kujiCategoryIds.has(row.product.category.id);
}

function getSubcategoryName(
  row: ProductWithInventory,
  parentNameMap: Map<string, string>,
): string {
  const parentName = parentNameMap.get(row.product.category.id);
  return parentName !== row.product.category.name ? row.product.category.name : "";
}

/**
 * Pure comparator for sorting ProductWithInventory rows.
 * Returns a negative, zero, or positive number for use with Array.sort.
 */
export function compareProducts(
  a: ProductWithInventory,
  b: ProductWithInventory,
  sort: ProductSort,
  parentNameMap: Map<string, string>,
): number {
  const dir = sort.direction === "asc" ? 1 : -1;

  switch (sort.column) {
    case "product":
      return dir * a.product.name.localeCompare(b.product.name);

    case "status": {
      // Active (true) = 0, Inactive (false) = 1 => active first when ascending
      const aVal = a.product.isActive ? 0 : 1;
      const bVal = b.product.isActive ? 0 : 1;
      return dir * (aVal - bVal);
    }

    case "category": {
      const aCat = parentNameMap.get(a.product.category.id) ?? a.product.category.name;
      const bCat = parentNameMap.get(b.product.category.id) ?? b.product.category.name;
      return dir * aCat.localeCompare(bCat);
    }

    case "subcategory": {
      const aSub = getSubcategoryName(a, parentNameMap);
      const bSub = getSubcategoryName(b, parentNameMap);
      // Items with no subcategory always sort last, regardless of direction
      if (!aSub && bSub) return 1;
      if (aSub && !bSub) return -1;
      return dir * aSub.localeCompare(bSub);
    }

    case "stock":
      return dir * (a.totalQuantity - b.totalQuantity);

    default:
      return 0;
  }
}
