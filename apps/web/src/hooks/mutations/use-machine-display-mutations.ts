"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import {
  setSiteMachineDisplay,
  setSiteMachineDisplayBatch,
  clearSiteMachineDisplay,
  clearSiteDisplayById,
  swapSiteMachineDisplay,
  batchSwapSiteDisplay,
  batchClearSiteDisplays,
  renewSiteDisplays,
  deleteSiteDisplayHistory,
} from "@/lib/api/site-machine-displays";
import type {
  SwapMachineDisplayRequest,
  BatchDisplaySwapRequest,
  BatchClearDisplaysRequest,
  RenewDisplayRequest,
} from "@/lib/api/machine-displays";
import { SetMachineDisplayRequest, SetMachineDisplayBatchRequest, MachineDisplay, LocationType } from "@/types/api";

// A mutation attempted with no resolved site throws instead of calling an undefined site path;
// siteId is captured from useCurrentSite() at the hook level and threaded through each
// mutationFn's variables so onSuccess can invalidate that same site's query key.

/**
 * Mutation to set or swap a machine display
 */
export function useSetMachineDisplayMutation() {
  const queryClient = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    MachineDisplay,
    Error,
    { payload: SetMachineDisplayRequest; siteId: string | undefined }
  >({
    mutationFn: ({ payload, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return setSiteMachineDisplay(originSiteId, payload);
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays", variables.siteId] });
    },
  });

  return {
    ...mutation,
    mutate: (payload: SetMachineDisplayRequest, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ payload, siteId }, options),
    mutateAsync: (payload: SetMachineDisplayRequest, options?: Parameters<typeof mutation.mutateAsync>[1]) =>
      mutation.mutateAsync({ payload, siteId }, options),
  };
}

/**
 * Mutation to add multiple products to a machine display in a single request
 */
export function useSetMachineDisplayBatchMutation() {
  const queryClient = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    MachineDisplay[],
    Error,
    { payload: SetMachineDisplayBatchRequest; siteId: string | undefined }
  >({
    mutationFn: ({ payload, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return setSiteMachineDisplayBatch(originSiteId, payload);
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays", variables.siteId] });
    },
  });

  return {
    ...mutation,
    mutate: (payload: SetMachineDisplayBatchRequest, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ payload, siteId }, options),
    mutateAsync: (payload: SetMachineDisplayBatchRequest, options?: Parameters<typeof mutation.mutateAsync>[1]) =>
      mutation.mutateAsync({ payload, siteId }, options),
  };
}

/**
 * Mutation to clear a machine display (all displays for a machine)
 */
