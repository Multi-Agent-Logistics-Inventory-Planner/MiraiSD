import { ApiClientError, apiGet, apiPatch, apiPost } from "./client";
import { getNALocationId, NOT_ASSIGNED_VIRTUAL_ID } from "./inventory";
import {
  AddKujiTierRequest,
  AddSlipRequest,
  CloseKujiBoxRequest,
  KujiAllocationByLocation,
  KujiAllocationByProduct,
  KujiBox,
  KujiBoxTier,
  OpenKujiBoxRequest,
  PatchKujiTierRequest,
  RecordDrawRequest,
  TransferInMoreRequest,
} from "@/types/api";

const BASE_PATH = "/api/kuji-boxes";

/** Translate the NOT_ASSIGNED virtual sentinel to the real NA location UUID, or pass through. */
async function resolveLocationId(
  locationId: string | null | undefined
): Promise<string | null | undefined> {
  if (locationId === NOT_ASSIGNED_VIRTUAL_ID) {
    return getNALocationId();
  }
  return locationId;
}

/**
 * Get the currently active (OPEN) kuji box for a product, or null if none exists.
 */
export async function getActiveKujiBox(
  productId: string
): Promise<KujiBox | null> {
  try {
    const result = await apiGet<KujiBox | null>(
      `${BASE_PATH}/by-product/${productId}/active`
    );
    return result ?? null;
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

/**
 * Get the full kuji box history for a product (most-recent first).
 */
export async function getKujiBoxHistory(productId: string): Promise<KujiBox[]> {
  return apiGet<KujiBox[]>(`${BASE_PATH}/by-product/${productId}/history`);
}

/**
 * Get the tier configuration of the most recently closed kuji box for a product.
 * Used to seed a new box from the previous configuration ("clone" affordance).
 */
export async function getLastClosedKujiTiers(
  productId: string
): Promise<KujiBoxTier[]> {
  return apiGet<KujiBoxTier[]>(
    `${BASE_PATH}/by-product/${productId}/last-tiers`
  );
}

/**
 * Get a single kuji box by ID.
 */
export async function getKujiBox(boxId: string): Promise<KujiBox> {
  return apiGet<KujiBox>(`${BASE_PATH}/${boxId}`);
}

/**
 * Open a new kuji box.
 */
export async function openKujiBox(
  req: OpenKujiBoxRequest
): Promise<KujiBox> {
  // Resolve any NOT_ASSIGNED sentinels to real UUIDs before serialization (Jackson requires real UUIDs).
  const resolvedLocationId = (await resolveLocationId(req.locationId)) as string;
  const resolvedTiers = await Promise.all(
    req.tiers.map(async (tier) => ({
      ...tier,
      sourceLocationId: (await resolveLocationId(tier.sourceLocationId)) ?? null,
    }))
  );
  const resolved: OpenKujiBoxRequest = {
    ...req,
    locationId: resolvedLocationId,
    tiers: resolvedTiers,
  };
  return apiPost<KujiBox, OpenKujiBoxRequest>(BASE_PATH, resolved);
}

/**
 * Close an open kuji box.
 */
export async function closeKujiBox(
  boxId: string,
  req: CloseKujiBoxRequest
): Promise<KujiBox> {
  const resolvedTargets = req.transferOutTargets
    ? await Promise.all(
        req.transferOutTargets.map(async (t) => ({
          ...t,
          destinationLocationId: (await resolveLocationId(
            t.destinationLocationId
          )) as string,
        }))
      )
    : req.transferOutTargets;
  const resolved: CloseKujiBoxRequest = {
    ...req,
    transferOutTargets: resolvedTargets,
  };
  return apiPatch<KujiBox, CloseKujiBoxRequest>(
    `${BASE_PATH}/${boxId}/close`,
    resolved
  );
}

/**
 * Reopen a closed kuji box.
 */
export async function reopenKujiBox(
  boxId: string,
  actorId: string
): Promise<KujiBox> {
  const params = new URLSearchParams({ actorId });
  return apiPatch<KujiBox>(
    `${BASE_PATH}/${boxId}/reopen?${params.toString()}`
  );
}

/**
 * Patch (edit) a tier on an open kuji box.
 */
export async function patchKujiTier(
  boxId: string,
  tierId: string,
  req: PatchKujiTierRequest
): Promise<KujiBox> {
  const resolved: PatchKujiTierRequest = {
    ...req,
    linkedProductDestinationLocationId:
      (await resolveLocationId(req.linkedProductDestinationLocationId)) ?? null,
  };
  return apiPatch<KujiBox, PatchKujiTierRequest>(
    `${BASE_PATH}/${boxId}/tiers/${tierId}`,
    resolved
  );
}

/**
 * Transfer additional inventory of the linked product into a tier.
 */
export async function transferInMoreToKujiTier(
  boxId: string,
  tierId: string,
  req: TransferInMoreRequest
): Promise<KujiBox> {
  const resolved: TransferInMoreRequest = {
    ...req,
    sourceLocationId:
      (await resolveLocationId(req.sourceLocationId)) ?? null,
  };
  return apiPost<KujiBox, TransferInMoreRequest>(
    `${BASE_PATH}/${boxId}/tiers/${tierId}/transfer-in`,
    resolved
  );
}

/**
 * Transfer additional inventory of the linked product into a tier WITHOUT
 * incrementing the tier's slip count. Used when holding prizes back.
 */
export async function transferInInventoryOnlyToKujiTier(
  boxId: string,
  tierId: string,
  req: TransferInMoreRequest
): Promise<KujiBox> {
  const resolved: TransferInMoreRequest = {
    ...req,
    sourceLocationId:
      (await resolveLocationId(req.sourceLocationId)) ?? null,
  };
  return apiPost<KujiBox, TransferInMoreRequest>(
    `${BASE_PATH}/${boxId}/tiers/${tierId}/transfer-in-inventory-only`,
    resolved
  );
}

/**
 * Record one or more draws against an open kuji box.
 */
export async function recordKujiDraw(
  boxId: string,
  req: RecordDrawRequest
): Promise<KujiBox> {
  return apiPost<KujiBox, RecordDrawRequest>(
    `${BASE_PATH}/${boxId}/draws`,
    req
  );
}

/**
 * Undo a previously recorded draw by audit-log entry.
 */
export async function undoKujiDraw(
  boxId: string,
  auditLogId: string,
  actorId: string
): Promise<KujiBox> {
  const params = new URLSearchParams({ actorId });
  return apiPost<KujiBox>(
    `${BASE_PATH}/${boxId}/draws/${auditLogId}/undo?${params.toString()}`
  );
}

/**
 * Add a brand-new tier to an already-open kuji box. Mirrors the per-tier shape
 * used at open-box; supports both linking an existing product and auto-creating
 * a fresh child product under the kuji parent.
 */
export async function addKujiTier(
  boxId: string,
  req: AddKujiTierRequest
): Promise<KujiBox> {
  return apiPost<KujiBox, AddKujiTierRequest>(
    `${BASE_PATH}/${boxId}/tiers`,
    req
  );
}

/**
 * Add additional slips (draw tickets) to a tier without affecting linked-product inventory.
 */
export async function addKujiSlip(
  boxId: string,
  tierId: string,
  req: AddSlipRequest
): Promise<KujiBox> {
  return apiPost<KujiBox, AddSlipRequest>(
    `${BASE_PATH}/${boxId}/tiers/${tierId}/add-slip`,
    req
  );
}


/**
 * List kuji-box allocations at a given location — drives the "S1-Display" virtual row
 * in the location detail modal and the adjust/transfer "available" cap.
 */
export async function getKujiAllocationsByLocation(
  locationId: string
): Promise<KujiAllocationByLocation[]> {
  return apiGet<KujiAllocationByLocation[]>(
    `${BASE_PATH}/allocations/by-location/${locationId}`
  );
}

/**
 * List kuji-box allocations referencing a given product — drives "where is this product?"
 * virtual entries in the product modal.
 */
export async function getKujiAllocationsByProduct(
  productId: string
): Promise<KujiAllocationByProduct[]> {
  return apiGet<KujiAllocationByProduct[]>(
    `${BASE_PATH}/allocations/by-product/${productId}`
  );
}
