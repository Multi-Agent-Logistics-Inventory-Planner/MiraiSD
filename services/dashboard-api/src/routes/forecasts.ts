import type { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { prisma } from '../lib/prisma.js';
import type { RiskLevel } from '../types.js';

function decimalToNumber(value: unknown): number | null {
  if (value === null || value === undefined) return null;
  return Number(value);
}

function calculateRiskLevel(daysToStockout: number | null): RiskLevel {
  if (daysToStockout === null) return 'normal';
  if (daysToStockout <= 3) return 'critical';
  if (daysToStockout <= 7) return 'warning';
  return 'normal';
}

const getForecastParams = z.object({
  itemId: z.string().uuid(),
});

const getAtRiskQuery = z.object({
  threshold: z.coerce.number().int().positive().default(7),
  limit: z.coerce.number().int().min(1).max(100).default(50),
  offset: z.coerce.number().int().min(0).default(0),
});

const forecastRoutes: FastifyPluginAsync = async (fastify) => {
  // GET /forecasts/:itemId
  fastify.get('/:itemId', { preHandler: [fastify.authenticate] }, async (request, reply) => {
    const { itemId } = getForecastParams.parse(request.params);

    const forecast = await prisma.forecast_predictions.findFirst({
      where: { item_id: itemId },
      orderBy: { computed_at: 'desc' },
      include: {
        products: {
          select: { id: true, sku: true, name: true, category: true },
        },
      },
    });

    if (!forecast) {
      return reply.status(404).send({
        timestamp: new Date().toISOString(),
        status: 404,
        error: 'Not Found',
        message: `Forecast not found for item ${itemId}`,
      });
    }

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
  });

  // GET /forecasts/at-risk
  fastify.get('/at-risk', { preHandler: [fastify.authenticate] }, async (request) => {
    const { threshold, limit, offset } = getAtRiskQuery.parse(request.query);

    const where = { days_to_stockout: { lte: threshold } };

    const [dataRaw, totalRaw] = await Promise.all([
      prisma.forecast_predictions.findMany({
        where,
        orderBy: { days_to_stockout: 'asc' },
        take: limit,
        skip: offset,
        include: {
          products: {
            select: { id: true, sku: true, name: true, category: true },
          },
        },
      }),
      prisma.forecast_predictions.count({ where }),
    ]);

    const data = Array.isArray(dataRaw) ? (dataRaw as any[]) : [];
    const total = typeof totalRaw === 'number' ? totalRaw : 0;

    return {
      data: data.map((forecast) => {
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
      }),
      pagination: {
        total,
        limit,
        offset,
        hasMore: offset + limit < total,
      },
    };
  });
};

export default forecastRoutes;
