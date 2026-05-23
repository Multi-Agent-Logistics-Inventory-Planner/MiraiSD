"use client";

import { useQuery } from "@tanstack/react-query";
import {
  getAdminCatalog,
  getBalance,
  getCatalog,
  getCoinEconomyConfig,
  getMyHistory,
  getMyPrizes,
  getPendingRedemptions,
  getRecentPlays,
  getUserProfile,
  getWalletBreakdown,
  listCrates,
} from "@/lib/api/lootbox";

export const lootboxKeys = {
  balance: ["lootbox", "balance"] as const,
  walletBreakdown: ["lootbox", "wallet-breakdown"] as const,
  catalog: ["lootbox", "catalog"] as const,
  adminCatalog: ["lootbox", "admin", "catalog"] as const,
  adminCrates: ["lootbox", "admin", "crates"] as const,
  myPrizes: ["lootbox", "my-prizes"] as const,
  myHistory: ["lootbox", "my-history"] as const,
  recent: (limit: number, crateId?: string) =>
    ["lootbox", "recent", limit, crateId ?? null] as const,
  pending: (status: "WON" | "REDEEMED", page: number, size: number) =>
    ["lootbox", "admin", "pending", status, page, size] as const,
  userProfile: (userId: string) => ["lootbox", "admin", "user", userId] as const,
  coinEconomyConfig: ["lootbox", "admin", "coin-config"] as const,
};

export function useLootboxWalletBreakdown() {
  return useQuery({
    queryKey: lootboxKeys.walletBreakdown,
    queryFn: getWalletBreakdown,
    staleTime: 30 * 1000,
  });
}

export function useLootboxAdminCrates() {
  return useQuery({
    queryKey: lootboxKeys.adminCrates,
    queryFn: listCrates,
    staleTime: 60 * 1000,
  });
}

export function useLootboxBalance() {
  return useQuery({
    queryKey: lootboxKeys.balance,
    queryFn: getBalance,
    staleTime: 60 * 1000,
  });
}

export function useLootboxCatalog() {
  return useQuery({
    queryKey: lootboxKeys.catalog,
    queryFn: getCatalog,
    staleTime: 5 * 60 * 1000,
  });
}

export function useLootboxAdminCatalog() {
  return useQuery({
    queryKey: lootboxKeys.adminCatalog,
    queryFn: getAdminCatalog,
    staleTime: 60 * 1000,
  });
}

export function useMyPrizes() {
  return useQuery({
    queryKey: lootboxKeys.myPrizes,
    queryFn: getMyPrizes,
    staleTime: 60 * 1000,
  });
}

export function useMyCoinHistory() {
  return useQuery({
    queryKey: lootboxKeys.myHistory,
    queryFn: getMyHistory,
    staleTime: 60 * 1000,
  });
}

export function useRecentLootboxPlays(limit = 20, crateId?: string) {
  // No refetchInterval: usePlayLootboxMutation invalidates this key on every spin,
  // and default focus/mount refetch (subject to the 60s staleness dedupe) surfaces
  // other players' drops without a per-tab background poll.
  return useQuery({
    queryKey: lootboxKeys.recent(limit, crateId),
    queryFn: () => getRecentPlays(limit, crateId),
    staleTime: 60 * 1000,
  });
}

export function useAdminPendingPrizes(
  page = 0,
  size = 20,
  status: "WON" | "REDEEMED" = "WON"
) {
  return useQuery({
    queryKey: lootboxKeys.pending(status, page, size),
    queryFn: () => getPendingRedemptions(page, size, status),
    staleTime: 30 * 1000,
  });
}

export function useCoinEconomyConfig() {
  return useQuery({
    queryKey: lootboxKeys.coinEconomyConfig,
    queryFn: getCoinEconomyConfig,
    staleTime: 60 * 1000,
  });
}

export function useAdminUserProfile(userId: string | undefined) {
  return useQuery({
    queryKey: userId ? lootboxKeys.userProfile(userId) : ["lootbox", "admin", "user", "none"],
    queryFn: () => getUserProfile(userId as string),
    enabled: !!userId,
    staleTime: 30 * 1000,
  });
}
