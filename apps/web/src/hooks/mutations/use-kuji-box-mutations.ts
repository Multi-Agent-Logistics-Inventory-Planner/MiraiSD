"use client";

import { useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import {
  addSiteKujiSlip,
  addSiteKujiTier,
  closeSiteKujiBox,
  deleteSiteKujiPrize,
  moveSiteKujiSlips,
  openSiteKujiBox,
  patchSiteKujiTier,
  recordSiteKujiDraw,
  reopenSiteKujiBox,
  transferInInventoryOnlyToSiteKujiTier,
  transferInMoreToSiteKujiTier,
  undoSiteKujiDraw,
} from "@/lib/api/site-kuji-boxes";
import { deleteProductImage } from "@/lib/storage/images";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import {
  KujiBoxStatus,
  type AddKujiTierRequest,
  type AddSlipRequest,
  type CloseKujiBoxRequest,
  type DeletePrizeRequest,
  type KujiBox,
  type MoveSlipsRequest,
  type OpenKujiBoxRequest,
  type PatchKujiTierRequest,
  type RecordDrawRequest,
  type TransferInMoreRequest,
} from "@/types/api";

/**
 * Apply the mutation response directly to the kuji-box query caches and
 * invalidate the kuji-only keys not covered by the response. Cross-cutting
 * caches (audit-logs, products, notifications, inventory) are intentionally
 * NOT invalidated here — the backend fires Supabase realtime broadcasts
 * (audit_log_created, inventory_updated, product_updated) at the end of every
 * mutation, and `useRealtimeBroadcast` invalidates those keys for us. Doing
 * both was paying for the same refetch twice.
 */
async function applyKujiMutationResponse(
  qc: QueryClient,
  siteId: string,
  box: KujiBox,
  productId: string | null | undefined,
) {
  const resolvedProductId = productId ?? box.productId;

  // Box detail by id always reflects the response.
  qc.setQueryData(["kuji-box", "detail", siteId, box.id], box);

  if (resolvedProductId) {
    if (box.status === KujiBoxStatus.OPEN) {
      // Active query expects the currently-open box for this product.
      qc.setQueryData(["kuji-box", "active", siteId, resolvedProductId], box);
    } else {
      // Closed: active query should now 404; force a refetch so the hook's
      // retry-on-non-404 logic resolves to "no active box".
      await qc.invalidateQueries({
        queryKey: ["kuji-box", "active", siteId, resolvedProductId],
      });
    }
    // History and last-tiers are per-product summaries not returned in the
    // mutation response; invalidate so they refetch.
    await qc.invalidateQueries({
      queryKey: ["kuji-box", "history", siteId, resolvedProductId],
    });
    await qc.invalidateQueries({
      queryKey: ["kuji-box", "last-tiers", siteId, resolvedProductId],
    });
  } else {
    await qc.invalidateQueries({ queryKey: ["kuji-box", "active", siteId] });
    await qc.invalidateQueries({ queryKey: ["kuji-box", "history", siteId] });
    await qc.invalidateQueries({ queryKey: ["kuji-box", "last-tiers", siteId] });
  }
}

/** A mutation attempted with no resolved site throws instead of calling an undefined site path. */
function requireSiteId(siteId: string | undefined): string {
  if (!siteId) throw new Error("No active site");
  return siteId;
}

export function useOpenKujiBoxMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, OpenKujiBoxRequest>({
    mutationFn: (payload) => openSiteKujiBox(requireSiteId(siteId), payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}

interface CloseKujiBoxVariables {
  boxId: string;
  payload: CloseKujiBoxRequest;
  productId?: string;
}

export function useCloseKujiBoxMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, CloseKujiBoxVariables>({
    mutationFn: ({ boxId, payload }) =>
      closeSiteKujiBox(requireSiteId(siteId), boxId, payload),
    onSuccess: async (box, variables) => {
      // Hard-delete images for auto-created prizes — the products themselves are
      // soft-deleted server-side. Failures are non-fatal: orphaned images can be
      // swept later via storage retention.
      const imageUrls = box.tiers
        .filter(
          (t) =>
            Boolean(t.autoCreatedProduct) &&
            typeof t.linkedProductImageUrl === "string" &&
            t.linkedProductImageUrl.length > 0,
        )
        .map((t) => t.linkedProductImageUrl as string);
      await Promise.all(
        imageUrls.map((url) =>
          deleteProductImage(url).catch(() => null),
        ),
      );

      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}

interface ReopenKujiBoxVariables {
  boxId: string;
  productId?: string;
}

export function useReopenKujiBoxMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, ReopenKujiBoxVariables>({
    mutationFn: ({ boxId }) => reopenSiteKujiBox(requireSiteId(siteId), boxId),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}

interface PatchKujiTierVariables {
  boxId: string;
  tierId: string;
  payload: PatchKujiTierRequest;
  productId?: string;
}

export function usePatchKujiTierMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, PatchKujiTierVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      patchSiteKujiTier(requireSiteId(siteId), boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}

interface TransferInMoreVariables {
  boxId: string;
  tierId: string;
  payload: TransferInMoreRequest;
  productId?: string;
}

export function useTransferInMoreToKujiTierMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, TransferInMoreVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      transferInMoreToSiteKujiTier(requireSiteId(siteId), boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}

export function useTransferInInventoryOnlyToKujiTierMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, TransferInMoreVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      transferInInventoryOnlyToSiteKujiTier(requireSiteId(siteId), boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}

interface RecordKujiDrawVariables {
  boxId: string;
  payload: RecordDrawRequest;
  productId?: string;
}

export function useRecordKujiDrawMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, RecordKujiDrawVariables>({
    mutationFn: ({ boxId, payload }) =>
      recordSiteKujiDraw(requireSiteId(siteId), boxId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}

interface UndoKujiDrawVariables {
  boxId: string;
  auditLogId: string;
  productId?: string;
}

export function useUndoKujiDrawMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, UndoKujiDrawVariables>({
    mutationFn: ({ boxId, auditLogId }) =>
      undoSiteKujiDraw(requireSiteId(siteId), boxId, auditLogId),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}

interface AddKujiSlipVariables {
  boxId: string;
  tierId: string;
  payload: AddSlipRequest;
  productId?: string;
}

export function useAddKujiSlipMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, AddKujiSlipVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      addSiteKujiSlip(requireSiteId(siteId), boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}

interface AddKujiTierVariables {
  boxId: string;
  payload: AddKujiTierRequest;
  productId?: string;
}

export function useAddKujiTierMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, AddKujiTierVariables>({
    mutationFn: ({ boxId, payload }) => addSiteKujiTier(requireSiteId(siteId), boxId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}

interface MoveKujiSlipsVariables {
  boxId: string;
  tierId: string;
  payload: MoveSlipsRequest;
  productId?: string;
}

export function useMoveKujiSlipsMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, MoveKujiSlipsVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      moveSiteKujiSlips(requireSiteId(siteId), boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}

interface DeleteKujiPrizeVariables {
  boxId: string;
  tierId: string;
  payload: DeletePrizeRequest;
  productId?: string;
}

export function useDeleteKujiPrizeMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  return useMutation<KujiBox, Error, DeleteKujiPrizeVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      deleteSiteKujiPrize(requireSiteId(siteId), boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, requireSiteId(siteId), box, variables.productId);
    },
  });
}
