"use client";

import { useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import {
  addKujiSlip,
  addKujiTier,
  closeKujiBox,
  openKujiBox,
  patchKujiTier,
  recordKujiDraw,
  reopenKujiBox,
  transferInInventoryOnlyToKujiTier,
  transferInMoreToKujiTier,
  undoKujiDraw,
} from "@/lib/api/kuji-boxes";
import { deleteProductImage } from "@/lib/supabase/storage";
import type {
  AddKujiTierRequest,
  AddSlipRequest,
  CloseKujiBoxRequest,
  KujiBox,
  OpenKujiBoxRequest,
  PatchKujiTierRequest,
  RecordDrawRequest,
  TransferInMoreRequest,
} from "@/types/api";

interface KujiInvalidateOptions {
  /** Inventory side-effects also occurred (open/close/reopen/draw/undo/transfer-in/etc). */
  affectsInventory?: boolean;
}

/**
 * Invalidate every cache key that may be affected by a kuji-box mutation.
 * Always invalidates the per-product `active` and `history` keys plus the
 * per-box `detail` key. When the mutation has inventory side-effects (open,
 * close, reopen, transfer-in, draw, undo, patch-tier with linked-product
 * change), it also invalidates the product, notification, and inventory keys.
 */
async function invalidateKujiQueries(
  qc: QueryClient,
  productId: string | null | undefined,
  boxId: string | null | undefined,
  options: KujiInvalidateOptions = {}
) {
  if (productId) {
    await qc.invalidateQueries({
      queryKey: ["kuji-box", "active", productId],
    });
    await qc.invalidateQueries({
      queryKey: ["kuji-box", "history", productId],
    });
    await qc.invalidateQueries({
      queryKey: ["kuji-box", "last-tiers", productId],
    });
  } else {
    // No productId — fall back to invalidating the entire kuji-box subtree.
    await qc.invalidateQueries({ queryKey: ["kuji-box"] });
  }

  if (boxId) {
    await qc.invalidateQueries({ queryKey: ["kuji-box", "detail", boxId] });
  }

  // Audit-log queries power the kuji activity log card and the Undo Draw history
  // list. Realtime broadcasts also invalidate these, but invalidating directly
  // makes the refresh deterministic right after a mutation.
  await qc.invalidateQueries({ queryKey: ["audit-logs"] });
  await qc.invalidateQueries({ queryKey: ["audit-log"] });

  if (options.affectsInventory) {
    await qc.invalidateQueries({ queryKey: ["products"] });
    await qc.invalidateQueries({ queryKey: ["notifications"] });
    // Inventory views are split across multiple top-level keys in this
    // codebase; invalidate each so kuji-driven inventory changes are visible.
    await qc.invalidateQueries({ queryKey: ["locationInventory"] });
    await qc.invalidateQueries({ queryKey: ["inventoryByItem"] });
    await qc.invalidateQueries({ queryKey: ["inventoryTotals"] });
    await qc.invalidateQueries({ queryKey: ["notAssignedInventory"] });
  }
}

export function useOpenKujiBoxMutation() {
  const qc = useQueryClient();
  return useMutation<KujiBox, Error, OpenKujiBoxRequest>({
    mutationFn: (payload) => openKujiBox(payload),
    onSuccess: async (box, variables) => {
      await invalidateKujiQueries(qc, variables.productId ?? box.productId, box.id, {
        affectsInventory: true,
      });
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

      await invalidateKujiQueries(qc, variables.productId ?? box.productId, box.id, {
        affectsInventory: true,
      });
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
      await invalidateKujiQueries(qc, variables.productId ?? box.productId, box.id, {
        affectsInventory: true,
      });
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
      // Only inventory-affecting if linked product changed (which may transfer the
      // old product's stock out via linkedProductDestinationLocationId).
      const affectsInventory =
        variables.payload.linkedProductId !== undefined ||
        variables.payload.clearLinkedProduct === true ||
        variables.payload.linkedProductDestinationLocationId !== undefined;
      await invalidateKujiQueries(
        qc,
        variables.productId ?? box.productId,
        box.id,
        { affectsInventory }
      );
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
      await invalidateKujiQueries(qc, variables.productId ?? box.productId, box.id, {
        affectsInventory: true,
      });
    },
  });
}

export function useTransferInInventoryOnlyToKujiTierMutation() {
  const qc = useQueryClient();
  return useMutation<KujiBox, Error, TransferInMoreVariables>({
    mutationFn: ({ boxId, tierId, payload }) =>
      transferInInventoryOnlyToKujiTier(boxId, tierId, payload),
    onSuccess: async (box, variables) => {
      await invalidateKujiQueries(qc, variables.productId ?? box.productId, box.id, {
        affectsInventory: true,
      });
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
      await invalidateKujiQueries(qc, variables.productId ?? box.productId, box.id, {
        affectsInventory: true,
      });
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
      await invalidateKujiQueries(qc, variables.productId ?? box.productId, box.id, {
        affectsInventory: true,
      });
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
      // Adding slips does NOT change linked-product inventory.
      await invalidateKujiQueries(
        qc,
        variables.productId ?? box.productId,
        box.id,
        { affectsInventory: false }
      );
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
      await invalidateKujiQueries(
        qc,
        variables.productId ?? box.productId,
        box.id,
        { affectsInventory: true },
      );
    },
  });
}

