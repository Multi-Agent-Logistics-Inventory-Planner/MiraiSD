import { apiGet, apiPost, apiPatch, apiDelete } from "./client";
import type {
  CoinAdjustment,
  CoinAdjustmentRequest,
  CoinHistoryEntry,
  LootboxBalance,
  LootboxPlay,
  LootboxPrize,
  LootboxTier,
  PageResponse,
  PlayLootboxResponse,
  UpsertPrizeRequest,
  UpsertTierRequest,
  UserCoinProfile,
} from "@/types/lootbox";

const BASE = "/api/lootbox";
const ADMIN = `${BASE}/admin`;

// ---------- User-facing ----------

export function getBalance(): Promise<LootboxBalance> {
  return apiGet<LootboxBalance>(`${BASE}/balance`);
}

export function getCatalog(): Promise<LootboxTier[]> {
  return apiGet<LootboxTier[]>(`${BASE}/catalog`);
}

/**
 * Play one lootbox. Generates a fresh idempotency key per click so retries don't
 * double-charge if the response is lost in transit.
 */
export function playLootbox(): Promise<PlayLootboxResponse> {
  const idempotencyKey = crypto.randomUUID();
  return apiPost<PlayLootboxResponse>(`${BASE}/play`, undefined, {
    headers: { "Idempotency-Key": idempotencyKey },
  });
}

export function getMyPrizes(): Promise<LootboxPlay[]> {
  return apiGet<LootboxPlay[]>(`${BASE}/my-prizes`);
}

export function getMyHistory(): Promise<CoinHistoryEntry[]> {
  return apiGet<CoinHistoryEntry[]>(`${BASE}/my-history`);
}

// ---------- Admin ----------

export function getAdminCatalog(): Promise<LootboxTier[]> {
  return apiGet<LootboxTier[]>(`${ADMIN}/catalog`);
}

export function createTier(body: UpsertTierRequest): Promise<LootboxTier> {
  return apiPost<LootboxTier, UpsertTierRequest>(`${ADMIN}/tiers`, body);
}

export function updateTier(id: string, body: UpsertTierRequest): Promise<LootboxTier> {
  return apiPatch<LootboxTier, UpsertTierRequest>(`${ADMIN}/tiers/${id}`, body);
}

export interface BulkUpdateTierProbabilitiesRequest {
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
  size = 20
): Promise<PageResponse<LootboxPlay>> {
  return apiGet<PageResponse<LootboxPlay>>(`${ADMIN}/pending?page=${page}&size=${size}`);
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
