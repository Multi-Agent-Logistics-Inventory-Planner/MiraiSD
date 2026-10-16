import { ApiClientError, apiGet, apiPatch, apiPost } from "./client";
import { getNALocationId, NOT_ASSIGNED_VIRTUAL_ID } from "./inventory";
import {
  AddKujiTierRequest,
  AddSlipRequest,
  CloseKujiBoxRequest,
  DeletePrizeRequest,
  KujiAllocationByLocation,
  KujiAllocationByProduct,
  KujiBox,
  KujiBoxTier,
  KujiDailyPayoutsResponse,
  MoveSlipsRequest,
  OpenKujiBoxRequest,
  PatchKujiTierRequest,
  RecordDrawRequest,
  TransferInMoreRequest,
} from "@/types/api";

// Every route here lives under /api/v1/sites/{siteId}/kuji-boxes/**, delegating server-side to
// the same KujiBoxService methods as the legacy /api/kuji-boxes routes in ./kuji-boxes.ts - v1
// added no new request/response fields for this slice, only site scoping, so the existing
// @/types/api interfaces are reused as-is rather than introducing a parallel generated-client
// mapping layer (.specs/phase-7-kuji-lootbox T-4). Most mutation DTOs (open/close/draw/tier/slip)
// still carry a required actorId field the server trusts as-is, unchanged from the legacy route -
// only reopen and undo, which never had a body actorId field (the legacy route took it as a query
// param instead), were changed to derive the actor from the resolved site context server-side;
// see each function's own doc comment.

function basePath(siteId: string): string {
  return `/api/v1/sites/${siteId}/kuji-boxes`;
}

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
 * Get the currently active (OPEN) kuji box for a product at this site, or null if none exists.
 */
