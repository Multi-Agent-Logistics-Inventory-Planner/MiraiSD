export type StockStatus = "good" | "low" | "critical" | "out-of-stock";

export interface StockLevelItem {
  itemId: string;
  sku: string;
  name: string;
  stock: number;
  maxStock: number;
  status: StockStatus;
  lastUpdated: string;
  daysToStockout?: number;
}

export interface CategoryData {
  name: string;
  value: number;
  fill: string;
}

export interface ActivityItem {
  id: string;
  action: string;
  time: string;
}

export interface DashboardStats {
  totalStockValue: number;
  stockValueChange: number | null;
  lowStockItems: number;
  criticalItems: number;
  outOfStockItems: number;
  totalSKUs: number;
  stockLevels: StockLevelItem[];
  categoryDistribution: CategoryData[];
}

