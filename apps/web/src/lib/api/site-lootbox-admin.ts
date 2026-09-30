import { apiGet, apiPost, apiPatch, apiDelete } from "./client";
import type {
  AdminCoinActivity,
  CoinAdjustment,
  CoinAdjustmentRequest,
  Lootbox,
  LootboxAdmin,
  LootboxPlay,
  LootboxPrize,
  LootboxTier,
  PageResponse,
  PlayerCoinRow,
  CoinStats,
  UpsertLootboxRequest,
  UpsertPrizeRequest,
  UpsertTierRequest,
  UserCoinProfile,
} from "@/types/lootbox";
import type { BulkUpdateTierProbabilitiesRequest } from "./lootbox";

// Every route here lives under /api/v1/sites/{siteId}/lootbox/admin/**, delegating server-side to
// the same LootboxAdminService/CoinAdminDashboardService methods as the legacy
// /api/lootbox/admin routes in ./lootbox.ts - v1 added no new request/response fields for this
// slice, only site scoping and server-derived siteId/actor (the backend no longer trusts a
// client-supplied siteId on create/update-crate, per SiteLootboxAdminController's Javadoc), so
// the existing @/types/lootbox interfaces are reused as-is (.specs/phase-7-kuji-lootbox T-4).
// coin-config/coin-economy has no v1 equivalent - coin_economy_config stays a single global row,
// not per-site - and stays on ./lootbox.ts's legacy route.

function basePath(siteId: string): string {
  return `/api/v1/sites/${siteId}/lootbox/admin`;
}

/** Admin catalog: ALL crates, active or not, including inactive tiers / prizes. */
export function getSiteAdminCatalog(siteId: string): Promise<Lootbox[]> {
  return apiGet<Lootbox[]>(`${basePath(siteId)}/catalog`);
}

// ----- Crate CRUD -----

export function listSiteCrates(siteId: string): Promise<LootboxAdmin[]> {
  return apiGet<LootboxAdmin[]>(`${basePath(siteId)}/crates`);
}

export function createSiteCrate(
  siteId: string,
  body: UpsertLootboxRequest
): Promise<LootboxAdmin> {
  return apiPost<LootboxAdmin, UpsertLootboxRequest>(`${basePath(siteId)}/crates`, body);
}

export function updateSiteCrate(
  siteId: string,
  id: string,
  body: UpsertLootboxRequest
): Promise<LootboxAdmin> {
  return apiPatch<LootboxAdmin, UpsertLootboxRequest>(`${basePath(siteId)}/crates/${id}`, body);
}

export function deleteSiteCrate(siteId: string, id: string): Promise<void> {
  return apiDelete<void>(`${basePath(siteId)}/crates/${id}`);
}

// ----- Tier / Prize CRUD -----

export function createSiteTier(
  siteId: string,
  body: UpsertTierRequest
): Promise<LootboxTier> {
  return apiPost<LootboxTier, UpsertTierRequest>(`${basePath(siteId)}/tiers`, body);
}

export function updateSiteTier(
  siteId: string,
  id: string,
  body: UpsertTierRequest
): Promise<LootboxTier> {
  return apiPatch<LootboxTier, UpsertTierRequest>(`${basePath(siteId)}/tiers/${id}`, body);
}

export function bulkUpdateSiteTierProbabilities(
  siteId: string,
  body: BulkUpdateTierProbabilitiesRequest
): Promise<LootboxTier[]> {
  return apiPost<LootboxTier[], BulkUpdateTierProbabilitiesRequest>(
    `${basePath(siteId)}/tiers/bulk-update`,
    body
  );
}

export function deleteSiteTier(siteId: string, id: string): Promise<void> {
  return apiDelete<void>(`${basePath(siteId)}/tiers/${id}`);
}

export function createSitePrize(
  siteId: string,
  body: UpsertPrizeRequest
): Promise<LootboxPrize> {
  return apiPost<LootboxPrize, UpsertPrizeRequest>(`${basePath(siteId)}/prizes`, body);
}

export function updateSitePrize(
  siteId: string,
  id: string,
  body: UpsertPrizeRequest
): Promise<LootboxPrize> {
  return apiPatch<LootboxPrize, UpsertPrizeRequest>(`${basePath(siteId)}/prizes/${id}`, body);
}

export function deleteSitePrize(siteId: string, id: string): Promise<void> {
  return apiDelete<void>(`${basePath(siteId)}/prizes/${id}`);
}

export function getSitePendingRedemptions(
  siteId: string,
  page = 0,
  size = 20,
  status: "WON" | "REDEEMED" = "WON"
): Promise<PageResponse<LootboxPlay>> {
  return apiGet<PageResponse<LootboxPlay>>(
    `${basePath(siteId)}/pending?page=${page}&size=${size}&status=${status}`
  );
}

export function markSiteRedeemed(siteId: string, playId: string): Promise<LootboxPlay> {
  return apiPost<LootboxPlay>(`${basePath(siteId)}/redeem/${playId}`);
}

export function adjustSiteCoins(
  siteId: string,
  body: CoinAdjustmentRequest
): Promise<CoinAdjustment> {
  return apiPost<CoinAdjustment, CoinAdjustmentRequest>(`${basePath(siteId)}/adjustments`, body);
}

export function getSiteUserProfile(siteId: string, userId: string): Promise<UserCoinProfile> {
  return apiGet<UserCoinProfile>(`${basePath(siteId)}/users/${userId}`);
}

// ----- Coins-tab dashboard -----

export function getSiteCoinStats(siteId: string): Promise<CoinStats> {
  return apiGet<CoinStats>(`${basePath(siteId)}/coin-stats`);
}

export function getSitePlayerCoinRows(
  siteId: string,
  search?: string
): Promise<PlayerCoinRow[]> {
  const qs = new URLSearchParams();
  if (search && search.trim().length > 0) qs.set("search", search.trim());
  const suffix = qs.toString();
  return apiGet<PlayerCoinRow[]>(
    suffix ? `${basePath(siteId)}/players?${suffix}` : `${basePath(siteId)}/players`
  );
}

export function getSiteAdminCoinActivity(
  siteId: string,
  limit = 20
): Promise<AdminCoinActivity[]> {
  return apiGet<AdminCoinActivity[]>(`${basePath(siteId)}/activity?limit=${limit}`);
}
