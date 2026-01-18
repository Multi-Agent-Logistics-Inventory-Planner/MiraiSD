import type { PrismaClient } from '@prisma/client';

export type LocationType =
  | 'box_bin'
  | 'rack'
  | 'cabinet'
  | 'single_claw_machine'
  | 'double_claw_machine'
  | 'keychain_machine';

export interface ItemFilters {
  locationType?: LocationType;
  category?: string;
  includeForecasts?: boolean;
  limit: number;
  offset: number;
}

export class ItemRepository {
  constructor(private prisma: PrismaClient) {}

  async findItems(filters: ItemFilters) {
    const { category, includeForecasts, limit, offset } = filters;

    const where = category ? { category } : {};

    const [data, total] = await Promise.all([
      this.prisma.products.findMany({
        where,
        take: limit,
        skip: offset,
        orderBy: { name: 'asc' },
        include: includeForecasts ? {
          forecast_predictions: {
            orderBy: { computed_at: 'desc' },
            take: 1,
          },
          box_bin_inventory: { select: { quantity: true } },
          rack_inventory: { select: { quantity: true } },
          cabinet_inventory: { select: { quantity: true } },
          single_claw_machine_inventory: { select: { quantity: true } },
          double_claw_machine_inventory: { select: { quantity: true } },
          keychain_machine_inventory: { select: { quantity: true } },
        } : {
          box_bin_inventory: { select: { quantity: true } },
          rack_inventory: { select: { quantity: true } },
          cabinet_inventory: { select: { quantity: true } },
          single_claw_machine_inventory: { select: { quantity: true } },
          double_claw_machine_inventory: { select: { quantity: true } },
          keychain_machine_inventory: { select: { quantity: true } },
        },
      }),
      this.prisma.products.count({ where }),
    ]);

    return { data, total };
  }

  async findItemsByLocationType(locationType: LocationType, filters: ItemFilters) {
    const { category, includeForecasts, limit, offset } = filters;

    // Map location type to the appropriate inventory relation
    const locationRelations = {
      box_bin: 'box_bin_inventory',
      rack: 'rack_inventory',
      cabinet: 'cabinet_inventory',
      single_claw_machine: 'single_claw_machine_inventory',
      double_claw_machine: 'double_claw_machine_inventory',
      keychain_machine: 'keychain_machine_inventory',
    } as const;

    const relationName = locationRelations[locationType];

    const where: any = {
      [relationName]: { some: {} },
    };

    if (category) {
      where.category = category;
    }

    const [data, total] = await Promise.all([
      this.prisma.products.findMany({
        where,
        take: limit,
        skip: offset,
        orderBy: { name: 'asc' },
        include: {
          ...(includeForecasts && {
            forecast_predictions: {
              orderBy: { computed_at: 'desc' },
              take: 1,
            },
          }),
          [relationName]: { select: { quantity: true } },
        },
      }),
      this.prisma.products.count({ where }),
    ]);

    return { data, total, locationType };
  }
}
