"use client";

import { useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import {
  adjustSiteInventory,
  transferSiteInventory,
  batchTransferSiteInventory,
  newIdempotencyKey,
  type AdjustSiteInventoryPayload,
  type TransferSiteInventoryPayload,
} from "@/lib/api/site-inventory";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { LocationType } from "@/types/api";

// --- Site-scoped stock mutations (Phase 6 checkpoint 6d, T-6d-7/T-6d-8) --------------------
// Replaces the legacy, unscoped batchAdjustStock/transferStock/batchTransferStock. Actor
// identity is derived server-side from AuthorizedSiteContext - the client no longer sends
// actorId (T-6d-7). Idempotency keys are generated once per user-initiated mutate() call (T-6d-2,
// see site-inventory.ts's newIdempotencyKey doc comment for why this must not happen inside
// mutationFn). Query-key invalidation is site-qualified throughout (T-6d-3).

export interface BatchAdjustVariables {
  payload: AdjustSiteInventoryPayload;
  /** Product ids touched by this batch - used to invalidate per-product query keys. */
  productIds: string[];
}

interface TransferStockVariables {
  payload: TransferSiteInventoryPayload;
  productId?: string;
}

export interface BatchTransferItem {
  payload: TransferSiteInventoryPayload;
  productId: string;
  productName: string;
}

interface BatchTransferVariables {
  transfers: BatchTransferItem[];
  sourceLocationId: string;
  destinationLocationId: string;
  sourceLocationType?: LocationType;
  destinationLocationType?: LocationType;
}

async function invalidateStockQueries(
  qc: QueryClient,
  siteId: string,
  productIds: string[]
) {
  const tasks: Promise<unknown>[] = [
    // Site-qualified prefix: invalidates every ["locationInventory", siteId, ...] key
    // (including the resolved-location sub-key and the NOT_ASSIGNED case) without needing to
    // know the exact location - deliberately broad within this one site, never cross-site.
    qc.invalidateQueries({ queryKey: ["locationInventory", siteId] }),
    qc.invalidateQueries({ queryKey: ["auditLogs"] }),
    qc.invalidateQueries({ queryKey: ["auditLog"] }),
    qc.invalidateQueries({ queryKey: ["inventoryTotals", siteId] }),
    // Site-scoped product list (5d's ["products", siteId, "site"] key) - NOT the bare
    // ["products"] prefix, which would also match the unrelated legacy, unscoped product list
    // query and any future non-inventory "products"-prefixed key (the prefix-collision bug
    // T-6d-3 was scoped to fix).
    qc.invalidateQueries({ queryKey: ["products", siteId, "site"] }),
  ];

  for (const id of new Set(productIds)) {
    tasks.push(qc.invalidateQueries({ queryKey: ["productInventoryEntries", siteId, id] }));
    tasks.push(qc.invalidateQueries({ queryKey: ["movementHistory", siteId, id] }));
  }

  await Promise.all(tasks);
}

export function useBatchAdjustStockMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    void,
    Error,
    BatchAdjustVariables & { idempotencyKey: string }
  >({
    mutationFn: ({ idempotencyKey, payload }) => {
      if (!siteId) {
        return Promise.reject(new Error("No active site"));
      }
      return adjustSiteInventory(siteId, idempotencyKey, payload);
    },
    onSuccess: async (_data, variables) => {
      if (!siteId) return;
      await invalidateStockQueries(qc, siteId, variables.productIds);
    },
  });

  return {
    ...mutation,
    mutate: (variables: BatchAdjustVariables, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ ...variables, idempotencyKey: newIdempotencyKey() }, options),
    mutateAsync: (variables: BatchAdjustVariables) =>
      mutation.mutateAsync({ ...variables, idempotencyKey: newIdempotencyKey() }),
  };
}

export function useTransferStockMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    void,
    Error,
    TransferStockVariables & { idempotencyKey: string }
  >({
    mutationFn: ({ idempotencyKey, payload }) => {
      if (!siteId) {
        return Promise.reject(new Error("No active site"));
      }
      return transferSiteInventory(siteId, idempotencyKey, payload);
    },
    onSuccess: async (_data, variables) => {
      if (!siteId) return;
      await invalidateStockQueries(qc, siteId, variables.productId ? [variables.productId] : []);
    },
  });

  return {
    ...mutation,
    mutate: (variables: TransferStockVariables, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ ...variables, idempotencyKey: newIdempotencyKey() }, options),
    mutateAsync: (variables: TransferStockVariables) =>
      mutation.mutateAsync({ ...variables, idempotencyKey: newIdempotencyKey() }),
  };
}

export function useBatchTransferMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    void,
    Error,
    BatchTransferVariables & { idempotencyKey: string }
  >({
    mutationFn: ({ idempotencyKey, transfers }) => {
      if (!siteId) {
        return Promise.reject(new Error("No active site"));
      }
      return batchTransferSiteInventory(
        siteId,
        idempotencyKey,
        transfers.map((t) => t.payload)
      );
    },
    onSuccess: async (_data, variables) => {
      if (!siteId) return;
      const productIds = [...new Set(variables.transfers.map((t) => t.productId))];
      await invalidateStockQueries(qc, siteId, productIds);
    },
  });

  return {
    ...mutation,
    mutate: (variables: BatchTransferVariables, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ ...variables, idempotencyKey: newIdempotencyKey() }, options),
    mutateAsync: (variables: BatchTransferVariables) =>
      mutation.mutateAsync({ ...variables, idempotencyKey: newIdempotencyKey() }),
  };
}
