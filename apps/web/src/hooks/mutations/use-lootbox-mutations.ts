"use client";

import {
  useMutation,
  useQueryClient,
  type QueryKey,
} from "@tanstack/react-query";
import {
  adjustSiteCoins,
  bulkUpdateSiteTierProbabilities,
  createSiteCrate,
  createSitePrize,
  createSiteTier,
  deleteSiteCrate,
  deleteSitePrize,
  deleteSiteTier,
  markSiteRedeemed,
  updateSiteCrate,
  updateSitePrize,
  updateSiteTier,
} from "@/lib/api/site-lootbox-admin";
import { playSiteLootbox } from "@/lib/api/site-lootbox";
import {
  updateCoinEconomyConfig,
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
import { useCurrentSite } from "@/hooks/queries/use-current-site";

type QC = ReturnType<typeof useQueryClient>;

const invalidate = (qc: QC, keys: readonly QueryKey[]) =>
  Promise.all(keys.map((key) => qc.invalidateQueries({ queryKey: key })));

/** A mutation attempted with no resolved site throws instead of calling an undefined site path. */
function requireSiteId(siteId: string | undefined): string {
  if (!siteId) throw new Error("No active site");
  return siteId;
}

// Crates/tiers/prizes all alter the admin catalog (full tree), the admin crate-list
// rail (tier/prize counts), and the player-facing catalog (visible tiers/prizes).
// Centralised so the three mutation families don't drift apart.
function catalogKeys(siteId: string | undefined): readonly QueryKey[] {
  return [
    lootboxKeys.adminCatalog(siteId),
    lootboxKeys.catalog(siteId),
    lootboxKeys.adminCrates(siteId),
  ];
}

export function usePlayLootboxMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<PlayLootboxResponse, Error, { crateId: string }>({
    mutationFn: ({ crateId }) => playSiteLootbox(requireSiteId(siteId), crateId),
    onSuccess: async () => {
      // NOTE: ["lootbox","recent"] is intentionally NOT invalidated here. If we did,
      // the ticker would refetch and reveal the user's prize BEFORE the spin reel
      // finishes (a visible flicker spoiler). The page-level handler invalidates
      // the recent ticker once the reel phase transitions to "won".
      await invalidate(qc, [
        lootboxKeys.balance(siteId),
        lootboxKeys.walletBreakdown(siteId),
        lootboxKeys.myPrizes(siteId),
        lootboxKeys.myHistory(siteId),
      ]);
    },
  });
}

export function useCreateCrateMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<LootboxAdmin, Error, UpsertLootboxRequest>({
    mutationFn: (body) => createSiteCrate(requireSiteId(siteId), body),
    onSuccess: async () => {
      await invalidate(qc, catalogKeys(siteId));
    },
  });
}

export function useUpdateCrateMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<LootboxAdmin, Error, { id: string; body: UpsertLootboxRequest }>({
    mutationFn: ({ id, body }) => updateSiteCrate(requireSiteId(siteId), id, body),
    onSuccess: async () => {
      await invalidate(qc, catalogKeys(siteId));
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
  const { siteId } = useCurrentSite();
  return useMutation<void, Error, { ordered: readonly LootboxAdmin[] }>({
    mutationFn: async ({ ordered }) => {
      const resolvedSiteId = requireSiteId(siteId);
      await Promise.all(
        ordered.map((c, idx) =>
          updateSiteCrate(resolvedSiteId, c.id, {
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
      await invalidate(qc, catalogKeys(siteId));
    },
  });
}

export function useDeleteCrateMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteSiteCrate(requireSiteId(siteId), id),
    onSuccess: async () => {
      await invalidate(qc, catalogKeys(siteId));
    },
  });
}

export function useMarkRedeemedMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<LootboxPlay, Error, { playId: string }>({
    mutationFn: ({ playId }) => markSiteRedeemed(requireSiteId(siteId), playId),
    onSuccess: async (data) => {
      // Pending queue shrinks; the redeemed user's admin profile now shows the
      // prize as redeemed. Prefix invalidation hits every cached `pending(page,size)` key.
      await invalidate(qc, [
        ["lootbox", "admin", "pending", siteId],
        lootboxKeys.userProfile(siteId, data.userId),
      ]);
    },
  });
}

export function useAdjustCoinsMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<CoinAdjustment, Error, CoinAdjustmentRequest>({
    mutationFn: (body) => adjustSiteCoins(requireSiteId(siteId), body),
    onSuccess: async (_data, variables) => {
      // The target user's admin profile is the primary view that changes.
      // balance/walletBreakdown/myHistory are the admin's own caches — only matter
      // if the admin is adjusting themselves, but cheap insurance to invalidate them.
      // The three coin-dashboard keys cover the admin Coins-tab stat strip, player
      // table, and recent activity feed — all three shift after any adjustment.
      await invalidate(qc, [
        lootboxKeys.userProfile(siteId, variables.userId),
        lootboxKeys.balance(siteId),
        lootboxKeys.walletBreakdown(siteId),
        lootboxKeys.myHistory(siteId),
        lootboxKeys.coinStats(siteId),
        ["lootbox", "admin", "players", siteId],
        ["lootbox", "admin", "activity", siteId],
      ]);
    },
  });
}

export function useCreateTierMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<LootboxTier, Error, UpsertTierRequest>({
    mutationFn: (body) => createSiteTier(requireSiteId(siteId), body),
    onSuccess: async () => {
      await invalidate(qc, catalogKeys(siteId));
    },
  });
}

export function useUpdateTierMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<LootboxTier, Error, { id: string; body: UpsertTierRequest }>({
    mutationFn: ({ id, body }) => updateSiteTier(requireSiteId(siteId), id, body),
    onSuccess: async () => {
      await invalidate(qc, catalogKeys(siteId));
    },
  });
}

export function useBulkUpdateTierProbabilitiesMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<LootboxTier[], Error, BulkUpdateTierProbabilitiesRequest>({
    mutationFn: (body) => bulkUpdateSiteTierProbabilities(requireSiteId(siteId), body),
    onSuccess: async () => {
      await invalidate(qc, catalogKeys(siteId));
    },
  });
}

export function useDeleteTierMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteSiteTier(requireSiteId(siteId), id),
    onSuccess: async () => {
      await invalidate(qc, catalogKeys(siteId));
    },
  });
}

export function useCreatePrizeMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<LootboxPrize, Error, UpsertPrizeRequest>({
    mutationFn: (body) => createSitePrize(requireSiteId(siteId), body),
    onSuccess: async () => {
      await invalidate(qc, catalogKeys(siteId));
    },
  });
}

export function useUpdatePrizeMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<LootboxPrize, Error, { id: string; body: UpsertPrizeRequest }>({
    mutationFn: ({ id, body }) => updateSitePrize(requireSiteId(siteId), id, body),
    onSuccess: async () => {
      await invalidate(qc, catalogKeys(siteId));
    },
  });
}

export function useDeletePrizeMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<void, Error, { id: string }>({
    mutationFn: ({ id }) => deleteSitePrize(requireSiteId(siteId), id),
    onSuccess: async () => {
      await invalidate(qc, catalogKeys(siteId));
    },
  });
}

/** coin_economy_config is a single global row, not per-site - stays on the legacy route. */
export function useUpdateCoinEconomyConfigMutation() {
  const qc = useQueryClient();
  return useMutation<CoinEconomyConfig, Error, UpdateCoinEconomyConfigRequest>({
    mutationFn: (body) => updateCoinEconomyConfig(body),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: lootboxKeys.coinEconomyConfig });
    },
  });
}
