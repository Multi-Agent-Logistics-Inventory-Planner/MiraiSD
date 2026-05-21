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
  recent: (limit: number) => ["lootbox", "recent", limit] as const,
  pending: (page: number, size: number) =>
    ["lootbox", "admin", "pending", page, size] as const,
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
    staleTime: 30 * 1000,
  });
}

export function useLootboxBalance() {
  return useQuery({
    queryKey: lootboxKeys.balance,
    queryFn: getBalance,
    staleTime: 30 * 1000,
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
    staleTime: 30 * 1000,
  });
}

export function useMyPrizes() {
  return useQuery({
    queryKey: lootboxKeys.myPrizes,
    queryFn: getMyPrizes,
    staleTime: 30 * 1000,
  });
}

export function useMyCoinHistory() {
  return useQuery({
    queryKey: lootboxKeys.myHistory,
    queryFn: getMyHistory,
    staleTime: 30 * 1000,
  });
}

export function useRecentLootboxPlays(limit = 20) {
  return useQuery({
    queryKey: lootboxKeys.recent(limit),
    queryFn: () => getRecentPlays(limit),
    staleTime: 30 * 1000,
    refetchInterval: 30 * 1000,
  });
}

export function useAdminPendingPrizes(page = 0, size = 20) {
  return useQuery({
    queryKey: lootboxKeys.pending(page, size),
    queryFn: () => getPendingRedemptions(page, size),
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
