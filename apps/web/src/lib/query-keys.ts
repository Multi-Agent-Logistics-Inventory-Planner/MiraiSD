/**
 * Centralized query key factory for TanStack Query.
 * Ensures consistent query key structure across the application.
 */

export const queryKeys = {
  // Analytics
  analytics: {
    all: () => ['analytics'] as const,
    predictions: () => ['analytics', 'predictions'] as const,
    insights: () => ['analytics', 'insights'] as const,
    demandLeaders: (period: string) => ['analytics', 'demand-leaders', period] as const,
    performanceMetrics: () => ['analytics', 'performance-metrics'] as const,
    salesSummary: () => ['analytics', 'sales-summary'] as const,
  },

  // Forecasts
  forecasts: {
    all: () => ['forecasts'] as const,
    list: (page: number, limit: number) => ['forecasts', 'list', { page, limit }] as const,
    atRisk: (daysThreshold: number) => ['forecasts', 'at-risk', daysThreshold] as const,
  },
} as const
