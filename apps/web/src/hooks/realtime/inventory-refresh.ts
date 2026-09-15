import type { QueryClient } from "@tanstack/react-query";
import { getSiteInventoryTotals, type SiteInventoryTotal } from "@/lib/api/site-inventory";

/**
 * The shared targeted-refresh executor local mutations (`use-stock-mutations.ts`,
 * `use-location-mutations.ts`) and the realtime broadcast handler both flush through
 * (.specs/phase-6-inventory 6e, AC-7). A known-ID flush issues exactly one bounded totals
 * request and merges the result into the cache; an unknown-ID flush (batch whose affected IDs
 * weren't cheaply known, reconnect, or site switch) falls back to a full site-scoped
 * invalidation. Authoritative server values always win for the fetched IDs - never client-side
 * delta arithmetic - so duplicate/reordered notifications converge to the same state by
 * construction, regardless of arrival order.
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
  const fetched = await getSiteInventoryTotals(siteId, uniqueIds);
  const fetchedById = new Map(fetched.map((t) => [t.productId, t]));

  queryClient.setQueryData<SiteInventoryTotal[]>(["inventoryTotals", siteId], (old) => {
    const byId = new Map((old ?? []).map((t) => [t.productId, t]));
    for (const id of uniqueIds) {
      // Per InventoryQueries.java's documented batched-mode contract, a requested ID absent
      // from the response means quantity 0 (e.g. the product just went out of stock) - seed
      // that default before applying whatever the response actually returned, so absence is
      // honored rather than leaving the old, now-stale non-zero quantity in the cache forever.
      const current = byId.get(id);
      const candidate: SiteInventoryTotal = fetchedById.get(id) ?? { productId: id, totalQuantity: 0 };

      // Two overlapping flushes for the same product can resolve out of order; whichever
      // setQueryData call runs last would otherwise win even if its own fetch was the older
      // one. Keep whichever of the current cache entry vs. the candidate is actually newer -
      // if either side lacks a timestamp, prefer the freshly fetched candidate.
      if (
        current?.lastUpdatedAt &&
        candidate.lastUpdatedAt &&
        current.lastUpdatedAt > candidate.lastUpdatedAt
      ) {
        continue;
      }
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
