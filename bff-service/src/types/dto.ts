import type { Decimal } from '@prisma/client/runtime/library';

export type RiskLevel = 'critical' | 'warning' | 'normal';

export interface ForecastDTO {
  id: string;
  itemId: string;
  horizonDays: number;
  avgDailyDelta: number | null;
  daysToStockout: number | null;
  suggestedReorderQty: number | null;
  suggestedOrderDate: string | null;
  confidence: number | null;
  computedAt: string;
  riskLevel: RiskLevel;
}

export interface ForecastWithItemDTO extends ForecastDTO {
  item?: {
    id: string;
    sku: string | null;
    name: string;
    category: string;
  };
}

export interface PaginatedResponse<T> {
  data: T[];
  pagination: {
    total: number;
    limit: number;
    offset: number;
    hasMore: boolean;
  };
}

export interface InventorySummaryDTO {
  totalItems: number;
  totalQuantity: number;
  atRiskCount: number;
  criticalCount: number;
  byLocation: {
    boxBins: LocationStats;
    racks: LocationStats;
    cabinets: LocationStats;
    singleClawMachines: LocationStats;
    doubleClawMachines: LocationStats;
    keychainMachines: LocationStats;
  };
  lastUpdated: string;
}

export interface LocationStats {
  itemCount: number;
  totalQuantity: number;
}

export interface ItemDTO {
  id: string;
  sku: string | null;
  name: string;
  category: string;
  subcategory: string | null;
  description: string | null;
  reorderPoint: number | null;
  targetStockLevel: number | null;
  leadTimeDays: number | null;
  isActive: boolean;
  totalQuantity: number;
  forecast?: ForecastDTO | null;
}

export function decimalToNumber(value: Decimal | null): number | null {
  if (value === null) return null;
  return Number(value);
}

export function calculateRiskLevel(daysToStockout: number | null): RiskLevel {
  if (daysToStockout === null) return 'normal';
  if (daysToStockout <= 3) return 'critical';
  if (daysToStockout <= 7) return 'warning';
  return 'normal';
}
