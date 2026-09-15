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

  queryClient.setQueryData<SiteInventoryTotal[]>(["inventoryTotals", siteId], (old) => {
    const byId = new Map((old ?? []).map((t) => [t.productId, t]));
    for (const total of fetched) {
      byId.set(total.productId, total);
    }
    return Array.from(byId.values());
  });

  await Promise.all(
    uniqueIds.flatMap((id) => [
      queryClient.invalidateQueries({ queryKey: ["productInventoryEntries", siteId, id] }),
      queryClient.invalidateQueries({ queryKey: ["movementHistory", siteId, id] }),
    ])
  );
}