export async function getSiteActiveKujiBox(
  siteId: string,
  productId: string
): Promise<KujiBox | null> {
  try {
    const result = await apiGet<KujiBox | null>(
      `${basePath(siteId)}/by-product/${productId}/active`
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
 * Get the full kuji box history for a product at this site (most-recent first).
 */
export async function getSiteKujiBoxHistory(
  siteId: string,
  productId: string
): Promise<KujiBox[]> {
  return apiGet<KujiBox[]>(`${basePath(siteId)}/by-product/${productId}/history`);
}

/**
 * Get the tier configuration of the most recently closed kuji box for a product at this site.
 * Used to seed a new box from the previous configuration ("clone" affordance).
 */
export async function getSiteLastClosedKujiTiers(
  siteId: string,
  productId: string
): Promise<KujiBoxTier[]> {
  return apiGet<KujiBoxTier[]>(`${basePath(siteId)}/by-product/${productId}/last-tiers`);
}

/**
 * Get a single kuji box by ID at this site.
 */
export async function getSiteKujiBox(siteId: string, boxId: string): Promise<KujiBox> {
  return apiGet<KujiBox>(`${basePath(siteId)}/${boxId}`);
}

/**
 * Per-day net payout series for a box at this site. tz must be a valid IANA zone id; the
 * server bucketizes movements by that zone's calendar day.
 */
export async function getSiteKujiDailyPayouts(
  siteId: string,
  boxId: string,
  params: { from?: string; to?: string; tz: string }
): Promise<KujiDailyPayoutsResponse> {
  const search = new URLSearchParams();
  if (params.from) search.set("from", params.from);
  if (params.to) search.set("to", params.to);
  search.set("tz", params.tz);
  return apiGet<KujiDailyPayoutsResponse>(
    `${basePath(siteId)}/${boxId}/daily-payouts?${search.toString()}`
  );
}

/**
 * Open a new kuji box at this site.
 */
export async function openSiteKujiBox(
  siteId: string,
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
  return apiPost<KujiBox, OpenKujiBoxRequest>(basePath(siteId), resolved);
}

/**
 * Close an open kuji box at this site.
 */
export async function closeSiteKujiBox(
  siteId: string,
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
  return apiPatch<KujiBox, CloseKujiBoxRequest>(`${basePath(siteId)}/${boxId}/close`, resolved);
}

/**
 * Reopen a closed kuji box at this site. Unlike the legacy route, the actor is always the
 * authenticated principal resolved server-side from the site context - no actorId is sent.
 */
export async function reopenSiteKujiBox(siteId: string, boxId: string): Promise<KujiBox> {
  return apiPatch<KujiBox>(`${basePath(siteId)}/${boxId}/reopen`);
}

/**
 * Patch (edit) a tier on an open kuji box at this site.
 */
export async function patchSiteKujiTier(
  siteId: string,
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
    `${basePath(siteId)}/${boxId}/tiers/${tierId}`,
    resolved
  );
}

/**
 * Transfer additional inventory of the linked product into a tier at this site.
 */
export async function transferInMoreToSiteKujiTier(
  siteId: string,
  boxId: string,
  tierId: string,
  req: TransferInMoreRequest
): Promise<KujiBox> {
  const resolved: TransferInMoreRequest = {
    ...req,
    sourceLocationId: (await resolveLocationId(req.sourceLocationId)) ?? null,
  };
  return apiPost<KujiBox, TransferInMoreRequest>(
    `${basePath(siteId)}/${boxId}/tiers/${tierId}/transfer-in`,
    resolved
  );
}

/**
 * Transfer additional inventory of the linked product into a tier at this site WITHOUT
 * incrementing the tier's slip count. Used when holding prizes back.
 */
export async function transferInInventoryOnlyToSiteKujiTier(
  siteId: string,
  boxId: string,
  tierId: string,
  req: TransferInMoreRequest
): Promise<KujiBox> {
  const resolved: TransferInMoreRequest = {
    ...req,
    sourceLocationId: (await resolveLocationId(req.sourceLocationId)) ?? null,
  };
  return apiPost<KujiBox, TransferInMoreRequest>(
    `${basePath(siteId)}/${boxId}/tiers/${tierId}/transfer-in-inventory-only`,
    resolved
  );
}

/**
 * Record one or more draws against an open kuji box at this site.
 */
export async function recordSiteKujiDraw(
  siteId: string,
  boxId: string,
  req: RecordDrawRequest
): Promise<KujiBox> {
  return apiPost<KujiBox, RecordDrawRequest>(`${basePath(siteId)}/${boxId}/draws`, req);
}

/**
 * Undo a previously recorded draw by audit-log entry at this site. Unlike the legacy route,
 * the actor is always the authenticated principal resolved server-side - no actorId is sent.
 */
export async function undoSiteKujiDraw(
  siteId: string,
  boxId: string,
  auditLogId: string
): Promise<KujiBox> {
  return apiPost<KujiBox>(`${basePath(siteId)}/${boxId}/draws/${auditLogId}/undo`);
}

/**
 * Add a brand-new tier to an already-open kuji box at this site. Mirrors the per-tier shape
 * used at open-box; supports both linking an existing product and auto-creating
 * a fresh child product under the kuji parent.
 */
export async function addSiteKujiTier(
  siteId: string,
  boxId: string,
  req: AddKujiTierRequest
): Promise<KujiBox> {
  return apiPost<KujiBox, AddKujiTierRequest>(`${basePath(siteId)}/${boxId}/tiers`, req);
}

/**
 * Add additional slips (draw tickets) to a tier at this site without affecting linked-product
 * inventory.
 */
export async function addSiteKujiSlip(
  siteId: string,
  boxId: string,
  tierId: string,
  req: AddSlipRequest
): Promise<KujiBox> {
  return apiPost<KujiBox, AddSlipRequest>(
    `${basePath(siteId)}/${boxId}/tiers/${tierId}/add-slip`,
    req
  );
}

/**
 * Move slips between the active and inactive buckets within a single tier at this site.
 * Counter-only — no inventory side-effect.
 */
export async function moveSiteKujiSlips(
  siteId: string,
  boxId: string,
  tierId: string,
  req: MoveSlipsRequest
): Promise<KujiBox> {
  return apiPost<KujiBox, MoveSlipsRequest>(
    `${basePath(siteId)}/${boxId}/tiers/${tierId}/move-slips`,
    req
  );
}

/**
 * Delete one prize from a tier at this site (active by default, or inactive if fromActive=false).
 * When the tier's total reaches 0 it's removed from the box.
 * Counter-only — no inventory side-effect.
 */
export async function deleteSiteKujiPrize(
  siteId: string,
  boxId: string,
  tierId: string,
  req: DeletePrizeRequest
): Promise<KujiBox> {
  return apiPost<KujiBox, DeletePrizeRequest>(
    `${basePath(siteId)}/${boxId}/tiers/${tierId}/delete-prize`,
    req
  );
}

/**
 * List kuji-box allocations at a given location at this site — drives the "S1-Display" virtual
 * row in the location detail modal and the adjust/transfer "available" cap.
 */
export async function getSiteKujiAllocationsByLocation(
  siteId: string,
  locationId: string
): Promise<KujiAllocationByLocation[]> {
  return apiGet<KujiAllocationByLocation[]>(
    `${basePath(siteId)}/allocations/by-location/${locationId}`
  );
}

/**
 * List kuji-box allocations referencing a given product at this site — drives "where is this
 * product?" virtual entries in the product modal.
 */
export async function getSiteKujiAllocationsByProduct(
  siteId: string,
  productId: string
): Promise<KujiAllocationByProduct[]> {
  return apiGet<KujiAllocationByProduct[]>(
    `${basePath(siteId)}/allocations/by-product/${productId}`
  );
}
