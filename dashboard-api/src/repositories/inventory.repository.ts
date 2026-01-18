import type { PrismaClient } from '@prisma/client';

export interface AggregateResult {
  _count: { id: number };
  _sum: { quantity: number | null };
}

export class InventoryRepository {
  constructor(private prisma: PrismaClient) {}

  async aggregateBoxBinInventory(): Promise<AggregateResult> {
    return this.prisma.box_bin_inventory.aggregate({
      _count: { id: true },
      _sum: { quantity: true },
    });
  }

  async aggregateRackInventory(): Promise<AggregateResult> {
    return this.prisma.rack_inventory.aggregate({
      _count: { id: true },
      _sum: { quantity: true },
    });
  }

  async aggregateCabinetInventory(): Promise<AggregateResult> {
    return this.prisma.cabinet_inventory.aggregate({
      _count: { id: true },
      _sum: { quantity: true },
    });
  }

  async aggregateSingleClawMachineInventory(): Promise<AggregateResult> {
    return this.prisma.single_claw_machine_inventory.aggregate({
      _count: { id: true },
      _sum: { quantity: true },
    });
  }

  async aggregateDoubleClawMachineInventory(): Promise<AggregateResult> {
    return this.prisma.double_claw_machine_inventory.aggregate({
      _count: { id: true },
      _sum: { quantity: true },
    });
  }

  async aggregateKeychainMachineInventory(): Promise<AggregateResult> {
    return this.prisma.keychain_machine_inventory.aggregate({
      _count: { id: true },
      _sum: { quantity: true },
    });
  }

  async countAtRiskForecasts(threshold: number): Promise<number> {
    return this.prisma.forecast_predictions.count({
      where: {
        days_to_stockout: { lte: threshold },
      },
    });
  }

  async countCriticalForecasts(threshold: number): Promise<number> {
    return this.prisma.forecast_predictions.count({
      where: {
        days_to_stockout: { lte: threshold },
      },
    });
  }
}
