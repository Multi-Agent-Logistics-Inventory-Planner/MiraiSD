import { apiGet } from "@/lib/api/client";

// ---- Types ----

export interface HeaderBundle {
  productId: string;
  productName: string | null;
  categoryName: string | null;
  currentStock: number | null;
  unitsSoldLast30: number;
  unitsSoldPrior30: number;
  velocity: number | null;
  daysToStockout: number | null;
  forecastConfidence: number | null;
  mape: number | null;
  lastRestockAt: string | null;
  damageCountLast30: number;
  onDisplay: boolean;
}

export interface DetailBundle {
  product: {
    id: string;
    sku: string | null;
    name: string;
    categoryName: string | null;
    imageUrl: string | null;
    reorderPoint: number | null;
    targetStockLevel: number | null;
    leadTimeDays: number | null;
    unitCost: number | null;
    currentStock: number;
  };
  inventoryByLocation: Array<{
    locationId: string | null;
    locationCode: string | null;
    storageLocationCode: string | null;
    quantity: number;
  }>;
  dailyRollups90d: Array<{
    date: string;
    unitsSold: number | null;
    revenue: number | null;
    restockUnits: number | null;
    damageUnits: number | null;
  }>;
  forecastSnapshots: Array<{
    date: string;
    muHat: number | null;
    confidence: number | null;
    mape: number | null;
    daysToStockout: number | null;
    currentStock: number | null;
  }>;
  latestPrediction: {
    horizonDays: number | null;
    avgDailyDelta: number | null;
    daysToStockout: number | null;
    suggestedReorderQty: number | null;
    suggestedOrderDate: string | null;
    confidence: number | null;
    computedAt: string | null;
  } | null;
  recentShipments: Array<{
    shipmentItemId: string;
    shipmentId: string | null;
    deliveredOn: string | null;
    orderedQuantity: number | null;
    receivedQuantity: number | null;
    damagedQuantity: number | null;
    unitCost: number | null;
  }>;
  activeDisplays: Array<{
    id: string;
    locationId: string | null;
    locationType: string | null;
    machineId: string | null;
    startedAt: string | null;
  }>;
}

export type StockMovementReason =
  | "INITIAL_STOCK"
  | "RESTOCK"
  | "SHIPMENT_RECEIPT"
  | "SHIPMENT_RECEIPT_REVERSED"
  | "SALE"
  | "DAMAGE"
  | "ADJUSTMENT"
  | "RETURN"
  | "TRANSFER"
  | "REMOVED"
  | "DISPLAY_SET"
  | "DISPLAY_REMOVED"
  | "DISPLAY_SWAP";

export interface MovementRow {
  id: number;
  at: string;
  reason: StockMovementReason;
  quantityChange: number;
  previousQuantity: number | null;
  currentQuantity: number | null;
  fromLocationId: string | null;
  toLocationId: string | null;
}

export interface MovementSummary {
  byReason: Partial<Record<StockMovementReason, number>>;
  lastByReason: Partial<Record<StockMovementReason, string>>;
  biggestSingleDay: { date: string; units: number } | null;
}

export interface ComparisonRow {
  productId: string;
  productName: string;
  metricValue: number | null;
  rank: number;
}

// ---- API wrappers ----

const base = (productId: string) => `/api/analytics/products/${productId}`;

export function fetchHeaderBundle(productId: string): Promise<HeaderBundle> {
  return apiGet<HeaderBundle>(`${base(productId)}/report-bundle/header`);
}

export function fetchDetailBundle(
  productId: string,
  days = 90,
): Promise<DetailBundle> {
  return apiGet<DetailBundle>(
    `${base(productId)}/report-bundle/detail?days=${days}`,
  );
}

export function fetchMovements(
  productId: string,
  params: {
    from: string;
    to: string;
    reasons?: StockMovementReason[];
    limit?: number;
  },
): Promise<MovementRow[]> {
  const qs = new URLSearchParams({ from: params.from, to: params.to });
  if (params.reasons?.length) qs.set("reasons", params.reasons.join(","));
  if (params.limit) qs.set("limit", String(params.limit));
  return apiGet<MovementRow[]>(`${base(productId)}/movements?${qs}`);
}

export function fetchMovementSummary(
  productId: string,
  params: { from: string; to: string },
): Promise<MovementSummary> {
  const qs = new URLSearchParams(params);
  return apiGet<MovementSummary>(`${base(productId)}/movements/summary?${qs}`);
}

export function fetchCategoryComparison(
  productId: string,
  metric: "sales_velocity" | "days_to_stockout",
  limit = 5,
): Promise<ComparisonRow[]> {
  const qs = new URLSearchParams({ metric, limit: String(limit) });
  return apiGet<ComparisonRow[]>(`${base(productId)}/comparison?${qs}`);
}
