"use client";

import {
  useMutation,
  useQueryClient,
  type QueryKey,
} from "@tanstack/react-query";
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

type QC = ReturnType<typeof useQueryClient>;

const invalidate = (qc: QC, keys: readonly QueryKey[]) =>
  Promise.all(keys.map((key) => qc.invalidateQueries({ queryKey: key })));

// Crates/tiers/prizes all alter the admin catalog (full tree), the admin crate-list
// rail (tier/prize counts), and the player-facing catalog (visible tiers/prizes).
// Centralised so the three mutation families don't drift apart.
const CATALOG_KEYS: readonly QueryKey[] = [
  lootboxKeys.adminCatalog,
  lootboxKeys.catalog,
  lootboxKeys.adminCrates,
];

export function usePlayLootboxMutation() {
  const qc = useQueryClient();
  return useMutation<PlayLootboxResponse, Error, { crateId: string }>({
    mutationFn: ({ crateId }) => playLootbox(crateId),
    onSuccess: async () => {
      // NOTE: ["lootbox","recent"] is intentionally NOT invalidated here. If we did,
      // the ticker would refetch and reveal the user's prize BEFORE the spin reel
      // finishes (a visible flicker spoiler). The page-level handler invalidates
      // the recent ticker once the reel phase transitions to "won".
      await invalidate(qc, [
        lootboxKeys.balance,
        lootboxKeys.walletBreakdown,
        lootboxKeys.myPrizes,
        lootboxKeys.myHistory,
      ]);
    },
  });
}

export function useCreateCrateMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxAdmin, Error, UpsertLootboxRequest>({
    mutationFn: (body) => createCrate(body),
    onSuccess: async () => {
      await invalidate(qc, CATALOG_KEYS);
    },
  });
}

export function useUpdateCrateMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxAdmin, Error, { id: string; body: UpsertLootboxRequest }>({
    mutationFn: ({ id, body }) => updateCrate(id, body),
    onSuccess: async () => {
      await invalidate(qc, CATALOG_KEYS);
    },
  });
}

/**
 * Reorder crates by renumbering sortOrder = array-index for every crate in `ordered`.
 * Always renumbers the full set so the move is unambiguous even when crates share
 * their default sortOrder (0). One catalog invalidation at the end, not N.
 *
 * Each crate's full update body must be passed so the backend's @NotBlank name
 * validation on UpsertLootboxRequestDTO is satisfied (the create + update endpoints
 * share that DTO).
 */
export function useReorderCratesMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { ordered: readonly LootboxAdmin[] }>({
    mutationFn: async ({ ordered }) => {
      await Promise.all(
        ordered.map((c, idx) =>
          updateCrate(c.id, {
            name: c.name,
            description: c.description,
            imageUrl: c.imageUrl,
            cost: c.cost,
            startsAt: c.startsAt,
            endsAt: c.endsAt,
            active: c.active,
            siteId: c.siteId,
            sortOrder: idx,
          })
        )
      );
    },
    onSuccess: async () => {
      await invalidate(qc, CATALOG_KEYS);
    },
  });
}

export function useDeleteCrateMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteCrate(id),
    onSuccess: async () => {
      await invalidate(qc, CATALOG_KEYS);
    },
  });
}

export function useMarkRedeemedMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxPlay, Error, { playId: string }>({
    mutationFn: ({ playId }) => markRedeemed(playId),
    onSuccess: async (data) => {
      // Pending queue shrinks; the redeemed user's admin profile now shows the
      // prize as redeemed. Prefix invalidation hits every cached `pending(page,size)` key.
      await invalidate(qc, [
        ["lootbox", "admin", "pending"],
        lootboxKeys.userProfile(data.userId),
      ]);
    },
  });
}

export function useAdjustCoinsMutation() {
  const qc = useQueryClient();
  return useMutation<CoinAdjustment, Error, CoinAdjustmentRequest>({
    mutationFn: (body) => adjustCoins(body),
    onSuccess: async (_data, variables) => {
      // The target user's admin profile is the primary view that changes.
      // balance/walletBreakdown/myHistory are the admin's own caches — only matter
      // if the admin is adjusting themselves, but cheap insurance to invalidate them.
      // The three coin-dashboard keys cover the admin Coins-tab stat strip, player
      // table, and recent activity feed — all three shift after any adjustment.
      await invalidate(qc, [
        lootboxKeys.userProfile(variables.userId),
        lootboxKeys.balance,
        lootboxKeys.walletBreakdown,
        lootboxKeys.myHistory,
        lootboxKeys.coinStats,
        ["lootbox", "admin", "players"],
        ["lootbox", "admin", "activity"],
      ]);
    },
  });
}

export function useCreateTierMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxTier, Error, UpsertTierRequest>({
    mutationFn: (body) => createTier(body),
    onSuccess: async () => {
      await invalidate(qc, CATALOG_KEYS);
    },
  });
}

export function useUpdateTierMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxTier, Error, { id: string; body: UpsertTierRequest }>({
    mutationFn: ({ id, body }) => updateTier(id, body),
    onSuccess: async () => {
      await invalidate(qc, CATALOG_KEYS);
    },
  });
}

export function useBulkUpdateTierProbabilitiesMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxTier[], Error, BulkUpdateTierProbabilitiesRequest>({
    mutationFn: (body) => bulkUpdateTierProbabilities(body),
    onSuccess: async () => {
      await invalidate(qc, CATALOG_KEYS);
    },
  });
}

export function useDeleteTierMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteTier(id),
    onSuccess: async () => {
      await invalidate(qc, CATALOG_KEYS);
    },
  });
}

export function useCreatePrizeMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxPrize, Error, UpsertPrizeRequest>({
    mutationFn: (body) => createPrize(body),
    onSuccess: async () => {
      await invalidate(qc, CATALOG_KEYS);
    },
  });
}

export function useUpdatePrizeMutation() {
  const qc = useQueryClient();
  return useMutation<LootboxPrize, Error, { id: string; body: UpsertPrizeRequest }>({
    mutationFn: ({ id, body }) => updatePrize(id, body),
    onSuccess: async () => {
      await invalidate(qc, CATALOG_KEYS);
    },
  });
}

export function useDeletePrizeMutation() {
  const qc = useQueryClient();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deletePrize(id),
    onSuccess: async () => {
      await invalidate(qc, CATALOG_KEYS);
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
