import { apiGet, apiPost, apiPut, apiDelete } from "./client";
import {
  LocationType,
  LOCATION_ENDPOINTS,
  BoxBin,
  Rack,
  Cabinet,
  SingleClawMachine,
  DoubleClawMachine,
  KeychainMachine,
  BoxBinRequest,
  RackRequest,
  CabinetRequest,
  SingleClawMachineRequest,
  DoubleClawMachineRequest,
  KeychainMachineRequest,
} from "@/types/api";

// Generic location API functions

function getBasePath(locationType: LocationType): string {
  return `/api/${LOCATION_ENDPOINTS[locationType]}`;
}

// BoxBin API

export async function getBoxBins(): Promise<BoxBin[]> {
  return apiGet<BoxBin[]>(getBasePath(LocationType.BOX_BIN));
}

export async function getBoxBinById(id: string): Promise<BoxBin> {
  return apiGet<BoxBin>(`${getBasePath(LocationType.BOX_BIN)}/${id}`);
}

export async function createBoxBin(data: BoxBinRequest): Promise<BoxBin> {
  return apiPost<BoxBin, BoxBinRequest>(getBasePath(LocationType.BOX_BIN), data);
}

export async function updateBoxBin(
  id: string,
  data: BoxBinRequest
): Promise<BoxBin> {
  return apiPut<BoxBin, BoxBinRequest>(
    `${getBasePath(LocationType.BOX_BIN)}/${id}`,
    data
  );
}

export async function deleteBoxBin(id: string): Promise<void> {
  return apiDelete<void>(`${getBasePath(LocationType.BOX_BIN)}/${id}`);
}

// Rack API

export async function getRacks(): Promise<Rack[]> {
  return apiGet<Rack[]>(getBasePath(LocationType.RACK));
}

export async function getRackById(id: string): Promise<Rack> {
  return apiGet<Rack>(`${getBasePath(LocationType.RACK)}/${id}`);
}

export async function createRack(data: RackRequest): Promise<Rack> {
  return apiPost<Rack, RackRequest>(getBasePath(LocationType.RACK), data);
}

export async function updateRack(id: string, data: RackRequest): Promise<Rack> {
  return apiPut<Rack, RackRequest>(
    `${getBasePath(LocationType.RACK)}/${id}`,
    data
  );
}

export async function deleteRack(id: string): Promise<void> {
  return apiDelete<void>(`${getBasePath(LocationType.RACK)}/${id}`);
}

// Cabinet API

export async function getCabinets(): Promise<Cabinet[]> {
  return apiGet<Cabinet[]>(getBasePath(LocationType.CABINET));
}

export async function getCabinetById(id: string): Promise<Cabinet> {
  return apiGet<Cabinet>(`${getBasePath(LocationType.CABINET)}/${id}`);
}

export async function createCabinet(data: CabinetRequest): Promise<Cabinet> {
  return apiPost<Cabinet, CabinetRequest>(
    getBasePath(LocationType.CABINET),
    data
  );
}

export async function updateCabinet(
  id: string,
  data: CabinetRequest
): Promise<Cabinet> {
  return apiPut<Cabinet, CabinetRequest>(
    `${getBasePath(LocationType.CABINET)}/${id}`,
    data
  );
}

export async function deleteCabinet(id: string): Promise<void> {
  return apiDelete<void>(`${getBasePath(LocationType.CABINET)}/${id}`);
}

// SingleClawMachine API

export async function getSingleClawMachines(): Promise<SingleClawMachine[]> {
  return apiGet<SingleClawMachine[]>(
    getBasePath(LocationType.SINGLE_CLAW_MACHINE)
  );
}

export async function getSingleClawMachineById(
  id: string
): Promise<SingleClawMachine> {
  return apiGet<SingleClawMachine>(
    `${getBasePath(LocationType.SINGLE_CLAW_MACHINE)}/${id}`
  );
}

export async function createSingleClawMachine(
  data: SingleClawMachineRequest
): Promise<SingleClawMachine> {
  return apiPost<SingleClawMachine, SingleClawMachineRequest>(
    getBasePath(LocationType.SINGLE_CLAW_MACHINE),
    data
  );
}

