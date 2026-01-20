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
  InventoryRequest,
  Inventory,
} from "@/types/api";

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
    default:
      throw new Error(`Unknown location type: ${locationType}`);
  }
}
