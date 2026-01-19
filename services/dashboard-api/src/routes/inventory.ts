import type { FastifyPluginAsync } from 'fastify';
import { prisma } from '../lib/prisma.js';

interface NormalizedAggregate {
  count: number;
  quantity: number;
}

function normalizeAggregate(value: unknown): NormalizedAggregate {
  const v = value as { _count?: { id?: number }; _sum?: { quantity?: number | null } } | null;
  return {
    count: typeof v?._count?.id === 'number' ? v._count.id : 0,
    quantity: typeof v?._sum?.quantity === 'number' ? v._sum.quantity : 0,
  };
}

const inventoryRoutes: FastifyPluginAsync = async (fastify) => {
  // GET /inventory/summary
  fastify.get('/summary', { preHandler: [fastify.authenticate] }, async () => {
    const [
      boxBinsRaw,
      racksRaw,
      cabinetsRaw,
      singleClawRaw,
      doubleClawRaw,
      keychainRaw,
      atRiskCount,
      criticalCount,
    ] = await Promise.all([
      prisma.box_bin_inventory.aggregate({ _count: { id: true }, _sum: { quantity: true } }),
      prisma.rack_inventory.aggregate({ _count: { id: true }, _sum: { quantity: true } }),
      prisma.cabinet_inventory.aggregate({ _count: { id: true }, _sum: { quantity: true } }),
      prisma.single_claw_machine_inventory.aggregate({ _count: { id: true }, _sum: { quantity: true } }),
      prisma.double_claw_machine_inventory.aggregate({ _count: { id: true }, _sum: { quantity: true } }),
      prisma.keychain_machine_inventory.aggregate({ _count: { id: true }, _sum: { quantity: true } }),
      prisma.forecast_predictions.count({ where: { days_to_stockout: { lte: 7 } } }),
      prisma.forecast_predictions.count({ where: { days_to_stockout: { lte: 3 } } }),
    ]);

    const boxBins = normalizeAggregate(boxBinsRaw);
    const racks = normalizeAggregate(racksRaw);
    const cabinets = normalizeAggregate(cabinetsRaw);
    const singleClaw = normalizeAggregate(singleClawRaw);
    const doubleClaw = normalizeAggregate(doubleClawRaw);
    const keychain = normalizeAggregate(keychainRaw);

    const totalItems =
      boxBins.count +
      racks.count +
      cabinets.count +
      singleClaw.count +
      doubleClaw.count +
      keychain.count;

    const totalQuantity =
      boxBins.quantity +
      racks.quantity +
      cabinets.quantity +
      singleClaw.quantity +
      doubleClaw.quantity +
      keychain.quantity;

    return {
      totalItems,
      totalQuantity,
      atRiskCount: typeof atRiskCount === 'number' ? atRiskCount : 0,
      criticalCount: typeof criticalCount === 'number' ? criticalCount : 0,
      byLocation: {
        boxBins: { itemCount: boxBins.count, totalQuantity: boxBins.quantity },
        racks: { itemCount: racks.count, totalQuantity: racks.quantity },
        cabinets: { itemCount: cabinets.count, totalQuantity: cabinets.quantity },
        singleClawMachines: { itemCount: singleClaw.count, totalQuantity: singleClaw.quantity },
        doubleClawMachines: { itemCount: doubleClaw.count, totalQuantity: doubleClaw.quantity },
        keychainMachines: { itemCount: keychain.count, totalQuantity: keychain.quantity },
      },
      lastUpdated: new Date().toISOString(),
    };
  });
};

export default inventoryRoutes;
