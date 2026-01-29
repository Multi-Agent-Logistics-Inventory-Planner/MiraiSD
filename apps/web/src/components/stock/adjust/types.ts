import type { Inventory, InventoryItem } from "@/types/api";
import {
  PRODUCT_CATEGORY_LABELS,
  PRODUCT_SUBCATEGORY_LABELS,
  type ProductCategory,
  type ProductSubcategory,
} from "@/types/api";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";

export type AdjustAction = "add" | "subtract";

/**
 * Normalized inventory representation for the adjust dialog
 * Used to unify the display of inventory items and products with inventory
 */
export interface NormalizedInventory {
  id: string;
  item: InventoryItem;
  quantity: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Create a normalized inventory object from a ProductWithInventory
 * This is used when adding stock to show products that have stock elsewhere
 */
export function createNormalizedInventory(
  productWithInventory: ProductWithInventory
): NormalizedInventory {
  const { product, totalQuantity } = productWithInventory;

  return {
    id: product.id,
    item: {
      id: product.id,
      sku: product.sku,
      name: product.name,
      category: product.category,
      subcategory: product.subcategory,
      imageUrl: product.imageUrl,
    },
    quantity: totalQuantity,
    createdAt: product.createdAt,
    updatedAt: product.updatedAt,
  };
}

/**
 * Convert an Inventory to NormalizedInventory
 */
export function normalizeInventory(inventory: Inventory): NormalizedInventory {
  return {
    id: inventory.id,
    item: inventory.item,
    quantity: inventory.quantity,
    createdAt: inventory.createdAt,
    updatedAt: inventory.updatedAt,
  };
}

export interface ProductListProps {
  items: NormalizedInventory[];
  selectedId: string | null;
  onSelect: (id: string, quantity: number) => void;
  isLoading: boolean;
  disabled: boolean;
  emptyMessage: string;
  searchQuery: string;
  categoryFilters: ProductCategory[];
  subcategoryFilters: ProductSubcategory[];
}

export interface QuantityControlsProps {
  quantity: number;
  maxQuantity: number;
  action: AdjustAction;
  disabled: boolean;
  onQuantityChange: (value: string) => void;
  onIncrement: () => void;
  onDecrement: () => void;
}

export interface AdjustSummaryProps {
  action: AdjustAction;
  currentQty: number;
  quantity: number;
  newQty: number;
  locationLabel: string;
}

/**
 * Generate a user-friendly message when no products match the current filters
 */
export function getNoResultsMessage(
  searchQuery: string,
  categoryFilters: ProductCategory[],
  subcategoryFilters: ProductSubcategory[]
): string {
  const parts: string[] = [];

  if (searchQuery) {
    parts.push(`matching "${searchQuery}"`);
  }

  if (categoryFilters.length > 0) {
    const categoryNames = categoryFilters
      .map((c) => PRODUCT_CATEGORY_LABELS[c])
      .join(", ");
    parts.push(`in ${categoryNames}`);
  }

  if (subcategoryFilters.length > 0) {
    const subcategoryNames = subcategoryFilters
      .map((s) => PRODUCT_SUBCATEGORY_LABELS[s])
      .join(", ");
    parts.push(`(${subcategoryNames})`);
  }

  if (parts.length === 0) {
    return "No products found";
  }

  return `No products ${parts.join(" ")}`;
}
