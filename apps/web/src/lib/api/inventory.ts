import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import {
  LocationType,
  LOCATION_ENDPOINTS,
  BoxBinInventory,
  RackInventory,
  CabinetInventory,
  SingleClawMachineInventory,
  DoubleClawMachineInventory,
  KeychainMachineInventory,
  FourCornerMachineInventory,
  PusherMachineInventory,
  NotAssignedInventory,
  InventoryRequest,
  Inventory,
  InventoryItem,
} from "@/types/api";
import { getLocationsByType } from "@/lib/api/locations";

// Helper to build inventory path
function getInventoryPath(locationType: LocationType, locationId: string): string {
  return `/api/${LOCATION_ENDPOINTS[locationType]}/${locationId}/inventory`;
}

// BoxBin Inventory

export async function getBoxBinInventory(
  boxBinId: string
): Promise<BoxBinInventory[]> {
  return apiGet<BoxBinInventory[]>(
    getInventoryPath(LocationType.BOX_BIN, boxBinId)
  );
}

export async function getBoxBinInventoryItem(
  boxBinId: string,
  inventoryId: string
): Promise<BoxBinInventory> {
  return apiGet<BoxBinInventory>(
    `${getInventoryPath(LocationType.BOX_BIN, boxBinId)}/${inventoryId}`
  );
}

export async function createBoxBinInventory(
  boxBinId: string,
  data: InventoryRequest
): Promise<BoxBinInventory> {
  return apiPost<BoxBinInventory, InventoryRequest>(
    getInventoryPath(LocationType.BOX_BIN, boxBinId),
    data
  );
}

export async function updateBoxBinInventory(
  boxBinId: string,
  inventoryId: string,
  data: InventoryRequest
): Promise<BoxBinInventory> {
  return apiPut<BoxBinInventory, InventoryRequest>(
    `${getInventoryPath(LocationType.BOX_BIN, boxBinId)}/${inventoryId}`,
    data
  );
}

export async function deleteBoxBinInventory(
  boxBinId: string,
  inventoryId: string
): Promise<void> {
  return apiDelete<void>(
    `${getInventoryPath(LocationType.BOX_BIN, boxBinId)}/${inventoryId}`
  );
}

// Rack Inventory

export async function getRackInventory(rackId: string): Promise<RackInventory[]> {
  return apiGet<RackInventory[]>(getInventoryPath(LocationType.RACK, rackId));
}

export async function getRackInventoryItem(
  rackId: string,
  inventoryId: string
): Promise<RackInventory> {
  return apiGet<RackInventory>(
    `${getInventoryPath(LocationType.RACK, rackId)}/${inventoryId}`
  );
}

export async function createRackInventory(
  rackId: string,
  data: InventoryRequest
): Promise<RackInventory> {
  return apiPost<RackInventory, InventoryRequest>(
    getInventoryPath(LocationType.RACK, rackId),
    data
  );
}

export async function updateRackInventory(
  rackId: string,
  inventoryId: string,
  data: InventoryRequest
): Promise<RackInventory> {
  return apiPut<RackInventory, InventoryRequest>(
    `${getInventoryPath(LocationType.RACK, rackId)}/${inventoryId}`,
    data
  );
}

export async function deleteRackInventory(
  rackId: string,
  inventoryId: string
): Promise<void> {
  return apiDelete<void>(
    `${getInventoryPath(LocationType.RACK, rackId)}/${inventoryId}`
  );
}

// Cabinet Inventory

export async function getCabinetInventory(
  cabinetId: string
): Promise<CabinetInventory[]> {
  return apiGet<CabinetInventory[]>(
    getInventoryPath(LocationType.CABINET, cabinetId)
  );
}

export async function getCabinetInventoryItem(
  cabinetId: string,
  inventoryId: string
): Promise<CabinetInventory> {
  return apiGet<CabinetInventory>(
    `${getInventoryPath(LocationType.CABINET, cabinetId)}/${inventoryId}`
  );
}

export async function createCabinetInventory(
  cabinetId: string,
  data: InventoryRequest
): Promise<CabinetInventory> {
  return apiPost<CabinetInventory, InventoryRequest>(
    getInventoryPath(LocationType.CABINET, cabinetId),
    data
  );
}

export async function updateCabinetInventory(
  cabinetId: string,
  inventoryId: string,
  data: InventoryRequest
): Promise<CabinetInventory> {
  return apiPut<CabinetInventory, InventoryRequest>(
    `${getInventoryPath(LocationType.CABINET, cabinetId)}/${inventoryId}`,
    data
  );
}

export async function deleteCabinetInventory(
  cabinetId: string,
  inventoryId: string
): Promise<void> {
  return apiDelete<void>(
    `${getInventoryPath(LocationType.CABINET, cabinetId)}/${inventoryId}`
  );
}

// SingleClawMachine Inventory

