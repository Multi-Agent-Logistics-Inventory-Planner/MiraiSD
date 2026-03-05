import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  setMachineDisplay,
  setMachineDisplayBatch,
  clearMachineDisplay,
  clearDisplayById,
} from "@/lib/api/machine-displays";
import { SetMachineDisplayRequest, SetMachineDisplayBatchRequest, MachineDisplay, LocationType } from "@/types/api";

interface SwapDisplayVariables {
  outgoingDisplayId: string;
  incomingProductId: string;
  locationType: LocationType;
  machineId: string;
  actorId?: string;
}

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
 * Ends the outgoing display record then creates a new one for the incoming product.
 */
export function useSwapDisplayMutation() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, SwapDisplayVariables>({
    mutationFn: async ({ outgoingDisplayId, incomingProductId, locationType, machineId, actorId }) => {
      await clearDisplayById(outgoingDisplayId, actorId);
      await setMachineDisplayBatch({ locationType, machineId, productIds: [incomingProductId], actorId });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["machine-displays"] });
    },
  });
}
