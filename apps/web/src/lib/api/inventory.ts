import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import {
  LocationType,
  LOCATION_ENDPOINTS,
  BoxBinInventory,
  RackInventory,
  CabinetInventory,
  SingleClawMachineInventory,
  DoubleClawMachineInventory,
  FourCornerMachineInventory,
  PusherMachineInventory,
  WindowInventory,
  NotAssignedInventory,
  InventoryRequest,
  Inventory,
  InventoryItem,
  ProductInventoryResponse,
  InventoryTotal,
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

// Window Inventory

export async function getWindowInventory(
  windowId: string
): Promise<WindowInventory[]> {
  return apiGet<WindowInventory[]>(
    getInventoryPath(LocationType.WINDOW, windowId)
  );
}

export async function getWindowInventoryItem(
  windowId: string,
  inventoryId: string
): Promise<WindowInventory> {
  return apiGet<WindowInventory>(
    `${getInventoryPath(LocationType.WINDOW, windowId)}/${inventoryId}`
  );
}

export async function createWindowInventory(
  windowId: string,
  data: InventoryRequest
): Promise<WindowInventory> {
  return apiPost<WindowInventory, InventoryRequest>(
    getInventoryPath(LocationType.WINDOW, windowId),
    data
  );
}

export async function updateWindowInventory(
  windowId: string,
  inventoryId: string,
  data: InventoryRequest
): Promise<WindowInventory> {
  return apiPut<WindowInventory, InventoryRequest>(
    `${getInventoryPath(LocationType.WINDOW, windowId)}/${inventoryId}`,
    data
  );
}

export async function deleteWindowInventory(
  windowId: string,
  inventoryId: string
): Promise<void> {
  return apiDelete<void>(
    `${getInventoryPath(LocationType.WINDOW, windowId)}/${inventoryId}`
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
      throw new Error("Keychain Machine is display-only and does not support inventory");
    case LocationType.FOUR_CORNER_MACHINE:
      return getFourCornerMachineInventory(locationId);
    case LocationType.PUSHER_MACHINE:
      return getPusherMachineInventory(locationId);
    case LocationType.WINDOW:
      return getWindowInventory(locationId);
    case LocationType.NOT_ASSIGNED:
      // NOT_ASSIGNED doesn't have a locationId
      return getNotAssignedInventory() as unknown as Inventory[];
    default:
      throw new Error(`Unknown location type: ${locationType}`);
  }
}

// Generic inventory creator by location type
export async function createInventory(
  locationType: LocationType,
  locationId: string,
  data: InventoryRequest
): Promise<Inventory> {
  switch (locationType) {
    case LocationType.BOX_BIN:
      return createBoxBinInventory(locationId, data);
    case LocationType.RACK:
      return createRackInventory(locationId, data);
    case LocationType.CABINET:
      return createCabinetInventory(locationId, data);
    case LocationType.SINGLE_CLAW_MACHINE:
      return createSingleClawMachineInventory(locationId, data);
    case LocationType.DOUBLE_CLAW_MACHINE:
      return createDoubleClawMachineInventory(locationId, data);
    case LocationType.KEYCHAIN_MACHINE:
      throw new Error("Keychain Machine is display-only and does not support inventory");
    case LocationType.FOUR_CORNER_MACHINE:
      return createFourCornerMachineInventory(locationId, data);
    case LocationType.PUSHER_MACHINE:
      return createPusherMachineInventory(locationId, data);
    case LocationType.WINDOW:
      return createWindowInventory(locationId, data);
    case LocationType.NOT_ASSIGNED:
      // NOT_ASSIGNED doesn't have a locationId, so we ignore it
      return createNotAssignedInventory(data) as unknown as Inventory;
    default:
      throw new Error(`Unknown location type: ${locationType}`);
  }
}

/**
 * Fetch all inventory entries for a product across all location types in a single request.
 *
 * @param productId The product ID to look up
 * @returns Product inventory response with all entries
 */
export async function getProductInventoryEntries(
  productId: string
): Promise<ProductInventoryResponse> {
  return apiGet<ProductInventoryResponse>(`/api/inventory/by-product/${productId}`);
}

/**
 * Fetch aggregated inventory totals for all products in a single query.
 * Returns total quantity and last updated time for each product.
 */
export async function getInventoryTotals(): Promise<InventoryTotal[]> {
  return apiGet<InventoryTotal[]>("/api/inventory/totals");
}
