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

/**
 * The shared targeted-refresh executor local mutations (`use-stock-mutations.ts`,
 * `use-location-mutations.ts`) and the realtime broadcast handler both flush through
 * (.specs/phase-6-inventory 6e, AC-7). A known-ID flush issues one or more bounded totals
 * requests (chunked to the backend's batch-size limit) and merges the result into the cache; an
 * unknown-ID flush (batch whose affected IDs weren't cheaply known, reconnect, or site switch)
 * falls back to a full site-scoped invalidation. Authoritative server values always win for the
 * fetched IDs - never client-side delta arithmetic - so duplicate/reordered notifications
 * converge to the same state by construction, regardless of arrival order.
 */
export async function flushInventorySiteRefresh(
  queryClient: QueryClient,
  siteId: string,
  productIds: string[] | undefined
): Promise<void> {
  if (!productIds || productIds.length === 0) {
    await queryClient.invalidateQueries({ queryKey: ["inventoryTotals", siteId] });
    return;
  }

  const existing = queryClient.getQueryData<SiteInventoryTotal[]>(["inventoryTotals", siteId]);
  if (existing === undefined) {
    // Nothing cached to merge into yet - a partial (targeted-IDs-only) array would look like
    // a complete totals list to every reader. Fall back to a full fetch instead.
    await queryClient.invalidateQueries({ queryKey: ["inventoryTotals", siteId] });
    return;
  }

  const uniqueIds = Array.from(new Set(productIds));

  // Claim this flush's sequence number for every id it covers *before* issuing any request, so
  // a flush that starts later (even for only some of the same ids) always outranks this one for
  // those ids once both resolve, whichever settles first.
  const seq = ++flushSequence;
  for (const id of uniqueIds) {
    latestSequenceByProductKey.set(productKey(siteId, id), seq);
  }

  const fetchedById = new Map<string, SiteInventoryTotal>();
  for (let i = 0; i < uniqueIds.length; i += MAX_PRODUCT_IDS_PER_REQUEST) {
    const chunk = uniqueIds.slice(i, i + MAX_PRODUCT_IDS_PER_REQUEST);
    const fetched = await getSiteInventoryTotals(siteId, chunk);
    for (const total of fetched) {
      fetchedById.set(total.productId, total);
    }
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
