"use client";

import { useQuery, skipToken } from "@tanstack/react-query";
import { getSiteKujiDailyPayouts } from "@/lib/api/site-kuji-boxes";
import { useCurrentSite } from "@/hooks/queries/use-current-site";

export function useKujiDailyPayouts(
  boxId: string | null | undefined,
  params: { from?: string; to?: string; tz: string }
) {
  const { siteId, isLoading: isSiteLoading, error: siteError } = useCurrentSite();

  const query = useQuery({
    queryKey: [
      "kuji-box",
      "daily-payouts",
      siteId,
      boxId,
      params.from ?? null,
      params.to ?? null,
      params.tz,
    ],
    queryFn:
      siteId && boxId ? () => getSiteKujiDailyPayouts(siteId, boxId, params) : skipToken,
  });

  return {
    ...query,
    isLoading: (Boolean(boxId) && isSiteLoading) || query.isLoading,
    error: siteError ?? query.error,
  };
}
