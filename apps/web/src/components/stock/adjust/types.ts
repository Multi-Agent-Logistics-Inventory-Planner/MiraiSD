import type { Inventory, InventoryItem, ProductCategory } from "@/types/api";
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
  categoryFilter: ProductCategory | null;
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