export async function getSingleClawMachineInventory(
  machineId: string
): Promise<SingleClawMachineInventory[]> {
  return apiGet<SingleClawMachineInventory[]>(
    getInventoryPath(LocationType.SINGLE_CLAW_MACHINE, machineId)
  );
}

export async function getSingleClawMachineInventoryItem(
  machineId: string,
  inventoryId: string
): Promise<SingleClawMachineInventory> {
  return apiGet<SingleClawMachineInventory>(
    `${getInventoryPath(LocationType.SINGLE_CLAW_MACHINE, machineId)}/${inventoryId}`
  );
}

export async function createSingleClawMachineInventory(
  machineId: string,
  data: InventoryRequest
): Promise<SingleClawMachineInventory> {
  return apiPost<SingleClawMachineInventory, InventoryRequest>(
    getInventoryPath(LocationType.SINGLE_CLAW_MACHINE, machineId),
    data
  );
}

export async function updateSingleClawMachineInventory(
  machineId: string,
  inventoryId: string,
  data: InventoryRequest
): Promise<SingleClawMachineInventory> {
  return apiPut<SingleClawMachineInventory, InventoryRequest>(
    `${getInventoryPath(LocationType.SINGLE_CLAW_MACHINE, machineId)}/${inventoryId}`,
    data
  );
}

export async function deleteSingleClawMachineInventory(
  machineId: string,
  inventoryId: string
): Promise<void> {
  return apiDelete<void>(
    `${getInventoryPath(LocationType.SINGLE_CLAW_MACHINE, machineId)}/${inventoryId}`
  );
}

// DoubleClawMachine Inventory

export async function getDoubleClawMachineInventory(
  machineId: string
): Promise<DoubleClawMachineInventory[]> {
  return apiGet<DoubleClawMachineInventory[]>(
    getInventoryPath(LocationType.DOUBLE_CLAW_MACHINE, machineId)
  );
}

export async function getDoubleClawMachineInventoryItem(
  machineId: string,
  inventoryId: string
): Promise<DoubleClawMachineInventory> {
  return apiGet<DoubleClawMachineInventory>(
    `${getInventoryPath(LocationType.DOUBLE_CLAW_MACHINE, machineId)}/${inventoryId}`
  );
}

export async function createDoubleClawMachineInventory(
  machineId: string,
  data: InventoryRequest
): Promise<DoubleClawMachineInventory> {
  return apiPost<DoubleClawMachineInventory, InventoryRequest>(
    getInventoryPath(LocationType.DOUBLE_CLAW_MACHINE, machineId),
    data
  );
}

export async function updateDoubleClawMachineInventory(
  machineId: string,
  inventoryId: string,
  data: InventoryRequest
): Promise<DoubleClawMachineInventory> {
  return apiPut<DoubleClawMachineInventory, InventoryRequest>(
    `${getInventoryPath(LocationType.DOUBLE_CLAW_MACHINE, machineId)}/${inventoryId}`,
    data
  );
}

export async function deleteDoubleClawMachineInventory(
  machineId: string,
  inventoryId: string
): Promise<void> {
  return apiDelete<void>(
    `${getInventoryPath(LocationType.DOUBLE_CLAW_MACHINE, machineId)}/${inventoryId}`
  );
}

// KeychainMachine Inventory

export async function getKeychainMachineInventory(
  machineId: string
): Promise<KeychainMachineInventory[]> {
  return apiGet<KeychainMachineInventory[]>(
    getInventoryPath(LocationType.KEYCHAIN_MACHINE, machineId)
  );
}

export async function getKeychainMachineInventoryItem(
  machineId: string,
  inventoryId: string
): Promise<KeychainMachineInventory> {
  return apiGet<KeychainMachineInventory>(
    `${getInventoryPath(LocationType.KEYCHAIN_MACHINE, machineId)}/${inventoryId}`
  );
}

export async function createKeychainMachineInventory(
  machineId: string,
  data: InventoryRequest
): Promise<KeychainMachineInventory> {
  return apiPost<KeychainMachineInventory, InventoryRequest>(
    getInventoryPath(LocationType.KEYCHAIN_MACHINE, machineId),
    data
  );
}

export async function updateKeychainMachineInventory(
  machineId: string,
  inventoryId: string,
  data: InventoryRequest
): Promise<KeychainMachineInventory> {
  return apiPut<KeychainMachineInventory, InventoryRequest>(
    `${getInventoryPath(LocationType.KEYCHAIN_MACHINE, machineId)}/${inventoryId}`,
    data
  );
}

export async function deleteKeychainMachineInventory(
  machineId: string,
  inventoryId: string
): Promise<void> {
  return apiDelete<void>(
    `${getInventoryPath(LocationType.KEYCHAIN_MACHINE, machineId)}/${inventoryId}`
  );
}

