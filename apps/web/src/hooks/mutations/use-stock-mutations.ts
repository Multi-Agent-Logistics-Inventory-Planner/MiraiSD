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
import { useAuth } from "@/hooks/use-auth";
import { flushInventorySiteRefresh } from "@/hooks/realtime/inventory-refresh";
import { LocationType } from "@/types/api";
import {
  clearUncertainStockSubmission,
  isDefinitiveStockSubmissionFailure,
  readUncertainStockSubmission,
  storeUncertainStockSubmission,
} from "@/lib/stock-submission-recovery";

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

/**
 * Reconcile the views affected by a stock command which has already committed.  This is
 * deliberately best-effort: a failed read must never turn a successful, idempotent write into
 * a failed mutation in the UI.
 */
export async function refreshCommittedStock(
  qc: QueryClient,
  siteId: string,
  productIds: string[]
) {
  // Totals go through the shared targeted-refresh executor (6e, T-6e-5/AC-7): a bounded fetch
  // of just these product IDs, merged into the cache, instead of a full-catalog invalidation -
  // this hook already has productIds in hand, unlike the realtime broadcast path pre-6e.
  //
  // Non-fatal (6e independent review, Blocker 2): this is a network call the mutation's own
  // write already succeeded before we get here. If it rejects, mutateAsync would otherwise
  // report the whole (already-committed) mutation as failed - adjust-stock-dialog.tsx would
  // show a false "Adjustment failed" toast, and a user retry would mint a fresh idempotency key
  // and risk a real double-adjustment. Swallow the error rather than fail the mutation.
  //
  // Does NOT fall back to a bare `invalidateQueries` here (follow-up review, second round): a
  // bare invalidate bypasses the sequencing `flushInventorySiteRefresh` itself already uses, so
  // it could arrive after - and unconditionally overwrite - a newer flush's already-applied,
  // correct value. `flushInventorySiteRefresh` already attempts its own sequenced recovery
  // internally on failure (only when this attempt is still the current claim holder); a second,
  // unsequenced fallback here would just risk undoing that.
  const totalsRefresh = flushInventorySiteRefresh(qc, siteId, productIds).catch(() => {
    // Best-effort; recovery (if warranted) already happened inside flushInventorySiteRefresh.
  });

  const tasks: Promise<unknown>[] = [
    totalsRefresh,
    // Site-qualified prefix: invalidates every ["locationInventory", siteId, ...] key
    // (including the resolved-location sub-key and the NOT_ASSIGNED case) without needing to
    // know the exact location - deliberately broad within this one site, never cross-site.
    qc.invalidateQueries({ queryKey: ["locationInventory", siteId] }),
    // Fixed in 6e (T-6e-8): these were ["auditLogs"]/["auditLog"], which match no real query
    // key (use-audit-log.ts uses "audit-log"/"audit-logs") - stock mutations had never
    // actually refreshed the audit-log page.
    qc.invalidateQueries({ queryKey: ["audit-log"] }),
    qc.invalidateQueries({ queryKey: ["audit-logs"] }),
    // Location cards and Storage utilization use this projection.  Realtime eventually did
    // this too, but command completion must converge without a broadcast.
    qc.invalidateQueries({ queryKey: ["locationsWithCounts", siteId] }),
  ];

  for (const id of new Set(productIds)) {
    tasks.push(qc.invalidateQueries({ queryKey: ["productInventoryEntries", siteId, id] }));
    tasks.push(qc.invalidateQueries({ queryKey: ["movementHistory", siteId, id] }));
  }

  await Promise.all(tasks).catch(() => {
    // Query invalidation itself is also best-effort after a committed command.  The totals
    // refresh has its own sequenced recovery; callers retain a successful mutation result.
  });
}

