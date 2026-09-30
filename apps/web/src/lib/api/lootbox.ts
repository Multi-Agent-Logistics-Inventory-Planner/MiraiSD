import { apiGet, apiPut } from "./client";
import type {
  CoinEconomyConfig,
  UpdateCoinEconomyConfigRequest,
} from "@/types/lootbox";

// Legacy client for the two lootbox admin routes with no v1 site-scoped equivalent:
// coin_economy_config is a single global/org-wide row, not per-site (see the T-2 site-ownership
// decision in .specs/phase-7-kuji-lootbox/log.md), so exposing it under /sites/{siteId}/ would
// falsely imply a per-site rate. Every other lootbox route migrated to ./site-lootbox.ts /
// ./site-lootbox-admin.ts in T-4's web migration.
const ADMIN = "/api/lootbox/admin";

export function getCoinEconomyConfig(): Promise<CoinEconomyConfig> {
  return apiGet<CoinEconomyConfig>(`${ADMIN}/coin-config`);
}

export function updateCoinEconomyConfig(
  body: UpdateCoinEconomyConfigRequest
): Promise<CoinEconomyConfig> {
  return apiPut<CoinEconomyConfig, UpdateCoinEconomyConfigRequest>(
    `${ADMIN}/coin-config`,
    body
  );
}

/** Shared with ./site-lootbox-admin.ts's bulkUpdateSiteTierProbabilities. */
export interface BulkUpdateTierProbabilitiesRequest {
  lootboxId: string;
  tiers: Array<{ id: string; probabilityPct: number }>;
}
