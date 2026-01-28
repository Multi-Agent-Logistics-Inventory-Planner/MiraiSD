"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import type {
  LocationType,
  InventoryRequest,
  Inventory,
  StorageLocation,
} from "@/types/api";
import {
  createBoxBin,
  updateBoxBin,
  deleteBoxBin,
  createRack,
  updateRack,
  deleteRack,
  createCabinet,
  updateCabinet,
  deleteCabinet,
  createSingleClawMachine,
  updateSingleClawMachine,
  deleteSingleClawMachine,
  createDoubleClawMachine,
  updateDoubleClawMachine,
  deleteDoubleClawMachine,
  createKeychainMachine,
  updateKeychainMachine,
  deleteKeychainMachine,
  createFourCornerMachine,
  updateFourCornerMachine,
  deleteFourCornerMachine,
  createPusherMachine,
  updatePusherMachine,
  deletePusherMachine,
} from "@/lib/api/locations";
import {
  createBoxBinInventory,
  updateBoxBinInventory,
  deleteBoxBinInventory,
  createRackInventory,
  updateRackInventory,
  deleteRackInventory,
  createCabinetInventory,
  updateCabinetInventory,
  deleteCabinetInventory,
  createSingleClawMachineInventory,
  updateSingleClawMachineInventory,
  deleteSingleClawMachineInventory,
  createDoubleClawMachineInventory,
  updateDoubleClawMachineInventory,
  deleteDoubleClawMachineInventory,
  createKeychainMachineInventory,
  updateKeychainMachineInventory,
  deleteKeychainMachineInventory,
  createFourCornerMachineInventory,
  updateFourCornerMachineInventory,
  deleteFourCornerMachineInventory,
  createPusherMachineInventory,
  updatePusherMachineInventory,
  deletePusherMachineInventory,
} from "@/lib/api/inventory";

type LocationPayload = Record<string, string>;

function invalidateLocations(qc: ReturnType<typeof useQueryClient>, locationType: LocationType) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: ["locations", locationType] }),
    qc.invalidateQueries({ queryKey: ["locationsWithCounts", locationType] }),
  ]);
}

function invalidateLocationInventory(
  qc: ReturnType<typeof useQueryClient>,
  locationType: LocationType,
  locationId: string
) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: ["locationInventory", locationType, locationId] }),
    qc.invalidateQueries({ queryKey: ["locationsWithCounts", locationType] }),
    qc.invalidateQueries({ queryKey: ["inventoryTotals"] }),
    qc.invalidateQueries({ queryKey: ["dashboardStats"] }),
  ]);
}

export function useCreateLocationMutation(locationType: LocationType) {
  const qc = useQueryClient();

  return useMutation<StorageLocation, Error, LocationPayload>({
    mutationFn: async (payload) => {
      switch (locationType) {
        case "BOX_BIN":
          return createBoxBin(payload as any);
        case "RACK":
          return createRack(payload as any);
        case "CABINET":
          return createCabinet(payload as any);
        case "SINGLE_CLAW_MACHINE":
          return createSingleClawMachine(payload as any);
        case "DOUBLE_CLAW_MACHINE":
          return createDoubleClawMachine(payload as any);
        case "KEYCHAIN_MACHINE":
          return createKeychainMachine(payload as any);
        case "FOUR_CORNER_MACHINE":
          return createFourCornerMachine(payload as any);
        case "PUSHER_MACHINE":
          return createPusherMachine(payload as any);
        default:
          throw new Error(`Unsupported location type: ${locationType}`);
      }
    },
    onSuccess: async () => {
      await invalidateLocations(qc, locationType);
    },
  });
}

export function useUpdateLocationMutation(locationType: LocationType) {
  const qc = useQueryClient();

  return useMutation<StorageLocation, Error, { id: string; payload: LocationPayload }>({
    mutationFn: async ({ id, payload }) => {
      switch (locationType) {
        case "BOX_BIN":
          return updateBoxBin(id, payload as any);
        case "RACK":
          return updateRack(id, payload as any);
        case "CABINET":
          return updateCabinet(id, payload as any);
        case "SINGLE_CLAW_MACHINE":
          return updateSingleClawMachine(id, payload as any);
        case "DOUBLE_CLAW_MACHINE":
          return updateDoubleClawMachine(id, payload as any);
        case "KEYCHAIN_MACHINE":
          return updateKeychainMachine(id, payload as any);
        case "FOUR_CORNER_MACHINE":
          return updateFourCornerMachine(id, payload as any);
        case "PUSHER_MACHINE":
          return updatePusherMachine(id, payload as any);
        default:
          throw new Error(`Unsupported location type: ${locationType}`);
      }
    },
    onSuccess: async () => {
      await invalidateLocations(qc, locationType);
    },
  });
}

