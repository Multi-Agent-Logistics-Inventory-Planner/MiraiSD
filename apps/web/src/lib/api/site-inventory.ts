import { webApiClient, unwrapGeneratedResponse, GeneratedApiError } from "./generated-client";
import type { components } from "@mirai/api-client";
import {
  LocationType,
  StockMovementReason,
  type BatchAdjustLine,
  type PaginatedResponse,
  type ProductInventoryEntry,
  type ProductInventoryResponse,
} from "@/types/api";

// --- Site-scoped inventory reads/mutations (Phase 6 checkpoint 6d) --------
// Every route here lives under /api/v1/sites/{siteId}/inventory/** or
// /api/v1/sites/{siteId}/inventory/locations/{locationId}/items** (T-6d-be-1..8, already
// implemented, reviewed and merged - see .specs/phase-6-inventory/log.md's 6d backend slice).
// Response DTOs are slim (no catalog metadata) per AC-5; callers join against the existing
// catalog/products query client-side (see ./inventory-join.ts for the shared helper).
//
// Mutation routes require an `Idempotency-Key` header. Per T-6d-2, the key is generated once
// per user-initiated attempt by the caller (mutation variables, not inside this module) so an
// internal TanStack Query retry of the same `mutate()` call reuses one key while two separate
// user actions never collide. Every function below takes `idempotencyKey` as an explicit
// parameter rather than generating its own.

type SiteInventoryTotalDTO = components["schemas"]["SiteInventoryTotalDTO"];
type SiteLocationInventoryEntryDTO = components["schemas"]["SiteLocationInventoryEntryDTO"];
type ProductInventoryEntryDTO = components["schemas"]["ProductInventoryEntryDTO"];
type SiteStockMovementResponseDTO = components["schemas"]["SiteStockMovementResponseDTO"];
type BatchAdjustStockRequestDTO = components["schemas"]["BatchAdjustStockRequestDTO"];
type TransferInventoryRequestDTO = components["schemas"]["TransferInventoryRequestDTO"];
type BatchTransferInventoryRequestDTO =
  components["schemas"]["BatchTransferInventoryRequestDTO"];
type CreateLocationInventoryRequestDTO =
  components["schemas"]["CreateLocationInventoryRequestDTO"];

/**
 * Fresh idempotency key for one user-initiated mutation attempt. Callers (mutation hooks) must
 * call this exactly once per attempt - when building the `mutate()`/`mutateAsync()` variables,
 * never inside a `mutationFn` body - so an internal TanStack Query retry of that same attempt
 * reuses one key while two separate user actions always get distinct keys (T-6d-2).
 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

// --- Totals -----------------------------------------------------------------

export interface SiteInventoryTotal {
  productId: string;
  totalQuantity: number;
  lastUpdatedAt?: string;
}

function toSiteInventoryTotal(dto: SiteInventoryTotalDTO): SiteInventoryTotal | null {
  if (!dto.productId) return null;
  return {
    productId: dto.productId,
    totalQuantity: dto.totalQuantity ?? 0,
    lastUpdatedAt: dto.lastUpdatedAt,
  };
}

/**
 * Site-scoped, slim inventory totals (product ID, quantity, last-update time only - AC-5).
 * `productIds` narrows to known IDs when provided; omitted, returns every product's total at
 * this site.
 */
export async function getSiteInventoryTotals(
  siteId: string,
  productIds?: string[]
): Promise<SiteInventoryTotal[]> {
  const result = await webApiClient.GET("/api/v1/sites/{siteId}/inventory/totals", {
    params: {
      path: { siteId },
      query: productIds && productIds.length > 0 ? { productIds } : undefined,
    },
  });
  const data = unwrapGeneratedResponse(result);
  return (data ?? [])
    .map(toSiteInventoryTotal)
    .filter((t): t is SiteInventoryTotal => t !== null);
}

// --- Per-location reads -------------------------------------------------------

export interface SiteLocationInventoryEntry {
  inventoryId: string;
  productId: string;
  quantity: number;
  updatedAt?: string;
}

function toSiteLocationInventoryEntry(
  dto: SiteLocationInventoryEntryDTO
): SiteLocationInventoryEntry | null {
  if (!dto.inventoryId || !dto.productId) return null;
  return {
    inventoryId: dto.inventoryId,
    productId: dto.productId,
    quantity: dto.quantity ?? 0,
    updatedAt: dto.updatedAt,
  };
}