// FourCornerMachine Inventory

export async function getFourCornerMachineInventory(
  machineId: string
): Promise<FourCornerMachineInventory[]> {
  return apiGet<FourCornerMachineInventory[]>(
    getInventoryPath(LocationType.FOUR_CORNER_MACHINE, machineId)
  );
}

export async function getFourCornerMachineInventoryItem(
  machineId: string,
  inventoryId: string
): Promise<FourCornerMachineInventory> {
  return apiGet<FourCornerMachineInventory>(
    `${getInventoryPath(LocationType.FOUR_CORNER_MACHINE, machineId)}/${inventoryId}`
  );
}

export async function createFourCornerMachineInventory(
  machineId: string,
  data: InventoryRequest
): Promise<FourCornerMachineInventory> {
  return apiPost<FourCornerMachineInventory, InventoryRequest>(
    getInventoryPath(LocationType.FOUR_CORNER_MACHINE, machineId),
    data
  );
}

export async function updateFourCornerMachineInventory(
  machineId: string,
  inventoryId: string,
  data: InventoryRequest
): Promise<FourCornerMachineInventory> {
  return apiPut<FourCornerMachineInventory, InventoryRequest>(
    `${getInventoryPath(LocationType.FOUR_CORNER_MACHINE, machineId)}/${inventoryId}`,
    data
  );
}

export async function deleteFourCornerMachineInventory(
  machineId: string,
  inventoryId: string
): Promise<void> {
  return apiDelete<void>(
    `${getInventoryPath(LocationType.FOUR_CORNER_MACHINE, machineId)}/${inventoryId}`
  );
}

// PusherMachine Inventory

export async function getPusherMachineInventory(
  machineId: string
): Promise<PusherMachineInventory[]> {
  return apiGet<PusherMachineInventory[]>(
    getInventoryPath(LocationType.PUSHER_MACHINE, machineId)
  );
}

export async function getPusherMachineInventoryItem(
  machineId: string,
  inventoryId: string
): Promise<PusherMachineInventory> {
  return apiGet<PusherMachineInventory>(
    `${getInventoryPath(LocationType.PUSHER_MACHINE, machineId)}/${inventoryId}`
  );
}

export async function createPusherMachineInventory(
  machineId: string,
  data: InventoryRequest
): Promise<PusherMachineInventory> {
  return apiPost<PusherMachineInventory, InventoryRequest>(
    getInventoryPath(LocationType.PUSHER_MACHINE, machineId),
    data
  );
}

export async function updatePusherMachineInventory(
  machineId: string,
  inventoryId: string,
  data: InventoryRequest
): Promise<PusherMachineInventory> {
  return apiPut<PusherMachineInventory, InventoryRequest>(
    `${getInventoryPath(LocationType.PUSHER_MACHINE, machineId)}/${inventoryId}`,
    data
  );
}

export async function deletePusherMachineInventory(
  machineId: string,
  inventoryId: string
): Promise<void> {
  return apiDelete<void>(
    `${getInventoryPath(LocationType.PUSHER_MACHINE, machineId)}/${inventoryId}`
  );
}

// NotAssigned Inventory

export async function getNotAssignedInventory(): Promise<NotAssignedInventory[]> {
  return apiGet<NotAssignedInventory[]>("/api/not-assigned/inventory");
}

export async function getNotAssignedInventoryByProduct(
  productId: string
): Promise<NotAssignedInventory[]> {
  return apiGet<NotAssignedInventory[]>(
    `/api/not-assigned/inventory/by-product/${productId}`
  );
}

export async function createNotAssignedInventory(
  data: InventoryRequest
): Promise<NotAssignedInventory> {
  return apiPost<NotAssignedInventory, InventoryRequest>(
    "/api/not-assigned/inventory",
    data
  );
}

export async function updateNotAssignedInventory(
  inventoryId: string,
  data: InventoryRequest
): Promise<NotAssignedInventory> {
  return apiPut<NotAssignedInventory, InventoryRequest>(
    `/api/not-assigned/inventory/${inventoryId}`,
    data
  );
}

export async function deleteNotAssignedInventory(
  inventoryId: string
): Promise<void> {
  return apiDelete<void>(`/api/not-assigned/inventory/${inventoryId}`);
}

// Generic inventory getter by location type
export async function getInventoryByLocation(
  locationType: LocationType,
  locationId: string
): Promise<Inventory[]> {
  switch (locationType) {
    case LocationType.BOX_BIN:
      return getBoxBinInventory(locationId);
    case LocationType.RACK:
      return getRackInventory(locationId);
    case LocationType.CABINET:
      return getCabinetInventory(locationId);
    case LocationType.SINGLE_CLAW_MACHINE:
      return getSingleClawMachineInventory(locationId);
    case LocationType.DOUBLE_CLAW_MACHINE:
      return getDoubleClawMachineInventory(locationId);
    case LocationType.KEYCHAIN_MACHINE:
      return getKeychainMachineInventory(locationId);
    case LocationType.FOUR_CORNER_MACHINE:
      return getFourCornerMachineInventory(locationId);
    case LocationType.PUSHER_MACHINE:
      return getPusherMachineInventory(locationId);
    default:
      throw new Error(`Unknown location type: ${locationType}`);
  }
}

