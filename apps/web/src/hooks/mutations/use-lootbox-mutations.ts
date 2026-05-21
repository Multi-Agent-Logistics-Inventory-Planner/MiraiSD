"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  adjustCoins,
  bulkUpdateTierProbabilities,
  createCrate,
  createPrize,
  createTier,
  deleteCrate,
  deletePrize,
  deleteTier,
  markRedeemed,
  playLootbox,
  updateCoinEconomyConfig,
  updateCrate,
  updatePrize,
  updateTier,
  type BulkUpdateTierProbabilitiesRequest,
} from "@/lib/api/lootbox";
import type {
  CoinAdjustment,
  CoinAdjustmentRequest,
  CoinEconomyConfig,
  LootboxAdmin,
  LootboxPlay,
  LootboxPrize,
  LootboxTier,
  PlayLootboxResponse,
  UpdateCoinEconomyConfigRequest,
  UpsertLootboxRequest,
  UpsertPrizeRequest,
  UpsertTierRequest,
} from "@/types/lootbox";
import { lootboxKeys } from "@/hooks/queries/use-lootbox";

function invalidateAllLootbox(qc: ReturnType<typeof useQueryClient>) {
  return qc.invalidateQueries({ queryKey: ["lootbox"] });
}

export function usePlayLootboxMutation() {
  const qc = useQueryClient();
  return useMutation<PlayLootboxResponse, Error, { crateId: string }>({
    mutationFn: ({ crateId }) => playLootbox(crateId),
    onSuccess: async () => {
      await Promise.all([
        qc.invalidateQueries({ queryKey: lootboxKeys.balance }),
        qc.invalidateQueries({ queryKey: lootboxKeys.myPrizes }),
        qc.invalidateQueries({ queryKey: lootboxKeys.myHistory }),
        qc.invalidateQueries({ queryKey: ["lootbox", "recent"] }),
      ]);
    },
  });
}

export function useCreateCrateMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxAdmin, Error, UpsertLootboxRequest>({
    mutationFn: (body) => createCrate(body),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useUpdateCrateMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxAdmin, Error, { id: string; body: UpsertLootboxRequest }>({
    mutationFn: ({ id, body }) => updateCrate(id, body),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useDeleteCrateMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteCrate(id),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useMarkRedeemedMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxPlay, Error, { playId: string }>({
    mutationFn: ({ playId }) => markRedeemed(playId),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useAdjustCoinsMutation() {
  const qc = useQueryClient();
  return useMutation<CoinAdjustment, Error, CoinAdjustmentRequest>({
    mutationFn: (body) => adjustCoins(body),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useCreateTierMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxTier, Error, UpsertTierRequest>({
    mutationFn: (body) => createTier(body),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useUpdateTierMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxTier, Error, { id: string; body: UpsertTierRequest }>({
    mutationFn: ({ id, body }) => updateTier(id, body),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useBulkUpdateTierProbabilitiesMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxTier[], Error, BulkUpdateTierProbabilitiesRequest>({
    mutationFn: (body) => bulkUpdateTierProbabilities(body),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useDeleteTierMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteTier(id),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useCreatePrizeMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxPrize, Error, UpsertPrizeRequest>({
    mutationFn: (body) => createPrize(body),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useUpdatePrizeMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxPrize, Error, { id: string; body: UpsertPrizeRequest }>({
    mutationFn: ({ id, body }) => updatePrize(id, body),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useDeletePrizeMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deletePrize(id),
    onSuccess: async () => {
      await invalidateAllLootbox(qc);
    },
  });
}

export function useUpdateCoinEconomyConfigMutation() {
  const qc = useQueryClient();
  return useMutation<CoinEconomyConfig, Error, UpdateCoinEconomyConfigRequest>({
    mutationFn: (body) => updateCoinEconomyConfig(body),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: lootboxKeys.coinEconomyConfig });
    },
  });
}
