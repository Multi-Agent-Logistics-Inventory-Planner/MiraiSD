import type { FastifyPluginAsync, FastifyRequest } from 'fastify';
import { z } from 'zod';
import { prisma } from '../lib/prisma.js';
import type { RiskLevel } from '../types.js';

// Helper functions
function decimalToNumber(value: any): number | null {
  if (value === null) return null;
  return Number(value);
}

function calculateRiskLevel(daysToStockout: number | null): RiskLevel {
  if (daysToStockout === null) return 'normal';
  if (daysToStockout <= 3) return 'critical';
  if (daysToStockout <= 7) return 'warning';
  return 'normal';
}

function sumQuantity(records: unknown): number {
  if (!Array.isArray(records)) return 0;
  return records.reduce((sum, rec) => sum + (typeof (rec as any)?.quantity === 'number' ? (rec as any).quantity : 0), 0);
}

type LocationType =
  | 'box_bin'
  | 'rack'
  | 'cabinet'
  | 'single_claw_machine'
  | 'double_claw_machine'
  | 'keychain_machine';

const locationRelations = {
  box_bin: 'box_bin_inventory',
  rack: 'rack_inventory',
  cabinet: 'cabinet_inventory',
  single_claw_machine: 'single_claw_machine_inventory',
  double_claw_machine: 'double_claw_machine_inventory',
  keychain_machine: 'keychain_machine_inventory',
} as const;

// Schema
const getItemsQuery = z.object({
  locationType: z.enum(['box_bin', 'rack', 'cabinet', 'single_claw_machine', 'double_claw_machine', 'keychain_machine']).optional(),
  category: z.string().optional(),
  includeForecasts: z.coerce.boolean().default(false),
  limit: z.coerce.number().int().min(1).max(100).default(50),
  offset: z.coerce.number().int().min(0).default(0),
});

const itemRoutes: FastifyPluginAsync = async (fastify) => {
  // GET /items
  fastify.get(
    '/',
    { preHandler: [fastify.authenticate] },
    async (request: FastifyRequest) => {
      const { locationType, category, includeForecasts, limit, offset } = getItemsQuery.parse(request.query);

      // Build where clause
      const where: any = {};
      if (category) where.category = category;
      if (locationType) {
        const relation = locationRelations[locationType as LocationType];
        where[relation] = { some: {} };
      }

      // Build include clause
      const include: any = {
        box_bin_inventory: { select: { quantity: true } },
        rack_inventory: { select: { quantity: true } },
        cabinet_inventory: { select: { quantity: true } },
        single_claw_machine_inventory: { select: { quantity: true } },
        double_claw_machine_inventory: { select: { quantity: true } },
        keychain_machine_inventory: { select: { quantity: true } },
      };

      if (includeForecasts) {
        include.forecast_predictions = {
          orderBy: { computed_at: 'desc' },
          take: 1,
        };
      }

      const [dataRaw, totalRaw] = await Promise.all([
        prisma.products.findMany({
          where,
          take: limit,
          skip: offset,
          orderBy: { name: 'asc' },
          include,
        }),
        prisma.products.count({ where }),
      ]);

      const data = Array.isArray(dataRaw) ? (dataRaw as any[]) : [];
      const total = typeof totalRaw === 'number' ? totalRaw : 0;

      return {
        data: data.map((item) => {
          // Calculate total quantity across all locations
          const totalQuantity =
            sumQuantity((item as any).box_bin_inventory) +
            sumQuantity((item as any).rack_inventory) +
            sumQuantity((item as any).cabinet_inventory) +
            sumQuantity((item as any).single_claw_machine_inventory) +
            sumQuantity((item as any).double_claw_machine_inventory) +
            sumQuantity((item as any).keychain_machine_inventory);

          // Map forecast if included
          let forecast = null;
          if (includeForecasts && (item as any).forecast_predictions?.[0]) {
            const f = (item as any).forecast_predictions[0];
            const daysToStockout = decimalToNumber(f.days_to_stockout);
            forecast = {
              id: f.id,
              itemId: f.item_id,
              horizonDays: f.horizon_days,
              avgDailyDelta: decimalToNumber(f.avg_daily_delta),
              daysToStockout,
              suggestedReorderQty: f.suggested_reorder_qty,
              suggestedOrderDate: f.suggested_order_date?.toISOString().split('T')[0] ?? null,
              confidence: decimalToNumber(f.confidence),
              computedAt: f.computed_at.toISOString(),
              riskLevel: calculateRiskLevel(daysToStockout),
            };
          }

          return {
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
            forecast,
          };
        }),
        pagination: {
          total,
          limit,
          offset,
          hasMore: offset + limit < total,
        },
      };
    }
  );
};

export default itemRoutes;
