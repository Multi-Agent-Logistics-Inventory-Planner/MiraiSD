import type { LocationInventory, InventoryItem, Category } from "@/types/api";
import { StockMovementReason } from "@/types/api";
import type { ProductWithInventory } from "@/hooks/queries/use-product-inventory";

export type AdjustAction = "add" | "subtract";

export interface ReasonOption {
  readonly value: StockMovementReason;
  readonly label: string;
}

export const REASON_OPTIONS_BY_ACTION: Record<AdjustAction, readonly ReasonOption[]> = {
  subtract: [
    { value: StockMovementReason.SALE, label: "Sale" },
    { value: StockMovementReason.ADJUSTMENT, label: "Adjustment" },
  ],
  // RESTOCK is reserved for the formal shipment-receiving flow; the manual
  // adjust dialog emits ADJUSTMENT so users have a single mental model for
  // ad-hoc stock additions. Historical RESTOCK rows still render in the
  // audit log, and the forecasting "last restock" signal still picks up
  // SHIPMENT_RECEIPT (plus any legacy RESTOCK) events.
  add: [
    { value: StockMovementReason.ADJUSTMENT, label: "Adjustment" },
  ],
} as const;

export const DEFAULT_REASON_BY_ACTION: Record<AdjustAction, StockMovementReason> = {
  subtract: StockMovementReason.SALE,
  add: StockMovementReason.ADJUSTMENT,
} as const;

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
      imageUrl: product.imageUrl,
    },
    quantity: totalQuantity,
    // List endpoints only return updatedAt; fall back to the same value for createdAt
    // since this normalized record is consumed as a stock-display row, not an audit object.
    createdAt: product.updatedAt,
    updatedAt: product.updatedAt,
  };
}

/**
 * Convert an Inventory to NormalizedInventory
 */
export function normalizeInventory(inventory: LocationInventory): NormalizedInventory {
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
  categoryFilters: string[];
  childCategoryFilters: string[];
  availableCategories: Category[];
  availableChildCategories: Category[];
}

/**
 * A staged adjustment line in the cart-mode dialog.
 * Persists per-product quantity and intake metadata until the batch submits.
 */
export interface CartLine {
  inventoryId: string;
  /** Always stored as a positive integer; the dialog applies the action sign at submit. */
  quantity: number;
  intakeUnit: "pack" | "box";
  intakeQty: number;
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
  categoryFilters: string[],
  childCategoryFilters: string[],
  availableCategories: Category[] = [],
  availableChildCategories: Category[] = []
): string {
  const parts: string[] = [];

  if (searchQuery) {
    parts.push(`matching "${searchQuery}"`);
  }

  if (categoryFilters.length > 0) {
    const categoryNames = categoryFilters
      .map((id) => availableCategories.find((c) => c.id === id)?.name ?? id)
      .join(", ");
    parts.push(`in ${categoryNames}`);
  }

  if (childCategoryFilters.length > 0) {
    const childCategoryNames = childCategoryFilters
      .map((id) => availableChildCategories.find((s) => s.id === id)?.name ?? id)
      .join(", ");
    parts.push(`(${childCategoryNames})`);
  }

  if (parts.length === 0) {
    return "No products found";
  }

  return `No products ${parts.join(" ")}`;
}
