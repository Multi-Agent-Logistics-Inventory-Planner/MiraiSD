import { apiGet, apiPost } from "./client";
import type {
  CoinHistoryEntry,
  Lootbox,
  LootboxBalance,
  LootboxPlay,
  PlayLootboxResponse,
  RecentLootboxPlay,
  WalletBreakdown,
} from "@/types/lootbox";

// Every route here lives under /api/v1/sites/{siteId}/lootbox/**, delegating server-side to the
// same LootboxService methods as the legacy /api/lootbox routes in ./lootbox.ts - v1 added no new
// request/response fields for this slice, only site scoping, so the existing @/types/lootbox
// interfaces are reused as-is rather than introducing a parallel generated-client mapping layer
// (.specs/phase-7-kuji-lootbox T-4). coin-config/coin-economy has no v1 equivalent -
// coin_economy_config stays a single global row, not per-site - and stays on ./lootbox.ts's
// legacy route.

function basePath(siteId: string): string {
  return `/api/v1/sites/${siteId}/lootbox`;
}

export function getSiteBalance(siteId: string): Promise<LootboxBalance> {
  return apiGet<LootboxBalance>(`${basePath(siteId)}/balance`);
}

export function getSiteWalletBreakdown(siteId: string): Promise<WalletBreakdown> {
  return apiGet<WalletBreakdown>(`${basePath(siteId)}/wallet/breakdown`);
}

/** Player-facing catalog: open crates only, with active tiers + prizes inside. */
export function getSiteCatalog(siteId: string): Promise<Lootbox[]> {
  return apiGet<Lootbox[]>(`${basePath(siteId)}/catalog`);
}

/**
 * Open a specific crate at this site. Generates a fresh idempotency key per click so retries
 * don't double-charge if the response is lost in transit.
 */
export function playSiteLootbox(
  siteId: string,
  crateId: string
): Promise<PlayLootboxResponse> {
  const idempotencyKey = crypto.randomUUID();
  return apiPost<PlayLootboxResponse, { crateId: string }>(
    `${basePath(siteId)}/play`,
    { crateId },
    { headers: { "Idempotency-Key": idempotencyKey } }
  );
}

export function getMySitePrizes(siteId: string): Promise<LootboxPlay[]> {
  return apiGet<LootboxPlay[]>(`${basePath(siteId)}/my-prizes`);
}

export function getMySiteHistory(siteId: string): Promise<CoinHistoryEntry[]> {
  return apiGet<CoinHistoryEntry[]>(`${basePath(siteId)}/my-history`);
}

export function getRecentSitePlays(
  siteId: string,
  limit = 20,
  crateId?: string
): Promise<RecentLootboxPlay[]> {
  const qs = new URLSearchParams({ limit: String(limit) });
  if (crateId) qs.set("crateId", crateId);
  return apiGet<RecentLootboxPlay[]>(`${basePath(siteId)}/recent?${qs.toString()}`);
}
