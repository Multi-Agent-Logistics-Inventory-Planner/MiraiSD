import type { QueryClient } from "@tanstack/react-query";
import { getSiteInventoryTotals, type SiteInventoryTotal } from "@/lib/api/site-inventory";

/**
 * Mirrors `InventoryTotalsRepository.MAX_PRODUCT_IDS_BATCH_SIZE` on the backend (follow-up
 * review finding, P2): the coalescing buffer has no cap of its own, so multiple rapid,
 * individually-valid events can accumulate more IDs than one request may carry. Requests are
 * chunked at this size rather than capped/dropped, so a large coalesced batch still refreshes
 * every affected product instead of failing outright and being silently swallowed by a
 * fire-and-forget caller's `.catch()`.
 */
const MAX_PRODUCT_IDS_PER_REQUEST = 500;

/**
 * Ensures a flush that started later always wins over one that started earlier for the same
 * product, regardless of arrival order (follow-up review finding, P1). The prior guard compared
 * each fetched row's own `lastUpdatedAt` (the max `location_inventory.updated_at` across that
 * product's *remaining* rows) against the cached value - but that timestamp is not monotonic
 * with correctness: deleting a product's newest row legitimately *decreases* it (so a correct,
 * lower total was being rejected), and the zero-quantity default seeded for a requested ID
 * absent from the response carries no timestamp at all (so an older, empty response could
 * bypass the guard and overwrite a newer restocked quantity with zero). Sequencing by request
 * issuance order, rather than by any value in the response, avoids depending on that timestamp's
 * semantics entirely.
 */
let flushSequence = 0;
const latestSequenceByProductKey = new Map<string, number>();

function productKey(siteId: string, productId: string): string {
  return `${siteId}:${productId}`;
}

/** Claims the next sequence number for every id in `ids`, before any request is issued. */
function claimSequence(siteId: string, ids: string[]): number {
  const seq = ++flushSequence;
  for (const id of ids) {
    latestSequenceByProductKey.set(productKey(siteId, id), seq);
  }
  return seq;
}

/**
 * Pure merge step shared by every full-refresh writer (follow-up review, second round): given
 * the current cache (`old`), this flush's own sequence number, and `fetched` (the full site
 * totals response), returns the array to write. Two directions matter:
 * <p>
 * - An id present in `fetched` but claimed by a strictly newer flush since this one started:
 *   keep whatever is currently cached for it (that newer flush already wrote the authoritative
 *   value) instead of regressing to this older response.
 * - An id present in `old` but ABSENT from `fetched`: the previous round treated this
 *   unconditionally as "the product was deleted, drop it" - but a product that's newer than
 *   this flush (e.g. just created, cached by a targeted flush that started after this full read
 *   did) is *also* absent from this read's response, since the read is a snapshot that predates
 *   it. Only drop an absent id when nothing newer than this flush owns it; otherwise preserve
 *   the newer-owned entry rather than silently deleting it.
 */
function mergeFullTotals(
  siteId: string,
  seq: number,
  old: SiteInventoryTotal[],
  fetched: SiteInventoryTotal[]
): SiteInventoryTotal[] {
  const oldById = new Map(old.map((t) => [t.productId, t]));
  const fetchedIds = new Set(fetched.map((t) => t.productId));
  const result = new Map<string, SiteInventoryTotal>();

  for (const [id, current] of oldById) {
    if (fetchedIds.has(id)) continue;
    const recorded = latestSequenceByProductKey.get(productKey(siteId, id));
    if (recorded !== undefined && recorded > seq) {
      result.set(id, current);
    }
    // else: genuinely absent from a read at least as current as this flush - a real deletion.
  }

  for (const total of fetched) {
    const key = productKey(siteId, total.productId);
    const recorded = latestSequenceByProductKey.get(key);
    if (recorded !== undefined && recorded > seq) {
      const current = oldById.get(total.productId);
      if (current) {
        result.set(total.productId, current);
      }
      continue;
    }
    latestSequenceByProductKey.set(key, seq);
    result.set(total.productId, total);
  }
  return Array.from(result.values());
}

