import type { PrismaClient } from '@prisma/client';
import { ForecastRepository } from '../repositories/forecast.repository.js';
import {
  ForecastDTO,
  ForecastWithItemDTO,
  PaginatedResponse,
  decimalToNumber,
  calculateRiskLevel,
} from '../types/dto.js';

export class ForecastService {
  private repository: ForecastRepository;

  constructor(prisma: PrismaClient) {
    this.repository = new ForecastRepository(prisma);
  }

  async getLatestForecast(itemId: string): Promise<ForecastWithItemDTO | null> {
    const forecast = await this.repository.findLatestByItemId(itemId);

    if (!forecast) {
      return null;
    }

    return this.mapToDTO(forecast);
  }

  async getAtRiskForecasts(
    threshold: number,
    limit: number,
    offset: number
  ): Promise<PaginatedResponse<ForecastWithItemDTO>> {
    const { data, total } = await this.repository.findAtRisk(threshold, limit, offset);

    return {
      data: data.map((f) => this.mapToDTO(f)),
      pagination: {
        total,
        limit,
        offset,
        hasMore: offset + limit < total,
      },
    };
  }

  private mapToDTO(forecast: {
    id: string;
    item_id: string;
    horizon_days: number;
    avg_daily_delta: any;
    days_to_stockout: any;
    suggested_reorder_qty: number | null;
    suggested_order_date: Date | null;
    confidence: any;
    computed_at: Date;
    products: {
      id: string;
      sku: string | null;
      name: string;
      category: string;
    };
  }): ForecastWithItemDTO {
    const daysToStockout = decimalToNumber(forecast.days_to_stockout);

    return {
      id: forecast.id,
      itemId: forecast.item_id,
      horizonDays: forecast.horizon_days,
      avgDailyDelta: decimalToNumber(forecast.avg_daily_delta),
      daysToStockout,
      suggestedReorderQty: forecast.suggested_reorder_qty,
      suggestedOrderDate: forecast.suggested_order_date?.toISOString().split('T')[0] ?? null,
      confidence: decimalToNumber(forecast.confidence),
      computedAt: forecast.computed_at.toISOString(),
      riskLevel: calculateRiskLevel(daysToStockout),
      item: {
        id: forecast.products.id,
        sku: forecast.products.sku,
        name: forecast.products.name,
        category: forecast.products.category,
      },
    };
  }
}
