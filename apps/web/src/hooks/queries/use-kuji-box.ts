"use client";

import { useQuery, skipToken } from "@tanstack/react-query";
import {
  getSiteActiveKujiBox,
  getSiteKujiAllocationsByLocation,
  getSiteKujiAllocationsByProduct,
  getSiteKujiBox,
  getSiteKujiBoxHistory,
  getSiteLastClosedKujiTiers,
} from "@/lib/api/site-kuji-boxes";
import { ApiClientError } from "@/lib/api/client";
import { useCurrentSite } from "@/hooks/queries/use-current-site";

// Query keys are site-qualified (siteId is always the second element) so cache entries never
// leak across sites; every queryFn stays disabled (skipToken) until siteId resolves, matching
// useSiteProductInventoryEntries' pattern (.specs/phase-7-kuji-lootbox T-4).

export function useActiveKujiBox(productId: string | null | undefined) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["kuji-box", "active", siteId, productId],
    queryFn:
      siteId && productId ? () => getSiteActiveKujiBox(siteId, productId) : skipToken,
    retry: (_, error) => !(error instanceof ApiClientError && error.status === 404),
  });

  return {
    ...query,
    isLoading: (Boolean(productId) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}

export function useKujiBoxHistory(productId: string | null | undefined) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["kuji-box", "history", siteId, productId],
    queryFn:
      siteId && productId ? () => getSiteKujiBoxHistory(siteId, productId) : skipToken,
  });

  return {
    ...query,
    isLoading: (Boolean(productId) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}

export function useLastClosedKujiTiers(productId: string | null | undefined) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["kuji-box", "last-tiers", siteId, productId],
    queryFn:
      siteId && productId ? () => getSiteLastClosedKujiTiers(siteId, productId) : skipToken,
  });

  return {
    ...query,
    isLoading: (Boolean(productId) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}

export function useKujiBox(boxId: string | null | undefined) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["kuji-box", "detail", siteId, boxId],
    queryFn: siteId && boxId ? () => getSiteKujiBox(siteId, boxId) : skipToken,
    retry: (_, error) => !(error instanceof ApiClientError && error.status === 404),
  });

  return {
    ...query,
    isLoading: (Boolean(boxId) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}

export function useKujiAllocationsByLocation(locationId: string | null | undefined) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["kuji-box", "allocations", "by-location", siteId, locationId],
    queryFn:
      siteId && locationId
        ? () => getSiteKujiAllocationsByLocation(siteId, locationId)
        : skipToken,
  });

  return {
    ...query,
    isLoading: (Boolean(locationId) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}

export function useKujiAllocationsByProduct(productId: string | null | undefined) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: ["kuji-box", "allocations", "by-product", siteId, productId],
    queryFn:
      siteId && productId
        ? () => getSiteKujiAllocationsByProduct(siteId, productId)
        : skipToken,
  });

  return {
    ...query,
    isLoading: (Boolean(productId) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}
