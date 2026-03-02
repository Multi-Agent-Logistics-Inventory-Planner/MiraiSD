"use client";

import { useQuery } from "@tanstack/react-query";
import { getUsers, getAllLastAudits } from "@/lib/api/users";
import { getPendingInvitations } from "@/lib/api/invitations";
import type { TeamMemberRow } from "@/components/team";
import type { Invitation } from "@/types/api";

export function useTeamData() {
  return useQuery({
    queryKey: ["team-data"],
    queryFn: async () => {
      const [usersData, invitationsData, lastAuditsData] = await Promise.all([
        getUsers(),
        getPendingInvitations().catch(() => [] as Invitation[]),
        getAllLastAudits().catch(() => ({} as Record<string, string>)),
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
    },
  });
}
