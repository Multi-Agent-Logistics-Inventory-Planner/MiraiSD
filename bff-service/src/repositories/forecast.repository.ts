import type { PrismaClient } from '@prisma/client';

export class ForecastRepository {
  constructor(private prisma: PrismaClient) {}

  async findLatestByItemId(itemId: string) {
    return this.prisma.forecast_predictions.findFirst({
      where: { item_id: itemId },
      orderBy: { computed_at: 'desc' },
      include: {
        products: {
          select: {
            id: true,
            sku: true,
            name: true,
            category: true,
          },
        },
      },
    });
  }

  async findAtRisk(threshold: number, limit: number, offset: number) {
    const where = {
      days_to_stockout: { lte: threshold },
    };

    const [data, total] = await Promise.all([
      this.prisma.forecast_predictions.findMany({
        where,
        orderBy: { days_to_stockout: 'asc' },
        take: limit,
        skip: offset,
        include: {
          products: {
            select: {
              id: true,
              sku: true,
              name: true,
              category: true,
            },
          },
        },
      }),
      this.prisma.forecast_predictions.count({ where }),
    ]);

    return { data, total };
  }

  async countAtRisk(threshold: number) {
    return this.prisma.forecast_predictions.count({
      where: {
        days_to_stockout: { lte: threshold },
      },
    });
  }

  async countCritical(threshold: number) {
    return this.prisma.forecast_predictions.count({
      where: {
        days_to_stockout: { lte: threshold },
      },
    });
  }
}
