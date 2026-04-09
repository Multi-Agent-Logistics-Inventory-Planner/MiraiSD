export enum AnalyticsTab {
  OVERVIEW = "overview",
  PREDICTIONS = "predictions",
  // Archived tabs (moved to _archive folder)
  // DEMAND_LEADERS = "demand-leaders",
  // LEGACY = "legacy",
  // NOTIFICATIONS = "notifications",
}

// Predictions Types - Demand-based metrics for reorder decisions
export type ActionUrgency = 'CRITICAL' | 'URGENT' | 'ATTENTION' | 'HEALTHY'

export interface ActionItem {
  itemId: string
  name: string
  sku: string
  imageUrl: string | null
  categoryName: string
  currentStock: number
  reorderPoint: number
  targetStockLevel: number
  daysToStockout: number | null
  avgDailyDelta: number
  suggestedReorderQty: number
  suggestedOrderDate: string | null
  leadTimeDays: number
  demandVelocity: number | null
  demandVolatility: number | null
  forecastAccuracy: number | null
  confidence: number
  urgency: ActionUrgency
  overdue: boolean
  computedAt: string | null
}

export interface RiskSummary {
  critical: number
  urgent: number
  attention: number
  healthy: number
}

export interface PredictionsData {
  items: ActionItem[]
  totalActionItems: number
  avgForecastAccuracy: number
  totalDemandVelocity: number
  riskSummary: RiskSummary
}

// Insights Types - Now with demand-based metrics
export interface DayOfWeekPattern {
  dayOfWeek: number
  dayName: string
  totalUnits: number
  avgDemandMultiplier: number
  percentOfWeeklyTotal: number
}

export type MoverDirection = 'UP' | 'DOWN' | 'STABLE'

export interface Mover {
  rank: number
  itemId: string
  name: string
  imageUrl: string | null
  categoryName: string
  currentPeriodUnits: number
  previousPeriodUnits: number
  percentChange: number
  direction: MoverDirection
  demandVelocity: number | null
  displayConfidence: number | null
}

export interface PeriodSummary {
  periodLabel: string
  totalUnits: number
  avgDemandVelocity: number
  avgForecastAccuracy: number
  uniqueItemsSold: number
  totalMovements: number
}

export interface InsightsData {
  dayOfWeekPatterns: DayOfWeekPattern[]
  topMovers: Mover[]
  bottomMovers: Mover[]
  currentPeriod: PeriodSummary
  previousPeriod: PeriodSummary
}

// Demand Leaders Types (renamed from Top Sellers)
export interface DemandLeader {
  rank: number
  itemId: string
  name: string
  sku: string
  imageUrl: string | null
  categoryName: string
  periodDemand: number
  demandVelocity: number
  demandVolatility: number
  forecastAccuracy: number
  stockVelocity: number
  percentOfTotal: number
}

export interface CategoryRanking {
  rank: number
  categoryId: string
  categoryName: string
  totalItems: number
  periodDemand: number
  totalDemandVelocity: number
  percentOfTotal: number
}

export interface DemandSummary {
  totalDemandVelocity: number
  totalPeriodDemand: number
  uniqueItemsWithDemand: number
  demandGrowthPercent: number
  systemForecastAccuracy: number
  periodLabel: string
}

export interface DemandLeadersData {
  byDemandVelocity: DemandLeader[]
  byStockVelocity: DemandLeader[]
  categoryRankings: CategoryRanking[]
  summary: DemandSummary
}

// Period filter options
export type DemandLeadersPeriod = '7d' | '30d' | '90d' | 'ytd'