export async function updateSingleClawMachine(
  id: string,
  data: SingleClawMachineRequest
): Promise<SingleClawMachine> {
  return apiPut<SingleClawMachine, SingleClawMachineRequest>(
    `${getBasePath(LocationType.SINGLE_CLAW_MACHINE)}/${id}`,
    data
  );
}

export async function deleteSingleClawMachine(id: string): Promise<void> {
  return apiDelete<void>(
    `${getBasePath(LocationType.SINGLE_CLAW_MACHINE)}/${id}`
  );
}

// DoubleClawMachine API

export async function getDoubleClawMachines(): Promise<DoubleClawMachine[]> {
  return apiGet<DoubleClawMachine[]>(
    getBasePath(LocationType.DOUBLE_CLAW_MACHINE)
  );
}

export async function getDoubleClawMachineById(
  id: string
): Promise<DoubleClawMachine> {
  return apiGet<DoubleClawMachine>(
    `${getBasePath(LocationType.DOUBLE_CLAW_MACHINE)}/${id}`
  );
}

export async function createDoubleClawMachine(
  data: DoubleClawMachineRequest
): Promise<DoubleClawMachine> {
  return apiPost<DoubleClawMachine, DoubleClawMachineRequest>(
    getBasePath(LocationType.DOUBLE_CLAW_MACHINE),
    data
  );
}

export async function updateDoubleClawMachine(
  id: string,
  data: DoubleClawMachineRequest
): Promise<DoubleClawMachine> {
  return apiPut<DoubleClawMachine, DoubleClawMachineRequest>(
    `${getBasePath(LocationType.DOUBLE_CLAW_MACHINE)}/${id}`,
    data
  );
}

export async function deleteDoubleClawMachine(id: string): Promise<void> {
  return apiDelete<void>(
    `${getBasePath(LocationType.DOUBLE_CLAW_MACHINE)}/${id}`
  );
}

// KeychainMachine API

export async function getKeychainMachines(): Promise<KeychainMachine[]> {
  return apiGet<KeychainMachine[]>(getBasePath(LocationType.KEYCHAIN_MACHINE));
}

export async function getKeychainMachineById(
  id: string
): Promise<KeychainMachine> {
  return apiGet<KeychainMachine>(
    `${getBasePath(LocationType.KEYCHAIN_MACHINE)}/${id}`
  );
}

export async function createKeychainMachine(
  data: KeychainMachineRequest
): Promise<KeychainMachine> {
  return apiPost<KeychainMachine, KeychainMachineRequest>(
    getBasePath(LocationType.KEYCHAIN_MACHINE),
    data
  );
}

export async function updateKeychainMachine(
  id: string,
  data: KeychainMachineRequest
): Promise<KeychainMachine> {
  return apiPut<KeychainMachine, KeychainMachineRequest>(
    `${getBasePath(LocationType.KEYCHAIN_MACHINE)}/${id}`,
    data
  );
}

export async function deleteKeychainMachine(id: string): Promise<void> {
  return apiDelete<void>(`${getBasePath(LocationType.KEYCHAIN_MACHINE)}/${id}`);
}

// Helper to get all locations of a specific type
export async function getLocationsByType(
  locationType: LocationType
): Promise<
  | BoxBin[]
  | Rack[]
  | Cabinet[]
  | SingleClawMachine[]
  | DoubleClawMachine[]
  | KeychainMachine[]
> {
  switch (locationType) {
    case LocationType.BOX_BIN:
      return getBoxBins();
    case LocationType.RACK:
      return getRacks();
    case LocationType.CABINET:
      return getCabinets();
    case LocationType.SINGLE_CLAW_MACHINE:
      return getSingleClawMachines();
    case LocationType.DOUBLE_CLAW_MACHINE:
      return getDoubleClawMachines();
    case LocationType.KEYCHAIN_MACHINE:
      return getKeychainMachines();
    default:
      throw new Error(`Unknown location type: ${locationType}`);
  }
}
