import { apiGet, apiPost, apiPut, apiPatch, apiDelete } from "./client";
import type {
  CoinAdjustment,
  CoinAdjustmentRequest,
  CoinEconomyConfig,
  CoinHistoryEntry,
  Lootbox,
  LootboxAdmin,
  LootboxBalance,
  LootboxPlay,
  LootboxPrize,
  LootboxTier,
  PageResponse,
  PlayLootboxResponse,
  RecentLootboxPlay,
  UpdateCoinEconomyConfigRequest,
  UpsertLootboxRequest,
  UpsertPrizeRequest,
  UpsertTierRequest,
  UserCoinProfile,
  WalletBreakdown,
} from "@/types/lootbox";

const BASE = "/api/lootbox";
const ADMIN = `${BASE}/admin`;

// ---------- User-facing ----------

export function getBalance(): Promise<LootboxBalance> {
  return apiGet<LootboxBalance>(`${BASE}/balance`);
}

export function getWalletBreakdown(): Promise<WalletBreakdown> {
  return apiGet<WalletBreakdown>(`${BASE}/wallet/breakdown`);
}

/** Player-facing catalog: open crates only, with active tiers + prizes inside. */
export function getCatalog(): Promise<Lootbox[]> {
  return apiGet<Lootbox[]>(`${BASE}/catalog`);
}

/**
 * Open a specific crate. Generates a fresh idempotency key per click so retries don't
 * double-charge if the response is lost in transit.
 */
export function playLootbox(crateId: string): Promise<PlayLootboxResponse> {
  const idempotencyKey = crypto.randomUUID();
  return apiPost<PlayLootboxResponse, { crateId: string }>(
    `${BASE}/play`,
    { crateId },
    { headers: { "Idempotency-Key": idempotencyKey } }
  );
}

export function getMyPrizes(): Promise<LootboxPlay[]> {
  return apiGet<LootboxPlay[]>(`${BASE}/my-prizes`);
}

export function getMyHistory(): Promise<CoinHistoryEntry[]> {
  return apiGet<CoinHistoryEntry[]>(`${BASE}/my-history`);
}

export function getRecentPlays(
  limit = 20,
  crateId?: string
): Promise<RecentLootboxPlay[]> {
  const qs = new URLSearchParams({ limit: String(limit) });
  if (crateId) qs.set("crateId", crateId);
  return apiGet<RecentLootboxPlay[]>(`${BASE}/recent?${qs.toString()}`);
}

// ---------- Admin ----------

/** Admin catalog: ALL crates, active or not, including inactive tiers / prizes. */
export function getAdminCatalog(): Promise<Lootbox[]> {
  return apiGet<Lootbox[]>(`${ADMIN}/catalog`);
}

// ----- Crate CRUD -----

export function listCrates(): Promise<LootboxAdmin[]> {
  return apiGet<LootboxAdmin[]>(`${ADMIN}/crates`);
}

export function createCrate(body: UpsertLootboxRequest): Promise<LootboxAdmin> {
  return apiPost<LootboxAdmin, UpsertLootboxRequest>(`${ADMIN}/crates`, body);
}

export function updateCrate(id: string, body: UpsertLootboxRequest): Promise<LootboxAdmin> {
  return apiPatch<LootboxAdmin, UpsertLootboxRequest>(`${ADMIN}/crates/${id}`, body);
}

export function deleteCrate(id: string): Promise<void> {
  return apiDelete<void>(`${ADMIN}/crates/${id}`);
}

// ----- Tier / Prize CRUD -----

export function createTier(body: UpsertTierRequest): Promise<LootboxTier> {
  return apiPost<LootboxTier, UpsertTierRequest>(`${ADMIN}/tiers`, body);
}

export function updateTier(id: string, body: UpsertTierRequest): Promise<LootboxTier> {
  return apiPatch<LootboxTier, UpsertTierRequest>(`${ADMIN}/tiers/${id}`, body);
}

export interface BulkUpdateTierProbabilitiesRequest {
  lootboxId: string;
  tiers: Array<{ id: string; probabilityPct: number }>;
}

export function bulkUpdateTierProbabilities(
  body: BulkUpdateTierProbabilitiesRequest
): Promise<LootboxTier[]> {
  return apiPost<LootboxTier[], BulkUpdateTierProbabilitiesRequest>(
    `${ADMIN}/tiers/bulk-update`,
    body
  );
}

export function deleteTier(id: string): Promise<void> {
  return apiDelete<void>(`${ADMIN}/tiers/${id}`);
}

export function createPrize(body: UpsertPrizeRequest): Promise<LootboxPrize> {
  return apiPost<LootboxPrize, UpsertPrizeRequest>(`${ADMIN}/prizes`, body);
}

export function updatePrize(id: string, body: UpsertPrizeRequest): Promise<LootboxPrize> {
  return apiPatch<LootboxPrize, UpsertPrizeRequest>(`${ADMIN}/prizes/${id}`, body);
}

export function deletePrize(id: string): Promise<void> {
  return apiDelete<void>(`${ADMIN}/prizes/${id}`);
}

export function getPendingRedemptions(
  page = 0,
  size = 20,
  status: "WON" | "REDEEMED" = "WON"
): Promise<PageResponse<LootboxPlay>> {
  return apiGet<PageResponse<LootboxPlay>>(
    `${ADMIN}/pending?page=${page}&size=${size}&status=${status}`
  );
}

export function markRedeemed(playId: string): Promise<LootboxPlay> {
  return apiPost<LootboxPlay>(`${ADMIN}/redeem/${playId}`);
}

export function adjustCoins(body: CoinAdjustmentRequest): Promise<CoinAdjustment> {
  return apiPost<CoinAdjustment, CoinAdjustmentRequest>(`${ADMIN}/adjustments`, body);
}

export function getUserProfile(userId: string): Promise<UserCoinProfile> {
  return apiGet<UserCoinProfile>(`${ADMIN}/users/${userId}`);
}

// ----- Coin economy config -----

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