export function useDeleteLocationMutation(locationType: LocationType) {
  const qc = useQueryClient();

  return useMutation<void, Error, { id: string }>({
    mutationFn: async ({ id }) => {
      switch (locationType) {
        case "BOX_BIN":
          return deleteBoxBin(id);
        case "RACK":
          return deleteRack(id);
        case "CABINET":
          return deleteCabinet(id);
        case "SINGLE_CLAW_MACHINE":
          return deleteSingleClawMachine(id);
        case "DOUBLE_CLAW_MACHINE":
          return deleteDoubleClawMachine(id);
        case "KEYCHAIN_MACHINE":
          return deleteKeychainMachine(id);
        case "FOUR_CORNER_MACHINE":
          return deleteFourCornerMachine(id);
        case "PUSHER_MACHINE":
          return deletePusherMachine(id);
        default:
          throw new Error(`Unsupported location type: ${locationType}`);
      }
    },
    onSuccess: async () => {
      await invalidateLocations(qc, locationType);
    },
  });
}

export function useCreateInventoryMutation(locationType: LocationType, locationId: string) {
  const qc = useQueryClient();

  return useMutation<Inventory, Error, InventoryRequest>({
    mutationFn: async (payload) => {
      switch (locationType) {
        case "BOX_BIN":
          return (await createBoxBinInventory(locationId, payload)) as any;
        case "RACK":
          return (await createRackInventory(locationId, payload)) as any;
        case "CABINET":
          return (await createCabinetInventory(locationId, payload)) as any;
        case "SINGLE_CLAW_MACHINE":
          return (await createSingleClawMachineInventory(locationId, payload)) as any;
        case "DOUBLE_CLAW_MACHINE":
          return (await createDoubleClawMachineInventory(locationId, payload)) as any;
        case "KEYCHAIN_MACHINE":
          return (await createKeychainMachineInventory(locationId, payload)) as any;
        case "FOUR_CORNER_MACHINE":
          return (await createFourCornerMachineInventory(locationId, payload)) as any;
        case "PUSHER_MACHINE":
          return (await createPusherMachineInventory(locationId, payload)) as any;
        default:
          throw new Error(`Unsupported location type: ${locationType}`);
      }
    },
    onSuccess: async () => {
      await invalidateLocationInventory(qc, locationType, locationId);
    },
  });
}

export function useUpdateInventoryMutation(
  locationType: LocationType,
  locationId: string
) {
  const qc = useQueryClient();

  return useMutation<Inventory, Error, { inventoryId: string; payload: InventoryRequest }>({
    mutationFn: async ({ inventoryId, payload }) => {
      switch (locationType) {
        case "BOX_BIN":
          return (await updateBoxBinInventory(locationId, inventoryId, payload)) as any;
        case "RACK":
          return (await updateRackInventory(locationId, inventoryId, payload)) as any;
        case "CABINET":
          return (await updateCabinetInventory(locationId, inventoryId, payload)) as any;
        case "SINGLE_CLAW_MACHINE":
          return (await updateSingleClawMachineInventory(locationId, inventoryId, payload)) as any;
        case "DOUBLE_CLAW_MACHINE":
          return (await updateDoubleClawMachineInventory(locationId, inventoryId, payload)) as any;
        case "KEYCHAIN_MACHINE":
          return (await updateKeychainMachineInventory(locationId, inventoryId, payload)) as any;
        case "FOUR_CORNER_MACHINE":
          return (await updateFourCornerMachineInventory(locationId, inventoryId, payload)) as any;
        case "PUSHER_MACHINE":
          return (await updatePusherMachineInventory(locationId, inventoryId, payload)) as any;
        default:
          throw new Error(`Unsupported location type: ${locationType}`);
      }
    },
    onSuccess: async () => {
      await invalidateLocationInventory(qc, locationType, locationId);
    },
  });
}

export function useDeleteInventoryMutation(
  locationType: LocationType,
  locationId: string
) {
  const qc = useQueryClient();

  return useMutation<void, Error, { inventoryId: string }>({
    mutationFn: async ({ inventoryId }) => {
      switch (locationType) {
        case "BOX_BIN":
          return deleteBoxBinInventory(locationId, inventoryId);
        case "RACK":
          return deleteRackInventory(locationId, inventoryId);
        case "CABINET":
          return deleteCabinetInventory(locationId, inventoryId);
        case "SINGLE_CLAW_MACHINE":
          return deleteSingleClawMachineInventory(locationId, inventoryId);
        case "DOUBLE_CLAW_MACHINE":
          return deleteDoubleClawMachineInventory(locationId, inventoryId);
        case "KEYCHAIN_MACHINE":
          return deleteKeychainMachineInventory(locationId, inventoryId);
        case "FOUR_CORNER_MACHINE":
          return deleteFourCornerMachineInventory(locationId, inventoryId);
        case "PUSHER_MACHINE":
          return deletePusherMachineInventory(locationId, inventoryId);
        default:
          throw new Error(`Unsupported location type: ${locationType}`);
      }
    },
    onSuccess: async () => {
      await invalidateLocationInventory(qc, locationType, locationId);
    },
  });
}

