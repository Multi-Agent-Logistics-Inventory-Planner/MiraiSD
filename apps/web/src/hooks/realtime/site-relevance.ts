/**
 * A payload with no `siteId` (not yet migrated to carry one, per 6e's backend decision to
 * migrate only inventory-module broadcast producers) is treated as possibly relevant to any
 * site - the same null-is-possibly-relevant precedent T-6d-11 established for null-`site_id`
 * `stock_movements` rows. Only an explicit, differing `siteId` is dropped.
 */
export function isRelevantToCurrentSite(
  payloadSiteId: string | undefined,
  currentSiteId: string | undefined
): boolean {
  if (!payloadSiteId) {
    return true;
  }
  if (!currentSiteId) {
    return true;
  }
  return payloadSiteId === currentSiteId;
}
