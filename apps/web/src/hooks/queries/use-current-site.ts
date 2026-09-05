"use client";

import { useQuery } from "@tanstack/react-query";
import { webApiClient, unwrapGeneratedResponse } from "@/lib/api/generated-client";
import type { components } from "@mirai/api-client";

const MAIN_SITE_CODE = "MAIN";

type SiteMembershipDTO = components["schemas"]["SiteMembershipDTO"];

export interface CurrentSite {
  siteId: string;
  siteCode: string;
  siteName: string;
}

async function fetchCurrentSite(): Promise<CurrentSite | null> {
  const result = await webApiClient.GET("/api/v1/me/sites", {});
  const data = unwrapGeneratedResponse(result);

  const memberships = (data ?? []) as SiteMembershipDTO[];
  // Fail closed rather than picking an arbitrary membership: every current user is mapped
  // to MAIN only (per this feature's product decision, .specs/track-d-web-client-adoption),
  // and other parts of the Locations page still resolve to MAIN server-side via the legacy,
  // unscoped endpoints. Silently falling back to a non-MAIN membership here would mean two
  // sites' data on one screen with no signal to the user - safer to surface "unresolved"
  // (siteId undefined) than a wrong site.
  const resolved = memberships.find((membership) => membership.siteCode === MAIN_SITE_CODE);

  if (!resolved?.siteId) {
    return null;
  }

  return {
    siteId: resolved.siteId,
    siteCode: resolved.siteCode ?? "",
    siteName: resolved.siteName ?? "",
  };
}

/**
 * Silently resolves the caller's site with no switcher UI - every current user is mapped to
 * MAIN only. staleTime is effectively session-lifetime: membership doesn't change mid-session,
 * and re-fetching on every mount would add a request the legacy site-blind endpoints never
 * needed for no benefit.
 */
export function useCurrentSite() {
  const query = useQuery({
    queryKey: ["me", "sites"],
    queryFn: fetchCurrentSite,
    staleTime: Infinity,
  });

  return {
    siteId: query.data?.siteId,
    siteCode: query.data?.siteCode,
    isLoading: query.isLoading,
    error: query.error,
  };
}