/**
 * Inventory rows at one location for this site. Excludes kuji-child/CUSTOM-kuji-parent rows
 * (backed by findByLocation_IdAndSite_Id's existing root-product filter) - this is an
 * intentional, verified behavior difference from the legacy NOT_ASSIGNED read for the same
 * storage location (see NotAssignedInventoryReadParityIT and T-6d-9 below).
 */
export async function getSiteLocationInventory(
  siteId: string,
  locationId: string
): Promise<SiteLocationInventoryEntry[]> {
  const result = await webApiClient.GET(
    "/api/v1/sites/{siteId}/inventory/locations/{locationId}",
    { params: { path: { siteId, locationId } } }
  );
  const data = unwrapGeneratedResponse(result);
  return (data ?? [])
    .map(toSiteLocationInventoryEntry)
    .filter((e): e is SiteLocationInventoryEntry => e !== null);
}

// --- Per-product reads --------------------------------------------------------

function toProductInventoryEntry(dto: ProductInventoryEntryDTO): ProductInventoryEntry | null {
  if (!dto.inventoryId) return null;
  return {
    inventoryId: dto.inventoryId,
    locationType: (dto.locationType as LocationType) ?? LocationType.NOT_ASSIGNED,
    locationId: dto.locationId ?? null,
    locationCode: dto.locationCode ?? "",
    locationLabel: dto.locationLabel ?? "",
    quantity: dto.quantity ?? 0,
    updatedAt: dto.updatedAt ?? "",
  };
}

/**
 * All inventory entries for one product across every location at this site, plus the
 * site-scoped total. Site-scoped counterpart to the legacy, unscoped
 * getProductInventoryEntries.
 */
export async function getSiteProductInventory(
  siteId: string,
  productId: string
): Promise<ProductInventoryResponse> {
  const result = await webApiClient.GET(
    "/api/v1/sites/{siteId}/inventory/products/{productId}",
    { params: { path: { siteId, productId } } }
  );
  const data = unwrapGeneratedResponse(result);
  if (!data) {
    // unwrapGeneratedResponse returns `undefined` for an ok response with an empty body -
    // guard consistently with getSiteInventoryTotals/getSiteLocationInventory's `data ?? []`
    // rather than letting `data.productId` below throw a raw TypeError (review finding 8).
    throw new GeneratedApiError(
      "Site product inventory response was empty",
      result.response.status
    );
  }
  return {
    productId: data.productId ?? productId,
    productSku: data.productSku ?? "",
    productName: data.productName ?? "",
    totalQuantity: data.totalQuantity ?? 0,
    entries: (data.entries ?? [])
      .map(toProductInventoryEntry)
      .filter((e): e is ProductInventoryEntry => e !== null),
  };
}

// --- Movement history ----------------------------------------------------------

export interface SiteMovementFilters {
  itemId?: string;
  search?: string;
  actorId?: string;
  reason?: StockMovementReason;
  fromDate?: string;
  toDate?: string;
}

export interface SiteStockMovement {
  id: number;
  itemId?: string;
  locationType: LocationType;
  fromLocationId?: string;
  toLocationId?: string;
  quantityChange: number;
  reason: StockMovementReason;
  actorId?: string;
  at: string;
  metadata?: Record<string, unknown>;
  /** "UNKNOWN" when this row predates the site backfill (T-6d-10). Absent otherwise. */
  siteAttribution?: "UNKNOWN";
}

function toSiteStockMovement(dto: SiteStockMovementResponseDTO): SiteStockMovement | null {
  if (dto.id === undefined || dto.id === null) return null;
  return {
    id: dto.id,
    itemId: dto.itemId,
    locationType: (dto.locationType as LocationType) ?? LocationType.NOT_ASSIGNED,
    fromLocationId: dto.fromLocationId,
    toLocationId: dto.toLocationId,
    quantityChange: dto.quantityChange ?? 0,
    reason: (dto.reason as StockMovementReason) ?? StockMovementReason.ADJUSTMENT,
    actorId: dto.actorId,
    at: dto.at ?? "",
    metadata: dto.metadata as Record<string, unknown> | undefined,
    siteAttribution: dto.siteAttribution === "UNKNOWN" ? "UNKNOWN" : undefined,
  };
}

