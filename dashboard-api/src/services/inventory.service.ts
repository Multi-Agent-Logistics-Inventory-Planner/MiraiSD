import type { PrismaClient } from '@prisma/client';
import { InventoryRepository } from '../repositories/inventory.repository.js';
import type { InventorySummaryDTO, LocationStats } from '../types/dto.js';

export class InventoryService {
  private repository: InventoryRepository;

  constructor(prisma: PrismaClient) {
    this.repository = new InventoryRepository(prisma);
  }

  async getSummary(): Promise<InventorySummaryDTO> {
    const [
      boxBin,
      rack,
      cabinet,
      singleClaw,
      doubleClaw,
      keychain,
      atRiskCount,
      criticalCount,
    ] = await Promise.all([
      this.repository.aggregateBoxBinInventory(),
      this.repository.aggregateRackInventory(),
      this.repository.aggregateCabinetInventory(),
      this.repository.aggregateSingleClawMachineInventory(),
      this.repository.aggregateDoubleClawMachineInventory(),
      this.repository.aggregateKeychainMachineInventory(),
      this.repository.countAtRiskForecasts(7),
      this.repository.countCriticalForecasts(3),
    ]);

    const byLocation = {
      boxBins: this.mapToLocationStats(boxBin),
      racks: this.mapToLocationStats(rack),
      cabinets: this.mapToLocationStats(cabinet),
      singleClawMachines: this.mapToLocationStats(singleClaw),
      doubleClawMachines: this.mapToLocationStats(doubleClaw),
      keychainMachines: this.mapToLocationStats(keychain),
    };

    const totalItems = Object.values(byLocation).reduce((sum, loc) => sum + loc.itemCount, 0);
    const totalQuantity = Object.values(byLocation).reduce((sum, loc) => sum + loc.totalQuantity, 0);

    return {
      totalItems,
      totalQuantity,
      atRiskCount,
      criticalCount,
      byLocation,
      lastUpdated: new Date().toISOString(),
    };
  }

  private mapToLocationStats(aggregate: {
    _count: { id: number };
    _sum: { quantity: number | null };
  }): LocationStats {
    return {
      itemCount: aggregate._count.id,
      totalQuantity: aggregate._sum.quantity ?? 0,
    };
  }
}
