"use client";

import { useQuery } from "@tanstack/react-query";
import {
  getAdminCatalog,
  getBalance,
  getCatalog,
  getMyHistory,
  getMyPrizes,
  getPendingRedemptions,
  getUserProfile,
} from "@/lib/api/lootbox";

export const lootboxKeys = {
  balance: ["lootbox", "balance"] as const,
  catalog: ["lootbox", "catalog"] as const,
  adminCatalog: ["lootbox", "admin", "catalog"] as const,
  myPrizes: ["lootbox", "my-prizes"] as const,
  myHistory: ["lootbox", "my-history"] as const,
  pending: (page: number, size: number) =>
    ["lootbox", "admin", "pending", page, size] as const,
  userProfile: (userId: string) => ["lootbox", "admin", "user", userId] as const,
};

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

export function useAdminPendingPrizes(page = 0, size = 20) {
  return useQuery({
    queryKey: lootboxKeys.pending(page, size),
    queryFn: () => getPendingRedemptions(page, size),
    staleTime: 30 * 1000,
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