/**
 * Serializes the `pageable` query param as flat `page`/`size`/`sort` keys, matching how Spring's
 * `Pageable` argument resolver actually binds a request - NOT as `pageable[page]=...`, which is
 * openapi-fetch's default `deepObject` serialization for an object-typed query param and would
 * silently fall back to the endpoint's `@PageableDefault` on every call (verified against
 * SiteInventoryController.getSiteMovements's `Pageable pageable` parameter).
 */
function sitePageableQuerySerializer(queryParams: Record<string, unknown>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(queryParams)) {
    if (value === undefined || value === null) continue;
    if (key === "pageable" && typeof value === "object") {
      const pageable = value as { page?: number; size?: number; sort?: string[] };
      if (pageable.page !== undefined) search.append("page", String(pageable.page));
      if (pageable.size !== undefined) search.append("size", String(pageable.size));
      for (const sortField of pageable.sort ?? []) search.append("sort", sortField);
      continue;
    }
    if (Array.isArray(value)) {
      for (const item of value) search.append(key, String(item));
      continue;
    }
    search.append(key, String(value));
  }
  return search.toString();
}

/** Site-scoped, paginated movement history. Site-scoped counterpart to getStockMovementHistory. */
export async function getSiteMovements(
  siteId: string,
  filters: SiteMovementFilters = {},
  page: number = 0,
  size: number = 20
): Promise<PaginatedResponse<SiteStockMovement>> {
  const result = await webApiClient.GET("/api/v1/sites/{siteId}/inventory/movements", {
    params: {
      path: { siteId },
      query: {
        itemId: filters.itemId,
        search: filters.search,
        actorId: filters.actorId,
        reason: filters.reason,
        fromDate: filters.fromDate,
        toDate: filters.toDate,
        pageable: { page, size },
      },
    },
    querySerializer: sitePageableQuerySerializer,
  });
  const data = unwrapGeneratedResponse(result);
  if (!data) {
    // Same guard as getSiteProductInventory above - an ok-but-empty body must not fall through
    // to an unguarded `data.content` TypeError (review finding 8).
    throw new GeneratedApiError("Site movements response was empty", result.response.status);
  }
  return {
    content: (data.content ?? [])
      .map(toSiteStockMovement)
      .filter((m): m is SiteStockMovement => m !== null),
    totalElements: data.totalElements ?? 0,
    totalPages: data.totalPages ?? 0,
    size: data.size ?? size,
    number: data.number ?? page,
    first: data.first ?? page === 0,
    last: data.last ?? true,
  };
}

// --- Mutations (require Idempotency-Key; actor derived server-side from AuthorizedSiteContext,
// never sent by the client - T-6d-7) -----------------------------------------------------------

export interface AdjustSiteInventoryPayload {
  locationType: LocationType;
  locationId: string;
  adjustments: BatchAdjustLine[];
  reason: StockMovementReason;
  notes?: string;
}

function toBatchAdjustStockRequestDTO(
  payload: AdjustSiteInventoryPayload
): BatchAdjustStockRequestDTO {
  return {
    locationType: payload.locationType,
    locationId: payload.locationId,
    reason: payload.reason,
    notes: payload.notes,
    adjustments: payload.adjustments.map((line) => ({
      inventoryId: line.inventoryId,
      quantityChange: line.quantityChange,
      intakeUnit: line.intakeUnit,
      intakeQty: line.intakeQty,
    })),
  };
}

/** Adjust stock for one or more inventory items at a single location, atomically. */
export async function adjustSiteInventory(
  siteId: string,
  idempotencyKey: string,
  payload: AdjustSiteInventoryPayload
): Promise<void> {
  const result = await webApiClient.POST("/api/v1/sites/{siteId}/inventory/adjustments", {
    params: { path: { siteId }, header: { "Idempotency-Key": idempotencyKey } },
    body: toBatchAdjustStockRequestDTO(payload),
  });
  unwrapGeneratedResponse(result);
}

