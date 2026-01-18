import type { PrismaClient } from '@prisma/client';
import { ItemRepository, LocationType, ItemFilters } from '../repositories/item.repository.js';
import {
  ItemDTO,
  ForecastDTO,
  PaginatedResponse,
  decimalToNumber,
  calculateRiskLevel,
} from '../types/dto.js';

export class ItemService {
  private repository: ItemRepository;

  constructor(prisma: PrismaClient) {
    this.repository = new ItemRepository(prisma);
  }

  async getItems(filters: ItemFilters): Promise<PaginatedResponse<ItemDTO>> {
    if (filters.locationType) {
      return this.getItemsByLocationType(filters.locationType, filters);
    }

    const { data, total } = await this.repository.findItems(filters);

    return {
      data: data.map((item) => this.mapToDTO(item, filters.includeForecasts)),
      pagination: {
        total,
        limit: filters.limit,
        offset: filters.offset,
        hasMore: filters.offset + filters.limit < total,
      },
    };
  }

  private async getItemsByLocationType(
    locationType: LocationType,
    filters: ItemFilters
  ): Promise<PaginatedResponse<ItemDTO>> {
    const { data, total } = await this.repository.findItemsByLocationType(locationType, filters);

    return {
      data: data.map((item) => this.mapToDTO(item, filters.includeForecasts, locationType)),
      pagination: {
        total,
        limit: filters.limit,
        offset: filters.offset,
        hasMore: filters.offset + filters.limit < total,
      },
    };
  }

  private mapToDTO(item: any, includeForecasts?: boolean, locationType?: LocationType): ItemDTO {
    // Calculate total quantity across all locations
    const totalQuantity = this.calculateTotalQuantity(item, locationType);

    const dto: ItemDTO = {
      id: item.id,
      sku: item.sku,
      name: item.name,
      category: item.category,
      subcategory: item.subcategory,
      description: item.description,
      reorderPoint: item.reorder_point,
      targetStockLevel: item.target_stock_level,
      leadTimeDays: item.lead_time_days,
      isActive: item.is_active,
      totalQuantity,
    };

    if (includeForecasts && item.forecast_predictions?.length > 0) {
      const forecast = item.forecast_predictions[0];
      const daysToStockout = decimalToNumber(forecast.days_to_stockout);

      dto.forecast = {
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
      };
    }

    return dto;
  }

  private calculateTotalQuantity(item: any, locationType?: LocationType): number {
    if (locationType) {
      // If filtering by location type, only sum that location's inventory
      const locationMap: Record<LocationType, string> = {
        box_bin: 'box_bin_inventory',
        rack: 'rack_inventory',
        cabinet: 'cabinet_inventory',
        single_claw_machine: 'single_claw_machine_inventory',
        double_claw_machine: 'double_claw_machine_inventory',
        keychain_machine: 'keychain_machine_inventory',
      };
      const inventoryKey = locationMap[locationType];
      return item[inventoryKey]?.reduce((sum: number, inv: any) => sum + (inv.quantity ?? 0), 0) ?? 0;
    }

    // Sum across all inventory locations
    const inventoryKeys = [
      'box_bin_inventory',
      'rack_inventory',
      'cabinet_inventory',
      'single_claw_machine_inventory',
      'double_claw_machine_inventory',
      'keychain_machine_inventory',
    ];

    return inventoryKeys.reduce((total, key) => {
      const inventory = item[key];
      if (Array.isArray(inventory)) {
        return total + inventory.reduce((sum: number, inv: any) => sum + (inv.quantity ?? 0), 0);
      }
      return total;
    }, 0);
  }
}
