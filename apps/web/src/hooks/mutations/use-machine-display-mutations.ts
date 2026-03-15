import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  setMachineDisplay,
  setMachineDisplayBatch,
  clearMachineDisplay,
  clearDisplayById,
  swapMachineDisplay,
  batchSwapDisplay,
  renewDisplays,
  deleteDisplayHistory,
  type SwapMachineDisplayRequest,
  type BatchDisplaySwapRequest,
  type RenewDisplayRequest,
} from "@/lib/api/machine-displays";
import { SetMachineDisplayRequest, SetMachineDisplayBatchRequest, MachineDisplay, LocationType } from "@/types/api";

/**
 * Mutation to set or swap a machine display
 */
export function useSetMachineDisplayMutation() {
  const queryClient = useQueryClient();

  return useMutation<MachineDisplay, Error, SetMachineDisplayRequest>({
    mutationFn: setMachineDisplay,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays"] });
    },
  });
}

/**
 * Mutation to add multiple products to a machine display in a single request
 */
export function useSetMachineDisplayBatchMutation() {
  const queryClient = useQueryClient();

  return useMutation<MachineDisplay[], Error, SetMachineDisplayBatchRequest>({
    mutationFn: setMachineDisplayBatch,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays"] });
    },
  });
}

/**
 * Mutation to clear a machine display (all displays for a machine)
 */
export function useClearMachineDisplayMutation() {
  const queryClient = useQueryClient();

  return useMutation<
    void,
    Error,
    { locationType: LocationType; machineId: string; actorId?: string }
  >({
    mutationFn: ({ locationType, machineId, actorId }) =>
      clearMachineDisplay(locationType, machineId, actorId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays"] });
    },
  });
}

/**
 * Mutation to clear a specific display by ID
 */
export function useClearDisplayByIdMutation() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, { displayId: string; actorId?: string }>({
    mutationFn: ({ displayId, actorId }) => clearDisplayById(displayId, actorId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays"] });
    },
  });
}

/**
 * Mutation to atomically swap one displayed product for another.
 * Delegates to the backend /swap endpoint which handles both ops in one transaction
 * and writes a DISPLAY_SWAP audit log entry.
 */
export function useSwapDisplayMutation() {
  const queryClient = useQueryClient();

  return useMutation<MachineDisplay[], Error, SwapMachineDisplayRequest>({
    mutationFn: swapMachineDisplay,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays"] });
    },
  });
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

  return useMutation<MachineDisplay[], Error, BatchDisplaySwapRequest>({
    mutationFn: batchSwapDisplay,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays"] });
    },
  });
}

/**
 * Mutation to renew display records - ends current displays and creates new ones
 * with fresh startedAt timestamps for the same products.
 * Used when restocking the same product to reset tracking.
 */
export function useRenewDisplayMutation() {
  const queryClient = useQueryClient();

  return useMutation<MachineDisplay[], Error, RenewDisplayRequest>({
    mutationFn: renewDisplays,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays"] });
    },
  });
}

/**
 * Mutation to delete a display history record (Admin only)
 */
export function useDeleteDisplayHistoryMutation() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, string>({
    mutationFn: deleteDisplayHistory,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays"] });
    },
  });
}