export function useClearMachineDisplayMutation() {
  const queryClient = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    void,
    Error,
    { locationType: LocationType; machineId: string; siteId: string | undefined }
  >({
    mutationFn: ({ locationType, machineId, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return clearSiteMachineDisplay(originSiteId, locationType, machineId);
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays", variables.siteId] });
    },
  });

  return {
    ...mutation,
    mutate: (
      variables: { locationType: LocationType; machineId: string },
      options?: Parameters<typeof mutation.mutate>[1]
    ) => mutation.mutate({ ...variables, siteId }, options),
    mutateAsync: (
      variables: { locationType: LocationType; machineId: string },
      options?: Parameters<typeof mutation.mutateAsync>[1]
    ) => mutation.mutateAsync({ ...variables, siteId }, options),
  };
}

/**
 * Mutation to clear a specific display by ID
 */
export function useClearDisplayByIdMutation() {
  const queryClient = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<void, Error, { displayId: string; siteId: string | undefined }>({
    mutationFn: ({ displayId, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return clearSiteDisplayById(originSiteId, displayId);
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays", variables.siteId] });
    },
  });

  return {
    ...mutation,
    mutate: (
      variables: { displayId: string },
      options?: Parameters<typeof mutation.mutate>[1]
    ) => mutation.mutate({ ...variables, siteId }, options),
    mutateAsync: (
      variables: { displayId: string },
      options?: Parameters<typeof mutation.mutateAsync>[1]
    ) => mutation.mutateAsync({ ...variables, siteId }, options),
  };
}

/**
 * Mutation to atomically swap one displayed product for another.
 * Delegates to the backend /swap endpoint which handles both ops in one transaction
 * and writes a DISPLAY_SWAP audit log entry.
 */
export function useSwapDisplayMutation() {
  const queryClient = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    MachineDisplay[],
    Error,
    { payload: SwapMachineDisplayRequest; siteId: string | undefined }
  >({
    mutationFn: ({ payload, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return swapSiteMachineDisplay(originSiteId, payload);
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays", variables.siteId] });
    },
  });

  return {
    ...mutation,
    mutate: (payload: SwapMachineDisplayRequest, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ payload, siteId }, options),
    mutateAsync: (payload: SwapMachineDisplayRequest, options?: Parameters<typeof mutation.mutateAsync>[1]) =>
      mutation.mutateAsync({ payload, siteId }, options),
  };
}

/**
 * Mutation for batch display swap operations.
 * Handles both swap modes in a single transaction:
 * 1. Swap with products - remove displays and add new products
 * 2. Swap with another machine - trade displays between two machines
 * Creates a single DISPLAY_SWAP audit log entry with all changes.
 */
export function useBatchSwapDisplayMutation() {
  const queryClient = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    MachineDisplay[],
    Error,
    { payload: BatchDisplaySwapRequest; siteId: string | undefined }
  >({
    mutationFn: ({ payload, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return batchSwapSiteDisplay(originSiteId, payload);
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays", variables.siteId] });
    },
  });

  return {
    ...mutation,
    mutate: (payload: BatchDisplaySwapRequest, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ payload, siteId }, options),
    mutateAsync: (payload: BatchDisplaySwapRequest, options?: Parameters<typeof mutation.mutateAsync>[1]) =>
      mutation.mutateAsync({ payload, siteId }, options),
  };
}

/**
 * Mutation to batch-clear multiple displays on a machine in one transaction.
 * Produces a single DISPLAY_REMOVED audit log entry for all cleared products.
 */
export function useBatchClearDisplaysMutation() {
  const queryClient = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    MachineDisplay[],
    Error,
    { payload: BatchClearDisplaysRequest; siteId: string | undefined }
  >({
    mutationFn: ({ payload, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return batchClearSiteDisplays(originSiteId, payload);
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays", variables.siteId] });
    },
  });

  return {
    ...mutation,
    mutate: (payload: BatchClearDisplaysRequest, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ payload, siteId }, options),
    mutateAsync: (payload: BatchClearDisplaysRequest, options?: Parameters<typeof mutation.mutateAsync>[1]) =>
      mutation.mutateAsync({ payload, siteId }, options),
  };
}

/**
 * Mutation to renew display records - ends current displays and creates new ones
 * with fresh startedAt timestamps for the same products.
 * Used when restocking the same product to reset tracking.
 */
export function useRenewDisplayMutation() {
  const queryClient = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<
    MachineDisplay[],
    Error,
    { payload: RenewDisplayRequest; siteId: string | undefined }
  >({
    mutationFn: ({ payload, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return renewSiteDisplays(originSiteId, payload);
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays", variables.siteId] });
    },
  });

  return {
    ...mutation,
    mutate: (payload: RenewDisplayRequest, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ payload, siteId }, options),
    mutateAsync: (payload: RenewDisplayRequest, options?: Parameters<typeof mutation.mutateAsync>[1]) =>
      mutation.mutateAsync({ payload, siteId }, options),
  };
}

/**
 * Mutation to delete a display history record (Admin only)
 */
export function useDeleteDisplayHistoryMutation() {
  const queryClient = useQueryClient();
  const { siteId } = useCurrentSite();

  const mutation = useMutation<void, Error, { displayId: string; siteId: string | undefined }>({
    mutationFn: ({ displayId, siteId: originSiteId }) => {
      if (!originSiteId) throw new Error("No active site");
      return deleteSiteDisplayHistory(originSiteId, displayId);
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays", variables.siteId] });
    },
  });

  return {
    ...mutation,
    mutate: (displayId: string, options?: Parameters<typeof mutation.mutate>[1]) =>
      mutation.mutate({ displayId, siteId }, options),
    mutateAsync: (displayId: string, options?: Parameters<typeof mutation.mutateAsync>[1]) =>
      mutation.mutateAsync({ displayId, siteId }, options),
  };
}