export function useBatchAdjustStockMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  const { user } = useAuth();

  const mutation = useMutation<
    void,
    Error,
    BatchAdjustVariables & { idempotencyKey: string; siteId: string | undefined }
  >({
    mutationFn: ({ idempotencyKey, payload, siteId: originSiteId }) => {
      if (!originSiteId) {
        return Promise.reject(new Error("No active site"));
      }
      return adjustSiteInventory(originSiteId, idempotencyKey, payload);
    },
    onSuccess: async (_data, variables) => {
      if (!variables.siteId) return;
      await refreshCommittedStock(qc, variables.siteId, variables.productIds);
    },
  });

  return {
    ...mutation,
    mutate: (variables: BatchAdjustVariables, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ ...variables, siteId, idempotencyKey: newIdempotencyKey() }, options),
    mutateAsync: async (variables: BatchAdjustVariables) => {
      if (!siteId || !user?.id) return mutation.mutateAsync({ ...variables, siteId, idempotencyKey: newIdempotencyKey() });
      if (readUncertainStockSubmission(user.id, siteId, "adjust")) {
        throw new Error("An earlier adjustment is awaiting explicit recovery. Retry that submission before creating a new one.");
      }
      const submitted = { ...variables, siteId, idempotencyKey: newIdempotencyKey() };
      const record = { version: 2 as const, createdAt: Date.now(), userId: user.id, siteId, idempotencyKey: submitted.idempotencyKey, kind: "adjust" as const, payload: variables.payload, productIds: variables.productIds };
      storeUncertainStockSubmission(record);
      try { const result = await mutation.mutateAsync(submitted); clearUncertainStockSubmission(record); return result; } catch (error) { if (isDefinitiveStockSubmissionFailure(error)) clearUncertainStockSubmission(record); throw error; }
    },
    uncertainSubmission: siteId && user?.id ? readUncertainStockSubmission(user.id, siteId, "adjust") : null,
    retryUncertain: async () => {
      if (!siteId || !user?.id) throw new Error("No active site or user");
      const record = readUncertainStockSubmission(user.id, siteId, "adjust");
      if (!record || record.kind !== "adjust") throw new Error("No uncertain adjustment to retry");
      try {
        const result = await mutation.mutateAsync({ siteId: record.siteId, idempotencyKey: record.idempotencyKey, payload: record.payload, productIds: record.productIds });
        clearUncertainStockSubmission(record);
        return result;
      } catch (error) {
        if (isDefinitiveStockSubmissionFailure(error)) clearUncertainStockSubmission(record);
        throw error;
      }
    },
  };
}

export function useTransferStockMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  const { user } = useAuth();

  const mutation = useMutation<
    void,
    Error,
    TransferStockVariables & { idempotencyKey: string; siteId: string | undefined }
  >({
    mutationFn: ({ idempotencyKey, payload, siteId: originSiteId }) => {
      if (!originSiteId) {
        return Promise.reject(new Error("No active site"));
      }
      return transferSiteInventory(originSiteId, idempotencyKey, payload);
    },
    onSuccess: async (_data, variables) => {
      if (!variables.siteId) return;
      await refreshCommittedStock(qc, variables.siteId, variables.productId ? [variables.productId] : []);
    },
  });

  return {
    ...mutation,
    mutate: (variables: TransferStockVariables, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ ...variables, siteId, idempotencyKey: newIdempotencyKey() }, options),
    mutateAsync: async (variables: TransferStockVariables) => {
      if (!siteId || !user?.id) return mutation.mutateAsync({ ...variables, siteId, idempotencyKey: newIdempotencyKey() });
      if (readUncertainStockSubmission(user.id, siteId, "transfer")) {
        throw new Error("An earlier transfer is awaiting explicit recovery. Retry that submission before creating a new one.");
      }
      const submitted = { ...variables, siteId, idempotencyKey: newIdempotencyKey() };
      const record = { version: 2 as const, createdAt: Date.now(), userId: user.id, siteId, idempotencyKey: submitted.idempotencyKey, kind: "transfer" as const, payload: variables.payload, productId: variables.productId };
      storeUncertainStockSubmission(record);
      try { const result = await mutation.mutateAsync(submitted); clearUncertainStockSubmission(record); return result; } catch (error) { if (isDefinitiveStockSubmissionFailure(error)) clearUncertainStockSubmission(record); throw error; }
    },
    uncertainSubmission: siteId && user?.id ? readUncertainStockSubmission(user.id, siteId, "transfer") : null,
    retryUncertain: async () => {
      if (!siteId || !user?.id) throw new Error("No active site or user");
      const record = readUncertainStockSubmission(user.id, siteId, "transfer");
      if (!record || record.kind !== "transfer") throw new Error("No uncertain transfer to retry");
      try {
        const result = await mutation.mutateAsync({ siteId: record.siteId, idempotencyKey: record.idempotencyKey, payload: record.payload, productId: record.productId });
        clearUncertainStockSubmission(record);
        return result;
      } catch (error) {
        if (isDefinitiveStockSubmissionFailure(error)) clearUncertainStockSubmission(record);
        throw error;
      }
    },
  };
}