/**
 * Fetches the full site totals and returns the correctly-merged result for an *already-claimed*
 * `seq` - never claims, never writes the cache itself. Kept separate from claiming so every
 * caller claims exactly once per attempt; claiming twice for the same ids (once by a caller,
 * again inside a shared helper) would mint a second, higher sequence number that could wrongly
 * supersede a genuinely concurrent flush that claimed in between the two claims.
 * <p>
 * Re-reads the cache after the fetch resolves (not the pre-fetch snapshot the caller claimed
 * against) so a concurrent write that landed while this fetch was in flight is the merge base,
 * not a stale snapshot from before this function's own await.
 */
async function fetchAndMergeFullTotals(
  queryClient: QueryClient,
  siteId: string,
  seq: number
): Promise<SiteInventoryTotal[]> {
  const fetched = await getSiteInventoryTotals(siteId);
  const current = queryClient.getQueryData<SiteInventoryTotal[]>(["inventoryTotals", siteId]) ?? [];
  return mergeFullTotals(siteId, seq, current, fetched);
}

/**
 * Claims sequence for every currently-cached id, fetches the full site totals, and returns the
 * correctly-merged result - never writing the cache itself. This is the function the real
 * `useQuery` backing this cache key (`use-product-inventory.ts`) uses as its `queryFn`, so a
 * query's own lifecycle refetches (mount, window focus, staleTime, manual refetch) are ordered
 * against explicit flushes exactly the same way an explicit full refresh is (follow-up review
 * finding, P1) - previously the query's own refetches never claimed or checked sequence at all,
 * so they could overwrite a newer targeted write unconditionally. React Query's own retry policy
 * covers a failure here; no separate recovery hook is needed for this path.
 */
export async function fetchSequencedInventoryTotals(
  queryClient: QueryClient,
  siteId: string
): Promise<SiteInventoryTotal[]> {
  const cachedIds = (queryClient.getQueryData<SiteInventoryTotal[]>(["inventoryTotals", siteId]) ?? []).map(
    (t) => t.productId
  );
  const seq = claimSequence(siteId, cachedIds);
  return fetchAndMergeFullTotals(queryClient, siteId, seq);
}

/**
 * A flush whose fetch failed must not just silently leave the cache stale with nothing to
 * correct it (follow-up review finding, P2): if flush B supersedes flush A's claim on some id
 * before A resolves, and B then fails, A's result - even if it had succeeded - is discarded by
 * design (A no longer owns that id's claim), and nothing else was scheduled to reconcile the
 * cache. Recovery only fires when the failing flush is still the *current* claim holder for at
 * least one of its ids (i.e. no even-newer flush has since superseded it too) - otherwise that
 * newer flush is already responsible for reconciling the id and a redundant recovery would just
 * race it. Recovers with a fresh, separately-claimed attempt through the same sequenced merge
 * path (follow-up review, second round) rather than a bare `invalidateQueries`, which bypassed
 * sequencing entirely.
 */
async function recoverOnFailure(queryClient: QueryClient, siteId: string, seq: number, ids: string[]): Promise<void> {
  const stillOwnsAnId = ids.some((id) => latestSequenceByProductKey.get(productKey(siteId, id)) === seq);
  if (!stillOwnsAnId) {
    return;
  }
  try {
    const recoverySeq = claimSequence(
      siteId,
      (queryClient.getQueryData<SiteInventoryTotal[]>(["inventoryTotals", siteId]) ?? []).map((t) => t.productId)
    );
    const result = await fetchAndMergeFullTotals(queryClient, siteId, recoverySeq);
    queryClient.setQueryData(["inventoryTotals", siteId], result);
  } catch {
    // Best-effort recovery; nothing else to fall back to here.
  }
}

/**
 * The "unknown IDs" path (reconnect, unknown-ID batch, or nothing cached yet to merge a
 * targeted response into) used to bypass sequencing entirely via a bare `invalidateQueries`
 * call, so it raced with targeted flushes in both directions (follow-up review finding, P1):
 * an older full read could overwrite a newer targeted write, and - because this path never
 * claimed anything - a targeted flush that started earlier but resolved later could overwrite a
 * newer full read too. Claims once, then routes through the same merge helper the real query and
 * failure recovery use.
 */
async function refreshAllInventoryTotals(queryClient: QueryClient, siteId: string): Promise<void> {
  const cachedIds = (queryClient.getQueryData<SiteInventoryTotal[]>(["inventoryTotals", siteId]) ?? []).map(
    (t) => t.productId
  );
  const seq = claimSequence(siteId, cachedIds);

  let result: SiteInventoryTotal[];
  try {
    result = await fetchAndMergeFullTotals(queryClient, siteId, seq);
  } catch (error) {
    await recoverOnFailure(queryClient, siteId, seq, cachedIds);
    throw error;
  }
  queryClient.setQueryData(["inventoryTotals", siteId], result);
}

