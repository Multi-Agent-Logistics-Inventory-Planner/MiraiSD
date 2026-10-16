"use client";

import { skipToken, useQuery } from "@tanstack/react-query";
import { useCurrentSite } from "@/hooks/queries/use-current-site";
import { getSiteUsers, getSiteLastAudits } from "@/lib/api/site-users";
import { getPendingInvitations } from "@/lib/api/invitations";
import type { TeamMemberRow } from "@/components/team";
import type { Invitation } from "@/types/api";

export function useTeamData() {
  const { siteId, isLoading: isSiteLoading } = useCurrentSite();

  const query = useQuery({
    queryKey: ["team-data", siteId],
    queryFn: siteId ? async () => {
      const [usersData, invitationsData, lastAuditsData] = await Promise.all([
        getSiteUsers(siteId),
        getPendingInvitations().catch(() => [] as Invitation[]),
        getSiteLastAudits(siteId).catch(() => ({} as Record<string, string>)),
      ]);

      const members: TeamMemberRow[] = usersData.map((user) => ({
        id: user.id,
        fullName: user.fullName,
        email: user.email,
        role: user.role,
        status: "active" as const,
        lastAudit: lastAuditsData[user.id] ?? null,
        createdAt: user.createdAt,
        type: "member" as const,
      }));

      const invitations: TeamMemberRow[] = invitationsData.map((inv) => ({
        id: inv.id,
        fullName: "-",
        email: inv.email,
        role: inv.role,
        status: "pending" as const,
        lastAudit: null,
        createdAt: inv.invitedAt,
        type: "invitation" as const,
      }));

      return [...members, ...invitations];
    } : skipToken,
  });

  return { ...query, isLoading: query.isLoading || isSiteLoading };
}