export interface TransferSiteInventoryPayload {
  sourceLocationType: LocationType;
  sourceInventoryId: string;
  destinationLocationType: LocationType;
  destinationInventoryId?: string;
  destinationLocationId?: string;
  quantity: number;
  notes?: string;
}

function toTransferInventoryRequestDTO(
  payload: TransferSiteInventoryPayload
): TransferInventoryRequestDTO {
  return {
    sourceLocationType: payload.sourceLocationType,
    sourceInventoryId: payload.sourceInventoryId,
    destinationLocationType: payload.destinationLocationType,
    destinationInventoryId: payload.destinationInventoryId,
    destinationLocationId: payload.destinationLocationId,
    quantity: payload.quantity,
    notes: payload.notes,
  };
}

/** Transfer stock between locations (single item) at this site. */
export async function transferSiteInventory(
  siteId: string,
  idempotencyKey: string,
  payload: TransferSiteInventoryPayload
): Promise<void> {
  const result = await webApiClient.POST("/api/v1/sites/{siteId}/inventory/transfers", {
    params: { path: { siteId }, header: { "Idempotency-Key": idempotencyKey } },
    body: toTransferInventoryRequestDTO(payload),
  });
  unwrapGeneratedResponse(result);
}

/**
 * Transfer multiple inventory items in a single atomic batch (one audit entry for the whole
 * operation). New in 6d (T-6d-be-5) - the legacy route had no batch equivalent with real
 * atomicity. Capped at 50 transfers server-side (`@Size(max = 50)`).
 */
export async function batchTransferSiteInventory(
  siteId: string,
  idempotencyKey: string,
  transfers: TransferSiteInventoryPayload[]
): Promise<void> {
  const body: BatchTransferInventoryRequestDTO = {
    transfers: transfers.map(toTransferInventoryRequestDTO),
  };
  const result = await webApiClient.POST("/api/v1/sites/{siteId}/inventory/transfers/batch", {
    params: { path: { siteId }, header: { "Idempotency-Key": idempotencyKey } },
    body,
  });
  unwrapGeneratedResponse(result);
}

export interface CreateSiteLocationInventoryPayload {
  productId: string;
  quantity: number;
  reason?: StockMovementReason;
  intakeUnit?: "pack" | "box";
  intakeQty?: number;
}

/**
 * Create inventory for a product at a location for this site (R-9's resolution). Replaces the
 * legacy, untracked `POST /api/locations/{id}/inventory` for every site-scoped caller.
 */
export async function createSiteLocationInventory(
  siteId: string,
  locationId: string,
  idempotencyKey: string,
  payload: CreateSiteLocationInventoryPayload
): Promise<SiteLocationInventoryEntry> {
  const body: CreateLocationInventoryRequestDTO = {
    productId: payload.productId,
    quantity: payload.quantity,
    reason: payload.reason,
    intakeUnit: payload.intakeUnit,
    intakeQty: payload.intakeQty,
  };
  const result = await webApiClient.POST(
    "/api/v1/sites/{siteId}/inventory/locations/{locationId}/items",
    {
      params: {
        path: { siteId, locationId },
        header: { "Idempotency-Key": idempotencyKey },
      },
      body,
    }
  );
  const data = unwrapGeneratedResponse(result);
  const entry = toSiteLocationInventoryEntry(data);
  if (!entry) {
    throw new Error("Site location inventory create response was missing identity fields");
  }
  return entry;
}

/**
 * Delete an inventory row at a location for this site (R-9's resolution). ADMIN/
 * ASSISTANT_MANAGER only, matching the legacy route's restriction. Replaces the legacy,
 * untracked `PUT`-as-set and `DELETE /api/locations/{id}/inventory/{invId}` for every
 * site-scoped caller - there is no "set exact quantity" route; all quantity edits go through
 * the audited adjustment endpoint instead.
 */
export async function deleteSiteLocationInventory(
  siteId: string,
  locationId: string,
  inventoryId: string,
  idempotencyKey: string
): Promise<void> {
  const result = await webApiClient.DELETE(
    "/api/v1/sites/{siteId}/inventory/locations/{locationId}/items/{inventoryId}",
    {
      params: {
        path: { siteId, locationId, inventoryId },
        header: { "Idempotency-Key": idempotencyKey },
      },
    }
  );
  unwrapGeneratedResponse(result);
}
