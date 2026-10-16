"use client";

import { skipToken, useQuery } from "@tanstack/react-query";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { getSiteUsers } from "@/lib/api/site-users";

export function useUsers() {
  const { siteId } = useCurrentSite();

  return useQuery({
    queryKey: ["users", "site", siteId],
    queryFn: siteId ? () => getSiteUsers(siteId) : skipToken,
  });
}
