"use client";

import { useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import {
  addKujiSlip,
  addKujiTier,
  closeKujiBox,
  deleteKujiPrize,
  moveKujiSlips,
  openKujiBox,
  patchKujiTier,
  recordKujiDraw,
  reopenKujiBox,
  transferInInventoryOnlyToKujiTier,
  transferInMoreToKujiTier,
  undoKujiDraw,
} from "@/lib/api/kuji-boxes";
import { deleteProductImage } from "@/lib/supabase/storage";
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
  box: KujiBox,
  productId: string | null | undefined,
) {
  const resolvedProductId = productId ?? box.productId;

  // Box detail by id always reflects the response.
  qc.setQueryData(["kuji-box", "detail", box.id], box);

  if (resolvedProductId) {
    if (box.status === KujiBoxStatus.OPEN) {
      // Active query expects the currently-open box for this product.
      qc.setQueryData(["kuji-box", "active", resolvedProductId], box);
    } else {
      // Closed: active query should now 404; force a refetch so the hook's
      // retry-on-non-404 logic resolves to "no active box".
      await qc.invalidateQueries({
        queryKey: ["kuji-box", "active", resolvedProductId],
      });
    }
    // History and last-tiers are per-product summaries not returned in the
    // mutation response; invalidate so they refetch.
    await qc.invalidateQueries({
      queryKey: ["kuji-box", "history", resolvedProductId],
    });
    await qc.invalidateQueries({
      queryKey: ["kuji-box", "last-tiers", resolvedProductId],
    });
  } else {
    await qc.invalidateQueries({ queryKey: ["kuji-box"] });
  }
}

export function useOpenKujiBoxMutation() {
  const qc = useQueryClient();
  return useMutation<KujiBox, Error, OpenKujiBoxRequest>({
    mutationFn: (payload) => openKujiBox(payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, box, variables.productId);
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
  return useMutation<KujiBox, Error, CloseKujiBoxVariables>({
    mutationFn: ({ boxId, payload }) => closeKujiBox(boxId, payload),
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

      await applyKujiMutationResponse(qc, box, variables.productId);
    },
  });
}

interface ReopenKujiBoxVariables {
  boxId: string;
  actorId: string;
  productId?: string;
}

export function useReopenKujiBoxMutation() {
  const qc = useQueryClient();
  return useMutation<KujiBox, Error, ReopenKujiBoxVariables>({
    mutationFn: ({ boxId, actorId }) => reopenKujiBox(boxId, actorId),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, box, variables.productId);
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
  return useMutation<KujiBox, Error, PatchKujiTierVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      patchKujiTier(boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, box, variables.productId);
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
  return useMutation<KujiBox, Error, TransferInMoreVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      transferInMoreToKujiTier(boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, box, variables.productId);
    },
  });
}

export function useTransferInInventoryOnlyToKujiTierMutation() {
  const qc = useQueryClient();
  return useMutation<KujiBox, Error, TransferInMoreVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      transferInInventoryOnlyToKujiTier(boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, box, variables.productId);
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
  return useMutation<KujiBox, Error, RecordKujiDrawVariables>({
    mutationFn: ({ boxId, payload }) => recordKujiDraw(boxId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, box, variables.productId);
    },
  });
}

interface UndoKujiDrawVariables {
  boxId: string;
  auditLogId: string;
  actorId: string;
  productId?: string;
}

export function useUndoKujiDrawMutation() {
  const qc = useQueryClient();
  return useMutation<KujiBox, Error, UndoKujiDrawVariables>({
    mutationFn: ({ boxId, auditLogId, actorId }) =>
      undoKujiDraw(boxId, auditLogId, actorId),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, box, variables.productId);
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
  return useMutation<KujiBox, Error, AddKujiSlipVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      addKujiSlip(boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, box, variables.productId);
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
  return useMutation<KujiBox, Error, AddKujiTierVariables>({
    mutationFn: ({ boxId, payload }) => addKujiTier(boxId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, box, variables.productId);
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
  return useMutation<KujiBox, Error, MoveKujiSlipsVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      moveKujiSlips(boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, box, variables.productId);
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
  return useMutation<KujiBox, Error, DeleteKujiPrizeVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      deleteKujiPrize(boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await applyKujiMutationResponse(qc, box, variables.productId);
    },
  });
}