export function useBatchTransferMutation() {
  const qc = useQueryClient();
  const { siteId } = useCurrentSite();
  const { user } = useAuth();

  const mutation = useMutation<
    void,
    Error,
    BatchTransferVariables & { idempotencyKey: string; siteId: string | undefined }
  >({
    mutationFn: ({ idempotencyKey, transfers, siteId: originSiteId }) => {
      if (!originSiteId) {
        return Promise.reject(new Error("No active site"));
      }
      return batchTransferSiteInventory(
        originSiteId,
        idempotencyKey,
        transfers.map((t) => t.payload)
      );
    },
    onSuccess: async (_data, variables) => {
      if (!variables.siteId) return;
      const productIds = [...new Set(variables.transfers.map((t) => t.productId))];
      await refreshCommittedStock(qc, variables.siteId, productIds);
    },
  });

  return {
    ...mutation,
    mutate: (variables: BatchTransferVariables, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ ...variables, siteId, idempotencyKey: newIdempotencyKey() }, options),
    mutateAsync: async (variables: BatchTransferVariables) => {
      if (!siteId || !user?.id) return mutation.mutateAsync({ ...variables, siteId, idempotencyKey: newIdempotencyKey() });
      if (readUncertainStockSubmission(user.id, siteId, "batch-transfer")) {
        throw new Error("An earlier transfer is awaiting explicit recovery. Retry that submission before creating a new one.");
      }
      const submitted = { ...variables, siteId, idempotencyKey: newIdempotencyKey() };
      const record = { version: 2 as const, createdAt: Date.now(), userId: user.id, siteId, idempotencyKey: submitted.idempotencyKey, kind: "batch-transfer" as const, transfers: variables.transfers, sourceLocationId: variables.sourceLocationId, destinationLocationId: variables.destinationLocationId };
      storeUncertainStockSubmission(record);
      try { const result = await mutation.mutateAsync(submitted); clearUncertainStockSubmission(record); return result; } catch (error) { if (isDefinitiveStockSubmissionFailure(error)) clearUncertainStockSubmission(record); throw error; }
    },
    uncertainSubmission: siteId && user?.id ? readUncertainStockSubmission(user.id, siteId, "batch-transfer") : null,
    retryUncertain: async () => {
      if (!siteId || !user?.id) throw new Error("No active site or user");
      const record = readUncertainStockSubmission(user.id, siteId, "batch-transfer");
      if (!record || record.kind !== "batch-transfer") throw new Error("No uncertain transfer to retry");
      try {
        const result = await mutation.mutateAsync({ siteId: record.siteId, idempotencyKey: record.idempotencyKey, transfers: record.transfers, sourceLocationId: record.sourceLocationId, destinationLocationId: record.destinationLocationId });
        clearUncertainStockSubmission(record);
        return result;
      } catch (error) {
        if (isDefinitiveStockSubmissionFailure(error)) clearUncertainStockSubmission(record);
        throw error;
      }
    },
  };
}