export interface InventoryLocationEntry {
  inventoryId: string;
  item: InventoryItem;
  quantity: number;
  locationType: LocationType;
  locationId: string;
  locationCode: string;
  locationLabel: string;
}

const ALL_LOCATION_TYPES: LocationType[] = [
  LocationType.BOX_BIN,
  LocationType.RACK,
  LocationType.CABINET,
  LocationType.SINGLE_CLAW_MACHINE,
  LocationType.DOUBLE_CLAW_MACHINE,
  LocationType.KEYCHAIN_MACHINE,
  LocationType.FOUR_CORNER_MACHINE,
  LocationType.PUSHER_MACHINE,
];

function formatLocationType(locationType: LocationType): string {
  return locationType
    .split("_")
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(" ");
}

function getLocationDetails(
  locationType: LocationType,
  inventory: Inventory
): { locationId: string; locationCode: string } {
  switch (locationType) {
    case LocationType.BOX_BIN: {
      const inv = inventory as BoxBinInventory;
      return { locationId: inv.boxBinId, locationCode: inv.boxBinCode };
    }
    case LocationType.RACK: {
      const inv = inventory as RackInventory;
      return { locationId: inv.rackId, locationCode: inv.rackCode };
    }
    case LocationType.CABINET: {
      const inv = inventory as CabinetInventory;
      return { locationId: inv.cabinetId, locationCode: inv.cabinetCode };
    }
    case LocationType.SINGLE_CLAW_MACHINE: {
      const inv = inventory as SingleClawMachineInventory;
      return {
        locationId: inv.singleClawMachineId,
        locationCode: inv.singleClawMachineCode,
      };
    }
    case LocationType.DOUBLE_CLAW_MACHINE: {
      const inv = inventory as DoubleClawMachineInventory;
      return {
        locationId: inv.doubleClawMachineId,
        locationCode: inv.doubleClawMachineCode,
      };
    }
    case LocationType.KEYCHAIN_MACHINE: {
      const inv = inventory as KeychainMachineInventory;
      return {
        locationId: inv.keychainMachineId,
        locationCode: inv.keychainMachineCode,
      };
    }
    case LocationType.FOUR_CORNER_MACHINE: {
      const inv = inventory as FourCornerMachineInventory;
      return {
        locationId: inv.fourCornerMachineId,
        locationCode: inv.fourCornerMachineCode,
      };
    }
    case LocationType.PUSHER_MACHINE: {
      const inv = inventory as PusherMachineInventory;
      return {
        locationId: inv.pusherMachineId,
        locationCode: inv.pusherMachineCode,
      };
    }
    default:
      throw new Error(`Unknown location type: ${locationType}`);
  }
}

export async function getInventoryEntriesByItemId(
  itemId: string
): Promise<InventoryLocationEntry[]> {
  const entries: InventoryLocationEntry[] = [];

  // Fetch NOT_ASSIGNED inventory first (different API pattern - no location)
  const notAssignedInventories = await getNotAssignedInventoryByProduct(itemId);
  for (const inv of notAssignedInventories) {
    entries.push({
      inventoryId: inv.id,
      item: inv.item,
      quantity: inv.quantity ?? 0,
      locationType: LocationType.NOT_ASSIGNED,
      locationId: "",
      locationCode: "-",
      locationLabel: "Not Assigned",
    });
  }

  // Fetch location-based inventory
  const locationsByType = await Promise.all(
    ALL_LOCATION_TYPES.map(async (locationType) => {
      const locations = await getLocationsByType(locationType);
      const ids = (locations as Array<{ id: string }>).map((l) => l.id);
      return { locationType, ids };
    })
  );

  await Promise.all(
    locationsByType.flatMap(({ locationType, ids }) =>
      ids.map(async (locationId) => {
        const inventories = await getInventoryByLocation(locationType, locationId);
        for (const inv of inventories) {
          if (inv.item.id !== itemId) continue;
          const { locationId: invLocationId, locationCode } =
            getLocationDetails(locationType, inv);
          entries.push({
            inventoryId: inv.id,
            item: inv.item,
            quantity: inv.quantity ?? 0,
            locationType,
            locationId: invLocationId ?? locationId,
            locationCode,
            locationLabel: `${formatLocationType(locationType)} ${locationCode}`,
          });
        }
      })
    )
  );

  return entries.sort((a, b) => a.locationLabel.localeCompare(b.locationLabel));
}
