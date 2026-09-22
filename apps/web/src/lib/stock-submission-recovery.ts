import type { AdjustSiteInventoryPayload, TransferSiteInventoryPayload } from "@/lib/api/site-inventory";

const STORAGE_KEY_PREFIX = "miraisd.phase6.uncertain-stock-submission.v2";
const MAX_AGE_MS = 24 * 60 * 60 * 1000;

export type StoredStockSubmission =
  | { version: 2; createdAt: number; userId: string; siteId: string; idempotencyKey: string; kind: "adjust"; payload: AdjustSiteInventoryPayload; productIds: string[] }
  | { version: 2; createdAt: number; userId: string; siteId: string; idempotencyKey: string; kind: "transfer"; payload: TransferSiteInventoryPayload; productId?: string }
  | { version: 2; createdAt: number; userId: string; siteId: string; idempotencyKey: string; kind: "batch-transfer"; transfers: { payload: TransferSiteInventoryPayload; productId: string; productName: string }[]; sourceLocationId: string; destinationLocationId: string }
  | { version: 2; createdAt: number; userId: string; siteId: string; idempotencyKey: string; kind: "initial-stock"; productId: string; locationId: string; quantity: number; intakeUnit?: "box"; intakeQty?: number };

function storage(): Storage | null {
  if (typeof window === "undefined") return null;
  try { return window.sessionStorage; } catch { return null; }
}

function key(userId: string, siteId: string, kind: StoredStockSubmission["kind"]): string {
  return `${STORAGE_KEY_PREFIX}:${encodeURIComponent(userId)}:${encodeURIComponent(siteId)}:${kind}`;
}

export function readUncertainStockSubmission(userId: string, siteId: string, kind: StoredStockSubmission["kind"]): StoredStockSubmission | null {
  const storageKey = key(userId, siteId, kind);
  const value = storage()?.getItem(storageKey);
  if (!value) return null;
  try {
    const record = JSON.parse(value) as StoredStockSubmission;
    if (record.version !== 2 || record.userId !== userId || record.siteId !== siteId || record.kind !== kind || Date.now() - record.createdAt > MAX_AGE_MS) {
      storage()?.removeItem(storageKey);
      return null;
    }
    return record;
  } catch {
    storage()?.removeItem(storageKey);
    return null;
  }
}

export function storeUncertainStockSubmission(record: StoredStockSubmission): void {
  try { storage()?.setItem(key(record.userId, record.siteId, record.kind), JSON.stringify(record)); } catch { /* session storage unavailable: command still relies on server idempotency */ }
}

export function clearUncertainStockSubmission(record: Pick<StoredStockSubmission, "userId" | "siteId" | "kind" | "idempotencyKey">): void {
  const storageKey = key(record.userId, record.siteId, record.kind);
  const value = storage()?.getItem(storageKey);
  if (!value) return;
  try {
    if ((JSON.parse(value) as StoredStockSubmission).idempotencyKey === record.idempotencyKey) storage()?.removeItem(storageKey);
  } catch { storage()?.removeItem(storageKey); }
}

/** A received client-error response proves this command was rejected before it committed. */
export function isDefinitiveStockSubmissionFailure(error: unknown): boolean {
  const status = typeof error === "object" && error !== null && "status" in error
    ? (error as { status?: unknown }).status
    : undefined;
  return typeof status === "number" && status >= 400 && status < 500 && status !== 408;
}