/**
 * The shared targeted-refresh executor local mutations (`use-stock-mutations.ts`,
 * `use-location-mutations.ts`) and the realtime broadcast handler both flush through
 * (.specs/phase-6-inventory 6e, AC-7). A known-ID flush issues one or more bounded totals
 * requests (chunked to the backend's batch-size limit) and merges the result into the cache; an
 * unknown-ID flush (batch whose affected IDs weren't cheaply known, reconnect, or site switch)
 * falls back to a full, still-sequenced site-scoped refresh. Authoritative server values always
 * win for the fetched IDs - never client-side delta arithmetic - so duplicate/reordered
 * notifications converge to the same state by construction, regardless of arrival order.
 */
export async function flushInventorySiteRefresh(
  queryClient: QueryClient,
  siteId: string,
  productIds: string[] | undefined
): Promise<void> {
  if (!productIds || productIds.length === 0) {
    await refreshAllInventoryTotals(queryClient, siteId);
    return;
  }

  const existing = queryClient.getQueryData<SiteInventoryTotal[]>(["inventoryTotals", siteId]);
  if (existing === undefined) {
    // Nothing cached to merge into yet - a partial (targeted-IDs-only) array would look like
    // a complete totals list to every reader. Fall back to a full, still-sequenced fetch.
    await refreshAllInventoryTotals(queryClient, siteId);
    return;
  }

  const uniqueIds = Array.from(new Set(productIds));

  // Claim this flush's sequence number for every id it covers *before* issuing any request, so
  // a flush that starts later (even for only some of the same ids) always outranks this one for
  // those ids once both resolve, whichever settles first.
  const seq = claimSequence(siteId, uniqueIds);

  const fetchedById = new Map<string, SiteInventoryTotal>();
  try {
    for (let i = 0; i < uniqueIds.length; i += MAX_PRODUCT_IDS_PER_REQUEST) {
      const chunk = uniqueIds.slice(i, i + MAX_PRODUCT_IDS_PER_REQUEST);
      const fetched = await getSiteInventoryTotals(siteId, chunk);
      for (const total of fetched) {
        fetchedById.set(total.productId, total);
      }
    }
  } catch (error) {
    await recoverOnFailure(queryClient, siteId, seq, uniqueIds);
    throw error;
  }

  queryClient.setQueryData<SiteInventoryTotal[]>(["inventoryTotals", siteId], (old) => {
    const byId = new Map((old ?? []).map((t) => [t.productId, t]));
    for (const id of uniqueIds) {
      // A newer flush has since claimed this id (issued after this one started) - let its own
      // result win instead, whichever resolves first; applying this now-superseded response
      // would risk overwriting a fresher state with a stale one.
      if (latestSequenceByProductKey.get(productKey(siteId, id)) !== seq) {
        continue;
      }
      // Per InventoryQueries.java's documented batched-mode contract, a requested ID absent
      // from the response means quantity 0 (e.g. the product just went out of stock) - seed
      // that default before applying whatever the response actually returned, so absence is
      // honored rather than leaving the old, now-stale non-zero quantity in the cache forever.
      const candidate: SiteInventoryTotal = fetchedById.get(id) ?? { productId: id, totalQuantity: 0 };
      byId.set(id, candidate);
    }
    return Array.from(byId.values());
  });

  await Promise.all(
    uniqueIds.flatMap((id) => [
      queryClient.invalidateQueries({ queryKey: ["productInventoryEntries", siteId, id] }),
      // Legacy, unscoped two-element key (6e independent review, Required 4): the Kuji dialogs
      // (tier-edit-dialog.tsx, transfer-in-dialog.tsx, tier-draft-ui.tsx) still read
      // useProductInventoryEntries, which uses this key, not the site-qualified one. Neither
      // this executor nor the broadcast handler invalidated it after 6e's site-scoping, so
      // those dialogs would never see a targeted refresh. Kept until Kuji's own site migration
      // (Phase 7) removes this key's last caller.
      queryClient.invalidateQueries({ queryKey: ["productInventoryEntries", id] }),
      queryClient.invalidateQueries({ queryKey: ["movementHistory", siteId, id] }),
    ])
  );
}
