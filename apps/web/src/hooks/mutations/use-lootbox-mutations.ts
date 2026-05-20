"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  adjustCoins,
  bulkUpdateTierProbabilities,
  createPrize,
  createTier,
  deletePrize,
  deleteTier,
  markRedeemed,
  playLootbox,
  updatePrize,
  updateTier,
  type BulkUpdateTierProbabilitiesRequest,
} from "@/lib/api/lootbox";
import type {
  CoinAdjustment,
  CoinAdjustmentRequest,
  LootboxPlay,
  LootboxPrize,
  LootboxTier,
  PlayLootboxResponse,
  UpsertPrizeRequest,
  UpsertTierRequest,
} from "@/types/lootbox";
import { lootboxKeys } from "@/hooks/queries/use-lootbox";

function invalidateAllLootbox(qc: ReturnType<typeof useQueryClient>) {
  return qc.invalidateQueries({ queryKey: ["lootbox"] });
}

export function usePlayLootboxMutation() {
  const qc = useQueryClient();
  return useMutation<PlayLootboxResponse, Error, void>({
    mutationFn: () => playLootbox(),
    onSuccess: async () => {
      await Promise.all([
        qc.invalidateQueries({ queryKey: lootboxKeys.balance }),
        qc.invalidateQueries({ queryKey: lootboxKeys.myPrizes }),
        qc.invalidateQueries({ queryKey: lootboxKeys.myHistory }),
      ]);
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
