"use client";

import { useQuery, skipToken } from "@tanstack/react-query";
import {
  getSiteAdminCatalog,
  getSiteAdminCoinActivity,
  getSiteCoinStats,
  getSitePendingRedemptions,
  getSitePlayerCoinRows,
  getSiteUserProfile,
  listSiteCrates,
} from "@/lib/api/site-lootbox-admin";
import {
  getMySiteHistory,
  getMySitePrizes,
  getRecentSitePlays,
  getSiteBalance,
  getSiteCatalog,
  getSiteWalletBreakdown,
} from "@/lib/api/site-lootbox";
import { getCoinEconomyConfig } from "@/lib/api/lootbox";
import { useCurrentSite } from "@/hooks/queries/use-current-site";

// coin-config/coin-economy has no v1 site-scoped equivalent (coin_economy_config is a single
// global row, not per-site - see ./site-lootbox-admin.ts's top comment) and stays on the legacy
// route/key. Every other key below is site-qualified (siteId is always the second element).

export const lootboxKeys = {
  balance: (siteId: string | undefined) => ["lootbox", "balance", siteId] as const,
  walletBreakdown: (siteId: string | undefined) =>
    ["lootbox", "wallet-breakdown", siteId] as const,
  catalog: (siteId: string | undefined) => ["lootbox", "catalog", siteId] as const,
  adminCatalog: (siteId: string | undefined) =>
    ["lootbox", "admin", "catalog", siteId] as const,
  adminCrates: (siteId: string | undefined) =>
    ["lootbox", "admin", "crates", siteId] as const,
  myPrizes: (siteId: string | undefined) => ["lootbox", "my-prizes", siteId] as const,
  myHistory: (siteId: string | undefined) => ["lootbox", "my-history", siteId] as const,
  recent: (siteId: string | undefined, limit: number, crateId?: string) =>
    ["lootbox", "recent", siteId, limit, crateId ?? null] as const,
  pending: (siteId: string | undefined, status: "WON" | "REDEEMED", page: number, size: number) =>
    ["lootbox", "admin", "pending", siteId, status, page, size] as const,
  userProfile: (siteId: string | undefined, userId: string) =>
    ["lootbox", "admin", "user", siteId, userId] as const,
  coinEconomyConfig: ["lootbox", "admin", "coin-config"] as const,
  coinStats: (siteId: string | undefined) => ["lootbox", "admin", "coin-stats", siteId] as const,
  players: (siteId: string | undefined, search: string) =>
    ["lootbox", "admin", "players", siteId, search] as const,
  activity: (siteId: string | undefined, limit: number) =>
    ["lootbox", "admin", "activity", siteId, limit] as const,
};

export function useLootboxWalletBreakdown() {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.walletBreakdown(siteId),
    queryFn: siteId ? () => getSiteWalletBreakdown(siteId) : skipToken,
    staleTime: 30 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

export function useLootboxAdminCrates() {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.adminCrates(siteId),
    queryFn: siteId ? () => listSiteCrates(siteId) : skipToken,
    staleTime: 60 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

export function useLootboxBalance() {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.balance(siteId),
    queryFn: siteId ? () => getSiteBalance(siteId) : skipToken,
    staleTime: 60 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

export function useLootboxCatalog() {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.catalog(siteId),
    queryFn: siteId ? () => getSiteCatalog(siteId) : skipToken,
    staleTime: 5 * 60 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

export function useLootboxAdminCatalog() {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.adminCatalog(siteId),
    queryFn: siteId ? () => getSiteAdminCatalog(siteId) : skipToken,
    staleTime: 60 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

export function useMyPrizes() {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.myPrizes(siteId),
    queryFn: siteId ? () => getMySitePrizes(siteId) : skipToken,
    staleTime: 60 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

export function useMyCoinHistory() {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.myHistory(siteId),
    queryFn: siteId ? () => getMySiteHistory(siteId) : skipToken,
    staleTime: 60 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

export function useRecentLootboxPlays(limit = 20, crateId?: string) {
  // No refetchInterval: usePlayLootboxMutation invalidates this key on every spin,
  // and default focus/mount refetch (subject to the 60s staleness dedupe) surfaces
  // other players' drops without a per-tab background poll.
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.recent(siteId, limit, crateId),
    queryFn: siteId ? () => getRecentSitePlays(siteId, limit, crateId) : skipToken,
    staleTime: 60 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

export function useAdminPendingPrizes(
  page = 0,
  size = 20,
  status: "WON" | "REDEEMED" = "WON"
) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.pending(siteId, status, page, size),
    queryFn: siteId ? () => getSitePendingRedemptions(siteId, page, size, status) : skipToken,
    staleTime: 30 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

/** coin_economy_config is a single global row, not per-site - stays on the legacy route. */
export function useCoinEconomyConfig() {
  return useQuery({
    queryKey: lootboxKeys.coinEconomyConfig,
    queryFn: getCoinEconomyConfig,
    staleTime: 60 * 1000,
  });
}

export function useCoinStats() {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.coinStats(siteId),
    queryFn: siteId ? () => getSiteCoinStats(siteId) : skipToken,
    staleTime: 30 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

/**
 * Player-balance rows for the admin Coins tab. `search` is part of the cache key
 * even though current scale filters client-side — when the server starts honoring
 * `?search=`, each search term will get its own cache entry without code changes.
 */
export function usePlayerCoinRows(search: string = "") {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.players(siteId, search),
    queryFn: siteId ? () => getSitePlayerCoinRows(siteId, search || undefined) : skipToken,
    staleTime: 30 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

export function useAdminCoinActivity(limit = 20) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: lootboxKeys.activity(siteId, limit),
    queryFn: siteId ? () => getSiteAdminCoinActivity(siteId, limit) : skipToken,
    staleTime: 30 * 1000,
  });
  return { ...query, isLoading: isSiteLoading || query.isLoading, error: siteError ?? query.error };
}

export function useAdminUserProfile(userId: string | undefined) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();
  const query = useQuery({
    queryKey: userId
      ? lootboxKeys.userProfile(siteId, userId)
      : ["lootbox", "admin", "user", siteId, "none"],
    queryFn: siteId && userId ? () => getSiteUserProfile(siteId, userId) : skipToken,
    staleTime: 30 * 1000,
  });
  return {
    ...query,
    isLoading: (Boolean(userId) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}
